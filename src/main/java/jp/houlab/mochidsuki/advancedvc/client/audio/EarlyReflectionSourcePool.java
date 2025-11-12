package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTEfx.*;

/**
 * 初期反射音用のSourceプール管理
 * 全プレイヤーで32個のSourceを共有し、優先度に基づいて動的割り当て
 */
public class EarlyReflectionSourcePool {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int POOL_SIZE = 8;  // 全体で8個のSource（軽量化）

    private final OpenALManager openAL;
    private final List<ReflectionSource> sourcePool;
    private final ConcurrentHashMap<UUID, List<ReflectionSource>> playerAssignments;

    /**
     * 反射音Source
     */
    public static class ReflectionSource {
        public int sourceId;                    // OpenAL Source ID
        public UUID assignedPlayerId;           // 割り当て先プレイヤー
        public ReflectionData reflectionData;   // 反射音データ
        public float priority;                  // 優先度スコア
        public long lastUpdateTime;             // 最終更新時刻
        public DelayBuffer delayBuffer;         // 遅延バッファ
        public int filterId;                    // OpenAL Filter ID（周波数特性用）

        public ReflectionSource(int sourceId) {
            this.sourceId = sourceId;
            this.assignedPlayerId = null;
            this.reflectionData = null;
            this.priority = 0.0f;
            this.lastUpdateTime = 0;
            this.delayBuffer = null;
            this.filterId = 0;
        }
    }

    /**
     * コンストラクタ
     */
    public EarlyReflectionSourcePool(OpenALManager openAL) {
        this.openAL = openAL;
        this.sourcePool = new ArrayList<>(POOL_SIZE);
        this.playerAssignments = new ConcurrentHashMap<>();

        // Sourceプールを初期化
        for (int i = 0; i < POOL_SIZE; i++) {
            int sourceId = openAL.createSource();
            if (sourceId != 0) {
                ReflectionSource source = new ReflectionSource(sourceId);

                // Filterを作成（周波数特性用）
                source.filterId = alGenFilters();
                if (source.filterId != 0) {
                    alFilteri(source.filterId, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                }

                sourcePool.add(source);
            }
        }

        LOGGER.info("Early Reflection Source Pool initialized with {} sources", sourcePool.size());
    }

    /**
     * プレイヤーの初期反射音を割り当て
     * @param playerId プレイヤーUUID
     * @param reflections 反射音リスト
     */
    public void assignReflections(UUID playerId, List<ReflectionData> reflections) {
        if (reflections == null || reflections.isEmpty()) {
            return;
        }

        // リスナー位置を取得
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 listenerPos = mc.player.position().add(0, 1.6, 0);

        // 各反射音の優先度を計算
        for (ReflectionData ref : reflections) {
            double distance = listenerPos.distanceTo(ref.reflectionPoint);
            ref.calculatePriority(distance);
        }

        // 優先度でソート（降順）
        reflections.sort(Comparator.comparingDouble((ReflectionData r) -> r.priority).reversed());

        // 既存の割り当てを解除
        releasePlayerSources(playerId);

        // 新しい反射音を割り当て
        List<ReflectionSource> assigned = new ArrayList<>();
        for (ReflectionData ref : reflections) {
            ReflectionSource source = findAvailableSource(ref.priority);
            if (source != null) {
                assignSourceToReflection(source, playerId, ref);
                assigned.add(source);
            }
        }

        playerAssignments.put(playerId, assigned);

        LOGGER.debug("Assigned {} reflection sources to player {}", assigned.size(), playerId);
    }

    /**
     * 利用可能なSourceを探す
     * 優先度が低いSourceを置き換える
     */
    private ReflectionSource findAvailableSource(float priority) {
        ReflectionSource lowestPrioritySource = null;
        float lowestPriority = Float.MAX_VALUE;

        for (ReflectionSource source : sourcePool) {
            if (source.assignedPlayerId == null) {
                // 未割り当て
                return source;
            }

            if (source.priority < lowestPriority) {
                lowestPriority = source.priority;
                lowestPrioritySource = source;
            }
        }

        // 新しい反射音の方が優先度が高ければ置き換え
        if (lowestPrioritySource != null && priority > lowestPriority) {
            return lowestPrioritySource;
        }

        return null;  // 空きがない
    }

    /**
     * Sourceに反射音を割り当て
     */
    private void assignSourceToReflection(ReflectionSource source, UUID playerId, ReflectionData ref) {
        // 既存の割り当てを解除
        if (source.assignedPlayerId != null) {
            List<ReflectionSource> oldAssignments = playerAssignments.get(source.assignedPlayerId);
            if (oldAssignments != null) {
                oldAssignments.remove(source);
            }
        }

        // 新しい割り当て
        source.assignedPlayerId = playerId;
        source.reflectionData = ref;
        source.priority = ref.priority;
        source.lastUpdateTime = System.currentTimeMillis();

        // 遅延バッファを作成
        source.delayBuffer = new DelayBuffer(ref.delayMs);

        // Source位置を設定
        openAL.setSourcePosition(
                source.sourceId,
                (float) ref.reflectionPoint.x,
                (float) ref.reflectionPoint.y,
                (float) ref.reflectionPoint.z
        );

        // 距離モデルを設定（反射音は距離減衰なし、ゲインで制御）
        alSourcef(source.sourceId, AL_REFERENCE_DISTANCE, 1.0f);
        alSourcef(source.sourceId, AL_MAX_DISTANCE, 100.0f);
        alSourcef(source.sourceId, AL_ROLLOFF_FACTOR, 0.0f);  // 距離減衰なし

        // ゲインを設定
        alSourcef(source.sourceId, AL_GAIN, ref.gain);

        // 周波数特性をFilterに設定
        if (source.filterId != 0) {
            float gainHF = ref.gain4000Hz / Math.max(0.01f, ref.gain1000Hz);
            alFilterf(source.filterId, AL_LOWPASS_GAIN, 1.0f);
            alFilterf(source.filterId, AL_LOWPASS_GAINHF, gainHF);

            // FilterをSourceに適用
            alSourcei(source.sourceId, AL_DIRECT_FILTER, source.filterId);
        }
    }

    /**
     * プレイヤーの音声サンプルを反射Sourceに追加
     * @param playerId プレイヤーUUID
     * @param samples 音声サンプル
     */
    public void addSamplesToReflections(UUID playerId, short[] samples) {
        List<ReflectionSource> sources = playerAssignments.get(playerId);
        if (sources == null || sources.isEmpty()) {
            return;
        }

        for (ReflectionSource source : sources) {
            if (source.delayBuffer != null) {
                source.delayBuffer.addSamples(samples);
            }
        }
    }

    /**
     * 全Sourceの位置とバッファを更新
     */
    public void updateAllSources() {
        for (ReflectionSource source : sourcePool) {
            if (source.assignedPlayerId == null || source.delayBuffer == null) {
                continue;
            }

            // 遅延されたサンプルを取得
            short[] delayedSamples = source.delayBuffer.getDelayedSamples();
            if (delayedSamples != null) {
                // バッファを作成してキューイング
                int bufferId = alGenBuffers();
                alBufferData(bufferId, AL_FORMAT_MONO16, delayedSamples, jp.houlab.mochidsuki.advancedvc.common.AudioConstants.SAMPLE_RATE);
                alSourceQueueBuffers(source.sourceId, bufferId);

                // Source再生（停止していれば開始）
                int state = alGetSourcei(source.sourceId, AL_SOURCE_STATE);
                if (state != AL_PLAYING) {
                    alSourcePlay(source.sourceId);
                }
            }

            // 処理済みバッファを削除
            int processed = alGetSourcei(source.sourceId, AL_BUFFERS_PROCESSED);
            if (processed > 0) {
                int[] buffers = new int[processed];
                alSourceUnqueueBuffers(source.sourceId, buffers);
                for (int bufferId : buffers) {
                    alDeleteBuffers(bufferId);
                }
            }
        }
    }

    /**
     * プレイヤーのSourceを解放
     * @param playerId プレイヤーUUID
     */
    public void releasePlayerSources(UUID playerId) {
        List<ReflectionSource> sources = playerAssignments.remove(playerId);
        if (sources == null) {
            return;
        }

        for (ReflectionSource source : sources) {
            // Sourceを停止
            alSourceStop(source.sourceId);

            // バッファをクリア
            if (source.delayBuffer != null) {
                source.delayBuffer.clear();
            }

            // 割り当て解除
            source.assignedPlayerId = null;
            source.reflectionData = null;
            source.priority = 0.0f;
            source.delayBuffer = null;
        }

        LOGGER.debug("Released reflection sources for player {}", playerId);
    }

    /**
     * プールをシャットダウン
     */
    public void shutdown() {
        for (ReflectionSource source : sourcePool) {
            alSourceStop(source.sourceId);
            openAL.deleteSource(source.sourceId);

            if (source.filterId != 0) {
                alDeleteFilters(source.filterId);
            }
        }

        sourcePool.clear();
        playerAssignments.clear();

        LOGGER.info("Early Reflection Source Pool shutdown");
    }
}
