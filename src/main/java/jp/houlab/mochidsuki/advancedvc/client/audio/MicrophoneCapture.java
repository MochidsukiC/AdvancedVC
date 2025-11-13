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

    // 入力ゲイン（0.0～2.0、デフォルト1.0）
    private volatile double inputGain = 1.0;
    private volatile String preferredMixerName = null;

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

        // macOS環境ではマイク許可を要求
        if (MacOSPermissionHelper.isMacOS()) {
            LOGGER.info("Detected macOS environment - checking microphone permission");
            if (!MacOSPermissionHelper.requestMicrophonePermission()) {
                LOGGER.error("Microphone permission not granted on macOS");
                MacOSPermissionHelper.showMacOSPermissionWarning();
                return;
            }
        }

        try {
            // 利用可能なマイクデバイスをログに出力
            logAvailableAudioDevices();

            // マイクデバイスを取得
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            TargetDataLine line = null;
            // 指定ミキサーがあれば優先
            if (preferredMixerName != null && !preferredMixerName.isBlank()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (mi.getName().equals(preferredMixerName)) {
                        Mixer mixer = AudioSystem.getMixer(mi);
                        if (mixer.isLineSupported(info)) {
                            line = (TargetDataLine) mixer.getLine(info);
                            break;
                        }
                    }
                }
            }
            // フォールバック: デフォルト
            if (line == null) {
                if (!AudioSystem.isLineSupported(info)) {
                    LOGGER.error("Microphone not supported");
                    return;
                }
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            microphone = line;

            // 使用するマイクデバイスをログに出力
            if (microphone != null && microphone.getLineInfo() != null) {
                LOGGER.info("Using microphone device: {}", microphone.getLineInfo().toString());
            }

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

            // macOS環境ではマイク許可が必要な可能性を案内
            if (MacOSPermissionHelper.isMacOS()) {
                LOGGER.error("Microphone access failed on macOS - permission may be required");
                MacOSPermissionHelper.showMacOSPermissionWarning();
            }
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

        LOGGER.info("Microphone capture loop started");
        int frameCount = 0;

        while (running.get()) {
            try {
                // マイクからデータを読み取り
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (frameCount < 5) {
                    LOGGER.info("Microphone read: {} bytes (frame {})", bytesRead, frameCount);
                    frameCount++;
                }

                if (bytesRead > 0) {
                    // byte[] → short[] に変換
                    short[] samples = bytesToShorts(buffer, bytesRead);

                    // 入力ゲインを適用
                    if (inputGain != 1.0) {
                        applyGain(samples, inputGain);
                    }

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
     * サンプルにゲインを適用（クリッピング防止付き）
     */
    private void applyGain(short[] samples, double gain) {
        for (int i = 0; i < samples.length; i++) {
            double amplified = samples[i] * gain;
            // クリッピング防止
            if (amplified > Short.MAX_VALUE) {
                samples[i] = Short.MAX_VALUE;
            } else if (amplified < Short.MIN_VALUE) {
                samples[i] = Short.MIN_VALUE;
            } else {
                samples[i] = (short) amplified;
            }
        }
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

    /**
     * 利用可能なオーディオデバイスをログに出力
     */
    private void logAvailableAudioDevices() {
        LOGGER.info("===== Available Audio Devices =====");
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);

            // 入力デバイス（TargetDataLine）をサポートしているか確認
            Line.Info[] targetLines = mixer.getTargetLineInfo();
            boolean hasInput = false;
            for (Line.Info lineInfo : targetLines) {
                if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                    hasInput = true;
                    break;
                }
            }

            if (hasInput) {
                LOGGER.info("  [INPUT] {}", mixerInfo.getName());
                LOGGER.info("          Description: {}", mixerInfo.getDescription());
            }
        }
        LOGGER.info("===================================");
    }

    /**
     * 入力ゲインを設定（0.0～2.0、1.0が標準）
     */
    public void setInputGain(double gain) {
        // ゲインの範囲を制限（0.0～2.0）
        this.inputGain = Math.max(0.0, Math.min(2.0, gain));
        LOGGER.info("Input gain set to: {}", this.inputGain);
    }

    /**
     * 使用するマイクのミキサー名を設定（開始前に設定）
     */
    public void setPreferredMixerName(String mixerName) {
        this.preferredMixerName = mixerName;
    }
}
