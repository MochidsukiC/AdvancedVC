package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.api.AdvancedProximityChatAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * クライアントサイド音響シミュレーションエンジン
 * 音源から聴者までの音響経路を計算し、DSPパラメータを決定
 */
public class AcousticSimulationEngine {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_RAY_BOUNCES = 3;           // 最大反射回数
    private static final float RAY_STEP_SIZE = 0.5f;        // レイトレーシングのステップサイズ
    private static final float AIR_ABSORPTION = 0.001f;     // 空気による吸収（1mあたり）

    /**
     * 音響シミュレーションを実行し、DSPパラメータを計算
     * @param sourcePos 音源位置
     * @param listenerPos 聴者位置
     * @param initialVolume 初期音量
     * @return DSPパラメータ
     */
    public DSPParameters simulate(Vec3 sourcePos, Vec3 listenerPos, float initialVolume) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return new DSPParameters();
        }

        DSPParameters params = new DSPParameters();

        // 直線距離
        double distance = sourcePos.distanceTo(listenerPos);
        params.distance = distance;

        // 距離減衰
        params.volume = calculateDistanceAttenuation(initialVolume, distance);

        // レイトレーシングでブロックによる遮蔽を計算
        RayTraceResult rayResult = traceAcousticRay(level, sourcePos, listenerPos);

        // 遮蔽による音量減衰
        params.volume *= rayResult.transmissionCoefficient;

        // EQ（周波数特性）調整
        params.lowPassFilter = calculateLowPassCutoff(rayResult.totalAbsorption);
        params.highPassFilter = calculateHighPassCutoff(rayResult.totalAbsorption);

        // リバーブ（反射）
        params.reverbAmount = calculateReverbAmount(rayResult.totalReflection, distance);
        params.reverbDelay = calculateReverbDelay(distance);

        return params;
    }

    /**
     * 音響レイトレーシング
     */
    private RayTraceResult traceAcousticRay(Level level, Vec3 start, Vec3 end) {
        RayTraceResult result = new RayTraceResult();

        Vec3 direction = end.subtract(start).normalize();
        double totalDistance = start.distanceTo(end);
        Vec3 currentPos = start;
        double traveledDistance = 0.0;

        // レイを進める
        while (traveledDistance < totalDistance) {
            BlockPos blockPos = BlockPos.containing(currentPos);
            BlockState blockState = level.getBlockState(blockPos);
            Block block = blockState.getBlock();

            // ブロックが空気でない場合
            if (!blockState.isAir()) {
                // 音響特性を取得
                AdvancedProximityChatAPI.AcousticProperties props =
                        AdvancedProximityChatAPI.getInstance().getAcousticProperties(block);

                // 吸収と反射を累積
                result.totalAbsorption += props.absorption;
                result.totalReflection += props.reflection;

                // 透過率を計算（吸収されなかった分が透過）
                float transmission = 1.0f - props.absorption;
                result.transmissionCoefficient *= transmission;

                // 完全に遮蔽された場合は終了
                if (result.transmissionCoefficient < 0.01f) {
                    break;
                }
            }

            // レイを進める
            currentPos = currentPos.add(direction.scale(RAY_STEP_SIZE));
            traveledDistance += RAY_STEP_SIZE;
        }

        // 空気による吸収
        result.totalAbsorption += (float) (totalDistance * AIR_ABSORPTION);

        return result;
    }

    /**
     * 距離減衰を計算
     */
    private float calculateDistanceAttenuation(float initialVolume, double distance) {
        // 逆二乗則 + 最小音量保証
        float attenuation = (float) (initialVolume / (1.0 + 0.15 * distance * distance));
        return Math.max(attenuation, 0.01f);
    }

    /**
     * ローパスフィルターのカットオフ周波数を計算
     * 吸収が大きいほど高周波が減衰
     */
    private float calculateLowPassCutoff(float absorption) {
        // 吸収0: 20kHz, 吸収1.0: 500Hz
        float minCutoff = 500f;
        float maxCutoff = 20000f;
        return maxCutoff - (absorption * (maxCutoff - minCutoff));
    }

    /**
     * ハイパスフィルターのカットオフ周波数を計算
     */
    private float calculateHighPassCutoff(float absorption) {
        // 低周波は比較的透過しやすい
        float minCutoff = 20f;
        float maxCutoff = 200f;
        return minCutoff + (absorption * (maxCutoff - minCutoff));
    }

    /**
     * リバーブ量を計算
     */
    private float calculateReverbAmount(float totalReflection, double distance) {
        // 反射が多く、距離が遠いほどリバーブが強い
        float baseReverb = totalReflection * 0.3f;
        float distanceFactor = (float) Math.min(distance / 50.0, 1.0);
        return Math.min(baseReverb * (1.0f + distanceFactor), 1.0f);
    }

    /**
     * リバーブディレイを計算（ミリ秒）
     */
    private float calculateReverbDelay(double distance) {
        // 距離に基づくディレイ（音速: 約340m/s）
        float baseDelay = 20f; // 最小20ms
        float distanceDelay = (float) (distance / 340.0 * 1000.0);
        return Math.min(baseDelay + distanceDelay, 200f); // 最大200ms
    }

    /**
     * レイトレーシング結果
     */
    private static class RayTraceResult {
        float totalAbsorption = 0.0f;
        float totalReflection = 0.0f;
        float transmissionCoefficient = 1.0f; // 透過率（0.0 = 完全遮蔽, 1.0 = 遮蔽なし）
    }

    /**
     * DSPパラメータ
     */
    public static class DSPParameters {
        public double distance = 0.0;
        public float volume = 1.0f;
        public float lowPassFilter = 20000f;  // Hz
        public float highPassFilter = 20f;     // Hz
        public float reverbAmount = 0.0f;      // 0.0 - 1.0
        public float reverbDelay = 0.0f;       // ms

        @Override
        public String toString() {
            return String.format("DSP[dist=%.1f, vol=%.2f, lpf=%.0fHz, hpf=%.0fHz, reverb=%.2f@%.0fms]",
                    distance, volume, lowPassFilter, highPassFilter, reverbAmount, reverbDelay);
        }
    }
}
