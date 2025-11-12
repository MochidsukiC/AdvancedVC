package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * DSPフィルター（IIR Butterworthフィルター）
 * 2次Butterworthフィルターを2段カスケード接続して4次フィルターを実現
 */
public class DSPFilter {
    private final int sampleRate;

    // ローパスフィルター（2段）
    private BiquadFilter lowPass1;
    private BiquadFilter lowPass2;

    // ハイパスフィルター（2段）
    private BiquadFilter highPass1;
    private BiquadFilter highPass2;

    public DSPFilter() {
        this.sampleRate = AudioConstants.SAMPLE_RATE;
    }

    /**
     * ローパスフィルターを設定（4次Butterworth）
     * @param cutoffHz カットオフ周波数（Hz）
     */
    public void setLowPass(float cutoffHz) {
        // 2次Butterworthフィルターを2段カスケード接続
        // Q値は4次Butterworthの標準値
        lowPass1 = BiquadFilter.createLowPass(sampleRate, cutoffHz, 0.5412f);
        lowPass2 = BiquadFilter.createLowPass(sampleRate, cutoffHz, 1.3065f);
    }

    /**
     * ハイパスフィルターを設定（4次Butterworth）
     * @param cutoffHz カットオフ周波数（Hz）
     */
    public void setHighPass(float cutoffHz) {
        // 2次Butterworthフィルターを2段カスケード接続
        highPass1 = BiquadFilter.createHighPass(sampleRate, cutoffHz, 0.5412f);
        highPass2 = BiquadFilter.createHighPass(sampleRate, cutoffHz, 1.3065f);
    }

    /**
     * 音声サンプルにフィルターを適用
     * @param samples 入力サンプル（モノラル）
     * @return フィルター適用後のサンプル
     */
    public short[] process(short[] samples) {
        short[] output = new short[samples.length];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i];

            // ハイパスフィルター（低周波カット）
            if (highPass1 != null && highPass2 != null) {
                sample = highPass1.process(sample);
                sample = highPass2.process(sample);
            }

            // ローパスフィルター（高周波カット）
            if (lowPass1 != null && lowPass2 != null) {
                sample = lowPass1.process(sample);
                sample = lowPass2.process(sample);
            }

            // クリッピング
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }

            output[i] = (short) sample;
        }

        return output;
    }

    /**
     * フィルターの状態をリセット
     */
    public void reset() {
        if (lowPass1 != null) lowPass1.reset();
        if (lowPass2 != null) lowPass2.reset();
        if (highPass1 != null) highPass1.reset();
        if (highPass2 != null) highPass2.reset();
    }

    /**
     * 2次IIRフィルター（Biquad Filter）
     * Direct Form I 実装
     */
    private static class BiquadFilter {
        // フィルター係数
        private float b0, b1, b2; // フィードフォワード係数
        private float a1, a2;     // フィードバック係数（a0は常に1）

        // 状態変数（過去の入力と出力）
        private float x1 = 0, x2 = 0; // 過去の入力
        private float y1 = 0, y2 = 0; // 過去の出力

        private BiquadFilter(float b0, float b1, float b2, float a0, float a1, float a2) {
            // 係数をa0で正規化
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        /**
         * ローパスフィルターを作成
         */
        public static BiquadFilter createLowPass(int sampleRate, float cutoffHz, float Q) {
            float omega = (float) (2.0 * Math.PI * cutoffHz / sampleRate);
            float cosOmega = (float) Math.cos(omega);
            float sinOmega = (float) Math.sin(omega);
            float alpha = sinOmega / (2.0f * Q);

            float a0 = 1.0f + alpha;
            float a1 = -2.0f * cosOmega;
            float a2 = 1.0f - alpha;
            float b0 = (1.0f - cosOmega) / 2.0f;
            float b1 = 1.0f - cosOmega;
            float b2 = (1.0f - cosOmega) / 2.0f;

            return new BiquadFilter(b0, b1, b2, a0, a1, a2);
        }

        /**
         * ハイパスフィルターを作成
         */
        public static BiquadFilter createHighPass(int sampleRate, float cutoffHz, float Q) {
            float omega = (float) (2.0 * Math.PI * cutoffHz / sampleRate);
            float cosOmega = (float) Math.cos(omega);
            float sinOmega = (float) Math.sin(omega);
            float alpha = sinOmega / (2.0f * Q);

            float a0 = 1.0f + alpha;
            float a1 = -2.0f * cosOmega;
            float a2 = 1.0f - alpha;
            float b0 = (1.0f + cosOmega) / 2.0f;
            float b1 = -(1.0f + cosOmega);
            float b2 = (1.0f + cosOmega) / 2.0f;

            return new BiquadFilter(b0, b1, b2, a0, a1, a2);
        }

        /**
         * サンプルを処理（Direct Form I）
         */
        public float process(float x0) {
            // y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
            float y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            // 状態更新
            x2 = x1;
            x1 = x0;
            y2 = y1;
            y1 = y0;

            return y0;
        }

        /**
         * フィルター状態をリセット
         */
        public void reset() {
            x1 = x2 = y1 = y2 = 0;
        }
    }
}
