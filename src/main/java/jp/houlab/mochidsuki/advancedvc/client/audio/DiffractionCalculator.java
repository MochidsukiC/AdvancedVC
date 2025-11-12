package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 音響回折計算機
 * Fresnel-Kirchhoff理論の簡易実装
 * 障害物の角を回り込む音の減衰を計算
 */
public class DiffractionCalculator {
    // 音速（m/s）
    private static final float SOUND_SPEED = 343.0f;

    /**
     * 回折による減衰を計算（簡略化版）
     * @param level ワールド
     * @param source 音源位置
     * @param listener 聴者位置
     * @param frequency 周波数（Hz）
     * @return 回折減衰係数（0.0=完全遮蔽, 1.0=減衰なし）
     */
    public static float calculateDiffraction(Level level, Vec3 source, Vec3 listener, float frequency) {
        // 直線経路をチェック
        if (hasDirectPath(level, source, listener)) {
            return 1.0f; // 直線経路があれば回折なし
        }

        // 簡略化版: 直線経路がない場合、距離に応じて減衰
        // 回折は低周波ほど効果的（波長が長いため）
        // 周波数が低いほど回折係数が大きい
        float wavelength = SOUND_SPEED / frequency; // 波長（m）
        double distance = source.distanceTo(listener);

        // 簡易回折係数: 距離と波長の関係
        // 近距離かつ低周波 → 回折しやすい
        float diffractionCoeff = (float) Math.min(wavelength / distance, 1.0);
        diffractionCoeff = (float) Math.pow(diffractionCoeff, 0.5); // 平方根で緩和

        // 最小値保証（完全に遮蔽されない）
        return Math.max(0.3f, diffractionCoeff);
    }

    /**
     * 直線経路が存在するかチェック
     */
    private static boolean hasDirectPath(Level level, Vec3 source, Vec3 listener) {
        Vec3 direction = listener.subtract(source).normalize();
        double distance = source.distanceTo(listener);
        Vec3 current = source;
        double step = 0.5;

        while (current.distanceTo(source) < distance) {
            BlockPos pos = BlockPos.containing(current);
            if (!level.getBlockState(pos).isAir()) {
                return false; // 障害物発見
            }
            current = current.add(direction.scale(step));
        }

        return true; // 直線経路あり
    }

    /**
     * 最も近い障害物エッジを探す（簡易版）
     */
    private static Vec3 findNearestEdge(Level level, Vec3 source, Vec3 listener) {
        Vec3 midpoint = source.add(listener).scale(0.5);

        // 中点周辺の空気ブロックを探す（簡易的なエッジ検出）
        double searchRadius = 3.0;
        Vec3 bestEdge = null;
        double minPathLength = Double.MAX_VALUE;

        for (double dx = -searchRadius; dx <= searchRadius; dx += 0.5) {
            for (double dy = -searchRadius; dy <= searchRadius; dy += 0.5) {
                for (double dz = -searchRadius; dz <= searchRadius; dz += 0.5) {
                    Vec3 testPoint = midpoint.add(dx, dy, dz);
                    BlockPos testPos = BlockPos.containing(testPoint);

                    // この点が空気で、隣接ブロックに障害物がある（＝エッジ）
                    if (level.getBlockState(testPos).isAir() && hasAdjacentSolid(level, testPos)) {
                        // 音源→エッジ→聴者の経路長
                        double pathLength = source.distanceTo(testPoint) + testPoint.distanceTo(listener);

                        // 両方のセグメントに障害物がない場合のみ
                        if (hasDirectPath(level, source, testPoint) &&
                            hasDirectPath(level, testPoint, listener)) {
                            if (pathLength < minPathLength) {
                                minPathLength = pathLength;
                                bestEdge = testPoint;
                            }
                        }
                    }
                }
            }
        }

        return bestEdge;
    }

    /**
     * 隣接ブロックに固体があるかチェック
     */
    private static boolean hasAdjacentSolid(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos adjacent = pos.offset(dx, dy, dz);
                    if (!level.getBlockState(adjacent).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Fresnel数を計算
     * N = 2 * δ / λ
     * δ: 経路差（直線経路との差）
     * λ: 波長
     */
    private static double calculateFresnelNumber(Vec3 source, Vec3 listener, Vec3 edge, float frequency) {
        // 波長（m）
        double wavelength = SOUND_SPEED / frequency;

        // 直線距離
        double directDistance = source.distanceTo(listener);

        // 回折経路の距離
        double diffractionDistance = source.distanceTo(edge) + edge.distanceTo(listener);

        // 経路差
        double pathDifference = diffractionDistance - directDistance;

        // Fresnel数
        return 2.0 * pathDifference / wavelength;
    }

    /**
     * Fresnel数から回折損失を計算
     * N < 0: 完全に見通せる（減衰小）
     * N = 0: エッジちょうど（中程度減衰）
     * N > 0: 完全に遮蔽（減衰大）
     */
    private static float calculateDiffractionLoss(double fresnelNumber) {
        // Fresnel-Kirchhoff回折の簡易近似
        // ITU-R P.526に基づく

        double N = fresnelNumber;
        double attenuation;

        if (N <= -0.8) {
            // 完全に見通せる
            attenuation = 0.0;
        } else if (N >= 1.6) {
            // 完全に遮蔽
            attenuation = 13.0 + 20.0 * Math.log10(N);
        } else {
            // 遷移領域（エッジ付近）
            attenuation = 6.9 + 20.0 * Math.log10(Math.sqrt(Math.pow(N - 0.1, 2) + 1.0) + N - 0.1);
        }

        // dBから係数に変換（0.0～1.0）
        double coefficient = Math.pow(10.0, -attenuation / 20.0);

        return (float) Math.max(0.0, Math.min(1.0, coefficient));
    }

    /**
     * 複数の周波数バンドに対して回折を計算
     * @param level ワールド
     * @param source 音源
     * @param listener 聴者
     * @param frequencies 各バンドの中心周波数
     * @return 各バンドの回折係数
     */
    public static float[] calculateBandDiffraction(Level level, Vec3 source, Vec3 listener, float[] frequencies) {
        float[] coefficients = new float[frequencies.length];

        for (int i = 0; i < frequencies.length; i++) {
            coefficients[i] = calculateDiffraction(level, source, listener, frequencies[i]);
        }

        return coefficients;
    }
}
