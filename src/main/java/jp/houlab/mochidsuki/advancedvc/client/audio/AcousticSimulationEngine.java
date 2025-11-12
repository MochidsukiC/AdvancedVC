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
    private static final float RAY_STEP_SIZE = 1.0f;        // レイトレーシングのステップサイズ（音が通りやすくするため1.0mに拡大）
    private static final float AIR_ABSORPTION = 0.001f;     // 空気による吸収（1mあたり）
    private static final float MIN_TRANSMISSION = 0.1f;     // 最小透過率（完全遮蔽を防ぐ）

    /**
     * 音響シミュレーションを実行し、DSPパラメータを計算（Phase 2拡張版）
     * @param sourcePos 音源位置
     * @param listenerPos 聴者位置
     * @param maxDistance 最大到達距離
     * @return DSPパラメータ
     */
    public DSPParameters simulate(Vec3 sourcePos, Vec3 listenerPos, float maxDistance) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return new DSPParameters();
        }

        DSPParameters params = new DSPParameters();

        // 直線距離
        double distance = sourcePos.distanceTo(listenerPos);
        params.distance = distance;

        // LODレベルの決定
        params.lodLevel = AcousticLOD.determineLOD(distance);
        int numBands = params.lodLevel.getNumBands();

        // 距離減衰（maxDistanceを基準に計算）
        float distanceAttenuation = calculateDistanceAttenuation(distance, maxDistance);
        params.volume = distanceAttenuation;

        LOGGER.debug("=== ACOUSTIC SIMULATION (Phase 2) ===");
        LOGGER.debug("Distance: {}, LOD: {}, Bands: {}", distance, params.lodLevel, numBands);

        // 周波数バンドごとの減衰を初期化（全て1.0からスタート）
        float[] bandAttenuations = new float[numBands];
        for (int i = 0; i < numBands; i++) {
            bandAttenuations[i] = 1.0f;
        }

        // バンド中心周波数を取得
        float[] centerFrequencies = AcousticLOD.getBandCenterFrequencies(params.lodLevel);

        // === 1. ブロック透過（Mass Law） ===
        RayTraceResult rayResult = traceAcousticRayWithFrequency(level, sourcePos, listenerPos, centerFrequencies);
        for (int i = 0; i < numBands; i++) {
            bandAttenuations[i] *= rayResult.bandTransmission[i];
        }

        // === 2. 空気吸収（ISO 9613-1） ===
        if (AcousticLOD.isFeatureEnabled(params.lodLevel, "air_absorption")) {
            float[] airAbsorptions = AirAbsorption.calculateBandAbsorption((float) distance, 20f, 50f);
            for (int i = 0; i < Math.min(numBands, airAbsorptions.length); i++) {
                bandAttenuations[i] *= airAbsorptions[i];
            }
        }

        // === 3. 回折（Fresnel-Kirchhoff） ===
        if (AcousticLOD.isFeatureEnabled(params.lodLevel, "diffraction")) {
            float[] diffractionCoeffs = DiffractionCalculator.calculateBandDiffraction(
                    level, sourcePos, listenerPos, centerFrequencies);
            for (int i = 0; i < numBands; i++) {
                bandAttenuations[i] *= diffractionCoeffs[i];
            }
        }

        // バンド減衰を設定
        params.bandAttenuations = bandAttenuations;

        // 全体の音量（全バンドの平均）
        float averageAttenuation = 0f;
        for (float att : bandAttenuations) {
            averageAttenuation += att;
        }
        averageAttenuation /= numBands;
        params.volume *= averageAttenuation;

        // EQ（周波数特性）調整（後方互換用、バンド処理がある場合は参考値）
        params.lowPassFilter = calculateLowPassCutoff(rayResult.totalAbsorption);
        params.highPassFilter = calculateHighPassCutoff(rayResult.totalAbsorption);

        // リバーブ（反射）
        if (AcousticLOD.isFeatureEnabled(params.lodLevel, "reverb")) {
            params.reverbAmount = calculateReverbAmount(rayResult.totalReflection, distance);
            params.reverbDelay = calculateReverbDelay(distance);
        }

        LOGGER.debug("Volume: {}, BandAtts: {}", params.volume, bandAttenuations);
        LOGGER.debug("======================================");

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
     * 距離減衰を計算（物理ベース：修正逆二乗則）
     *
     * 標準的な逆二乗則 (I ∝ 1/r²) をゲーム用に調整:
     * I = I₀ / (1 + (r/r₀)²)
     *
     * これにより:
     * - r=0: I = I₀ (100%)
     * - r=r₀: I = I₀/2 (50%)  ← r₀ = maxDistance/2
     * - r→∞: I → 0 (0%)
     *
     * maxDistanceは声量による到達距離を表し、
     * その半分の距離で音量が50%になるという物理的な減衰を実現
     */
    private float calculateDistanceAttenuation(double distance, float maxDistance) {
        // r₀ = maxDistance / 2 とする（この距離で音量が半分になる）
        float referenceDistance = maxDistance / 2.0f;

        // 修正逆二乗則: I = I₀ / (1 + (r/r₀)²)
        float ratio = (float) (distance / referenceDistance);
        float attenuation = 1.0f / (1.0f + ratio * ratio);

        return attenuation;
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
     * リバーブ量を計算（調整版：より強いリバーブ）
     */
    private float calculateReverbAmount(float totalReflection, double distance) {
        // 基本リバーブ量を増やす（0.3f → 0.6f）
        float baseReverb = Math.max(0.2f, totalReflection * 0.6f);
        float distanceFactor = (float) Math.min(distance / 30.0, 1.0); // 30mまでで最大
        float reverbAmount = baseReverb * (1.0f + distanceFactor);
        // 最大値を引き上げ（1.0f → 0.7f、クリッピング防止のため）
        return Math.min(reverbAmount, 0.7f);
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
     * 周波数依存の音響レイトレーシング（Phase 2）
     */
    private RayTraceResult traceAcousticRayWithFrequency(Level level, Vec3 start, Vec3 end, float[] frequencies) {
        RayTraceResult result = new RayTraceResult();
        result.bandTransmission = new float[frequencies.length];

        // 各バンドの透過率を1.0で初期化
        for (int i = 0; i < frequencies.length; i++) {
            result.bandTransmission[i] = 1.0f;
        }

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
                // 各周波数バンドに対してMass Law適用
                for (int i = 0; i < frequencies.length; i++) {
                    float transmissionLossDb = BlockAcousticDatabase.calculateTransmissionLoss(block, frequencies[i]);
                    float transmission = BlockAcousticDatabase.transmissionLossToCoefficient(transmissionLossDb);
                    result.bandTransmission[i] *= transmission;
                }

                // 吸収と反射を累積（後方互換用）
                float absorption = BlockAcousticDatabase.getAbsorptionCoefficient(block);
                float reflection = BlockAcousticDatabase.getReflectionCoefficient(block);
                result.totalAbsorption += absorption;
                result.totalReflection += reflection;

                // 全バンドが最小透過率を下回った場合は終了
                // ただし、最小透過率は保証する
                boolean allBlocked = true;
                for (int i = 0; i < result.bandTransmission.length; i++) {
                    if (result.bandTransmission[i] < MIN_TRANSMISSION) {
                        result.bandTransmission[i] = MIN_TRANSMISSION;
                    }
                    if (result.bandTransmission[i] > MIN_TRANSMISSION * 1.1f) {
                        allBlocked = false;
                    }
                }
                // 最小透過率があるため、完全遮蔽での終了は行わない
                // if (allBlocked) {
                //     break;
                // }
            }

            // レイを進める
            currentPos = currentPos.add(direction.scale(RAY_STEP_SIZE));
            traveledDistance += RAY_STEP_SIZE;
        }

        // 全バンドの平均透過率を計算（後方互換用）
        float avgTransmission = 0f;
        for (float trans : result.bandTransmission) {
            avgTransmission += trans;
        }
        result.transmissionCoefficient = avgTransmission / result.bandTransmission.length;

        return result;
    }

    /**
     * レイトレーシング結果（拡張版：周波数バンド対応）
     */
    private static class RayTraceResult {
        float totalAbsorption = 0.0f;
        float totalReflection = 0.0f;
        float transmissionCoefficient = 1.0f; // 透過率（0.0 = 完全遮蔽, 1.0 = 遮蔽なし）
        float[] bandTransmission = null;       // 周波数バンドごとの透過率
    }

    /**
     * DSPパラメータ（拡張版：周波数バンド対応 + ドップラー効果）
     */
    public static class DSPParameters {
        public double distance = 0.0;
        public float volume = 1.0f;
        public float lowPassFilter = 20000f;  // Hz（後方互換用）
        public float highPassFilter = 20f;     // Hz（後方互換用）
        public float reverbAmount = 0.0f;      // 0.0 - 1.0
        public float reverbDelay = 0.0f;       // ms

        // Phase 2: 周波数バンド対応
        public float[] bandAttenuations = null;  // 各バンドの減衰係数（0.0～1.0）
        public AcousticLOD.LODLevel lodLevel = AcousticLOD.LODLevel.HIGH;

        // Phase 3: ドップラー効果
        public float dopplerFactor = 1.0f;  // 周波数シフト係数（1.0=変化なし、>1.0=高音、<1.0=低音）

        @Override
        public String toString() {
            if (bandAttenuations != null) {
                return String.format("DSP[dist=%.1f, vol=%.2f, LOD=%s, bands=%d, doppler=%.3f, reverb=%.2f@%.0fms]",
                        distance, volume, lodLevel, bandAttenuations.length, dopplerFactor, reverbAmount, reverbDelay);
            }
            return String.format("DSP[dist=%.1f, vol=%.2f, doppler=%.3f, lpf=%.0fHz, hpf=%.0fHz, reverb=%.2f@%.0fms]",
                    distance, volume, dopplerFactor, lowPassFilter, highPassFilter, reverbAmount, reverbDelay);
        }
    }

    /**
     * ドップラー効果の周波数シフト係数を計算
     * @param sourceVelocity 音源の速度ベクトル（m/s）
     * @param listenerVelocity 聴者の速度ベクトル（m/s）
     * @param direction 音源から聴者への単位方向ベクトル
     * @return 周波数シフト係数（1.0=変化なし）
     */
    public static float calculateDopplerFactor(Vec3 sourceVelocity, Vec3 listenerVelocity, Vec3 direction) {
        // 音速（m/s）
        final float SOUND_SPEED = 343.0f;

        // 音源と聴者の相対速度（音波の伝播方向成分）
        double sourceRadialVelocity = sourceVelocity.dot(direction);
        double listenerRadialVelocity = listenerVelocity.dot(direction);

        // ドップラー効果の式: f' = f × (v + v_listener) / (v - v_source)
        // ここで v は音速、v_listener は聴者が音源に近づく速度（正）、v_source は音源が聴者に近づく速度（正）
        double numerator = SOUND_SPEED + listenerRadialVelocity;
        double denominator = SOUND_SPEED - sourceRadialVelocity;

        // ゼロ除算回避
        if (Math.abs(denominator) < 0.1) {
            denominator = 0.1;
        }

        float factor = (float) (numerator / denominator);

        // 実用範囲に制限（0.5倍～2.0倍）
        return Math.max(0.5f, Math.min(2.0f, factor));
    }
}
