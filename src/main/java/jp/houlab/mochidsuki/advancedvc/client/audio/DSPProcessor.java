package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

/**
 * DSP（Digital Signal Processing）プロセッサー
 * 音響シミュレーション結果を実際の音声データに適用
 */
public class DSPProcessor {

    /**
     * DSPパラメータを音声データに適用
     * @param samples PCMサンプル
     * @param params DSPパラメータ
     * @return 処理後のPCMサンプル
     */
    public static short[] applyDSP(short[] samples, AcousticSimulationEngine.DSPParameters params) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        short[] processed = samples.clone();

        // 1. 音量調整
        applyVolumeAdjustment(processed, params.volume);

        // 2. フィルター適用（簡易版）
        applySimpleFilter(processed, params.lowPassFilter, params.highPassFilter);

        // 3. リバーブ（簡易版）
        if (params.reverbAmount > 0.01f) {
            applySimpleReverb(processed, params.reverbAmount, params.reverbDelay);
        }

        return processed;
    }

    /**
     * 音量調整
     */
    private static void applyVolumeAdjustment(short[] samples, float volume) {
        for (int i = 0; i < samples.length; i++) {
            int adjusted = (int) (samples[i] * volume);
            // クリッピング防止
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
        }
    }

    /**
     * 簡易フィルター適用
     * TODO: より高度なIIR/FIRフィルター実装
     */
    private static void applySimpleFilter(short[] samples, float lowPassCutoff, float highPassCutoff) {
        // ローパスフィルター（高周波減衰）の簡易実装
        float lpfFactor = Math.min(lowPassCutoff / 20000f, 1.0f);

        // ハイパスフィルター（低周波減衰）の簡易実装
        float hpfFactor = Math.min((AudioConstants.SAMPLE_RATE / 2 - highPassCutoff) /
                (AudioConstants.SAMPLE_RATE / 2f), 1.0f);

        float combinedFactor = lpfFactor * hpfFactor;

        // 移動平均フィルター（簡易ローパス）
        if (combinedFactor < 0.9f) {
            for (int i = 1; i < samples.length - 1; i++) {
                int smoothed = (samples[i - 1] + samples[i] * 2 + samples[i + 1]) / 4;
                samples[i] = (short) ((samples[i] * combinedFactor) + (smoothed * (1 - combinedFactor)));
            }
        }
    }

    /**
     * 簡易リバーブ適用
     */
    private static void applySimpleReverb(short[] samples, float amount, float delayMs) {
        // ディレイサンプル数を計算
        int delaySamples = (int) (AudioConstants.SAMPLE_RATE * delayMs / 1000f);
        delaySamples = Math.min(delaySamples, samples.length / 2);

        if (delaySamples < 1) {
            return;
        }

        // 簡易エコー効果
        for (int i = delaySamples; i < samples.length; i++) {
            int echo = (int) (samples[i - delaySamples] * amount * 0.5f);
            int combined = samples[i] + echo;
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, combined));
        }
    }

    /**
     * デバイスモード用の音質劣化フィルター（ウォーキートーキー風）
     */
    public static short[] applyWalkieTalkieFilter(short[] samples) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        short[] processed = samples.clone();

        // 1. バンドパスフィルター（300Hz - 3400Hz）
        applyBandPassFilter(processed, 300f, 3400f);

        // 2. 軽いディストーション（クリッピング）
        applyDistortion(processed, 0.7f);

        // 3. ノイズ追加
        addNoise(processed, 0.02f);

        return processed;
    }

    /**
     * バンドパスフィルター（簡易版）
     */
    private static void applyBandPassFilter(short[] samples, float lowCutoff, float highCutoff) {
        // 簡易実装: 移動平均でハイカット、差分でローカット
        int windowSize = 5;

        for (int i = windowSize; i < samples.length - windowSize; i++) {
            // ローパス（移動平均）
            int sum = 0;
            for (int j = -windowSize / 2; j <= windowSize / 2; j++) {
                sum += samples[i + j];
            }
            int lowPassed = sum / windowSize;

            // ハイパス（差分）
            int highPassed = samples[i] - lowPassed;

            // バンドパス（組み合わせ）
            samples[i] = (short) ((lowPassed + highPassed) / 2);
        }
    }

    /**
     * ディストーション（クリッピング）
     */
    private static void applyDistortion(short[] samples, float threshold) {
        int clipLevel = (int) (Short.MAX_VALUE * threshold);

        for (int i = 0; i < samples.length; i++) {
            if (samples[i] > clipLevel) {
                samples[i] = (short) clipLevel;
            } else if (samples[i] < -clipLevel) {
                samples[i] = (short) -clipLevel;
            }
        }
    }

    /**
     * ホワイトノイズ追加
     */
    private static void addNoise(short[] samples, float amount) {
        int noiseLevel = (int) (Short.MAX_VALUE * amount);

        for (int i = 0; i < samples.length; i++) {
            int noise = (int) ((Math.random() - 0.5) * 2 * noiseLevel);
            int combined = samples[i] + noise;
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, combined));
        }
    }
}
