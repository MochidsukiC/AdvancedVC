package jp.houlab.mochidsuki.advancedvc.client.audio.vgp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voxel-Graph Pathfinding (VGP) - ボクセル空間の音響グラフ
 *
 * Minecraftのボクセル空間を音響伝播グラフに変換します。
 * 各ボクセル（ブロック）がノード、隣接ボクセル間がエッジとなります。
 *
 * 参考文献: "G-SpAR: GPU-Based Voxel Graph Pathfinding for Spatial Audio Rendering"
 */
public class VoxelAcousticGraph {

    /**
     * 音響ノード（ボクセル）
     */
    public static class AcousticNode {
        public final BlockPos pos;
        public final boolean traversable;  // 通行可能か（空気または透過性ブロック）
        public final float acousticCost;   // 音響通過コスト（1.0 = 空気、高い = 吸収/遮蔽）
        public final float absorptionCoeff; // 吸音係数（0.0 = 反射、1.0 = 完全吸収）

        public AcousticNode(BlockPos pos, boolean traversable, float acousticCost, float absorptionCoeff) {
            this.pos = pos;
            this.traversable = traversable;
            this.acousticCost = acousticCost;
            this.absorptionCoeff = absorptionCoeff;
        }
    }

    // ノードキャッシュ（動的ジオメトリ対応）
    private final ConcurrentHashMap<BlockPos, AcousticNode> nodeCache;
    private final Level level;

    // サンプリング範囲（リスナー周辺）
    private static final int SAMPLE_RADIUS = 32; // ブロック単位

    /**
     * コンストラクタ
     */
    public VoxelAcousticGraph(Level level) {
        this.level = level;
        this.nodeCache = new ConcurrentHashMap<>();
    }

    /**
     * 指定座標のノードを取得（キャッシュあり、動的生成）
     * @param pos ブロック座標
     * @return 音響ノード
     */
    public AcousticNode getNode(BlockPos pos) {
        // キャッシュから取得
        AcousticNode cached = nodeCache.get(pos);
        if (cached != null) {
            return cached;
        }

        // 動的生成
        AcousticNode node = createNodeFromWorld(pos);
        nodeCache.put(pos, node);
        return node;
    }

    /**
     * ワールドデータからノードを生成
     * @param pos ブロック座標
     * @return 音響ノード
     */
    private AcousticNode createNodeFromWorld(BlockPos pos) {
        if (level == null) {
            // フォールバック: 空気として扱う
            return new AcousticNode(pos, true, 1.0f, 0.0f);
        }

        Block block = level.getBlockState(pos).getBlock();

        // 通行可能性と音響特性を決定
        boolean traversable;
        float acousticCost;
        float absorptionCoeff;

        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
            // 空気: 通行可能、コスト最小、吸収なし
            traversable = true;
            acousticCost = 1.0f;
            absorptionCoeff = 0.0f;

        } else if (isTransparent(block)) {
            // ガラス・葉など透過性ブロック: 通行可能、低コスト、低吸収
            traversable = true;
            acousticCost = 5.0f;
            absorptionCoeff = 0.3f;

        } else if (isSoftMaterial(block)) {
            // 羊毛・カーペットなど吸音材: 通行可能、中コスト、高吸収
            traversable = true;
            acousticCost = 15.0f;
            absorptionCoeff = 0.8f;

        } else if (isHardMaterial(block)) {
            // 石・木材など硬い材質: 通行困難、高コスト、低吸収
            traversable = true;  // 透過は可能だが高コスト
            acousticCost = 50.0f;
            absorptionCoeff = 0.2f;

        } else if (isImpassable(block)) {
            // 岩盤・バリアなど: 通行不可
            traversable = false;
            acousticCost = Float.POSITIVE_INFINITY;
            absorptionCoeff = 1.0f;

        } else {
            // デフォルト: 中程度の遮蔽
            traversable = true;
            acousticCost = 30.0f;
            absorptionCoeff = 0.5f;
        }

        return new AcousticNode(pos, traversable, acousticCost, absorptionCoeff);
    }

    /**
     * 透過性ブロックか判定
     */
    private boolean isTransparent(Block block) {
        return block == Blocks.GLASS ||
               block == Blocks.GLASS_PANE ||
               block == Blocks.OAK_LEAVES ||
               block == Blocks.BIRCH_LEAVES ||
               block == Blocks.SPRUCE_LEAVES ||
               block == Blocks.JUNGLE_LEAVES ||
               block == Blocks.ACACIA_LEAVES ||
               block == Blocks.DARK_OAK_LEAVES ||
               block == Blocks.MANGROVE_LEAVES ||
               block == Blocks.CHERRY_LEAVES ||
               block == Blocks.ICE ||
               block == Blocks.IRON_BARS;
    }

    /**
     * 吸音材（柔らかい材質）か判定
     */
    private boolean isSoftMaterial(Block block) {
        return block == Blocks.WHITE_WOOL ||
               block == Blocks.ORANGE_WOOL ||
               block == Blocks.MAGENTA_WOOL ||
               block == Blocks.LIGHT_BLUE_WOOL ||
               block == Blocks.YELLOW_WOOL ||
               block == Blocks.LIME_WOOL ||
               block == Blocks.PINK_WOOL ||
               block == Blocks.GRAY_WOOL ||
               block == Blocks.LIGHT_GRAY_WOOL ||
               block == Blocks.CYAN_WOOL ||
               block == Blocks.PURPLE_WOOL ||
               block == Blocks.BLUE_WOOL ||
               block == Blocks.BROWN_WOOL ||
               block == Blocks.GREEN_WOOL ||
               block == Blocks.RED_WOOL ||
               block == Blocks.BLACK_WOOL ||
               block == Blocks.WHITE_CARPET ||
               block == Blocks.MOSS_BLOCK ||
               block == Blocks.MOSS_CARPET;
    }

    /**
     * 硬い材質か判定
     */
    private boolean isHardMaterial(Block block) {
        return block == Blocks.STONE ||
               block == Blocks.COBBLESTONE ||
               block == Blocks.GRANITE ||
               block == Blocks.DIORITE ||
               block == Blocks.ANDESITE ||
               block == Blocks.DEEPSLATE ||
               block == Blocks.TUFF ||
               block == Blocks.OAK_PLANKS ||
               block == Blocks.BIRCH_PLANKS ||
               block == Blocks.SPRUCE_PLANKS ||
               block == Blocks.JUNGLE_PLANKS ||
               block == Blocks.ACACIA_PLANKS ||
               block == Blocks.DARK_OAK_PLANKS ||
               block == Blocks.MANGROVE_PLANKS ||
               block == Blocks.CHERRY_PLANKS ||
               block == Blocks.BRICKS ||
               block == Blocks.DIRT ||
               block == Blocks.SAND ||
               block == Blocks.GRAVEL;
    }

    /**
     * 通行不可ブロックか判定
     */
    private boolean isImpassable(Block block) {
        return block == Blocks.BEDROCK ||
               block == Blocks.BARRIER ||
               block == Blocks.COMMAND_BLOCK ||
               block == Blocks.CHAIN_COMMAND_BLOCK ||
               block == Blocks.REPEATING_COMMAND_BLOCK;
    }

    /**
     * 隣接ノードを取得（6近傍: 上下左右前後）
     * @param pos 中心座標
     * @return 隣接ノードのリスト
     */
    public List<AcousticNode> getNeighbors(BlockPos pos) {
        List<AcousticNode> neighbors = new ArrayList<>(6);

        // 6方向の隣接ノード
        BlockPos[] adjacentPositions = {
            pos.above(),    // 上
            pos.below(),    // 下
            pos.north(),    // 北
            pos.south(),    // 南
            pos.east(),     // 東
            pos.west()      // 西
        };

        for (BlockPos adjPos : adjacentPositions) {
            AcousticNode neighbor = getNode(adjPos);
            if (neighbor.traversable) {
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    /**
     * ノードキャッシュをクリア（メモリ管理用）
     */
    public void clearCache() {
        nodeCache.clear();
    }

    /**
     * 特定のノードを無効化（ブロック破壊/設置時の動的更新用）
     * @param pos 更新されたブロック座標
     */
    public void invalidateNode(BlockPos pos) {
        nodeCache.remove(pos);
    }

    /**
     * 領域のノードを無効化
     * @param center 中心座標
     * @param radius 半径（ブロック単位）
     */
    public void invalidateRegion(BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    nodeCache.remove(pos);
                }
            }
        }
    }

    /**
     * グラフ統計情報の取得（デバッグ用）
     */
    public String getStatistics() {
        return String.format("VoxelAcousticGraph: %d cached nodes", nodeCache.size());
    }
}
