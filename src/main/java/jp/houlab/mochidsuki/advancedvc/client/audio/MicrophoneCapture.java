package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * マイク入力キャプチャ
 * Java Sound APIを使用してマイクから音声を録音
 */
public class MicrophoneCapture {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TargetDataLine microphone;
    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // オーディオフレームのキュー
    private final BlockingQueue<short[]> audioFrameQueue = new LinkedBlockingQueue<>(100);

    // オーディオフォーマット
    private final AudioFormat audioFormat = new AudioFormat(
            AudioConstants.SAMPLE_RATE,
            16, // 16-bit
            AudioConstants.CHANNELS,
            true, // signed
            false // little-endian
    );

    /**
     * マイクキャプチャを開始
     */
    public void start() {
        if (running.get()) {
            LOGGER.warn("Microphone capture is already running");
            return;
        }

        try {
            // マイクデバイスを取得
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.error("Microphone not supported");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat, AudioConstants.NETWORK_BUFFER_SIZE);
            microphone.start();

            running.set(true);

            // キャプチャスレッド開始
            captureThread = new Thread(this::captureLoop, "Microphone-Capture");
            captureThread.setDaemon(true);
            captureThread.start();

            LOGGER.info("Microphone capture started");
        } catch (LineUnavailableException e) {
            LOGGER.error("Failed to start microphone capture", e);
        }
    }

    /**
     * マイクキャプチャを停止
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        audioFrameQueue.clear();

        LOGGER.info("Microphone capture stopped");
    }

    /**
     * キャプチャループ
     */
    private void captureLoop() {
        // フレームサイズ（バイト）= サンプル数 * チャンネル数 * バイト/サンプル
        int frameSizeBytes = AudioConstants.FRAME_SIZE * AudioConstants.CHANNELS * 2;
        byte[] buffer = new byte[frameSizeBytes];

        while (running.get()) {
            try {
                // マイクからデータを読み取り
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    // byte[] → short[] に変換
                    short[] samples = bytesToShorts(buffer, bytesRead);

                    // キューに追加（キューが満杯の場合は古いフレームを破棄）
                    if (!audioFrameQueue.offer(samples)) {
                        audioFrameQueue.poll(); // 古いフレームを削除
                        audioFrameQueue.offer(samples); // 新しいフレームを追加
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    LOGGER.error("Error in capture loop", e);
                }
            }
        }
    }

    /**
     * byte配列をshort配列に変換（リトルエンディアン）
     */
    private short[] bytesToShorts(byte[] bytes, int length) {
        short[] shorts = new short[length / 2];
        ByteBuffer.wrap(bytes, 0, length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(shorts);
        return shorts;
    }

    /**
     * 次のオーディオフレームを取得（ブロッキング）
     * @return PCMサンプル（short[]）、利用可能なデータがない場合はnull
     */
    public short[] getNextFrame() {
        return audioFrameQueue.poll();
    }

    /**
     * 次のオーディオフレームを取得（ブロッキング、タイムアウト付き）
     * @param timeoutMs タイムアウト（ミリ秒）
     * @return PCMサンプル（short[]）、タイムアウトした場合はnull
     */
    public short[] getNextFrame(long timeoutMs) {
        try {
            return audioFrameQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 利用可能なマイクデバイス一覧を取得
     */
    public static Mixer.Info[] getAvailableMicrophones() {
        return AudioSystem.getMixerInfo();
    }
}
