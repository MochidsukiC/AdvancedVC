package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio.NativeLibraryLoader;
import jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio.SteamAudioLibrary;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Steam Audioを使用したオーディオ再生システム
 * Phase 1: 基本的なバイノーラル再生のみ
 */
public class AudioPlayerSteamAudio {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Steam Audioリソース
    private Pointer context = null;
    private Pointer hrtf = null;
    private final ConcurrentHashMap<UUID, PlayerAudioSource> playerSources = new ConcurrentHashMap<>();

    // 出力システム
    private SourceDataLine outputLine;
    private Thread playbackThread;
    private volatile boolean running = false;
    private volatile double outputGain = 1.0;

    // オーディオフォーマット（ステレオ出力）
    private final AudioFormat audioFormat = new AudioFormat(
            AudioConstants.SAMPLE_RATE,
            16,
            2,  // ステレオ出力
            true,
            false
    );

    /**
     * プレイヤー音源
     */
    private class PlayerAudioSource {
        Vec3 position;
        final LinkedBlockingQueue<short[]> audioQueue = new LinkedBlockingQueue<>(50);
        Pointer binauralEffect = null;

        // Steam Audioバッファ（iplAudioBufferAllocate使用）
        // ネイティブメモリへの直接ポインタを保持
        Pointer inBufferPtr = null;
        Pointer outBufferPtr = null;

        PlayerAudioSource(Pointer binauralEffect) {
            this.binauralEffect = binauralEffect;
            allocateBuffers();
        }

        private void allocateBuffers() {
            try {
                // 入力バッファ（モノラル）- iplAudioBufferAllocateを使用
                // ネイティブメモリを確保（IPLAudioBuffer構造体用）
                inBufferPtr = new com.sun.jna.Memory(16);  // 16バイト（int+int+Pointer）
                int result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(
                    context,
                    1,  // モノラル
                    AudioConstants.FRAME_SIZE,
                    inBufferPtr
                );

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to allocate input buffer. Error code: {}", result);
                    return;
                }

                // 構造体の値を確認（デバッグ用）
                int inChannels = inBufferPtr.getInt(0);
                int inSamples = inBufferPtr.getInt(4);
                Pointer inDataPtr = inBufferPtr.getPointer(8);
                LOGGER.info("Input buffer allocated: channels={}, samples={}, data={}", inChannels, inSamples, inDataPtr);

                if (inDataPtr == null || Pointer.nativeValue(inDataPtr) == 0) {
                    LOGGER.error("CRITICAL: Input buffer data pointer is NULL after iplAudioBufferAllocate!");
                }

                // 出力バッファ（ステレオ）- iplAudioBufferAllocateを使用
                // ネイティブメモリを確保（IPLAudioBuffer構造体用）
                outBufferPtr = new com.sun.jna.Memory(16);  // 16バイト（int+int+Pointer）
                result = SteamAudioLibrary.INSTANCE.iplAudioBufferAllocate(
                    context,
                    2,  // ステレオ
                    AudioConstants.FRAME_SIZE,
                    outBufferPtr
                );

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to allocate output buffer. Error code: {}", result);
                    return;
                }

                // 構造体の値を確認（デバッグ用）
                int outChannels = outBufferPtr.getInt(0);
                int outSamples = outBufferPtr.getInt(4);
                Pointer outDataPtr = outBufferPtr.getPointer(8);
                LOGGER.info("Output buffer allocated: channels={}, samples={}, data={}", outChannels, outSamples, outDataPtr);

                if (outDataPtr == null || Pointer.nativeValue(outDataPtr) == 0) {
                    LOGGER.error("CRITICAL: Output buffer data pointer is NULL after iplAudioBufferAllocate!");
                }

                LOGGER.info("Allocated Steam Audio buffers successfully using iplAudioBufferAllocate");

            } catch (Exception e) {
                LOGGER.error("Failed to allocate Steam Audio buffers", e);
            }
        }

        void cleanup() {
            // バッファのクリーンアップ - iplAudioBufferFreeを使用
            if (inBufferPtr != null) {
                try {
                    SteamAudioLibrary.INSTANCE.iplAudioBufferFree(context, inBufferPtr);
                    LOGGER.debug("Freed input buffer");
                } catch (Exception e) {
                    LOGGER.error("Failed to free input buffer", e);
                }
                inBufferPtr = null;
            }

            if (outBufferPtr != null) {
                try {
                    SteamAudioLibrary.INSTANCE.iplAudioBufferFree(context, outBufferPtr);
                    LOGGER.debug("Freed output buffer");
                } catch (Exception e) {
                    LOGGER.error("Failed to free output buffer", e);
                }
                outBufferPtr = null;
            }

            // エフェクトのクリーンアップ
            if (binauralEffect != null) {
                try {
                    PointerByReference effectRef = new PointerByReference(binauralEffect);
                    SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
                    LOGGER.debug("Released binaural effect for player");
                } catch (Exception e) {
                    LOGGER.error("Failed to release binaural effect", e);
                }
                binauralEffect = null;
            }
        }
    }

    /**
     * Steam Audioプレイヤーを開始
     */
    public synchronized void start() {
        if (running) {
            LOGGER.warn("Steam Audio player is already running");
            return;
        }

        try {
            // Step 1: ネイティブライブラリのロード
            if (!NativeLibraryLoader.loadSteamAudio()) {
                LOGGER.error("Failed to load Steam Audio native libraries");
                return;
            }

            // Step 2: Steam Audio Contextの作成
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            contextSettings.logCallback = null;
            contextSettings.allocateCallback = null;
            contextSettings.freeCallback = null;
            contextSettings.write();

            PointerByReference contextRef = new PointerByReference();
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("Failed to create Steam Audio context. Error code: {}", result);
                return;
            }

            context = contextRef.getValue();
            LOGGER.info("Steam Audio context created successfully");

            // Step 3: HRTFの作成
            // IMPORTANT: frameSize must match the actual audio frame size used (960 samples for Opus 20ms @ 48kHz)
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = AudioConstants.SAMPLE_RATE;
            audioSettings.frameSize = AudioConstants.FRAME_SIZE; // 960 samples
            audioSettings.write();

            LOGGER.info("Steam Audio settings: {}Hz, {} samples/frame", audioSettings.samplingRate, audioSettings.frameSize);

            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            hrtfSettings.sofaFileName = null;
            hrtfSettings.sofaData = null;
            hrtfSettings.sofaDataSize = 0;
            hrtfSettings.write();

            PointerByReference hrtfRef = new PointerByReference();
            result = SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("Failed to create HRTF. Error code: {}", result);
                cleanup();
                return;
            }

            hrtf = hrtfRef.getValue();
            LOGGER.info("Steam Audio HRTF created successfully");

            // Step 4: Java Sound出力ラインの初期化
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Audio output format not supported");
                cleanup();
                return;
            }

            outputLine = (SourceDataLine) AudioSystem.getLine(info);
            outputLine.open(audioFormat, AudioConstants.NETWORK_BUFFER_SIZE);
            outputLine.start();

            running = true;

            // Step 5: 再生スレッド開始
            playbackThread = new Thread(this::playbackLoop, "SteamAudio-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            LOGGER.info("Steam Audio player started successfully");

        } catch (Exception e) {
            LOGGER.error("Failed to start Steam Audio player", e);
            cleanup();
        }
    }

    /**
     * Steam Audioプレイヤーを停止
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        // 再生スレッドの停止を待機
        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 出力ラインのクリーンアップ
        if (outputLine != null) {
            outputLine.drain();
            outputLine.stop();
            outputLine.close();
        }

        // 全てのプレイヤーSourceをクリーンアップ
        playerSources.forEach((uuid, source) -> source.cleanup());
        playerSources.clear();

        // Steam Audioリソースのクリーンアップ
        cleanup();

        LOGGER.info("Steam Audio player stopped");
    }

    /**
     * Steam Audioリソースのクリーンアップ
     */
    private void cleanup() {
        if (hrtf != null) {
            try {
                PointerByReference hrtfRef = new PointerByReference(hrtf);
                SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
                LOGGER.info("Released HRTF");
            } catch (Exception e) {
                LOGGER.error("Failed to release HRTF", e);
            }
            hrtf = null;
        }

        if (context != null) {
            try {
                PointerByReference contextRef = new PointerByReference(context);
                SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);
                LOGGER.info("Released Steam Audio context");
            } catch (Exception e) {
                LOGGER.error("Failed to release context", e);
            }
            context = null;
        }
    }

    /**
     * 非ポジショナルオーディオを追加（ウォーキートーキーなど）
     * @param samples PCMサンプル
     */
    public void addNonPositionalAudio(short[] samples) {
        // TODO: Phase 1では非ポジショナル音声は未実装
        // 必要に応じて、センター定位で再生する機能を追加
        LOGGER.debug("Non-positional audio not yet implemented in Steam Audio player");
    }

    /**
     * 使用する出力のミキサー名を設定（Steam Audioでは未使用）
     */
    public void setPreferredMixerName(String mixerName) {
        // Steam AudioはJava Sound APIの出力のみ使用するため、設定は保持しない
        LOGGER.debug("Mixer name setting ignored for Steam Audio (using default Java Sound output)");
    }

    /**
     * ポジショナルオーディオを追加
     * @param playerId プレイヤーUUID
     * @param samples PCMサンプル（モノラル）
     * @param position 3D座標
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position) {
        if (!running || context == null || hrtf == null) {
            return;
        }

        PlayerAudioSource source = playerSources.computeIfAbsent(playerId, id -> {
            try {
                // バイノーラルエフェクトを作成
                SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
                audioSettings.samplingRate = AudioConstants.SAMPLE_RATE;
                audioSettings.frameSize = AudioConstants.FRAME_SIZE;
                audioSettings.write();

                SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
                effectSettings.hrtf = hrtf;
                effectSettings.write();

                PointerByReference effectRef = new PointerByReference();
                int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to create binaural effect for player {}. Error code: {}", id, result);
                    return null;
                }

                LOGGER.debug("Created binaural effect for player {}", id);
                return new PlayerAudioSource(effectRef.getValue());

            } catch (Exception e) {
                LOGGER.error("Failed to create player audio source", e);
                return null;
            }
        });

        if (source != null) {
            source.position = position;
            if (!source.audioQueue.offer(samples)) {
                source.audioQueue.poll(); // 古いフレームを削除
                source.audioQueue.offer(samples);
            }
        }
    }

    /**
     * ポジショナルオーディオを追加（声量レベル付き）
     * @param playerId プレイヤーUUID
     * @param samples PCMサンプル（モノラル）
     * @param position 3D座標
     * @param volumeLevel 声量レベル（Phase 1では未使用）
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position, jp.houlab.mochidsuki.advancedvc.common.VolumeLevel volumeLevel) {
        // Phase 1では声量レベルは無視し、基本的なバイノーラル再生のみ
        addPositionalAudio(playerId, samples, position);
    }

    /**
     * プレイヤーストリームを削除
     */
    public void removePlayerStream(UUID playerId) {
        PlayerAudioSource source = playerSources.remove(playerId);
        if (source != null) {
            source.cleanup();
            LOGGER.debug("Removed player audio source: {}", playerId);
        }
    }

    /**
     * 再生ループ
     */
    private void playbackLoop() {
        while (running) {
            try {
                // ステレオミキシングバッファ
                int[] mixBuffer = new int[AudioConstants.FRAME_SIZE * 2];

                // 各プレイヤーの音声を処理してミキシング
                mixPositionalAudio(mixBuffer);

                // int[] → short[] に変換してクリッピング
                short[] outputSamples = new short[AudioConstants.FRAME_SIZE * 2];
                for (int i = 0; i < AudioConstants.FRAME_SIZE * 2; i++) {
                    int sample = mixBuffer[i];
                    if (outputGain != 1.0) {
                        sample = (int) Math.round(sample * outputGain);
                    }
                    // クリッピング
                    if (sample > Short.MAX_VALUE) {
                        sample = Short.MAX_VALUE;
                    } else if (sample < Short.MIN_VALUE) {
                        sample = Short.MIN_VALUE;
                    }
                    outputSamples[i] = (short) sample;
                }

                // byte配列に変換して再生
                byte[] outputBytes = shortsToBytes(outputSamples);
                outputLine.write(outputBytes, 0, outputBytes.length);

            } catch (Exception e) {
                if (running) {
                    LOGGER.error("Error in playback loop", e);
                }
            }
        }
    }

    /**
     * ポジショナルオーディオをSteam Audioで処理してミキシング
     */
    private void mixPositionalAudio(int[] mixBuffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 listenerPos = mc.player.position().add(0, 1.5, 0); // 耳の高さ
        float listenerYaw = mc.player.getYRot(); // プレイヤーの向き（度数法）
        float listenerPitch = mc.player.getXRot(); // プレイヤーの俯角（度数法）

        playerSources.forEach((playerId, source) -> {
            short[] samples = source.audioQueue.poll();
            if (samples == null || source.position == null || source.binauralEffect == null) {
                return;
            }

            if (source.inBufferPtr == null || source.outBufferPtr == null) {
                LOGGER.warn("Steam Audio buffers not allocated for player {}", playerId);
                return;
            }

            LOGGER.debug("Processing audio for player {}: {} samples", playerId, samples.length);

            try {
                // === Step 1: 音源方向ベクトルの計算 ===
                Vec3 sourcePos = source.position.add(0, 1.5, 0); // 音源の口の高さ
                Vec3 direction = sourcePos.subtract(listenerPos).normalize();

                // プレイヤーの向きを考慮した相対方向ベクトル
                double yawRad = Math.toRadians(listenerYaw);
                double pitchRad = Math.toRadians(listenerPitch);

                // リスナーの前方ベクトル
                double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
                double forwardY = -Math.sin(pitchRad);
                double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

                // リスナーの右ベクトル
                double rightX = Math.cos(yawRad);
                double rightZ = Math.sin(yawRad);

                // リスナーの上ベクトル
                double upX = -Math.sin(yawRad) * Math.sin(pitchRad);
                double upY = Math.cos(pitchRad);
                double upZ = Math.cos(yawRad) * Math.sin(pitchRad);

                // 音源方向ベクトルをリスナー座標系に変換
                float relativeX = (float) (direction.x * rightX + direction.z * rightZ);
                float relativeY = (float) (direction.x * upX + direction.y * upY + direction.z * upZ);
                float relativeZ = (float) (direction.x * forwardX + direction.y * forwardY + direction.z * forwardZ);

                // === Step 2: short[] → float[] 変換 ===
                // サンプル数の確認
                if (samples.length != AudioConstants.FRAME_SIZE) {
                    LOGGER.warn("Sample size mismatch: expected {}, got {}. Skipping.", AudioConstants.FRAME_SIZE, samples.length);
                    return;
                }

                float[] inputSamples = new float[AudioConstants.FRAME_SIZE];
                for (int i = 0; i < AudioConstants.FRAME_SIZE; i++) {
                    inputSamples[i] = samples[i] / 32768.0f; // short → float (-1.0 ～ +1.0)
                }

                LOGGER.debug("Direction: ({}, {}, {})", relativeX, relativeY, relativeZ);

                // === Step 3: Steam Audioバッファに書き込み（iplAudioBufferDeinterleaveを使用） ===
                SteamAudioLibrary.INSTANCE.iplAudioBufferDeinterleave(context, inputSamples, source.inBufferPtr);
                LOGGER.debug("Wrote input samples to buffer using iplAudioBufferDeinterleave");

                // === Step 4: バイノーラルエフェクトパラメータ設定 ===
                SteamAudioLibrary.IPLVector3 iplDirection = new SteamAudioLibrary.IPLVector3(relativeX, relativeY, relativeZ);
                SteamAudioLibrary.IPLBinauralEffectParams params = new SteamAudioLibrary.IPLBinauralEffectParams();
                params.direction = iplDirection;
                params.interpolation = 1; // Linear interpolation
                params.spatialBlend = 1.0f; // Full 3D
                params.write();

                // === Step 5: バイノーラルエフェクトを適用 ===
                LOGGER.debug("Calling iplBinauralEffectApply with IPLAudioBuffer");

                int result = SteamAudioLibrary.INSTANCE.iplBinauralEffectApply(
                        source.binauralEffect,
                        params,
                        source.inBufferPtr,
                        source.outBufferPtr
                );

                if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                    LOGGER.error("Failed to apply binaural effect for player {}. Error code: {}", playerId, result);
                    return;
                }

                LOGGER.debug("Binaural effect applied successfully for player {}", playerId);

                // === Step 6: Steam Audioバッファから読み取り（iplAudioBufferInterleaveを使用） ===
                float[] outputSamples = new float[AudioConstants.FRAME_SIZE * 2];  // ステレオ
                SteamAudioLibrary.INSTANCE.iplAudioBufferInterleave(context, source.outBufferPtr, outputSamples);
                LOGGER.debug("Read output samples using iplAudioBufferInterleave");

                // 出力サンプルの最初のいくつかをログに出力（デバッグ用）
                if (LOGGER.isDebugEnabled() && outputSamples.length > 10) {
                    LOGGER.debug("Output samples[0-5]: {}, {}, {}, {}, {}, {}",
                        outputSamples[0], outputSamples[1], outputSamples[2],
                        outputSamples[3], outputSamples[4], outputSamples[5]);
                }

                // === Step 7: float[] → int[] でミキシング ===
                int nonZeroCount = 0;
                for (int i = 0; i < outputSamples.length; i++) {
                    if (i < mixBuffer.length) {
                        int sample = (int) (outputSamples[i] * 32768.0f);
                        mixBuffer[i] += sample;
                        if (sample != 0) nonZeroCount++;
                    }
                }

                if (nonZeroCount > 0) {
                    LOGGER.debug("Mixed {} non-zero samples into buffer", nonZeroCount);
                } else {
                    LOGGER.warn("All output samples are zero!");
                }

            } catch (Exception e) {
                LOGGER.error("Error processing positional audio for player {}", playerId, e);
            }
        });
    }

    /**
     * short配列をbyte配列に変換
     */
    private byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .put(shorts);
        return bytes;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 出力ゲインを設定（0.0～2.0）
     */
    public void setOutputGain(double gain) {
        this.outputGain = Math.max(0.0, Math.min(2.0, gain));
        LOGGER.info("Output gain set to: {}", this.outputGain);
    }
}
