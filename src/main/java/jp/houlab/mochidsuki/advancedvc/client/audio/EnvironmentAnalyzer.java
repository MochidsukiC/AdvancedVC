package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 環境音響解析
 * リスナー周辺の空間を解析してリバーブパラメータを計算
 */
public class EnvironmentAnalyzer {

    /**
     * 環境情報
     */
    public static class EnvironmentInfo {
        public float volume;              // 空間体積（m³）
        public float avgAbsorption;       // 平均吸音係数
        public float rt60;                // 残響時間（秒）
        public boolean isIndoor;          // 屋内かどうか
        public float enclosureRatio;      // 閉鎖度（0.0=完全開放, 1.0=完全閉鎖）

        public EnvironmentInfo(float volume, float avgAbsorption, float rt60, boolean isIndoor, float enclosureRatio) {
            this.volume = volume;
            this.avgAbsorption = avgAbsorption;
            this.rt60 = rt60;
            this.isIndoor = isIndoor;
            this.enclosureRatio = enclosureRatio;
        }

        @Override
        public String toString() {
            return String.format("EnvironmentInfo{volume=%.1f m³, rt60=%.2f s, indoor=%s, enclosure=%.2f}",
                    volume, rt60, isIndoor, enclosureRatio);
        }
    }

    /**
     * リスナー位置の環境を解析
     * @param level ワールド
     * @param listenerPos リスナー位置
     * @return 環境情報
     */
    public static EnvironmentInfo analyzeEnvironment(Level level, Vec3 listenerPos) {
        if (level == null || listenerPos == null) {
            // デフォルト: 屋外
            return new EnvironmentInfo(1000f, 0.1f, 0.2f, false, 0.0f);
        }

        // サンプリング範囲（リスナー周辺の球状領域）
        int sampleRadius = 20; // 20ブロック（20m）- 広い空間も認識できるように拡大

        int airBlocks = 0;
        int solidBlocks = 0;
        float totalAbsorption = 0f;

        BlockPos centerPos = BlockPos.containing(listenerPos);

        // 球状サンプリング
        for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
            for (int dy = -sampleRadius; dy <= sampleRadius; dy++) {
                for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
                    // 球の内部のみサンプリング
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > sampleRadius * sampleRadius) {
                        continue;
                    }

                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    Block block = level.getBlockState(pos).getBlock();

                    if (level.getBlockState(pos).isAir()) {
                        airBlocks++;
                    } else {
                        solidBlocks++;
                        // ブロックの吸音係数を取得
                        float absorption = BlockAcousticDatabase.getAbsorptionCoefficient(block);
                        totalAbsorption += absorption;
                    }
                }
            }
        }

        int totalBlocks = airBlocks + solidBlocks;
        if (totalBlocks == 0) {
            // エラー回避
            return new EnvironmentInfo(1000f, 0.1f, 0.2f, false, 0.0f);
        }

        // 空間体積の推定（空気ブロックの数 × 1m³）
        float volume = Math.max(10f, airBlocks * 1.0f);

        // 平均吸音係数
        float avgAbsorption = solidBlocks > 0 ? totalAbsorption / solidBlocks : 0.1f;

        // 閉鎖度（固体ブロックの割合）
        float enclosureRatio = (float) solidBlocks / totalBlocks;

        // 屋内判定（閉鎖度が55%以上で屋内）
        // 屋外（地面のみ）: 約50%
        // 屋内（壁と天井）: 55%以上
        boolean isIndoor = enclosureRatio > 0.55f;

        // RT60計算（Sabine式）
        float rt60 = ReverbProcessor.calculateRT60(volume, avgAbsorption);

        // 体積に応じたRT60調整（広い空間ほど長い残響）
        if (isIndoor) {
            // 屋内: 体積に応じて残響時間を調整
            if (volume < 100f) {
                // 小部屋（10m³未満）: 0.3-0.5秒
                rt60 = Math.min(rt60, 0.5f);
            } else if (volume < 500f) {
                // 中部屋（100-500m³）: 0.5-1.0秒
                rt60 = Math.min(rt60, 1.0f);
            } else if (volume < 2000f) {
                // 大部屋（500-2000m³）: 1.0-2.0秒
                rt60 = Math.min(rt60, 2.0f);
            } else {
                // 巨大空間（2000m³以上）: 2.0-3.0秒
                rt60 = Math.min(rt60, 3.0f);
            }
        } else {
            // 屋外: リバーブなし（0.1秒以下）
            rt60 = Math.min(rt60, 0.1f);
        }

        return new EnvironmentInfo(volume, avgAbsorption, rt60, isIndoor, enclosureRatio);
    }

    /**
     * 環境情報からEFXReverbSettingsを生成
     * @param env 環境情報
     * @return リバーブ設定
     */
    public static EFXReverbSettings createReverbSettings(EnvironmentInfo env) {
        if (env == null) {
            return EFXReverbSettings.outdoor();
        }

        if (!env.isIndoor) {
            // 屋外: 最小限のリバーブ
            return EFXReverbSettings.outdoor();
        }

        // 屋内: RT60と体積から設定を生成
        EFXReverbSettings settings = EFXReverbSettings.fromRT60(env.rt60, env.volume);

        // 閉鎖度に応じて調整
        settings.density = env.enclosureRatio;
        settings.diffusion = Math.min(1.0f, env.enclosureRatio + 0.3f);

        // 吸音係数に応じてゲインを調整（指数関数的に減衰）
        // absorption=0.0（完全反射）→ reverbGain=1.0（100%）
        // absorption=0.5（中程度）→ reverbGain=0.25（25%）
        // absorption=0.8（羊毛）→ reverbGain=0.04（4%）→ ほぼDRY
        float reverbGain = (float) Math.pow(1.0f - env.avgAbsorption, 2.0);
        settings.lateReverbGain = Math.max(0.02f, Math.min(2.0f, reverbGain * 2.0f));

        return settings;
    }

    /**
     * レイスキャン結果からEFXリバーブ設定を生成
     * @param scan RayEnvironmentScannerの結果
     * @return EFXリバーブ設定
     */
    public static EFXReverbSettings createReverbSettingsFromScan(RayEnvironmentScanner.ScanResult scan) {
        if (scan == null) {
            return EFXReverbSettings.outdoor();
        }

        // RT60（Sabine）を使用。体積と平均吸音から算出
        float rt60 = ReverbProcessor.calculateRT60(Math.max(10f, scan.volumeEstimate), clamp01(scan.absorptionMean));

        // ベース設定をRT60と体積から生成
        EFXReverbSettings settings = EFXReverbSettings.fromRT60(rt60, Math.max(10f, scan.volumeEstimate));

        // 開放度（openness）が高いほど屋外的 → 密度/拡散を下げる
        float enclosure = 1.0f - clamp01(scan.openness);
        // ヒット率が低いほど空間の散逸が大きい → 密度/拡散も下げる
        float hit = clamp01(scan.hitRatio);
        settings.density = clamp01(0.15f + 0.85f * enclosure * (float) Math.sqrt(hit));
        settings.diffusion = clamp01(0.2f + 0.8f * enclosure * (float) Math.sqrt(hit));

        // 反射エネルギーの近似: ヒット率 × 平均反射率 × 閉鎖度
        float reflectivity = 1.0f - clamp01(scan.absorptionMean);
        float returnEnergy = hit * reflectivity * (float) Math.pow(enclosure, 1.2f);

        // WET量は返ってくるエネルギーに比例（過剰抑制を避けてスケール）
        float wet;
        if (enclosure > 0.4f && hit >= 0.15f) {
            // 室内寄りで反射面が一定以上ある場合は、最低限のWETを確保
            wet = Math.max(0.15f, returnEnergy * 3.0f);
        } else {
            // 開放気味/ヒット少の環境では控えめに
            wet = returnEnergy * 2.0f;
        }
        // しきい値未満ならほぼDRYにする（拡散が大きく音が戻らない環境）
        if (returnEnergy < 0.06f && scan.openness > 0.7f) {
            settings.lateReverbGain = 0.02f;
            settings.decayTime = Math.min(settings.decayTime, 0.18f);
        } else {
            settings.lateReverbGain = clamp(wet, 0.02f, 1.0f);
        }

        // 初期反射の遅延は最短ヒット距離から計算（上限20ms）
        float preDelaySec = clamp((scan.minHitDistance / 343.0f), 0.001f, 0.020f);
        settings.reflectionsDelay = preDelaySec;
        settings.lateReverbDelay = clamp(preDelaySec * 1.5f, 0.002f, 0.040f);

        // 非常に近い壁（極短プリディレイ: <=4ms）は「残響」ではなく「初期反射」扱い
        // → Late Reverb を抑制し、Early Reflections を強める
        if (preDelaySec <= 0.004f) {
            settings.lateReverbGain = Math.min(settings.lateReverbGain * 0.2f, 0.15f);
            settings.decayTime = Math.min(settings.decayTime, 0.18f);
            settings.reflectionsGain = clamp(settings.reflectionsGain + 0.12f, 0.0f, 1.0f);
        } else if (preDelaySec <= 0.008f) {
            settings.lateReverbGain = Math.min(settings.lateReverbGain * 0.5f, 0.3f);
            settings.decayTime = Math.min(settings.decayTime, 0.25f);
            settings.reflectionsGain = clamp(settings.reflectionsGain + 0.06f, 0.0f, 1.0f);
        }

        // OpenALの自動リバーブ減衰は無効化（距離WETは手動制御）
        settings.roomRolloffFactor = 0.0f;

        // 非常に開放的 or 低ヒット率の環境ではDecayを短く抑える
        if (scan.openness > 0.8f || hit < 0.2f) {
            settings.decayTime = Math.min(settings.decayTime, 0.25f);
        }

        return settings;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }
}
