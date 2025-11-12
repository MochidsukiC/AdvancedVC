package jp.houlab.mochidsuki.advancedvc.client.audio.vgp;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * VGP Acoustic Engine - VGPシステムとオーディオの統合エンジン
 *
 * VoxelAcousticGraphとVGPPathfinderを管理し、音響経路計算を
 * 非同期で実行してオーディオシステムに提供します。
 */
public class VGPAcousticEngine {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final VoxelAcousticGraph graph;
    private final VGPPathfinder pathfinder;
    private final ExecutorService executorService;

    // 結果キャッシュ（パフォーマンス最適化）
    private final Map<String, CachedResult> resultCache;
    private static final long CACHE_VALIDITY_MS = 200;  // 200ms有効

    // パスファインディング設定
    private static final int MAX_ITERATIONS = 10000;  // A*の最大反復回数

    /**
     * キャッシュされた結果
     */
    private static class CachedResult {
        final AcousticPathResult result;
        final long timestamp;

        CachedResult(AcousticPathResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_MS;
        }
    }

    /**
     * コンストラクタ
     */
    public VGPAcousticEngine(Level level) {
        this.graph = new VoxelAcousticGraph(level);
        this.pathfinder = new VGPPathfinder(graph);
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VGP-Pathfinder");
            t.setDaemon(true);
            return t;
        });
        this.resultCache = new ConcurrentHashMap<>();

        LOGGER.info("VGP Acoustic Engine initialized");
    }

    /**
     * 音響経路を計算（同期版）
     *
     * @param sourcePos 音源位置
     * @param listenerPos リスナー位置
     * @return 音響処理結果
     */
    public AcousticPathResult calculatePath(Vec3 sourcePos, Vec3 listenerPos) {
        // Vec3をBlockPosに変換
        BlockPos sourcePosBlock = BlockPos.containing(sourcePos);
        BlockPos listenerPosBlock = BlockPos.containing(listenerPos);

        // キャッシュキーを生成
        String cacheKey = getCacheKey(sourcePosBlock, listenerPosBlock);

        // キャッシュチェック
        CachedResult cached = resultCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.result;
        }

        // パスファインディング実行
        VGPPathfinder.AcousticPath path = pathfinder.findPath(
                sourcePosBlock,
                listenerPosBlock,
                MAX_ITERATIONS
        );

        // 結果を音響処理パラメータに変換
        AcousticPathResult result = AcousticPathResult.fromPath(path);

        // キャッシュに保存
        resultCache.put(cacheKey, new CachedResult(result));

        return result;
    }

    /**
     * 音響経路を計算（非同期版）
     *
     * @param sourcePos 音源位置
     * @param listenerPos リスナー位置
     * @param callback 完了時のコールバック
     */
    public void calculatePathAsync(Vec3 sourcePos, Vec3 listenerPos,
                                   java.util.function.Consumer<AcousticPathResult> callback) {
        // Vec3をBlockPosに変換
        BlockPos sourcePosBlock = BlockPos.containing(sourcePos);
        BlockPos listenerPosBlock = BlockPos.containing(listenerPos);

        // キャッシュキーを生成
        String cacheKey = getCacheKey(sourcePosBlock, listenerPosBlock);

        // キャッシュチェック
        CachedResult cached = resultCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            callback.accept(cached.result);
            return;
        }

        // 非同期実行
        executorService.submit(() -> {
            try {
                // パスファインディング実行
                VGPPathfinder.AcousticPath path = pathfinder.findPath(
                        sourcePosBlock,
                        listenerPosBlock,
                        MAX_ITERATIONS
                );

                // 結果を音響処理パラメータに変換
                AcousticPathResult result = AcousticPathResult.fromPath(path);

                // キャッシュに保存
                resultCache.put(cacheKey, new CachedResult(result));

                // コールバック実行
                callback.accept(result);

            } catch (Exception e) {
                LOGGER.error("VGP pathfinding failed", e);
            }
        });
    }

    /**
     * キャッシュキーを生成
     */
    private String getCacheKey(BlockPos source, BlockPos listener) {
        return String.format("%d,%d,%d-%d,%d,%d",
                source.getX(), source.getY(), source.getZ(),
                listener.getX(), listener.getY(), listener.getZ());
    }

    /**
     * ブロック更新通知（動的ジオメトリ対応）
     *
     * @param pos 更新されたブロック座標
     */
    public void notifyBlockUpdate(BlockPos pos) {
        // グラフのノードを無効化
        graph.invalidateNode(pos);

        // 影響を受ける可能性のあるキャッシュをクリア
        // （簡易版：全クリア。最適化版：近傍のキャッシュのみクリア）
        resultCache.clear();
    }

    /**
     * 領域のブロック更新通知
     *
     * @param center 中心座標
     * @param radius 半径
     */
    public void notifyRegionUpdate(BlockPos center, int radius) {
        graph.invalidateRegion(center, radius);
        resultCache.clear();
    }

    /**
     * キャッシュをクリア
     */
    public void clearCache() {
        resultCache.clear();
        graph.clearCache();
    }

    /**
     * エンジンをシャットダウン
     */
    public void shutdown() {
        executorService.shutdownNow();
        clearCache();
        LOGGER.info("VGP Acoustic Engine shutdown");
    }

    /**
     * 統計情報を取得（デバッグ用）
     */
    public String getStatistics() {
        return String.format("VGP Engine: %d cached results, %s",
                resultCache.size(),
                graph.getStatistics());
    }
}
