package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DSP（Digital Signal Processing）プロセッサー
 * 音響シミュレーション結果を実際の音声データに適用
 */
public class DSPProcessor {
    // プレイヤーごとのDSPFilterインスタンス
    private static final Map<UUID, DSPFilter> playerFilters = new ConcurrentHashMap<>();

    /**
     * DSPパラメータを音声データに適用（Phase 2拡張版：周波数バンド対応）
     * @param playerId プレイヤーID（フィルター状態管理用）
     * @param samples PCMサンプル
     * @param params DSPパラメータ
     * @return 処理後のPCMサンプル
     */
    public static short[] applyDSP(UUID playerId, short[] samples, AcousticSimulationEngine.DSPParameters params) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        short[] processed = samples.clone();

        // 1. 音量調整
        applyVolumeAdjustment(processed, params.volume);

        // 2. 周波数バンド処理（Phase 2）
        // TODO: FFT/IFFT処理が重すぎるため一時的に無効化、Butterworthフィルターのみ使用
        if (params.bandAttenuations != null && params.bandAttenuations.length > 0) {
            // バンド減衰の平均を計算して、全体の音量調整のみ適用
            float averageAttenuation = 0f;
            for (float att : params.bandAttenuations) {
                averageAttenuation += att;
            }
            averageAttenuation /= params.bandAttenuations.length;

            // 音量調整（バンド減衰の平均を反映）
            for (int i = 0; i < processed.length; i++) {
                int adjusted = (int) (processed[i] * averageAttenuation);
                processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
            }
        }

        // IIR Butterworthフィルター適用（4次）
        processed = applyButterworthFilter(playerId, processed, params.lowPassFilter, params.highPassFilter);

        // 3. Multi-tap Delay Reverb（Phase 3）
        if (params.reverbAmount > 0.01f) {
            processed = ReverbProcessor.applyMultiTapReverb(processed, params.reverbAmount, params.reverbDelay);
        }

        return processed;
    }

    /**
     * 4次IIR Butterworthフィルターを適用
     */
    private static short[] applyButterworthFilter(UUID playerId, short[] samples,
                                                    float lowPassCutoff, float highPassCutoff) {
        // プレイヤーのDSPFilterを取得（なければ作成）
        DSPFilter filter = playerFilters.computeIfAbsent(playerId, id -> new DSPFilter());

        // フィルターパラメータを設定
        filter.setLowPass(lowPassCutoff);
        filter.setHighPass(highPassCutoff);

        // フィルター適用
        return filter.process(samples);
    }

    /**
     * プレイヤーのフィルター状態をクリア（プレイヤーが離脱した時など）
     */
    public static void clearPlayerFilter(UUID playerId) {
        DSPFilter filter = playerFilters.remove(playerId);
        if (filter != null) {
            filter.reset();
        }
    }

    /**
     * 全プレイヤーのフィルター状態をクリア
     */
    public static void clearAllFilters() {
        playerFilters.values().forEach(DSPFilter::reset);
        playerFilters.clear();
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
