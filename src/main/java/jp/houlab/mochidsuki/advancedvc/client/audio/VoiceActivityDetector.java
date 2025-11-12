package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * 音声アクティビティ検出（VAD）
 * マイク入力から音声の有無を判定
 */
public class VoiceActivityDetector {

    private float threshold;
    private final int minFrameCount;
    private int activeFrameCount = 0;
    private boolean isActive = false;
    private float currentLevel = 0.0f;

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
        currentLevel = rms;

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
    public void setThreshold(double threshold) {
        this.threshold = (float) threshold;
    }

    /**
     * 現在の声量レベルを取得（0.0～1.0、視覚的表示用に増幅）
     */
    public float getCurrentLevel() {
        // 通常の会話音声は正規化RMSで0.01-0.1程度なので、
        // UI表示のために10倍に増幅（最大1.0でクリップ）
        float amplified = currentLevel * 10.0f;
        return Math.min(amplified, 1.0f);
    }

    /**
     * 音声が検出されているかを取得
     */
    public boolean isVoiceDetected() {
        return isActive;
    }
}
