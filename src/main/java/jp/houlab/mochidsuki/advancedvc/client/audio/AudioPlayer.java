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

    // オーディオフォーマット
    private final AudioFormat audioFormat = new AudioFormat(
            AudioConstants.SAMPLE_RATE,
            16,
            AudioConstants.CHANNELS,
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

            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Audio output not supported");
                return;
            }

            outputLine = (SourceDataLine) AudioSystem.getLine(info);
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
     * 再生ループ
     */
    private void playbackLoop() {
        int frameSizeBytes = AudioConstants.FRAME_SIZE * AudioConstants.CHANNELS * 2;

        while (running) {
            try {
                // ミキシングバッファ
                int[] mixBuffer = new int[AudioConstants.FRAME_SIZE];

                // ポジショナルオーディオをミキシング
                mixPositionalAudio(mixBuffer);

                // 非ポジショナルオーディオをミキシング
                mixNonPositionalAudio(mixBuffer);

                // int[] → short[] に変換してクリッピング
                short[] outputSamples = new short[AudioConstants.FRAME_SIZE];
                for (int i = 0; i < AudioConstants.FRAME_SIZE; i++) {
                    int sample = mixBuffer[i];
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
     * ポジショナルオーディオをミキシング
     */
    private void mixPositionalAudio(int[] mixBuffer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 listenerPos = mc.player.position();

        audioStreams.forEach((playerId, stream) -> {
            short[] samples = stream.audioQueue.poll();
            if (samples != null && stream.position != null) {
                // 距離減衰を計算
                double distance = listenerPos.distanceTo(stream.position);
                float volume = calculateDistanceAttenuation(distance);

                // 3Dポジショナル処理（簡易版）
                // TODO: より高度な3Dオーディオ処理（HRTF等）
                for (int i = 0; i < Math.min(samples.length, mixBuffer.length); i++) {
                    mixBuffer[i] += (int) (samples[i] * volume);
                }
            }
        });
    }

    /**
     * 非ポジショナルオーディオをミキシング
     */
    private void mixNonPositionalAudio(int[] mixBuffer) {
        short[] samples = nonPositionalQueue.poll();
        if (samples != null) {
            for (int i = 0; i < Math.min(samples.length, mixBuffer.length); i++) {
                mixBuffer[i] += samples[i];
            }
        }
    }

    /**
     * 距離減衰を計算
     */
    private float calculateDistanceAttenuation(double distance) {
        // 逆二乗則に基づく減衰
        float attenuation = (float) (1.0 / (1.0 + AudioConstants.ATTENUATION_FACTOR * distance * distance));
        return Math.max(attenuation, AudioConstants.MIN_AUDIBLE_VOLUME);
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
        // TODO: 出力ゲインの実装
        // 実際の実装では、再生するサンプルに対してゲインを適用する必要があります
        LOGGER.info("Output gain set to: {}", gain);
    }
}
