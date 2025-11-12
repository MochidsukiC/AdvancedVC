package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * ブロックの音響特性データベース
 * Mass Lawに基づく透過損失計算用のブロック質量データ
 */
public class BlockAcousticDatabase {
    // ブロックごとの面密度（kg/m²）
    private static final Map<Block, Float> BLOCK_MASS = new HashMap<>();

    // デフォルト値
    private static final float DEFAULT_MASS = 50f; // kg/m² （標準的な木材）

    static {
        // === 空気・透明ブロック（質量ゼロ） ===
        BLOCK_MASS.put(Blocks.AIR, 0f);
        BLOCK_MASS.put(Blocks.CAVE_AIR, 0f);
        BLOCK_MASS.put(Blocks.VOID_AIR, 0f);

        // === 軽量ブロック（ガラス、氷系）===
        BLOCK_MASS.put(Blocks.GLASS, 12f);
        BLOCK_MASS.put(Blocks.GLASS_PANE, 6f);
        BLOCK_MASS.put(Blocks.ICE, 18f);
        BLOCK_MASS.put(Blocks.PACKED_ICE, 22f);
        BLOCK_MASS.put(Blocks.BLUE_ICE, 25f);

        // 色付きガラス
        BLOCK_MASS.put(Blocks.WHITE_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.ORANGE_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.MAGENTA_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.LIGHT_BLUE_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.YELLOW_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.LIME_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.PINK_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.GRAY_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.LIGHT_GRAY_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.CYAN_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.PURPLE_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.BLUE_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.BROWN_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.GREEN_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.RED_STAINED_GLASS, 12f);
        BLOCK_MASS.put(Blocks.BLACK_STAINED_GLASS, 12f);

        // === 木材系（軽～中量）===
        BLOCK_MASS.put(Blocks.OAK_PLANKS, 30f);
        BLOCK_MASS.put(Blocks.SPRUCE_PLANKS, 30f);
        BLOCK_MASS.put(Blocks.BIRCH_PLANKS, 30f);
        BLOCK_MASS.put(Blocks.JUNGLE_PLANKS, 32f);
        BLOCK_MASS.put(Blocks.ACACIA_PLANKS, 30f);
        BLOCK_MASS.put(Blocks.DARK_OAK_PLANKS, 32f);
        BLOCK_MASS.put(Blocks.MANGROVE_PLANKS, 31f);
        BLOCK_MASS.put(Blocks.CHERRY_PLANKS, 28f);
        BLOCK_MASS.put(Blocks.BAMBOO_PLANKS, 25f);
        BLOCK_MASS.put(Blocks.OAK_LOG, 40f);
        BLOCK_MASS.put(Blocks.SPRUCE_LOG, 40f);
        BLOCK_MASS.put(Blocks.BIRCH_LOG, 40f);
        BLOCK_MASS.put(Blocks.JUNGLE_LOG, 45f);

        // === 土系（中量）===
        BLOCK_MASS.put(Blocks.DIRT, 60f);
        BLOCK_MASS.put(Blocks.GRASS_BLOCK, 60f);
        BLOCK_MASS.put(Blocks.COARSE_DIRT, 62f);
        BLOCK_MASS.put(Blocks.SAND, 70f);
        BLOCK_MASS.put(Blocks.RED_SAND, 70f);
        BLOCK_MASS.put(Blocks.GRAVEL, 75f);
        BLOCK_MASS.put(Blocks.CLAY, 80f);

        // === 石系（重量）===
        BLOCK_MASS.put(Blocks.STONE, 120f);
        BLOCK_MASS.put(Blocks.COBBLESTONE, 115f);
        BLOCK_MASS.put(Blocks.GRANITE, 125f);
        BLOCK_MASS.put(Blocks.DIORITE, 125f);
        BLOCK_MASS.put(Blocks.ANDESITE, 125f);
        BLOCK_MASS.put(Blocks.DEEPSLATE, 140f);
        BLOCK_MASS.put(Blocks.COBBLED_DEEPSLATE, 135f);
        BLOCK_MASS.put(Blocks.TUFF, 110f);
        BLOCK_MASS.put(Blocks.CALCITE, 105f);
        BLOCK_MASS.put(Blocks.DRIPSTONE_BLOCK, 115f);

        // === レンガ系（重量）===
        BLOCK_MASS.put(Blocks.BRICKS, 130f);
        BLOCK_MASS.put(Blocks.STONE_BRICKS, 125f);
        BLOCK_MASS.put(Blocks.DEEPSLATE_BRICKS, 140f);
        BLOCK_MASS.put(Blocks.NETHER_BRICKS, 135f);
        BLOCK_MASS.put(Blocks.RED_NETHER_BRICKS, 135f);

        // === 金属系（超重量）===
        BLOCK_MASS.put(Blocks.IRON_BLOCK, 400f);
        BLOCK_MASS.put(Blocks.GOLD_BLOCK, 900f);
        BLOCK_MASS.put(Blocks.COPPER_BLOCK, 420f);
        BLOCK_MASS.put(Blocks.NETHERITE_BLOCK, 1000f);
        BLOCK_MASS.put(Blocks.IRON_DOOR, 200f);
        BLOCK_MASS.put(Blocks.IRON_TRAPDOOR, 150f);

        // === コンクリート系（超重量）===
        BLOCK_MASS.put(Blocks.WHITE_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.ORANGE_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.MAGENTA_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.LIGHT_BLUE_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.YELLOW_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.LIME_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.PINK_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.GRAY_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.LIGHT_GRAY_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.CYAN_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.PURPLE_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.BLUE_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.BROWN_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.GREEN_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.RED_CONCRETE, 200f);
        BLOCK_MASS.put(Blocks.BLACK_CONCRETE, 200f);

        // === 羊毛系（軽量・吸音）===
        BLOCK_MASS.put(Blocks.WHITE_WOOL, 8f);
        BLOCK_MASS.put(Blocks.ORANGE_WOOL, 8f);
        BLOCK_MASS.put(Blocks.MAGENTA_WOOL, 8f);
        BLOCK_MASS.put(Blocks.LIGHT_BLUE_WOOL, 8f);
        BLOCK_MASS.put(Blocks.YELLOW_WOOL, 8f);
        BLOCK_MASS.put(Blocks.LIME_WOOL, 8f);
        BLOCK_MASS.put(Blocks.PINK_WOOL, 8f);
        BLOCK_MASS.put(Blocks.GRAY_WOOL, 8f);
        BLOCK_MASS.put(Blocks.LIGHT_GRAY_WOOL, 8f);
        BLOCK_MASS.put(Blocks.CYAN_WOOL, 8f);
        BLOCK_MASS.put(Blocks.PURPLE_WOOL, 8f);
        BLOCK_MASS.put(Blocks.BLUE_WOOL, 8f);
        BLOCK_MASS.put(Blocks.BROWN_WOOL, 8f);
        BLOCK_MASS.put(Blocks.GREEN_WOOL, 8f);
        BLOCK_MASS.put(Blocks.RED_WOOL, 8f);
        BLOCK_MASS.put(Blocks.BLACK_WOOL, 8f);

        // === 黒曜石（最重量）===
        BLOCK_MASS.put(Blocks.OBSIDIAN, 1200f);
        BLOCK_MASS.put(Blocks.CRYING_OBSIDIAN, 1200f);

        // === エンドストーン系 ===
        BLOCK_MASS.put(Blocks.END_STONE, 130f);
        BLOCK_MASS.put(Blocks.END_STONE_BRICKS, 135f);

        // === 鉱石ブロック ===
        BLOCK_MASS.put(Blocks.COAL_BLOCK, 80f);
        BLOCK_MASS.put(Blocks.DIAMOND_BLOCK, 600f);
        BLOCK_MASS.put(Blocks.EMERALD_BLOCK, 550f);
        BLOCK_MASS.put(Blocks.LAPIS_BLOCK, 140f);
        BLOCK_MASS.put(Blocks.REDSTONE_BLOCK, 250f);
    }

    /**
     * ブロックの面密度を取得（kg/m²）
     */
    public static float getBlockMass(Block block) {
        return BLOCK_MASS.getOrDefault(block, DEFAULT_MASS);
    }

    /**
     * Mass Lawによる透過損失を計算（ゲーム用に調整）
     * TL = 20 × log₁₀(f × m) - 42 (dB)
     *
     * @param block ブロック
     * @param frequency 周波数（Hz）
     * @return 透過損失（dB）
     */
    public static float calculateTransmissionLoss(Block block, float frequency) {
        float mass = getBlockMass(block);

        // 空気ブロックは透過損失なし
        if (mass < 0.1f) {
            return 0f;
        }

        // Mass Law: TL = 20 × log₁₀(f × m) - 42
        double tl = 20.0 * Math.log10(frequency * mass) - 42.0;

        // ゲーム用に調整: 透過損失を1/4に減らす（音が通りやすくする）
        tl = tl * 0.25;

        // 負の値にはならない（最低0dB）
        // 最大値も制限（1ブロックあたり最大6dB = 約50%透過）
        return Math.max(0f, Math.min(6.0f, (float) tl));
    }

    /**
     * 透過損失（dB）を透過係数（0.0～1.0）に変換
     * @param transmissionLossDb 透過損失（dB）
     * @return 透過係数（0.0=完全遮蔽, 1.0=透過）
     */
    public static float transmissionLossToCoefficient(float transmissionLossDb) {
        // TL(dB) = -20 × log₁₀(T)
        // T = 10^(-TL/20)
        return (float) Math.pow(10.0, -transmissionLossDb / 20.0);
    }

    /**
     * ブロックの吸音係数を取得（反射率の逆）
     * @param block ブロック
     * @return 吸音係数（0.0=完全反射, 1.0=完全吸音）
     */
    public static float getAbsorptionCoefficient(Block block) {
        // 羊毛系は高吸音
        if (block == Blocks.WHITE_WOOL || block == Blocks.BLACK_WOOL ||
                block.toString().contains("wool")) {
            return 0.6f;
        }

        // 柔らかいブロック（中吸音）
        if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK ||
                block == Blocks.SAND || block == Blocks.GRAVEL) {
            return 0.3f;
        }

        // 硬いブロック（低吸音、高反射）
        float mass = getBlockMass(block);
        if (mass > 100f) {
            return 0.05f; // 石、コンクリート、金属など
        } else if (mass > 20f) {
            return 0.15f; // 木材など
        } else {
            return 0.1f;  // その他
        }
    }

    /**
     * 反射係数を取得（吸音係数の逆）
     */
    public static float getReflectionCoefficient(Block block) {
        return 1.0f - getAbsorptionCoefficient(block);
    }

    /**
     * 透過係数を計算（透過損失から変換）
     * @param block ブロック
     * @param frequency 周波数（Hz）
     * @return 透過係数（0.0=完全遮蔽, 1.0=完全透過）
     */
    public static float calculateTransmissionCoefficient(Block block, float frequency) {
        float transmissionLoss = calculateTransmissionLoss(block, frequency);
        return transmissionLossToCoefficient(transmissionLoss);
    }
}
