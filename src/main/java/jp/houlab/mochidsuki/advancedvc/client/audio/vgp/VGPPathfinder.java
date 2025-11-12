package jp.houlab.mochidsuki.advancedvc.client.audio.vgp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * VGP Pathfinder - A*アルゴリズムによる音響経路探索
 *
 * 音源（Source）からリスナー（Listener）への最適な音響伝播経路を、
 * A*アルゴリズムを用いて探索します。
 *
 * 参考文献: "Approximate diffraction modeling for real-time sound propagation simulation"
 */
public class VGPPathfinder {

    private final VoxelAcousticGraph graph;

    /**
     * A*探索のノード（優先度付きキュー用）
     */
    private static class SearchNode implements Comparable<SearchNode> {
        final BlockPos pos;
        final SearchNode parent;
        final float gCost;  // 開始からこのノードまでの実コスト
        final float hCost;  // このノードからゴールまでの推定コスト（ヒューリスティック）
        final float fCost;  // gCost + hCost（総コスト）

        SearchNode(BlockPos pos, SearchNode parent, float gCost, float hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(SearchNode other) {
            return Float.compare(this.fCost, other.fCost);
        }
    }

    /**
     * コンストラクタ
     */
    public VGPPathfinder(VoxelAcousticGraph graph) {
        this.graph = graph;
    }

    /**
     * A*アルゴリズムで音響経路を探索
     *
     * @param sourcePos 音源座標（ブロック座標）
     * @param listenerPos リスナー座標（ブロック座標）
     * @param maxIterations 最大反復回数（タイムアウト対策）
     * @return 音響経路（見つからない場合はnull）
     */
    public AcousticPath findPath(BlockPos sourcePos, BlockPos listenerPos, int maxIterations) {
        // 開始・終了ノードが同じ場合
        if (sourcePos.equals(listenerPos)) {
            return createDirectPath(sourcePos, listenerPos);
        }

        // オープンリスト（探索候補）- 優先度付きキュー
        PriorityQueue<SearchNode> openSet = new PriorityQueue<>();

        // クローズドリスト（探索済み）
        Set<BlockPos> closedSet = new HashSet<>();

        // gCostの記録（より良い経路が見つかった場合の更新用）
        Map<BlockPos, Float> gCostMap = new HashMap<>();

        // 開始ノードを追加
        SearchNode startNode = new SearchNode(
            sourcePos,
            null,
            0.0f,
            heuristic(sourcePos, listenerPos)
        );
        openSet.add(startNode);
        gCostMap.put(sourcePos, 0.0f);

        int iterations = 0;

        // A*メインループ
        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            // 最小コストのノードを取得
            SearchNode current = openSet.poll();

            // ゴール到達
            if (current.pos.equals(listenerPos)) {
                return reconstructPath(current, sourcePos, listenerPos);
            }

            // クローズドリストに追加
            closedSet.add(current.pos);

            // 隣接ノードを探索
            List<VoxelAcousticGraph.AcousticNode> neighbors = graph.getNeighbors(current.pos);

            for (VoxelAcousticGraph.AcousticNode neighborNode : neighbors) {
                BlockPos neighborPos = neighborNode.pos;

                // 既に探索済み
                if (closedSet.contains(neighborPos)) {
                    continue;
                }

                // エッジコスト = 距離 × 音響コスト
                float edgeCost = 1.0f * neighborNode.acousticCost;

                // 新しいgCost
                float tentativeGCost = current.gCost + edgeCost;

                // より良い経路が見つかったか確認
                Float existingGCost = gCostMap.get(neighborPos);
                if (existingGCost != null && tentativeGCost >= existingGCost) {
                    continue;  // より悪い経路なのでスキップ
                }

                // 新しい経路を記録
                gCostMap.put(neighborPos, tentativeGCost);

                // 隣接ノードをオープンリストに追加
                SearchNode neighborSearchNode = new SearchNode(
                    neighborPos,
                    current,
                    tentativeGCost,
                    heuristic(neighborPos, listenerPos)
                );
                openSet.add(neighborSearchNode);
            }
        }

        // 経路が見つからなかった場合 - 直線距離でフォールバック
        return createDirectPath(sourcePos, listenerPos);
    }

    /**
     * ヒューリスティック関数（推定距離）
     * ユークリッド距離を使用
     */
    private float heuristic(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 経路を再構築
     */
    private AcousticPath reconstructPath(SearchNode goalNode, BlockPos sourcePos, BlockPos listenerPos) {
        // 経路を逆順にたどる
        List<BlockPos> pathNodes = new ArrayList<>();
        SearchNode current = goalNode;

        while (current != null) {
            pathNodes.add(current.pos);
            current = current.parent;
        }

        // 正順に並び替え
        Collections.reverse(pathNodes);

        // 経路情報を計算
        float totalDistance = goalNode.gCost;
        float straightLineDistance = heuristic(sourcePos, listenerPos);

        // 回折判定: 経路長差が大きいほど回折が発生している
        float pathLengthDifference = totalDistance - straightLineDistance;
        boolean isDiffracted = pathLengthDifference > 2.0f;  // 2ブロック以上の迂回

        // 累積吸収係数を計算
        float cumulativeAbsorption = calculateCumulativeAbsorption(pathNodes);

        return new AcousticPath(
            pathNodes,
            totalDistance,
            straightLineDistance,
            isDiffracted,
            pathLengthDifference,
            cumulativeAbsorption
        );
    }

    /**
     * 経路の累積吸収係数を計算
     */
    private float calculateCumulativeAbsorption(List<BlockPos> pathNodes) {
        if (pathNodes.isEmpty()) {
            return 0.0f;
        }

        float totalAbsorption = 0.0f;
        for (BlockPos pos : pathNodes) {
            VoxelAcousticGraph.AcousticNode node = graph.getNode(pos);
            totalAbsorption += node.absorptionCoeff;
        }

        // 平均吸収係数
        return totalAbsorption / pathNodes.size();
    }

    /**
     * 直線経路を作成（フォールバック用）
     * 直線上のブロックをサンプリングして吸収係数を計算
     */
    private AcousticPath createDirectPath(BlockPos sourcePos, BlockPos listenerPos) {
        List<BlockPos> pathNodes = Arrays.asList(sourcePos, listenerPos);
        float distance = heuristic(sourcePos, listenerPos);

        // 直線上のブロックをサンプリング（1mステップ）
        float totalAbsorption = 0.0f;
        int sampleCount = 0;

        Vec3 start = new Vec3(sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5);
        Vec3 end = new Vec3(listenerPos.getX() + 0.5, listenerPos.getY() + 0.5, listenerPos.getZ() + 0.5);
        Vec3 direction = end.subtract(start).normalize();

        for (float d = 0; d < distance; d += 1.0f) {
            Vec3 samplePos = start.add(direction.scale(d));
            BlockPos blockPos = BlockPos.containing(samplePos);
            VoxelAcousticGraph.AcousticNode node = graph.getNode(blockPos);

            // 空気以外のブロックの吸収係数を累積
            if (!node.traversable || node.absorptionCoeff > 0.0f) {
                totalAbsorption += node.absorptionCoeff;
                sampleCount++;
            }
        }

        float averageAbsorption = sampleCount > 0 ? totalAbsorption / sampleCount : 0.0f;

        return new AcousticPath(
            pathNodes,
            distance,
            distance,
            false,  // 直線なので回折なし
            0.0f,
            averageAbsorption  // 直線上のブロックの吸収係数
        );
    }

    /**
     * 音響経路の結果
     */
    public static class AcousticPath {
        public final List<BlockPos> nodes;              // 経路のノードリスト
        public final float totalDistance;               // 経路の総距離
        public final float straightLineDistance;        // 直線距離
        public final boolean isDiffracted;              // 回折が発生したか
        public final float diffractionAmount;           // 回折量（経路長差）
        public final float averageAbsorption;           // 平均吸収係数

        public AcousticPath(List<BlockPos> nodes, float totalDistance, float straightLineDistance,
                           boolean isDiffracted, float diffractionAmount, float averageAbsorption) {
            this.nodes = nodes;
            this.totalDistance = totalDistance;
            this.straightLineDistance = straightLineDistance;
            this.isDiffracted = isDiffracted;
            this.diffractionAmount = diffractionAmount;
            this.averageAbsorption = averageAbsorption;
        }

        /**
         * 経路を3D座標に変換
         */
        public List<Vec3> toVec3List() {
            List<Vec3> vec3List = new ArrayList<>(nodes.size());
            for (BlockPos pos : nodes) {
                vec3List.add(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            }
            return vec3List;
        }

        @Override
        public String toString() {
            return String.format(
                "AcousticPath{nodes=%d, distance=%.2f, straight=%.2f, diffracted=%s, diffAmount=%.2f, absorption=%.2f}",
                nodes.size(), totalDistance, straightLineDistance, isDiffracted, diffractionAmount, averageAbsorption
            );
        }
    }
}
