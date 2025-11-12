package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Ray Tracing結果から詳細な環境特性を抽出
 * リバーブの質感を決定する重要な分析クラス
 */
public class DetailedReflectionAnalyzer {

    /**
     * 空間特性
     */
    public static class SpatialCharacteristics {
        public float estimatedVolume;           // 推定体積（m³）
        public Vec3 roomDimensions;             // 推定寸法（幅×奥行×高さ）
        public float averageWallDistance;       // 平均壁距離（m）
        public float geometricComplexity;       // 形状複雑度（0.0-1.0）
        public float surfaceIrregularity;       // 表面不規則性（0.0-1.0）
        public int distinctReflectionClusters;  // 反射音クラスター数
    }

    /**
     * 材質特性
     */
    public static class MaterialCharacteristics {
        public float averageAbsorption250Hz;    // 低周波平均吸音率
        public float averageAbsorption1000Hz;   // 中周波平均吸音率
        public float averageAbsorption4000Hz;   // 高周波平均吸音率
        public float hfDecayRatio;              // 高周波減衰比（4kHz/1kHz）
        public float lfDecayRatio;              // 低周波減衰比（250Hz/1kHz）
        public MaterialType dominantMaterial;   // 主要材質タイプ
        public float materialHardness;          // 材質硬度（0.0-1.0）
    }

    /**
     * 材質タイプ
     */
    public enum MaterialType {
        STONE,      // 石系
        WOOD,       // 木系
        METAL,      // 金属系
        FABRIC,     // 布系
        GLASS,      // ガラス系
        MIXED       // 混合
    }

    /**
     * 拡散特性
     */
    public static class DiffusionCharacteristics {
        public float spatialDiffusion;          // 空間的拡散度（方向均等性）
        public float temporalDiffusion;         // 時間的拡散度（時間分散）
        public float energyDistribution;        // エネルギー分布均等性
        public boolean hasFlutterEcho;          // フラッターエコーの有無
        public boolean hasStandingWaves;        // 定在波の有無
        public float modalDensity;              // モード密度
    }

    /**
     * 時間特性
     */
    public static class TemporalCharacteristics {
        public float firstReflectionDelay;      // 最初の反射到達時間（ms）
        public float earlyReflectionDensity;    // 初期反射の密度（個/ms）
        public float earlyToLateRatio;          // 初期反射/後部残響のエネルギー比
        public float lateReverbOnset;           // 後部残響開始時間（ms）
        public float rt60;                      // 残響時間（60dB減衰）
        public float edt;                       // Early Decay Time（初期減衰時間）
    }

    /**
     * 空間特性を解析
     */
    public static SpatialCharacteristics analyzeSpatialCharacteristics(List<ReflectionData> reflections) {
        SpatialCharacteristics spatial = new SpatialCharacteristics();

        if (reflections.isEmpty()) {
            // デフォルト値
            spatial.estimatedVolume = 100.0f;
            spatial.roomDimensions = new Vec3(5, 3, 5);
            spatial.averageWallDistance = 2.5f;
            spatial.geometricComplexity = 0.5f;
            spatial.surfaceIrregularity = 0.5f;
            spatial.distinctReflectionClusters = 1;
            return spatial;
        }

        // 反射点の分布から空間境界を推定
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        float totalDistance = 0.0f;

        for (ReflectionData ref : reflections) {
            Vec3 pos = ref.reflectionPoint;
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minY = Math.min(minY, pos.y);
            maxY = Math.max(maxY, pos.y);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
            totalDistance += ref.totalPathLength;
        }

        // 寸法と体積を計算
        float width = (float) (maxX - minX);
        float height = (float) (maxY - minY);
        float depth = (float) (maxZ - minZ);
        spatial.roomDimensions = new Vec3(width, height, depth);
        spatial.estimatedVolume = width * height * depth;

        // 平均壁距離
        spatial.averageWallDistance = totalDistance / reflections.size();

        // 形状複雑度（反射点の分散）
        Vec3 center = new Vec3((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        double variance = 0.0;
        for (ReflectionData ref : reflections) {
            double dist = ref.reflectionPoint.distanceTo(center);
            variance += dist * dist;
        }
        variance /= reflections.size();
        spatial.geometricComplexity = (float) Math.min(1.0, variance / 100.0);

        // 表面不規則性（法線ベクトルの分散）
        Vec3 averageNormal = Vec3.ZERO;
        for (ReflectionData ref : reflections) {
            averageNormal = averageNormal.add(ref.reflectionNormal);
        }
        averageNormal = averageNormal.normalize();

        double normalVariance = 0.0;
        for (ReflectionData ref : reflections) {
            double dot = ref.reflectionNormal.dot(averageNormal);
            normalVariance += (1.0 - dot) * (1.0 - dot);
        }
        normalVariance /= reflections.size();
        spatial.surfaceIrregularity = (float) Math.min(1.0, normalVariance * 2.0);

        // クラスター数（簡易推定）
        spatial.distinctReflectionClusters = Math.max(1, reflections.size() / 4);

        return spatial;
    }

    /**
     * 材質特性を解析
     */
    public static MaterialCharacteristics analyzeMaterialCharacteristics(List<ReflectionData> reflections) {
        MaterialCharacteristics material = new MaterialCharacteristics();

        if (reflections.isEmpty()) {
            // デフォルト値
            material.averageAbsorption250Hz = 0.15f;
            material.averageAbsorption1000Hz = 0.15f;
            material.averageAbsorption4000Hz = 0.20f;
            material.hfDecayRatio = 1.0f;
            material.lfDecayRatio = 1.0f;
            material.dominantMaterial = MaterialType.MIXED;
            material.materialHardness = 0.5f;
            return material;
        }

        // 各反射ブロックの吸音率を重み付け平均
        float totalWeight = 0.0f;
        float weighted250Hz = 0.0f;
        float weighted1000Hz = 0.0f;
        float weighted4000Hz = 0.0f;

        Map<MaterialType, Float> materialCounts = new HashMap<>();
        for (MaterialType type : MaterialType.values()) {
            materialCounts.put(type, 0.0f);
        }

        for (ReflectionData ref : reflections) {
            float weight = ref.gain;  // ゲインで重み付け
            totalWeight += weight;

            Block block = ref.reflectionBlock;
            float absorption = BlockAcousticDatabase.getAbsorptionCoefficient(block);

            weighted250Hz += absorption * weight;
            weighted1000Hz += absorption * weight;
            weighted4000Hz += absorption * weight * 1.5f;  // 高周波はより吸収されやすい

            // 材質タイプをカウント
            MaterialType type = classifyMaterial(block);
            materialCounts.put(type, materialCounts.get(type) + weight);
        }

        if (totalWeight > 0) {
            material.averageAbsorption250Hz = weighted250Hz / totalWeight;
            material.averageAbsorption1000Hz = weighted1000Hz / totalWeight;
            material.averageAbsorption4000Hz = weighted4000Hz / totalWeight;
        }

        // 減衰比を計算
        material.hfDecayRatio = material.averageAbsorption4000Hz / Math.max(0.01f, material.averageAbsorption1000Hz);
        material.lfDecayRatio = material.averageAbsorption250Hz / Math.max(0.01f, material.averageAbsorption1000Hz);

        // 主要材質タイプを決定
        material.dominantMaterial = MaterialType.MIXED;
        float maxCount = 0.0f;
        for (Map.Entry<MaterialType, Float> entry : materialCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                material.dominantMaterial = entry.getKey();
            }
        }

        // 材質硬度（吸音率の逆）
        material.materialHardness = 1.0f - material.averageAbsorption1000Hz;

        return material;
    }

    /**
     * ブロックを材質タイプに分類
     */
    private static MaterialType classifyMaterial(Block block) {
        // 石系
        if (block == Blocks.STONE || block == Blocks.COBBLESTONE ||
            block == Blocks.GRANITE || block == Blocks.DIORITE || block == Blocks.ANDESITE ||
            block.toString().contains("stone") || block.toString().contains("brick")) {
            return MaterialType.STONE;
        }

        // 木系
        if (block.toString().contains("wood") || block.toString().contains("plank") ||
            block.toString().contains("log")) {
            return MaterialType.WOOD;
        }

        // 金属系
        if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK ||
            block.toString().contains("iron") || block.toString().contains("metal")) {
            return MaterialType.METAL;
        }

        // 布系
        if (block.toString().contains("wool") || block.toString().contains("carpet")) {
            return MaterialType.FABRIC;
        }

        // ガラス系
        if (block.toString().contains("glass")) {
            return MaterialType.GLASS;
        }

        return MaterialType.MIXED;
    }

    /**
     * 拡散特性を解析
     */
    public static DiffusionCharacteristics analyzeDiffusionCharacteristics(List<ReflectionData> reflections) {
        DiffusionCharacteristics diffusion = new DiffusionCharacteristics();

        if (reflections.isEmpty()) {
            diffusion.spatialDiffusion = 0.5f;
            diffusion.temporalDiffusion = 0.5f;
            diffusion.energyDistribution = 0.5f;
            diffusion.hasFlutterEcho = false;
            diffusion.hasStandingWaves = false;
            diffusion.modalDensity = 0.5f;
            return diffusion;
        }

        // 空間的拡散度（方向ベクトルの均等性）
        Vec3 sumDirection = Vec3.ZERO;
        for (ReflectionData ref : reflections) {
            sumDirection = sumDirection.add(ref.directionToListener);
        }
        double directionMagnitude = sumDirection.length();
        diffusion.spatialDiffusion = (float) (1.0 - directionMagnitude / reflections.size());

        // 時間的拡散度（到達時間の標準偏差）
        float averageDelay = 0.0f;
        for (ReflectionData ref : reflections) {
            averageDelay += ref.delayMs;
        }
        averageDelay /= reflections.size();

        float delayVariance = 0.0f;
        for (ReflectionData ref : reflections) {
            float diff = ref.delayMs - averageDelay;
            delayVariance += diff * diff;
        }
        delayVariance /= reflections.size();
        float delayStdDev = (float) Math.sqrt(delayVariance);
        diffusion.temporalDiffusion = Math.min(1.0f, delayStdDev / 50.0f);

        // エネルギー分布の均等性
        float averageGain = 0.0f;
        for (ReflectionData ref : reflections) {
            averageGain += ref.gain;
        }
        averageGain /= reflections.size();

        float gainVariance = 0.0f;
        for (ReflectionData ref : reflections) {
            float diff = ref.gain - averageGain;
            gainVariance += diff * diff;
        }
        gainVariance /= reflections.size();
        diffusion.energyDistribution = (float) (1.0 - Math.min(1.0, gainVariance * 10.0));

        // フラッターエコー検出（周期的な遅延パターン）
        diffusion.hasFlutterEcho = detectPeriodicPattern(reflections);

        // 定在波検出（特定周波数でのエネルギー集中）
        diffusion.hasStandingWaves = diffusion.hasFlutterEcho;  // 簡易判定

        // モード密度
        diffusion.modalDensity = Math.min(1.0f, reflections.size() / 16.0f);

        return diffusion;
    }

    /**
     * 周期的パターンを検出（フラッターエコー判定）
     */
    private static boolean detectPeriodicPattern(List<ReflectionData> reflections) {
        if (reflections.size() < 4) {
            return false;
        }

        // 遅延時間をソート
        List<Float> delays = new ArrayList<>();
        for (ReflectionData ref : reflections) {
            delays.add(ref.delayMs);
        }
        Collections.sort(delays);

        // 連続する遅延の差を計算
        List<Float> delayDiffs = new ArrayList<>();
        for (int i = 1; i < delays.size(); i++) {
            delayDiffs.add(delays.get(i) - delays.get(i - 1));
        }

        // 差の標準偏差が小さい = 周期的
        float avgDiff = 0.0f;
        for (float diff : delayDiffs) {
            avgDiff += diff;
        }
        avgDiff /= delayDiffs.size();

        float variance = 0.0f;
        for (float diff : delayDiffs) {
            float d = diff - avgDiff;
            variance += d * d;
        }
        variance /= delayDiffs.size();

        return variance < 1.0f;  // 標準偏差が1ms未満なら周期的
    }

    /**
     * 時間特性を解析
     */
    public static TemporalCharacteristics analyzeTemporalCharacteristics(
            List<ReflectionData> reflections,
            float estimatedVolume
    ) {
        TemporalCharacteristics temporal = new TemporalCharacteristics();

        if (reflections.isEmpty()) {
            temporal.firstReflectionDelay = 10.0f;
            temporal.earlyReflectionDensity = 0.1f;
            temporal.earlyToLateRatio = 0.5f;
            temporal.lateReverbOnset = 50.0f;
            temporal.rt60 = 0.5f;
            temporal.edt = 0.3f;
            return temporal;
        }

        // 最初の反射到達時間
        temporal.firstReflectionDelay = Float.MAX_VALUE;
        for (ReflectionData ref : reflections) {
            temporal.firstReflectionDelay = Math.min(temporal.firstReflectionDelay, ref.delayMs);
        }

        // 初期反射の密度
        float maxDelay = 0.0f;
        for (ReflectionData ref : reflections) {
            maxDelay = Math.max(maxDelay, ref.delayMs);
        }
        temporal.earlyReflectionDensity = reflections.size() / Math.max(1.0f, maxDelay);

        // 初期反射の総エネルギー
        float earlyEnergy = 0.0f;
        for (ReflectionData ref : reflections) {
            earlyEnergy += ref.gain;
        }

        // 後部残響の推定エネルギー（簡易計算）
        float lateEnergy = earlyEnergy * 0.5f;  // 初期反射の50%と仮定
        temporal.earlyToLateRatio = earlyEnergy / Math.max(0.01f, lateEnergy);

        // 後部残響開始時間（平均遅延 + 20ms）
        float avgDelay = 0.0f;
        for (ReflectionData ref : reflections) {
            avgDelay += ref.delayMs;
        }
        avgDelay /= reflections.size();
        temporal.lateReverbOnset = avgDelay + 20.0f;

        // RT60計算（簡易Sabine式）
        float surfaceArea = (float) Math.pow(estimatedVolume, 2.0 / 3.0) * 6.0f;
        float avgAbsorption = 0.15f;  // デフォルト
        float totalAbsorption = surfaceArea * avgAbsorption;
        temporal.rt60 = 0.161f * estimatedVolume / Math.max(1.0f, totalAbsorption);
        temporal.rt60 = Math.max(0.1f, Math.min(5.0f, temporal.rt60));

        // EDT（Early Decay Time）はRT60の約60-80%
        temporal.edt = temporal.rt60 * 0.7f;

        return temporal;
    }
}
