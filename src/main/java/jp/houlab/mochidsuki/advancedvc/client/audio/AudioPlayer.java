package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
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
 * オーディオ再生システム
 * デコードされた音声をポジショナル/非ポジショナルで再生
 */
public class AudioPlayer {
    private static final Logger LOGGER = LogUtils.getLogger();

    // プレイヤーごとのオーディオストリーム
    private final ConcurrentHashMap<UUID, PlayerAudioStream> audioStreams = new ConcurrentHashMap<>();

    // 非ポジショナルオーディオストリーム（ウォーキートーキーなど）
    private final LinkedBlockingQueue<short[]> nonPositionalQueue = new LinkedBlockingQueue<>(100);

    private SourceDataLine outputLine;
    private Thread playbackThread;
    private volatile boolean running = false;
    private volatile double outputGain = 1.0;
    private volatile String preferredMixerName = null;

    // オーディオフォーマット（ステレオ出力）
    // 入力はモノラルだが、出力は3Dパンニングのためステレオにする
    private final AudioFormat audioFormat = new AudioFormat(
            AudioConstants.SAMPLE_RATE,
            16,
            2,  // ステレオ出力
            true,
            false
    );

    /**
     * オーディオプレイヤーを開始
     */
    public void start() {
        if (running) {
            LOGGER.warn("Audio player is already running");
            return;
        }

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

            SourceDataLine line = null;
            if (preferredMixerName != null && !preferredMixerName.isBlank()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (mi.getName().equals(preferredMixerName)) {
                        Mixer mixer = AudioSystem.getMixer(mi);
                        if (mixer.isLineSupported(info)) {
                            line = (SourceDataLine) mixer.getLine(info);
                            break;
                        }
                    }
                }
            }
            if (line == null) {
                if (!AudioSystem.isLineSupported(info)) {
                    LOGGER.error("Audio output not supported");
                    return;
                }
                line = (SourceDataLine) AudioSystem.getLine(info);
            }
            outputLine = line;
            outputLine.open(audioFormat, AudioConstants.NETWORK_BUFFER_SIZE);
            outputLine.start();

            running = true;

            // 再生スレッド開始
            playbackThread = new Thread(this::playbackLoop, "Audio-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            LOGGER.info("Audio player started");
        } catch (LineUnavailableException e) {
            LOGGER.error("Failed to start audio player", e);
        }
    }

    /**
     * オーディオプレイヤーを停止
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (outputLine != null) {
            outputLine.drain();
            outputLine.stop();
            outputLine.close();
        }

        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        audioStreams.clear();
        nonPositionalQueue.clear();

        LOGGER.info("Audio player stopped");
    }

    /**
     * ポジショナルオーディオを追加（特定プレイヤーの音声）
     * @param playerId プレイヤーUUID
     * @param samples PCMサンプル
     * @param position 3D座標
     */
    public void addPositionalAudio(UUID playerId, short[] samples, Vec3 position) {
        PlayerAudioStream stream = audioStreams.computeIfAbsent(playerId, id -> new PlayerAudioStream());
        stream.position = position;
        stream.audioQueue.offer(samples);
    }

    /**
     * 非ポジショナルオーディオを追加（ウォーキートーキーなど）
     * @param samples PCMサンプル
     */
    public void addNonPositionalAudio(short[] samples) {
        if (!nonPositionalQueue.offer(samples)) {
            nonPositionalQueue.poll(); // 古いフレームを削除
            nonPositionalQueue.offer(samples);
        }
    }

    /**
     * プレイヤーのオーディオストリームを削除
     */
    public void removePlayerStream(UUID playerId) {
        audioStreams.remove(playerId);
    }

    /**
     * 再生ループ（ステレオ出力）
     */
    private void playbackLoop() {
        while (running) {
            try {
                // ステレオミキシングバッファ（インターリーブ: L, R, L, R, ...）
                int[] mixBuffer = new int[AudioConstants.FRAME_SIZE * 2];

                // ポジショナルオーディオをミキシング（ステレオパンニング適用）
                mixPositionalAudio(mixBuffer);

                // 非ポジショナルオーディオをミキシング（センター定位）
                mixNonPositionalAudio(mixBuffer);

                // int[] → short[] に変換してクリッピング + 出力ゲイン適用
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

                // byte配列に変換
                byte[] outputBytes = shortsToBytes(outputSamples);

                // 再生
                outputLine.write(outputBytes, 0, outputBytes.length);

            } catch (Exception e) {
                if (running) {
                    LOGGER.error("Error in playback loop", e);
                }
            }
        }
    }

    /**
     * ポジショナルオーディオをミキシング（HRTF: ITD/ILD適用）
     * Phase 3拡張版: 両耳間時間差（ITD）と両耳間レベル差（ILD）を追加
     */
    private void mixPositionalAudio(int[] mixBuffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 listenerPos = mc.player.position().add(0, 1.5, 0); // 耳の高さ
        float listenerYaw = mc.player.getYRot(); // プレイヤーの向き（度数法）

        // 頭部の幅（耳間距離）: 約17cm
        final float HEAD_WIDTH = 0.17f; // m
        final float SOUND_SPEED = 343.0f; // m/s

        audioStreams.forEach((playerId, stream) -> {
            short[] samples = stream.audioQueue.poll();
            if (samples != null && stream.position != null) {
                // 音源方向の計算（水平面）
                Vec3 sourcePos = stream.position.add(0, 1.5, 0); // 音源の口の高さ
                double dx = sourcePos.x - listenerPos.x;
                double dz = sourcePos.z - listenerPos.z;
                double distance = Math.sqrt(dx * dx + dz * dz);

                // 音源の角度（ラジアン、Minecraft座標系: +Z=南, +X=東）
                double angleToSource = Math.atan2(dz, dx); // -π ～ +π

                // 相対角度（プレイヤーの正面を0とする）
                double relativeAngle = angleToSource - Math.toRadians(listenerYaw - 90);

                // -π ～ +π の範囲に正規化
                while (relativeAngle > Math.PI) relativeAngle -= 2 * Math.PI;
                while (relativeAngle < -Math.PI) relativeAngle += 2 * Math.PI;

                // パン値計算（-1.0=左, 0.0=中央, +1.0=右）
                float pan = (float) Math.sin(relativeAngle);
                pan = Math.max(-1.0f, Math.min(1.0f, pan)); // クランプ

                // === ITD (Interaural Time Difference): 両耳間時間差 ===
                // 音源が左にある場合、左耳に先に到達する
                // ITD(秒) = (頭部幅 / 音速) × sin(角度)
                float itdSeconds = (HEAD_WIDTH / SOUND_SPEED) * pan;
                int itdSamples = (int) (itdSeconds * AudioConstants.SAMPLE_RATE);
                itdSamples = Math.max(-10, Math.min(10, itdSamples)); // ±10サンプルに制限

                // === ILD (Interaural Level Difference): 両耳間レベル差 ===
                // 頭部による遮蔽（Shadow Zone Model）
                // 反対側の耳は-6dB～-20dB程度減衰
                float shadowAttenuation = Math.abs(pan) * 0.7f; // 0.0～0.7の範囲
                float leftILD = pan > 0 ? shadowAttenuation : 0f;  // 右にある場合、左耳が減衰
                float rightILD = pan < 0 ? shadowAttenuation : 0f; // 左にある場合、右耳が減衰

                // Equal Power Panningとの組み合わせ
                float leftGain = (float) Math.sqrt((1.0f - pan) / 2.0f) * (1.0f - leftILD);
                float rightGain = (float) Math.sqrt((1.0f + pan) / 2.0f) * (1.0f - rightILD);

                // ステレオミキシング（ITD適用、簡略化版）
                // TODO: ITD実装を一時的に簡略化（範囲外アクセスを防ぐ）
                for (int i = 0; i < samples.length; i++) {
                    int monoSample = samples[i];

                    // 左チャンネル
                    int leftIndex = i * 2;
                    if (leftIndex < mixBuffer.length) {
                        mixBuffer[leftIndex] += (int) (monoSample * leftGain);
                    }

                    // 右チャンネル
                    int rightIndex = i * 2 + 1;
                    if (rightIndex < mixBuffer.length) {
                        mixBuffer[rightIndex] += (int) (monoSample * rightGain);
                    }
                }
            }
        });
    }

    /**
     * 非ポジショナルオーディオをミキシング（センター定位）
     */
    private void mixNonPositionalAudio(int[] mixBuffer) {
        short[] samples = nonPositionalQueue.poll();
        if (samples != null) {
            // センター定位（L/R均等）
            for (int i = 0; i < samples.length; i++) {
                int monoSample = samples[i];
                mixBuffer[i * 2] += monoSample;     // Left
                mixBuffer[i * 2 + 1] += monoSample; // Right
            }
        }
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

    /**
     * プレイヤーオーディオストリーム
     */
    private static class PlayerAudioStream {
        Vec3 position;
        final LinkedBlockingQueue<short[]> audioQueue = new LinkedBlockingQueue<>(50);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 出力ゲインを設定（0.0～1.0）
     */
    public void setOutputGain(double gain) {
        this.outputGain = Math.max(0.0, Math.min(2.0, gain));
        LOGGER.info("Output gain set to: {}", this.outputGain);
    }

    /**
     * 使用する出力のミキサー名を設定（開始前に設定）
     */
    public void setPreferredMixerName(String mixerName) {
        this.preferredMixerName = mixerName;
    }
}
