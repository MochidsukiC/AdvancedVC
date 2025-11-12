package jp.houlab.mochidsuki.advancedvc.client.audio;

/**
 * ISO 9613-1準拠の空気による音響吸収計算
 * 周波数・温度・湿度依存の減衰を計算
 */
public class AirAbsorption {
    // 標準大気圧（Pa）
    private static final double STANDARD_PRESSURE = 101325.0;

    /**
     * 空気吸収係数を計算（dB/m）
     * ISO 9613-1に基づく
     *
     * @param frequency 周波数（Hz）
     * @param temperature 温度（℃）
     * @param humidity 相対湿度（%）
     * @return 吸収係数（dB/m）
     */
    public static float calculateAbsorption(float frequency, float temperature, float humidity) {
        // 絶対温度（K）
        double T = temperature + 273.15;
        double T0 = 293.15; // 20℃

        // 相対湿度の飽和水蒸気圧比
        double h = humidity;

        // モル濃度（ISO 9613-1式）
        double C = -6.8346 * Math.pow(T0 / T, 1.261) + 4.6151;
        double psat_pr = Math.pow(10, C);
        double h_molar = h * psat_pr * (STANDARD_PRESSURE / 101325.0);

        // 緩和周波数
        double frO = (24.0 + 4.04e4 * h_molar * (0.02 + h_molar) / (0.391 + h_molar)) / (T / T0);
        double frN = Math.pow(T / T0, -0.5) * (9.0 + 280.0 * h_molar * Math.exp(-4.170 * (Math.pow(T / T0, -1.0/3.0) - 1.0)));

        // 周波数（Hz）
        double f = frequency;

        // 吸収係数（dB/m）- ISO 9613-1 式(1)
        double alpha = f * f * (
                1.84e-11 * Math.pow(T / T0, 0.5) * (STANDARD_PRESSURE / 101325.0) +
                        Math.pow(T / T0, -2.5) * (
                                0.01275 * Math.exp(-2239.1 / T) * (frO + f * f / frO) / (frO * frO + f * f) +
                                        0.1068 * Math.exp(-3352.0 / T) * (frN + f * f / frN) / (frN * frN + f * f)
                        )
        );

        return (float) alpha;
    }

    /**
     * 空気吸収による減衰を計算（dB）
     * @param frequency 周波数（Hz）
     * @param distance 距離（m）
     * @param temperature 温度（℃）
     * @param humidity 相対湿度（%）
     * @return 減衰量（dB）
     */
    public static float calculateAttenuationDb(float frequency, float distance,
                                                float temperature, float humidity) {
        float absorptionCoeff = calculateAbsorption(frequency, temperature, humidity);
        return absorptionCoeff * distance;
    }

    /**
     * 減衰量（dB）を減衰係数（0.0～1.0）に変換
     * @param attenuationDb 減衰量（dB）
     * @return 減衰係数（0.0=完全減衰, 1.0=減衰なし）
     */
    public static float attenuationDbToCoefficient(float attenuationDb) {
        // A(dB) = -20 × log₁₀(coeff)
        // coeff = 10^(-A/20)
        return (float) Math.pow(10.0, -attenuationDb / 20.0);
    }

    /**
     * 6バンド周波数に対する空気吸収係数を計算
     * @param distance 距離（m）
     * @param temperature 温度（℃）
     * @param humidity 相対湿度（%）
     * @return 各バンドの減衰係数（0.0～1.0）
     */
    public static float[] calculateBandAbsorption(float distance, float temperature, float humidity) {
        // バンド中心周波数
        float[] centerFrequencies = {125f, 375f, 750f, 1500f, 3000f, 6000f};
        float[] coefficients = new float[centerFrequencies.length];

        for (int i = 0; i < centerFrequencies.length; i++) {
            float attenuationDb = calculateAttenuationDb(centerFrequencies[i], distance, temperature, humidity);
            coefficients[i] = attenuationDbToCoefficient(attenuationDb);
        }

        return coefficients;
    }

    /**
     * 簡易版: 距離と周波数だけから減衰係数を推定（標準条件: 20℃, 50%）
     */
    public static float simpleAbsorption(float frequency, float distance) {
        return attenuationDbToCoefficient(
                calculateAttenuationDb(frequency, distance, 20f, 50f)
        );
    }
}
