package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * レイベース環境スキャナ
 * プレイヤー位置から球面一様にレイを放射し、周辺地形から開放度・平均吸音などを推定する。
 */
public class RayEnvironmentScanner {
    // デバッグ可視化用の最新スキャンデータ
    public static class DebugRay {
        public final Vec3 start;
        public final Vec3 end;
        public final boolean hit;
        public DebugRay(Vec3 start, Vec3 end, boolean hit) {
            this.start = start; this.end = end; this.hit = hit;
        }
    }

    public static class DebugScanData {
        public final List<DebugRay> rays;
        public final long timestamp;
        public DebugScanData(List<DebugRay> rays, long timestamp) {
            this.rays = rays; this.timestamp = timestamp;
        }
    }

    private static volatile DebugScanData lastDebugScan = null;
    public static DebugScanData getLastDebugScan() { return lastDebugScan; }

    /**
     * スキャン結果
     */
    public static class ScanResult {
        public final float openness;          // 開放度（0.0=閉鎖、1.0=開放）
        public final float meanHitDistance;   // 平均ヒット距離（m）
        public final float hitRatio;          // ヒット率（0..1）
        public final float absorptionMean;    // ヒット面の平均吸音係数（0..1）
        public final float minHitDistance;    // 最小ヒット距離（m）
        public final float volumeEstimate;    // 近傍体積の推定値（m^3）

        public ScanResult(float openness, float meanHitDistance, float hitRatio,
                          float absorptionMean, float minHitDistance, float volumeEstimate) {
            this.openness = openness;
            this.meanHitDistance = meanHitDistance;
            this.hitRatio = hitRatio;
            this.absorptionMean = absorptionMean;
            this.minHitDistance = minHitDistance;
            this.volumeEstimate = volumeEstimate;
        }
    }

    /**
     * 環境をレイでスキャンする
     * @param level ワールド
     * @param origin 原点（通常はリスナー位置）
     * @param maxDistance 最大レイ距離（声量に基づく）
     * @param rayCount レイ本数（64～128推奨）
     * @param stepMeters ステップ長（m、0.5～1.0推奨）
     */
    public static ScanResult scan(Level level, Vec3 origin, float maxDistance, int rayCount, float stepMeters, long seed) {
        if (level == null || origin == null || maxDistance <= 0 || rayCount <= 0) {
            // フォールバック: 開放空間
            return new ScanResult(1.0f, maxDistance, 0.0f, 0.1f, maxDistance, maxDistance * maxDistance * maxDistance * 0.5f);
        }

        // 球面一様サンプル（フィボナッチ球）
        List<Vec3> directions = fibonacciSphere(rayCount, seed);
        List<DebugRay> debugRays = null;
        boolean visualize = jp.houlab.mochidsuki.advancedvc.client.ClientConfig.get().visualizeRays;
        if (visualize) debugRays = new ArrayList<>(rayCount);

        float totalDist = 0f;
        float totalHitDist = 0f;
        float minHitDist = maxDistance;
        int hitCount = 0;
        float totalAbsorption = 0f;

        for (Vec3 dir : directions) {
            float dist = castRay(level, origin, dir, maxDistance, stepMeters);
            // ヒット距離は maxDistance 未満の場合のみヒットとみなす
            if (dist < maxDistance) {
                hitCount++;
                totalHitDist += dist;
                if (dist < minHitDist) {
                    minHitDist = dist;
                }
                // ヒット時のブロック吸音係数を取得（最終サンプル位置のブロック）
                Vec3 p = origin.add(dir.scale(dist));
                BlockPos bp = BlockPos.containing(p);
                Block block = level.getBlockState(bp).getBlock();
                totalAbsorption += BlockAcousticDatabase.getAbsorptionCoefficient(block);
            }
            // 到達距離（ヒットならその距離、未ヒットならmaxDistance）
            totalDist += Math.min(dist, maxDistance);

            if (visualize) {
                float drawDist = Math.min(dist, maxDistance);
                Vec3 end = origin.add(dir.scale(drawDist));
                debugRays.add(new DebugRay(origin, end, dist < maxDistance));
            }
        }

        float meanDist = totalDist / rayCount;
        float openness = clamp(meanDist / maxDistance, 0f, 1f);
        float hitRatio = (float) hitCount / (float) rayCount;
        float meanHitDistance = hitCount > 0 ? (totalHitDist / hitCount) : maxDistance;
        float absorptionMean = hitCount > 0 ? (totalAbsorption / hitCount) : 0.1f;

        // 体積推定（直感的モデル） V ≈ k * meanDist^3
        float k = 0.5f;
        float volumeEstimate = k * meanDist * meanDist * meanDist;

        ScanResult result = new ScanResult(openness, meanHitDistance, hitRatio, absorptionMean, minHitDist, volumeEstimate);
        if (visualize && debugRays != null) {
            lastDebugScan = new DebugScanData(debugRays, System.currentTimeMillis());
        }
        return result;
    }

    // DDA的にボクセルを進む簡易レイキャスト（ブロックステップ）
    private static float castRay(Level level, Vec3 origin, Vec3 dir, float maxDistance, float stepMeters) {
        // 正規化（保険）
        Vec3 nd = dir.normalize();
        double steps = Math.ceil(maxDistance / stepMeters);
        Vec3 p = origin;
        for (int i = 1; i <= (int) steps; i++) {
            p = p.add(nd.scale(stepMeters));
            BlockPos pos = BlockPos.containing(p);
            if (!level.getBlockState(pos).isAir()) {
                // 最初のヒット
                return (float) origin.distanceTo(p);
            }
        }
        return maxDistance;
    }

    // フィボナッチ球で一様分布の方向ベクトルを生成
    private static List<Vec3> fibonacciSphere(int samples, long seed) {
        List<Vec3> dirs = new ArrayList<>(samples);
        // 乱数ジッタは簡易に位相をずらす
        double rnd = (seed % 1000) / 1000.0; // 0..1
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < samples; i++) {
            double y = 1 - (i + 0.5) * (2.0 / samples);
            double r = Math.sqrt(Math.max(0.0, 1 - y * y));
            double theta = (i + rnd) * phi;
            double x = Math.cos(theta) * r;
            double z = Math.sin(theta) * r;
            dirs.add(new Vec3(x, y, z));
        }
        return dirs;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
