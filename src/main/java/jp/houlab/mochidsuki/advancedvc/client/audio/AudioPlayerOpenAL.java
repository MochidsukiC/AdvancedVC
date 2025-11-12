package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.client.audio.vgp.AcousticPathResult;
import jp.houlab.mochidsuki.advancedvc.Config;
import jp.houlab.mochidsuki.advancedvc.client.ClientConfig;
import jp.houlab.mochidsuki.advancedvc.client.audio.vgp.VGPAcousticEngine;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.openal.AL10.*;

/**
 * OpenAL EFX対応のオーディオプレイヤー
 * Phase 1: 基本的な3D位置音響とリバーブ
 */
public class AudioPlayerOpenAL {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final OpenALManager openAL;

    // プレイヤーごとのOpenAL Source
    private final ConcurrentHashMap<UUID, PlayerSource> playerSources = new ConcurrentHashMap<>();

    // バッファプール（再利用）
    private final Queue<Integer> freeBuffers = new ConcurrentLinkedQueue<>();
    private static final int BUFFER_POOL_SIZE = 100;

    private volatile boolean running = false;
    private Thread updateThread;

    // 環境リバーブ更新用
    private long lastEnvironmentUpdateTime = 0;
    private static final long ENVIRONMENT_UPDATE_INTERVAL = 1000; // 1秒ごと

    // 現在の平均音量レベル（リバーブ調整用）
    private volatile VolumeLevel currentAverageVolumeLevel = VolumeLevel.NORMAL;

    // 現在の基準WETゲイン（環境リバーブから計算）
    private volatile float currentBaseWetGain = 0.5f;

    // 環境リバーブ設定の直近値（スムージング用）
    private volatile EFXReverbSettings lastReverbSettings = null;

    // 初期反射システム（無効化中）
    private EarlyReflectionSourcePool reflectionPool;
    private ExecutorService reflectionExecutor;
    private long lastReflectionUpdateTime = 0;
    private static final long REFLECTION_UPDATE_INTERVAL = 200; // Ray Tracing計算間隔
    private long lastReflectionSourceUpdateTime = 0;
    private static final long REFLECTION_SOURCE_UPDATE_INTERVAL = 50; // Source更新間隔（軽量化：50ms）

    // VGPシステム（Voxel-Graph Pathfinding）
    private VGPAcousticEngine vgpEngine;

    /**
     * コンストラクタ
     */
    public AudioPlayerOpenAL() {
        this.openAL = new OpenALManager();
    }

    /**
     * プレイヤーを開始
     */
    public void start() {
        if (running) {
            LOGGER.warn("AudioPlayerOpenAL is already running");
            return;
        }

        // OpenAL初期化
        if (!openAL.initialize()) {
            LOGGER.error("Failed to initialize OpenAL");
            return;
        }

        // バッファプールを作成
        for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
            int bufferId = alGenBuffers();
            if (bufferId != 0) {
                freeBuffers.offer(bufferId);
            }
        }

        // 初期反射システムを初期化（完全無効化：パフォーマンス問題により）
        // reflectionPool = new EarlyReflectionSourcePool(openAL);
        // reflectionExecutor = Executors.newSingleThreadExecutor(r -> {
        //     Thread t = new Thread(r, "EarlyReflection-Tracer");
        //     t.setDaemon(true);
        //     return t;
        // });

        // VGPシステムを初期化
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            vgpEngine = new VGPAcousticEngine(mc.level);
            LOGGER.info("VGP Acoustic Engine initialized");
        } else {
            LOGGER.warn("Cannot initialize VGP: level is null");
        }

        running = true;

        // 更新スレッド開始
        updateThread = new Thread(this::updateLoop, "AudioPlayerOpenAL-Update");
        updateThread.setDaemon(true);
        updateThread.start();

        LOGGER.info("AudioPlayerOpenAL started (EFX: {}, Reflections: enabled)", openAL.isEfxSupported() ? "enabled" : "disabled");
    }

    /**
     * プレイヤーを停止
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (updateThread != null) {
            try {
                updateThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 全てのSourceを削除
        playerSources.forEach((playerId, source) -> {
            source.stop();
            openAL.deleteSource(source.sourceId);
        });
        playerSources.clear();

        // 初期反射システムをシャットダウン
        if (reflectionExecutor != null) {
            reflectionExecutor.shutdownNow();
            reflectionExecutor = null;
        }
        if (reflectionPool != null) {
            reflectionPool.shutdown();
            reflectionPool = null;
        }

        // VGPシステムをシャットダウン
        if (vgpEngine != null) {
            vgpEngine.shutdown();
            vgpEngine = null;
        }

        // バッファプールをクリア
        while (!freeBuffers.isEmpty()) {
            Integer bufferId = freeBuffers.poll();
            if (bufferId != null) {
                openAL.deleteBuffer(bufferId);
            }
        }

        // OpenALシャットダウン
        openAL.shutdown();

        LOGGER.info("AudioPlayerOpenAL stopped");
    }

    /**
     * ポジショナル音声を追加
     * @param playerId プレイヤーUUID
     * @param samples PCMサンプル（モノラル16bit）
     * @param position 3D位置
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position) {
        addPositionalAudio(playerId, samples, position, VolumeLevel.NORMAL);
    }

    /**
     * ポジショナル音声を追加（音量レベル指定版）
     * @param playerId プレイヤーUUID
     * @param samples PCMサンプル（モノラル16bit）
     * @param position 3D位置
     * @param volumeLevel 音量レベル
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position, VolumeLevel volumeLevel) {
        if (!running) {
            return;
        }

        // デバッグログ：音声受信を確認（1秒に1回程度）
        if (System.currentTimeMillis() % 1000 < 50) {
            LOGGER.info("addPositionalAudio called: playerId={}, position={}, volumeLevel={}, samples={}",
                        playerId, position, volumeLevel, samples.length);
        }

        // PlayerSourceを取得または作成
        PlayerSource source = playerSources.computeIfAbsent(playerId, id -> {
            int sourceId = openAL.createSource();
            if (sourceId == 0) {
                LOGGER.error("Failed to create OpenAL source for player {}", id);
                return null;
            }
            PlayerSource newSource = new PlayerSource(sourceId);
            newSource.playerId = id;
            LOGGER.info("Created new PlayerSource: playerId={}, sourceId={}", id, sourceId);
            return newSource;
        });

        if (source == null) {
            LOGGER.error("PlayerSource is null for player {}", playerId);
            return;
        }

        // 位置と音量レベルを更新
        source.position = position;
        source.volumeLevel = volumeLevel;

        // サンプルをキューに追加
        source.sampleQueue.offer(samples);

        // キューが溜まりすぎた場合は古いデータを削除
        while (source.sampleQueue.size() > 10) {
            source.sampleQueue.poll();
        }

        // 平均音量レベルを更新（リバーブ計算用）
        updateAverageVolumeLevel();

        // 初期反射Sourceにも同じサンプルを追加（完全無効化）
        // if (reflectionPool != null) {
        //     reflectionPool.addSamplesToReflections(playerId, samples);
        // }
    }

    /**
     * 全プレイヤーの平均音量レベルを計算
     */
    private void updateAverageVolumeLevel() {
        if (playerSources.isEmpty()) {
            currentAverageVolumeLevel = VolumeLevel.NORMAL;
            return;
        }

        int totalOrdinal = 0;
        int count = 0;
        for (PlayerSource source : playerSources.values()) {
            if (source.volumeLevel != null) {
                totalOrdinal += source.volumeLevel.ordinal();
                count++;
            }
        }

        if (count > 0) {
            int avgOrdinal = totalOrdinal / count;
            VolumeLevel[] levels = VolumeLevel.values();
            currentAverageVolumeLevel = levels[Math.min(avgOrdinal, levels.length - 1)];
        }
    }

    /**
     * プレイヤーのSourceを削除
     */
    public void removePlayerSource(UUID playerId) {
        PlayerSource source = playerSources.remove(playerId);
        if (source != null) {
            source.stop();
            openAL.deleteSource(source.sourceId);
        }
    }

    /**
     * 更新ループ
     * Listenerの位置更新とSourceのバッファ管理
     */
    private void updateLoop() {
        while (running) {
            try {
                // Listenerの位置と向きを更新
                updateListener();

        // グローバル環境リバーブ更新は無効化（発言者ごとのリバーブに移行）

                // 各プレイヤーのSourceを更新
                playerSources.forEach((playerId, source) -> {
                    updatePlayerSource(source);
                });

                // 初期反射Sourceを更新（完全無効化）
                // long reflectionTime = System.currentTimeMillis();
                // if (reflectionPool != null &&
                //     reflectionTime - lastReflectionSourceUpdateTime > REFLECTION_SOURCE_UPDATE_INTERVAL) {
                //     reflectionPool.updateAllSources();
                //     lastReflectionSourceUpdateTime = reflectionTime;
                // }

                // 20ms待機
                Thread.sleep(20);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    LOGGER.error("Error in update loop", e);
                }
            }
        }
    }

    /**
     * Listenerの位置と向きを更新
     */
    private void updateListener() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // リスナー位置（プレイヤーの頭の位置）
        Vec3 pos = mc.player.position().add(0, 1.6, 0);
        openAL.setListenerPosition((float) pos.x, (float) pos.y, (float) pos.z);

        // リスナーの向き
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        // 前方ベクトル（Minecraft座標系: +Z=南, -Z=北, +X=東, -X=西）
        float atX = (float) (-Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        float atY = (float) (-Math.sin(Math.toRadians(pitch)));
        float atZ = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));

        // 上方ベクトル
        float upX = 0;
        float upY = 1;
        float upZ = 0;

        openAL.setListenerOrientation(atX, atY, atZ, upX, upY, upZ);
    }

    /**
     * 環境リバーブを更新
     * シンプルな環境解析でリバーブパラメータを動的に変更
     */
    private void updateEnvironmentReverb() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // リスナー位置
        Vec3 listenerPos = mc.player.position().add(0, 1.6, 0);

        // レイベース環境スキャン（ローカルの声量に応じて最大レイ距離を設定）
        VolumeLevel localVolume = getLocalVolumeLevel();
        float maxDist = localVolume.getMaxDistance();
        int rayCount = (maxDist <= 15f) ? 48 : (maxDist <= 40f ? 80 : 112);
        // 秒ごとにシードを変化させてジッタを与える
        long seed = System.currentTimeMillis() / 1000L;
        RayEnvironmentScanner.ScanResult scan;
        try {
            scan = RayEnvironmentScanner.scan(mc.level, listenerPos, maxDist, rayCount, 1.0f, seed);
        } catch (Exception e) {
            // フォールバック：従来の球状サンプリングで最低限の値を生成
            LOGGER.warn("Ray scan failed, fallback to legacy analyzer", e);
            EnvironmentAnalyzer.EnvironmentInfo env = EnvironmentAnalyzer.analyzeEnvironment(mc.level, listenerPos);
            EFXReverbSettings legacy = EnvironmentAnalyzer.createReverbSettings(env);
            // フォールバック時の最小限出力
            currentBaseWetGain = legacy.lateReverbGain;
            openAL.updateReverb(legacy);
            return;
        }

        // デバッグログ: スキャン結果
        LOGGER.info("RayScan: vol={}, maxDist={}, openness={}, hitRatio={}, meanHitDist={}, minHitDist={}, absorptionMean={}, volumeEst={}",
                localVolume.name(),
                String.format("%.1f", maxDist),
                String.format("%.2f", scan.openness),
                String.format("%.2f", scan.hitRatio),
                String.format("%.1f", scan.meanHitDistance),
                String.format("%.1f", scan.minHitDistance),
                String.format("%.2f", scan.absorptionMean),
                String.format("%.0f", scan.volumeEstimate));

        // レイ結果からEFX設定を生成
        EFXReverbSettings settings = EnvironmentAnalyzer.createReverbSettingsFromScan(scan);

        // 大声時の屋外山びこは、開放度が非常に高い場合のみわずかに強化
        if (currentAverageVolumeLevel == VolumeLevel.LOUD || currentAverageVolumeLevel == VolumeLevel.SHOUT) {
            if (scan.openness > 0.85f) {
                settings.decayTime = Math.min(1.2f, Math.max(0.4f, settings.decayTime * 1.5f));
                settings.lateReverbGain = Math.min(0.5f, settings.lateReverbGain * 1.3f);
            }
        }

        // 軽いスムージング（EMA的に前回値と補間）
        if (lastReverbSettings != null) {
            settings = EFXReverbSettings.lerp(lastReverbSettings, settings, 0.3f);
        }
        lastReverbSettings = settings.copy();

        // 最終ログ
        LOGGER.info("Reverb: decayTime={}, density={}, diffusion={}, lateReverbGain={}",
                settings.decayTime, settings.density, settings.diffusion, settings.lateReverbGain);

        // 基準WETゲインを保存（各Sourceの距離ベース調整で使用）
        currentBaseWetGain = settings.lateReverbGain;

        // OpenALのリバーブパラメータを更新
        openAL.updateReverb(settings);
    }

    private VolumeLevel getLocalVolumeLevel() {
        try {
            String v = ClientConfig.get().volumeLevel;
            if (v != null) {
                return VolumeLevel.valueOf(v);
            }
        } catch (Exception ignored) {}
        return VolumeLevel.NORMAL;
    }

    /**
     * PlayerSourceを更新
     */
    private void updatePlayerSource(PlayerSource source) {
        if (source == null || source.sourceId == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // デバッグログ：更新開始（1秒に1回程度）
        if (System.currentTimeMillis() % 1000 < 50) {
            LOGGER.info("updatePlayerSource: sourceId={}, position={}, reverbInit={}",
                        source.sourceId, source.position, source.reverbInitialized);
        }

        // 位置を更新
        if (source.position != null) {
            openAL.setSourcePosition(
                    source.sourceId,
                    (float) source.position.x,
                    (float) source.position.y + 1.6f, // 口の高さ
                    (float) source.position.z
            );

            // VolumeLevelに応じた距離減衰パラメータを更新
            if (source.volumeLevel != null) {
                openAL.setSourceDistanceModel(source.sourceId, source.volumeLevel.getMaxDistance());
            }

            Vec3 listenerPos = mc.player.position().add(0, 1.6, 0); // 耳の高さ
            Vec3 sourcePos = source.position.add(0, 1.6, 0); // 口の高さ

            // 初期反射を更新（完全無効化）
            // long currentTime = System.currentTimeMillis();
            // if (reflectionPool != null && reflectionExecutor != null &&
            //     currentTime - lastReflectionUpdateTime > REFLECTION_UPDATE_INTERVAL) {
            //     updateEarlyReflectionsAsync(source, mc.level, sourcePos, listenerPos);
            //     lastReflectionUpdateTime = currentTime;
            // }

            // VGPで音響パスを計算（伝播・回折・吸収）
            if (vgpEngine != null) {
                AcousticPathResult vgpResult = vgpEngine.calculatePath(sourcePos, listenerPos);

                // VGP結果をOpenALフィルタに適用
                // totalGain: 距離減衰 + 回折減衰 + 吸収減衰の総合ゲイン
                // lowpassCutoff: 回折と吸収による高周波カットオフ
                float gainHF = vgpResult.filterGain;  // 高周波ゲイン（0.0-1.0）

                openAL.setSourceOcclusion(source.sourceId, vgpResult.totalGain, gainHF);

                // デバッグログ（頻度制御）
                if (Config.debugMode && System.currentTimeMillis() % 1000 < 50) {
                    LOGGER.debug("VGP: {}", vgpResult);
                }
            } else {
                // VGPが無効な場合はフォールバック（減衰なし）
                openAL.setSourceOcclusion(source.sourceId, 1.0f, 1.0f);
            }

            // 発言者ごとの環境リバーブ（レイスキャンは発言者位置・声量で実行）
            // 頻度制御：200ms間隔、または初回（reverbInitialized = false）
            long currentTime = System.currentTimeMillis();
            boolean shouldUpdateReverb = !source.reverbInitialized ||
                                        (currentTime - source.lastReverbUpdateTime > 200);

            // デバッグログ（初回または1秒に1回）
            if (!source.reverbInitialized || System.currentTimeMillis() % 1000 < 50) {
                LOGGER.info("Reverb update check: sourceId={}, shouldUpdate={}, reverbInit={}, timeSinceLastUpdate={}",
                            source.sourceId, shouldUpdateReverb, source.reverbInitialized,
                            currentTime - source.lastReverbUpdateTime);
            }

            if (shouldUpdateReverb) {
                LOGGER.info("Starting reverb update for source {}", source.sourceId);
                try {
                    float maxDist = source.volumeLevel != null ? source.volumeLevel.getMaxDistance() : VolumeLevel.NORMAL.getMaxDistance();
                    long seed = currentTime / 1000L;
                    int rayCount = (maxDist <= 15f) ? 48 : (maxDist <= 40f ? 80 : 112);

                    LOGGER.info("Executing ray scan: maxDist={}, rayCount={}", maxDist, rayCount);
                    RayEnvironmentScanner.ScanResult scan = RayEnvironmentScanner.scan(mc.level, sourcePos, maxDist, rayCount, 1.0f, seed);
                    LOGGER.info("Ray scan completed: openness={}, hitRatio={}", scan.openness, scan.hitRatio);

                    EFXReverbSettings settings = EnvironmentAnalyzer.createReverbSettingsFromScan(scan);
                    LOGGER.info("Reverb settings created: decayTime={}, lateReverbGain={}",
                                settings.decayTime, settings.lateReverbGain);

                    // Source専用のエフェクトスロットに適用
                    LOGGER.info("Applying reverb to source {}", source.sourceId);
                    openAL.updateSourceReverb(source.sourceId, settings);

                    // このSourceの基準WET（距離WETのベース）
                    float baseWet = settings.lateReverbGain;
                    // 距離に応じたリバーブWETゲインを調整
                    float distance = (float) listenerPos.distanceTo(sourcePos);
                    openAL.setSourceReverbGain(source.sourceId, distance, baseWet);

                    // 更新成功
                    source.lastReverbUpdateTime = currentTime;
                    source.reverbInitialized = true;
                    LOGGER.info("Reverb update completed successfully for source {}", source.sourceId);

                } catch (Exception e) {
                    // 例外をログに出力
                    LOGGER.error("Failed to update reverb for source {}, using default settings", source.sourceId, e);

                    // デフォルトリバーブ設定を適用してAUX送信を初期化
                    try {
                        EFXReverbSettings defaultSettings = EFXReverbSettings.mediumRoom();
                        openAL.updateSourceReverb(source.sourceId, defaultSettings);

                        float distance = (float) listenerPos.distanceTo(sourcePos);
                        openAL.setSourceReverbGain(source.sourceId, distance, defaultSettings.lateReverbGain);

                        source.reverbInitialized = true; // 初期化完了とマーク
                        LOGGER.info("Applied default reverb settings for source {}", source.sourceId);

                    } catch (Exception e2) {
                        LOGGER.error("Failed to apply default reverb settings", e2);
                    }
                }
            } else {
                // レイスキャンはスキップするが、距離ベースWET調整は毎フレーム更新
                if (source.reverbInitialized) {
                    float distance = (float) listenerPos.distanceTo(sourcePos);
                    openAL.setSourceReverbGain(source.sourceId, distance, currentBaseWetGain);
                }
            }
        }

        // 処理済みバッファを回収
        java.util.List<Integer> processed = openAL.unqueueBuffers(source.sourceId);
        for (Integer bufferId : processed) {
            freeBuffers.offer(bufferId); // プールに戻す
        }

        // 新しいサンプルをバッファに追加
        while (!source.sampleQueue.isEmpty()) {
            Integer bufferId = freeBuffers.poll();
            if (bufferId == null) {
                // バッファプールが枯渇
                break;
            }

            short[] samples = source.sampleQueue.poll();
            if (samples == null) {
                freeBuffers.offer(bufferId); // 使わなかったので戻す
                break;
            }

            // バッファにデータをロード
            alBufferData(bufferId, AL_FORMAT_MONO16,
                    samples, AudioConstants.SAMPLE_RATE);

            // Sourceにキューイング
            openAL.queueBuffer(source.sourceId, bufferId);
        }

        // Sourceを再生（停止していれば開始）
        int state = alGetSourcei(source.sourceId, AL_SOURCE_STATE);
        if (state != AL_PLAYING) {
            openAL.playSource(source.sourceId);
        }
    }

    /**
     * 初期反射を非同期で更新
     */
    private void updateEarlyReflectionsAsync(PlayerSource source, net.minecraft.world.level.Level level,
                                             Vec3 sourcePos, Vec3 listenerPos) {
        if (source.playerId == null) {
            return;
        }

        final UUID playerId = source.playerId;
        double distance = listenerPos.distanceTo(sourcePos);

        // LOD判定（距離に応じて音線数を調整）
        int numRays = distance < 15.0 ? 512 : (distance < 30.0 ? 256 : 0);
        if (numRays == 0) {
            return;  // 遠距離は初期反射省略
        }

        final int finalNumRays = numRays;

        // 非同期Ray Tracing
        reflectionExecutor.submit(() -> {
            try {
                EarlyReflectionTracer.EarlyReflectionResult result =
                        EarlyReflectionTracer.traceEarlyReflections(level, sourcePos, listenerPos, finalNumRays);

                // メインスレッドで適用
                Minecraft.getInstance().execute(() -> {
                    if (running && reflectionPool != null) {
                        reflectionPool.assignReflections(playerId, result.reflections);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Early reflection calculation failed", e);
            }
        });
    }

    /**
     * PlayerSourceクラス
     */
    private static class PlayerSource {
        UUID playerId;  // NEW: プレイヤーIDを追加
        final int sourceId;
        Vec3 position;
        VolumeLevel volumeLevel = VolumeLevel.NORMAL;
        final ConcurrentLinkedQueue<short[]> sampleQueue = new ConcurrentLinkedQueue<>();
        long lastReverbUpdateTime = 0; // リバーブ更新時刻（レイスキャン頻度制御用）
        boolean reverbInitialized = false; // リバーブが初期化されたか

        PlayerSource(int sourceId) {
            this.sourceId = sourceId;
        }

        void stop() {
            if (sourceId != 0) {
                alSourceStop(sourceId);
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * OpenAL Managerを取得（高度な設定用）
     */
    public OpenALManager getOpenALManager() {
        return openAL;
    }

    /**
     * 非ポジショナル音声を追加（ウォーキートーキー用）
     * TODO: Phase 2で実装
     */
    public void addNonPositionalAudio(short[] samples) {
        LOGGER.warn("Non-positional audio not yet implemented in OpenAL version");
    }

    /**
     * 出力ゲインを設定
     */
    public void setOutputGain(double gain) {
        // OpenALのマスターゲイン設定
        if (running) {
            alListenerf(AL_GAIN, (float) Math.max(0.0, Math.min(1.0, gain)));
        }
    }

    /**
     * 優先ミキサー名を設定（OpenAL版では使用しない）
     */
    public void setPreferredMixerName(String mixerName) {
        // OpenALはデフォルトデバイスを使用
        LOGGER.info("Preferred mixer name setting ignored in OpenAL version");
    }
}
