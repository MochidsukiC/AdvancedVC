package jp.houlab.mochidsuki.advancedvc.client.audio;

import jp.houlab.mochidsuki.advancedvc.client.audio.DetailedReflectionAnalyzer.*;

/**
 * 環境解析結果をOpenAL EFXの13パラメータに精密変換
 * リバーブの質感を正確に再現するための核心クラス
 */
public class PreciseEFXConverter {

    /**
     * 環境特性をEFXパラメータに変換
     * @param spatial 空間特性
     * @param material 材質特性
     * @param diffusion 拡散特性
     * @param temporal 時間特性
     * @param isIndoor 屋内判定
     * @return EFXReverbSettings
     */
    public static EFXReverbSettings convertToEFX(
            SpatialCharacteristics spatial,
            MaterialCharacteristics material,
            DiffusionCharacteristics diffusion,
            TemporalCharacteristics temporal,
            boolean isIndoor
    ) {
        EFXReverbSettings settings = new EFXReverbSettings();

        // ===== パラメータ1: Density（密度） =====
        settings.density = calculateDensity(spatial, temporal, diffusion);

        // ===== パラメータ2: Diffusion（拡散度） =====
        settings.diffusion = calculateDiffusion(diffusion, spatial);

        // ===== パラメータ3: Decay Time（減衰時間 = RT60） =====
        settings.decayTime = calculateDecayTime(temporal, spatial, material, isIndoor);

        // ===== パラメータ4: Decay HF Ratio（高周波減衰比） =====
        settings.decayHFRatio = calculateDecayHFRatio(material, spatial);

        // ===== パラメータ5: Reflections Gain（初期反射ゲイン） =====
        settings.reflectionsGain = calculateReflectionsGain(temporal, spatial);

        // ===== パラメータ6: Reflections Delay（初期反射遅延） =====
        settings.reflectionsDelay = calculateReflectionsDelay(temporal);

        // ===== パラメータ7: Late Reverb Gain（後部残響ゲイン） =====
        settings.lateReverbGain = calculateLateReverbGain(temporal, material, spatial);

        // ===== パラメータ8: Late Reverb Delay（後部残響遅延） =====
        settings.lateReverbDelay = calculateLateReverbDelay(temporal);

        // ===== パラメータ9: Room Rolloff Factor（距離減衰係数） =====
        settings.roomRolloffFactor = calculateRoomRolloff(spatial, isIndoor);

        // ===== パラメータ10: Air Absorption HF（空気吸収） =====
        settings.airAbsorptionHF = calculateAirAbsorption(spatial);

        // ===== パラメータ11: HF Reference（高周波基準周波数） =====
        settings.hfReference = calculateHFReference(material);

        // ===== パラメータ12: LF Reference（低周波基準周波数） =====
        settings.lfReference = calculateLFReference(spatial);

        // ===== パラメータ13: Room Size（空間サイズ感） =====
        settings.roomSize = calculateRoomSize(spatial);

        return settings;
    }

    /**
     * パラメータ1: Density（密度）
     */
    private static float calculateDensity(SpatialCharacteristics spatial, TemporalCharacteristics temporal, DiffusionCharacteristics diffusion) {
        float density = 0.4f +
                spatial.geometricComplexity * 0.3f +
                temporal.earlyReflectionDensity * 0.02f +  // 密度は 0-10 個/ms程度
                diffusion.modalDensity * 0.1f;

        return clamp(density, 0.0f, 1.0f);
    }

    /**
     * パラメータ2: Diffusion（拡散度）
     */
    private static float calculateDiffusion(DiffusionCharacteristics diffusion, SpatialCharacteristics spatial) {
        float diff = 0.3f +
                diffusion.spatialDiffusion * 0.4f +
                diffusion.temporalDiffusion * 0.2f +
                spatial.surfaceIrregularity * 0.1f;

        // フラッターエコー補正
        if (diffusion.hasFlutterEcho) {
            diff *= 1.3f;  // 拡散度を上げて緩和
        }

        return clamp(diff, 0.0f, 1.0f);
    }

    /**
     * パラメータ3: Decay Time（減衰時間 = RT60）
     */
    private static float calculateDecayTime(TemporalCharacteristics temporal, SpatialCharacteristics spatial, MaterialCharacteristics material, boolean isIndoor) {
        float decayTime = temporal.rt60;

        // 体積に応じた調整
        float volume = spatial.estimatedVolume;
        if (volume < 100f) {
            decayTime = clamp(decayTime, 0.15f, 0.5f);  // 小部屋
        } else if (volume < 500f) {
            decayTime = clamp(decayTime, 0.3f, 1.0f);   // 中部屋
        } else if (volume < 2000f) {
            decayTime = clamp(decayTime, 0.5f, 2.0f);   // 大部屋
        } else {
            decayTime = clamp(decayTime, 1.0f, 5.0f);   // 巨大空間
        }

        // 屋外は短い
        if (!isIndoor) {
            decayTime = Math.min(decayTime, 0.2f);
        }

        return decayTime;
    }

    /**
     * パラメータ4: Decay HF Ratio（高周波減衰比）
     */
    private static float calculateDecayHFRatio(MaterialCharacteristics material, SpatialCharacteristics spatial) {
        float ratio = material.hfDecayRatio;

        // 材質による調整
        switch (material.dominantMaterial) {
            case WOOD:
                ratio *= 0.7f;  // 木造: 暖かい音
                break;
            case STONE:
                ratio *= 1.2f;  // 石造: 明るい音
                break;
            case METAL:
                ratio *= 1.5f;  // 金属: 硬い音
                break;
            case FABRIC:
                ratio *= 0.5f;  // 布: 柔らかい音
                break;
            case GLASS:
                ratio *= 1.1f;  // ガラス: やや明るい音
                break;
        }

        // 空気吸収の考慮
        float airAbsorption = spatial.averageWallDistance * 0.01f;
        ratio *= (1.0f - airAbsorption);

        return clamp(ratio, 0.3f, 1.5f);
    }

    /**
     * パラメータ5: Reflections Gain（初期反射ゲイン）
     */
    private static float calculateReflectionsGain(TemporalCharacteristics temporal, SpatialCharacteristics spatial) {
        // 独自Sourceで実装するため、EFXは補助的に低く設定
        float gain = 0.05f + temporal.earlyToLateRatio * 0.05f;

        // 壁が近い → 強い初期反射
        if (spatial.averageWallDistance < 3.0f) {
            gain *= 1.5f;
        }

        return clamp(gain, 0.0f, 0.3f);
    }

    /**
     * パラメータ6: Reflections Delay（初期反射遅延）
     */
    private static float calculateReflectionsDelay(TemporalCharacteristics temporal) {
        float delay = temporal.firstReflectionDelay / 1000.0f;  // msを秒に変換
        return clamp(delay, 0.0f, 0.3f);
    }

    /**
     * パラメータ7: Late Reverb Gain（後部残響ゲイン）
     */
    private static float calculateLateReverbGain(TemporalCharacteristics temporal, MaterialCharacteristics material, SpatialCharacteristics spatial) {
        float gain = 1.0f;

        // RT60が長い → 残響強い
        if (temporal.rt60 > 2.0f) {
            gain *= 1.5f;
        }

        // 吸音率が低い → 残響強い
        gain *= (1.0f - material.averageAbsorption1000Hz) * 1.5f;

        // 空間が大きい → 残響強い
        if (spatial.estimatedVolume > 1000f) {
            gain *= 1.2f;
        }

        return clamp(gain, 0.1f, 3.0f);
    }

    /**
     * パラメータ8: Late Reverb Delay（後部残響遅延）
     */
    private static float calculateLateReverbDelay(TemporalCharacteristics temporal) {
        // 初期反射の終わりから後部残響の開始まで
        float delay = temporal.lateReverbOnset / 1000.0f;  // msを秒に変換
        return clamp(delay, 0.0f, 0.1f);
    }

    /**
     * パラメータ9: Room Rolloff Factor（距離減衰係数）
     */
    private static float calculateRoomRolloff(SpatialCharacteristics spatial, boolean isIndoor) {
        float rolloff = 2.0f;

        if (!isIndoor) {
            rolloff = 8.0f;  // 屋外は急減衰
        } else {
            float volume = spatial.estimatedVolume;
            if (volume < 100f) {
                rolloff = 3.0f;  // 小部屋: 急な減衰
            } else if (volume < 1000f) {
                rolloff = 1.5f;  // 中空間: 標準
            } else {
                rolloff = 0.8f;  // 大空間: 緩やか
            }
        }

        return clamp(rolloff, 0.0f, 10.0f);
    }

    /**
     * パラメータ10: Air Absorption HF（空気吸収）
     */
    private static float calculateAirAbsorption(SpatialCharacteristics spatial) {
        float absorption = spatial.averageWallDistance * 0.05f;

        // 大空間ほど高周波が減衰
        if (spatial.estimatedVolume > 1000f) {
            absorption *= 2.0f;
        }

        return clamp(absorption, 0.0f, 10.0f);
    }

    /**
     * パラメータ11: HF Reference（高周波基準周波数）
     */
    private static float calculateHFReference(MaterialCharacteristics material) {
        float hfRef = 5000.0f;  // デフォルト

        switch (material.dominantMaterial) {
            case WOOD:
                hfRef = 3000.0f;
                break;
            case STONE:
                hfRef = 6000.0f;
                break;
            case METAL:
                hfRef = 10000.0f;
                break;
            case FABRIC:
                hfRef = 2000.0f;
                break;
            case GLASS:
                hfRef = 7000.0f;
                break;
        }

        return clamp(hfRef, 1000.0f, 20000.0f);
    }

    /**
     * パラメータ12: LF Reference（低周波基準周波数）
     */
    private static float calculateLFReference(SpatialCharacteristics spatial) {
        // 小空間ほど低周波の基準が高い
        float lfRef = 250.0f / (float) Math.pow(spatial.estimatedVolume / 100.0f, 0.3f);
        return clamp(lfRef, 20.0f, 1000.0f);
    }

    /**
     * パラメータ13: Room Size（空間サイズ感）
     */
    private static float calculateRoomSize(SpatialCharacteristics spatial) {
        // 対数スケーリング
        float roomSize = (float) (Math.log10(spatial.estimatedVolume + 1.0) / 4.0);
        return clamp(roomSize, 0.0f, 1.0f);
    }

    /**
     * 値をクランプ
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
