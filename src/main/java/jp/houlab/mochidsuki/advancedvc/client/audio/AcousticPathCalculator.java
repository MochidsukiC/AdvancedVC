package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 音響パス計算機（OpenAL EFX統合版）
 * オクルージョン（遮蔽）と回折（回り込み）を計算
 */
public class AcousticPathCalculator {

    // 音速（m/s）
    private static final float SOUND_SPEED = 343.0f;

    // レイトレーシングのステップサイズ
    private static final double RAY_STEP_SIZE = 1.0;

    /**
     * 音響パス情報
     */
    public static class PathResult {
        public float occlusionGain;      // オクルージョンゲイン（0.0～1.0）
        public float gainHF;              // 高周波ゲイン（0.0～1.0）
        public float diffractionCoeff;   // 回折係数（0.0～1.0）
        public boolean isMuffledSound;   // 貫通音かどうか（回折経路がない）

        public PathResult(float occlusionGain, float gainHF, float diffractionCoeff, boolean isMuffledSound) {
            this.occlusionGain = occlusionGain;
            this.gainHF = gainHF;
            this.diffractionCoeff = diffractionCoeff;
            this.isMuffledSound = isMuffledSound;
        }
    }

    /**
     * 音源からリスナーへの音響パスを計算
     * @param level ワールド
     * @param sourcePos 音源位置
     * @param listenerPos リスナー位置
     * @return 音響パス情報
     */
    public static PathResult calculatePath(Level level, Vec3 sourcePos, Vec3 listenerPos) {
        if (level == null || sourcePos == null || listenerPos == null) {
            // エラー時は遮蔽なし
            return new PathResult(1.0f, 1.0f, 1.0f, false);
        }

        // 直線経路をレイトレーシング
        boolean hasDirectPath = hasDirectPath(level, sourcePos, listenerPos);

        if (hasDirectPath) {
            // 直線経路がある場合: 遮蔽なし、回折なし、貫通音でない
            return new PathResult(1.0f, 1.0f, 1.0f, false);
        } else {
            // 直線経路がない場合: オクルージョンと回折を計算
            float occlusion = calculateOcclusion(level, sourcePos, listenerPos);
            float gainHF = calculateHighFrequencyAttenuation(level, sourcePos, listenerPos);
            float diffraction = DiffractionCalculator.calculateDiffraction(level, sourcePos, listenerPos, 1000f);

            // 回折経路の有無を判定（回折係数が低い = 回り込めない = 貫通音）
            boolean isMuffledSound = (diffraction < 0.4f);

            if (isMuffledSound) {
                // 貫通音: 強いローパスフィルタとボリューム減少
                // 高周波を大幅カット（0.05 = 95%カット）
                gainHF = Math.min(gainHF, 0.05f);
                // 全体音量も減少（オクルージョンを0.5倍に）
                occlusion *= 0.5f;
            } else {
                // 回折経路あり: 通常の統合
                // オクルージョン × (1.0 + 回折 × 0.5)
                occlusion = occlusion + (1.0f - occlusion) * diffraction * 0.5f;
            }

            occlusion = Math.max(0.0f, Math.min(1.0f, occlusion));

            return new PathResult(occlusion, gainHF, diffraction, isMuffledSound);
        }
    }

    /**
     * 直線経路が存在するかチェック
     */
    private static boolean hasDirectPath(Level level, Vec3 source, Vec3 listener) {
        Vec3 direction = listener.subtract(source).normalize();
        double distance = source.distanceTo(listener);
        Vec3 current = source;

        while (current.distanceTo(source) < distance) {
            BlockPos pos = BlockPos.containing(current);
            if (!level.getBlockState(pos).isAir()) {
                return false; // 障害物発見
            }
            current = current.add(direction.scale(RAY_STEP_SIZE));
        }

        return true; // 直線経路あり
    }

    /**
     * オクルージョン（遮蔽）を計算
     * レイトレーシングでブロックを通過する際の減衰を計算
     * @return オクルージョンゲイン（0.0=完全遮蔽, 1.0=遮蔽なし）
     */
    private static float calculateOcclusion(Level level, Vec3 source, Vec3 listener) {
        Vec3 direction = listener.subtract(source).normalize();
        double distance = source.distanceTo(listener);
        Vec3 current = source;

        float totalTransmissionLoss = 0f; // dB
        int blockCount = 0;

        while (current.distanceTo(source) < distance) {
            BlockPos pos = BlockPos.containing(current);
            Block block = level.getBlockState(pos).getBlock();

            if (!level.getBlockState(pos).isAir()) {
                // ブロックの透過損失を計算（中心周波数1kHz）
                float transmissionLoss = BlockAcousticDatabase.calculateTransmissionLoss(block, 1000f);
                totalTransmissionLoss += transmissionLoss;
                blockCount++;
            }

            current = current.add(direction.scale(RAY_STEP_SIZE));
        }

        // 透過損失をゲインに変換
        float gain = BlockAcousticDatabase.transmissionLossToCoefficient(totalTransmissionLoss);

        // 最小透過保証（10%）
        gain = Math.max(0.1f, gain);

        return gain;
    }

    /**
     * 高周波減衰を計算
     * ブロックを通過する際、高周波ほど減衰しやすい
     * @return 高周波ゲイン（0.0=完全減衰, 1.0=減衰なし）
     */
    private static float calculateHighFrequencyAttenuation(Level level, Vec3 source, Vec3 listener) {
        Vec3 direction = listener.subtract(source).normalize();
        double distance = source.distanceTo(listener);
        Vec3 current = source;

        float totalTransmissionLossHF = 0f; // dB
        int blockCount = 0;

        while (current.distanceTo(source) < distance) {
            BlockPos pos = BlockPos.containing(current);
            Block block = level.getBlockState(pos).getBlock();

            if (!level.getBlockState(pos).isAir()) {
                // 高周波（4kHz）での透過損失を計算
                float transmissionLoss = BlockAcousticDatabase.calculateTransmissionLoss(block, 4000f);
                totalTransmissionLossHF += transmissionLoss;
                blockCount++;
            }

            current = current.add(direction.scale(RAY_STEP_SIZE));
        }

        // 透過損失をゲインに変換
        float gainHF = BlockAcousticDatabase.transmissionLossToCoefficient(totalTransmissionLossHF);

        // 最小透過保証（5% - 中周波より厳しい）
        gainHF = Math.max(0.05f, gainHF);

        return gainHF;
    }

    /**
     * 複数の周波数バンドに対してオクルージョンを計算
     * @param level ワールド
     * @param source 音源
     * @param listener 聴者
     * @param frequencies 各バンドの中心周波数
     * @return 各バンドのオクルージョンゲイン
     */
    public static float[] calculateBandOcclusion(Level level, Vec3 source, Vec3 listener, float[] frequencies) {
        float[] gains = new float[frequencies.length];

        Vec3 direction = listener.subtract(source).normalize();
        double distance = source.distanceTo(listener);
        Vec3 current = source;

        // 各バンドの透過損失を蓄積
        float[] totalLosses = new float[frequencies.length];

        while (current.distanceTo(source) < distance) {
            BlockPos pos = BlockPos.containing(current);
            Block block = level.getBlockState(pos).getBlock();

            if (!level.getBlockState(pos).isAir()) {
                for (int i = 0; i < frequencies.length; i++) {
                    float transmissionLoss = BlockAcousticDatabase.calculateTransmissionLoss(block, frequencies[i]);
                    totalLosses[i] += transmissionLoss;
                }
            }

            current = current.add(direction.scale(RAY_STEP_SIZE));
        }

        // 透過損失をゲインに変換
        for (int i = 0; i < frequencies.length; i++) {
            gains[i] = BlockAcousticDatabase.transmissionLossToCoefficient(totalLosses[i]);
            gains[i] = Math.max(0.05f, gains[i]); // 最小透過保証
        }

        return gains;
    }
}
