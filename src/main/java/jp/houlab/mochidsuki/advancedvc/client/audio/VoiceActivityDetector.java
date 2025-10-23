package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * 音声アクティビティ検出（VAD）
 * マイク入力から音声の有無を判定
 */
public class VoiceActivityDetector {

    private final float threshold;
    private final int minFrameCount;
    private int activeFrameCount = 0;
    private boolean isActive = false;

    public VoiceActivityDetector(float threshold, int minFrameCount) {
        this.threshold = threshold;
        this.minFrameCount = minFrameCount;
    }

    public VoiceActivityDetector() {
        this(AudioConstants.VAD_THRESHOLD, AudioConstants.VAD_FRAME_COUNT);
    }

    /**
     * フレームを処理して音声アクティビティを判定
     * @param samples PCMサンプル
     * @return 音声がアクティブかどうか
     */
    public boolean process(short[] samples) {
        if (samples == null || samples.length == 0) {
            return false;
        }

        // RMS（二乗平均平方根）を計算
        float rms = calculateRMS(samples);

        // 閾値と比較
        if (rms > threshold) {
            activeFrameCount++;
            if (activeFrameCount >= minFrameCount) {
                isActive = true;
            }
        } else {
            activeFrameCount = 0;
            isActive = false;
        }

        return isActive;
    }

    /**
     * RMS（二乗平均平方根）を計算
     */
    private float calculateRMS(short[] samples) {
        long sum = 0;
        for (short sample : samples) {
            sum += (long) sample * sample;
        }
        double mean = (double) sum / samples.length;
        return (float) Math.sqrt(mean) / Short.MAX_VALUE; // 正規化
    }

    /**
     * 現在の音声アクティビティ状態を取得
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * 状態をリセット
     */
    public void reset() {
        activeFrameCount = 0;
        isActive = false;
    }

    /**
     * 閾値を動的に設定
     */
    public void setThreshold(float threshold) {
        // 実装は省略（必要に応じて追加）
    }
}
