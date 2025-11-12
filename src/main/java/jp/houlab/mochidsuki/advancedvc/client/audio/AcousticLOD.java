package jp.houlab.mochidsuki.advancedvc.client.audio;

/**
 * 音響処理のLOD (Level of Detail) システム
 * 距離に応じて処理品質を段階的に変更し、パフォーマンスを最適化
 */
public class AcousticLOD {
    /**
     * LODレベル
     */
    public enum LODLevel {
        HIGH(0, 15.0f, 6),      // 近距離: 0-15m, 6バンド処理
        MEDIUM(1, 40.0f, 3),    // 中距離: 15-40m, 3バンド処理
        LOW(2, 100.0f, 1);      // 遠距離: 40m-, 1バンド処理

        private final int level;
        private final float maxDistance;
        private final int numBands;

        LODLevel(int level, float maxDistance, int numBands) {
            this.level = level;
            this.maxDistance = maxDistance;
            this.numBands = numBands;
        }

        public int getLevel() {
            return level;
        }

        public float getMaxDistance() {
            return maxDistance;
        }

        public int getNumBands() {
            return numBands;
        }
    }

    /**
     * 距離からLODレベルを決定
     * @param distance 音源までの距離（m）
     * @return LODレベル
     */
    public static LODLevel determineLOD(double distance) {
        if (distance <= LODLevel.HIGH.getMaxDistance()) {
            return LODLevel.HIGH;
        } else if (distance <= LODLevel.MEDIUM.getMaxDistance()) {
            return LODLevel.MEDIUM;
        } else {
            return LODLevel.LOW;
        }
    }

    /**
     * LODレベルに応じて6バンドの減衰配列を簡略化
     * @param fullBandAttenuations 6バンド全ての減衰値
     * @param lodLevel LODレベル
     * @return 簡略化された減衰値（LODレベルに応じたバンド数）
     */
    public static float[] simplifyBands(float[] fullBandAttenuations, LODLevel lodLevel) {
        int targetBands = lodLevel.getNumBands();

        if (targetBands == 6) {
            // HIGHレベル: そのまま返す
            return fullBandAttenuations.clone();
        } else if (targetBands == 3) {
            // MEDIUMレベル: 6バンドを3バンドに統合
            // Band 0-1 → Low (0-500Hz)
            // Band 2-3 → Mid (500-2000Hz)
            // Band 4-5 → High (2000-8000Hz)
            float[] simplified = new float[3];
            simplified[0] = (fullBandAttenuations[0] + fullBandAttenuations[1]) / 2.0f; // Low
            simplified[1] = (fullBandAttenuations[2] + fullBandAttenuations[3]) / 2.0f; // Mid
            simplified[2] = (fullBandAttenuations[4] + fullBandAttenuations[5]) / 2.0f; // High
            return simplified;
        } else {
            // LOWレベル: 全バンドの平均
            float average = 0f;
            for (float attenuation : fullBandAttenuations) {
                average += attenuation;
            }
            average /= fullBandAttenuations.length;
            return new float[]{average};
        }
    }

    /**
     * 簡略化されたバンドを6バンドに展開（処理用）
     * @param simplifiedBands 簡略化されたバンド減衰値
     * @param lodLevel LODレベル
     * @return 6バンドに展開された減衰値
     */
    public static float[] expandBands(float[] simplifiedBands, LODLevel lodLevel) {
        int sourceBands = lodLevel.getNumBands();

        if (sourceBands == 6) {
            return simplifiedBands.clone();
        } else if (sourceBands == 3) {
            // 3バンド→6バンドに展開
            float[] expanded = new float[6];
            expanded[0] = simplifiedBands[0]; // Low → Band 0
            expanded[1] = simplifiedBands[0]; // Low → Band 1
            expanded[2] = simplifiedBands[1]; // Mid → Band 2
            expanded[3] = simplifiedBands[1]; // Mid → Band 3
            expanded[4] = simplifiedBands[2]; // High → Band 4
            expanded[5] = simplifiedBands[2]; // High → Band 5
            return expanded;
        } else {
            // 1バンド→6バンド（全て同じ値）
            float[] expanded = new float[6];
            for (int i = 0; i < 6; i++) {
                expanded[i] = simplifiedBands[0];
            }
            return expanded;
        }
    }

    /**
     * LODレベルに応じて処理を有効化するか判定
     * @param lodLevel LODレベル
     * @param featureName 機能名（"diffraction", "reverb"など）
     * @return 処理を有効化するか
     */
    public static boolean isFeatureEnabled(LODLevel lodLevel, String featureName) {
        return switch (featureName.toLowerCase()) {
            case "diffraction" ->
                // 回折計算は全距離で有効（重要な機能）
                    true;
            case "reverb" ->
                // リバーブは全距離で有効
                    true;
            case "frequency_bands" ->
                // 周波数バンド処理は全距離（ただしバンド数は変化）
                    true;
            case "air_absorption" ->
                // 空気吸収は全距離
                    true;
            case "block_transmission" ->
                // ブロック透過は全距離
                    true;
            default -> true;
        };
    }

    /**
     * 周波数バンドの中心周波数を取得（LODレベル対応）
     * @param lodLevel LODレベル
     * @return 中心周波数配列
     */
    public static float[] getBandCenterFrequencies(LODLevel lodLevel) {
        return switch (lodLevel) {
            case HIGH ->
                // 6バンド
                    new float[]{125f, 375f, 750f, 1500f, 3000f, 6000f};
            case MEDIUM ->
                // 3バンド
                    new float[]{250f, 1000f, 4000f};
            case LOW ->
                // 1バンド（広帯域の代表周波数）
                    new float[]{1000f};
        };
    }
}
