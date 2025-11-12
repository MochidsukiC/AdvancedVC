package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * Multi-tap Delay Reverbプロセッサー
 * 初期反射音（Early Reflections）と後部残響（Late Reverberation）を実現
 */
public class ReverbProcessor {
    private static final int MAX_DELAY_SAMPLES = AudioConstants.SAMPLE_RATE; // 1秒分のバッファ

    // 初期反射用のタップ（8タップ）- 時間（ms）とゲイン
    private static final float[][] EARLY_REFLECTION_TAPS = {
            {12f, 0.4f},   // 壁からの直接反射
            {18f, 0.35f},
            {25f, 0.3f},
            {32f, 0.25f},
            {41f, 0.22f},
            {48f, 0.18f},
            {55f, 0.15f},
            {63f, 0.12f}
    };

    // 後部残響用のタップ（32タップ）- Schroederリバーブ風
    private static final int LATE_REVERB_TAPS = 32;

    /**
     * Multi-tap Delay Reverbを適用
     * @param samples 入力サンプル
     * @param amount リバーブ量（0.0～1.0）
     * @param baseDelayMs ベースディレイ（ms）
     * @return 処理後のサンプル
     */
    public static short[] applyMultiTapReverb(short[] samples, float amount, float baseDelayMs) {
        if (samples == null || samples.length == 0 || amount < 0.01f) {
            return samples;
        }

        // クリッピング防止のため、wet信号を60%に制限（より強いリバーブ）
        amount = Math.min(amount, 0.6f);

        short[] output = samples.clone();

        // 初期反射音（Early Reflections）
        applyEarlyReflections(output, amount, baseDelayMs);

        // 後部残響（Late Reverberation）
        applyLateReverb(output, amount, baseDelayMs);

        return output;
    }

    /**
     * 初期反射音を適用（8-tap delay）
     */
    private static void applyEarlyReflections(short[] samples, float amount, float baseDelayMs) {
        int sampleRate = AudioConstants.SAMPLE_RATE;

        for (float[] tap : EARLY_REFLECTION_TAPS) {
            float tapDelayMs = baseDelayMs + tap[0];
            float tapGain = tap[1] * amount;

            int delaySamples = (int) (tapDelayMs * sampleRate / 1000f);
            if (delaySamples >= samples.length) {
                continue; // ディレイが長すぎる場合はスキップ
            }

            // ディレイ信号をミックス
            for (int i = delaySamples; i < samples.length; i++) {
                int reflection = (int) (samples[i - delaySamples] * tapGain);
                int mixed = samples[i] + reflection;

                // クリッピング
                samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            }
        }
    }

    /**
     * 後部残響を適用（32-tap delay with decay）
     */
    private static void applyLateReverb(short[] samples, float amount, float baseDelayMs) {
        int sampleRate = AudioConstants.SAMPLE_RATE;

        // 後部残響の開始時間（初期反射の後）
        float lateStartMs = baseDelayMs + 70f;

        // タップ間隔（ランダム性を持たせる）
        float tapInterval = 15f; // ms

        for (int tapIndex = 0; tapIndex < LATE_REVERB_TAPS; tapIndex++) {
            // タップのディレイ時間（指数的に増加）
            float tapDelayMs = lateStartMs + (tapIndex * tapInterval) + (tapIndex * tapIndex * 0.5f);

            // タップのゲイン（指数的に減衰）
            float decayFactor = (float) Math.exp(-tapIndex / 10.0);
            float tapGain = amount * 0.15f * decayFactor;

            int delaySamples = (int) (tapDelayMs * sampleRate / 1000f);
            if (delaySamples >= samples.length) {
                break; // ディレイが長すぎる場合は終了
            }

            // ディレイ信号をミックス
            for (int i = delaySamples; i < samples.length; i++) {
                int reverb = (int) (samples[i - delaySamples] * tapGain);
                int mixed = samples[i] + reverb;

                // クリッピング
                samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            }
        }
    }

    /**
     * Sabineの式に基づくRT60（残響時間）を計算
     * @param volume 空間の体積（m³）
     * @param absorptionCoefficient 平均吸音係数
     * @return RT60（秒）
     */
    public static float calculateRT60(float volume, float absorptionCoefficient) {
        // Sabine式: RT60 = 0.161 × V / A
        // V: 体積（m³）, A: 総吸音力（m²）
        // 簡略化: A ≈ 表面積 × 吸音係数
        float surfaceArea = (float) Math.pow(volume, 2.0/3.0) * 6.0f; // 立方体近似
        float totalAbsorption = surfaceArea * absorptionCoefficient;

        if (totalAbsorption < 0.1f) {
            totalAbsorption = 0.1f; // ゼロ除算回避
        }

        float rt60 = 0.161f * volume / totalAbsorption;

        // 実用範囲に制限
        return Math.max(0.1f, Math.min(rt60, 10.0f));
    }
}
