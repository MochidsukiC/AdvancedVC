package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 初期反射音（1-2次反射）のRay Tracingシステム
 * 音源から512本の音線を放射し、壁での反射を追跡する
 */
public class EarlyReflectionTracer {
    private static final Logger LOGGER = LogUtils.getLogger();

    // レイトレーシングパラメータ
    private static final double RAY_STEP_SIZE = 0.5;  // ステップサイズ（m）
    private static final double MAX_TRACE_DISTANCE = 50.0;  // 最大追跡距離（m）
    private static final double LISTENER_CAPTURE_RADIUS = 2.0;  // リスナー捕捉半径（m）
    private static final float MIN_GAIN_THRESHOLD = 0.05f;  // 最小ゲイン閾値（これ以下は打ち切り）

    // 音速（m/s）
    private static final float SOUND_SPEED = 343.0f;

    /**
     * 初期反射の計算結果
     */
    public static class EarlyReflectionResult {
        public final List<ReflectionData> reflections;  // 重要な反射音（8-16個）
        public final float averageDelay;                // 平均遅延時間（ms）
        public final float totalEnergy;                 // 総エネルギー

        public EarlyReflectionResult(List<ReflectionData> reflections, float averageDelay, float totalEnergy) {
            this.reflections = reflections;
            this.averageDelay = averageDelay;
            this.totalEnergy = totalEnergy;
        }
    }

    /**
     * 音線の状態
     */
    private static class RayState {
        Vec3 position;           // 現在位置
        Vec3 direction;          // 進行方向
        float energy;            // 残りエネルギー
        float traveledDistance;  // 進んだ距離
        int reflectionCount;     // 反射回数
        List<Block> traversedBlocks;  // 通過したブロック

        RayState(Vec3 position, Vec3 direction) {
            this.position = position;
            this.direction = direction.normalize();
            this.energy = 1.0f;
            this.traveledDistance = 0.0f;
            this.reflectionCount = 0;
            this.traversedBlocks = new ArrayList<>();
        }
    }

    /**
     * 初期反射音を計算
     * @param level Minecraftワールド
     * @param sourcePos 音源位置
     * @param listenerPos リスナー位置
     * @param numRays 音線の数（512本推奨）
     * @return 初期反射の計算結果
     */
    public static EarlyReflectionResult traceEarlyReflections(
            Level level,
            Vec3 sourcePos,
            Vec3 listenerPos,
            int numRays
    ) {
        if (level == null || sourcePos == null || listenerPos == null) {
            return new EarlyReflectionResult(new ArrayList<>(), 0.0f, 0.0f);
        }

        List<ReflectionData> allReflections = new ArrayList<>();

        // Fibonacci Sphere分布で音線を生成
        Vec3[] rayDirections = generateFibonacciSphere(numRays);

        // 各音線を追跡
        for (Vec3 direction : rayDirections) {
            traceRay(level, sourcePos, listenerPos, direction, allReflections);
        }

        // 優先度でソート
        for (ReflectionData reflection : allReflections) {
            double distance = listenerPos.distanceTo(reflection.reflectionPoint);
            reflection.calculatePriority(distance);
        }
        allReflections.sort(Comparator.comparingDouble((ReflectionData r) -> r.priority).reversed());

        // 上位8-16個を選択
        int maxReflections = Math.min(16, allReflections.size());
        List<ReflectionData> topReflections = allReflections.subList(0, maxReflections);

        // 統計情報を計算
        float averageDelay = calculateAverageDelay(topReflections);
        float totalEnergy = calculateTotalEnergy(topReflections);

        LOGGER.debug("Early reflections traced: {} rays, {} reflections found, top {} selected",
                numRays, allReflections.size(), topReflections.size());

        return new EarlyReflectionResult(topReflections, averageDelay, totalEnergy);
    }

    /**
     * 1本の音線を追跡
     */
    private static void traceRay(
            Level level,
            Vec3 sourcePos,
            Vec3 listenerPos,
            Vec3 initialDirection,
            List<ReflectionData> outReflections
    ) {
        RayState ray = new RayState(sourcePos, initialDirection);

        // 最大2次反射まで追跡
        while (ray.reflectionCount < 2 && ray.energy > MIN_GAIN_THRESHOLD && ray.traveledDistance < MAX_TRACE_DISTANCE) {
            // 次の衝突点を探す
            CollisionResult collision = findNextCollision(level, ray);

            if (collision == null) {
                // 衝突なし、追跡終了
                break;
            }

            // 衝突点までの距離を加算
            ray.traveledDistance += (float) collision.distance;
            ray.position = collision.hitPoint;

            // エネルギー減衰（距離による）
            ray.energy *= calculateDistanceAttenuation(collision.distance);

            // ブロックの反射係数を取得
            float reflectionCoeff = BlockAcousticDatabase.getReflectionCoefficient(collision.block);
            ray.energy *= reflectionCoeff;

            // 反射音がリスナーに到達するかチェック
            Vec3 toListener = listenerPos.subtract(collision.hitPoint);
            double distanceToListener = toListener.length();

            if (distanceToListener < LISTENER_CAPTURE_RADIUS) {
                // リスナーに到達する反射音を記録
                float totalDelay = (ray.traveledDistance + (float) distanceToListener) / SOUND_SPEED * 1000.0f;  // ms

                // 周波数依存ゲインを計算
                float gain250Hz = ray.energy * BlockAcousticDatabase.calculateTransmissionCoefficient(collision.block, 250f);
                float gain1000Hz = ray.energy * BlockAcousticDatabase.calculateTransmissionCoefficient(collision.block, 1000f);
                float gain4000Hz = ray.energy * BlockAcousticDatabase.calculateTransmissionCoefficient(collision.block, 4000f);

                ReflectionData reflection = new ReflectionData(
                        totalDelay,
                        ray.energy,
                        collision.hitPoint,
                        toListener.normalize(),
                        ray.reflectionCount + 1,
                        collision.block,
                        collision.normal,
                        ray.traveledDistance + (float) distanceToListener,
                        gain250Hz,
                        gain1000Hz,
                        gain4000Hz
                );

                outReflections.add(reflection);
            }

            // 反射方向を計算
            ray.direction = calculateReflection(ray.direction, collision.normal);
            ray.reflectionCount++;
        }
    }

    /**
     * 次の衝突点を探す
     */
    private static class CollisionResult {
        Vec3 hitPoint;
        Vec3 normal;
        Block block;
        double distance;

        CollisionResult(Vec3 hitPoint, Vec3 normal, Block block, double distance) {
            this.hitPoint = hitPoint;
            this.normal = normal;
            this.block = block;
            this.distance = distance;
        }
    }

    private static CollisionResult findNextCollision(Level level, RayState ray) {
        Vec3 current = ray.position;
        Vec3 direction = ray.direction;
        double distanceTraveled = 0.0;

        while (distanceTraveled < MAX_TRACE_DISTANCE) {
            current = current.add(direction.scale(RAY_STEP_SIZE));
            distanceTraveled += RAY_STEP_SIZE;

            BlockPos pos = BlockPos.containing(current);
            if (!level.getBlockState(pos).isAir()) {
                // 衝突検出
                Block block = level.getBlockState(pos).getBlock();

                // 法線を推定（ブロック中心から衝突点へのベクトル）
                Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vec3 normal = current.subtract(blockCenter).normalize();

                return new CollisionResult(current, normal, block, distanceTraveled);
            }
        }

        return null;  // 衝突なし
    }

    /**
     * 反射方向を計算
     * 反射方向 = 入射方向 - 2 * (入射方向・法線) * 法線
     */
    private static Vec3 calculateReflection(Vec3 incident, Vec3 normal) {
        double dot = incident.dot(normal);
        return incident.subtract(normal.scale(2.0 * dot)).normalize();
    }

    /**
     * 距離減衰を計算
     */
    private static float calculateDistanceAttenuation(double distance) {
        // 逆二乗則（簡易版）
        return (float) (1.0 / (1.0 + distance * distance * 0.01));
    }

    /**
     * Fibonacci Sphere分布で球面上に均等に点を配置
     * @param numPoints 点の数
     * @return 方向ベクトルの配列
     */
    private static Vec3[] generateFibonacciSphere(int numPoints) {
        Vec3[] directions = new Vec3[numPoints];
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));  // Golden angle

        for (int i = 0; i < numPoints; i++) {
            double y = 1.0 - (i / (double) (numPoints - 1)) * 2.0;  // y: 1 to -1
            double radius = Math.sqrt(1.0 - y * y);

            double theta = phi * i;

            double x = Math.cos(theta) * radius;
            double z = Math.sin(theta) * radius;

            directions[i] = new Vec3(x, y, z).normalize();
        }

        return directions;
    }

    /**
     * 平均遅延時間を計算
     */
    private static float calculateAverageDelay(List<ReflectionData> reflections) {
        if (reflections.isEmpty()) {
            return 0.0f;
        }

        float sum = 0.0f;
        for (ReflectionData reflection : reflections) {
            sum += reflection.delayMs;
        }
        return sum / reflections.size();
    }

    /**
     * 総エネルギーを計算
     */
    private static float calculateTotalEnergy(List<ReflectionData> reflections) {
        float sum = 0.0f;
        for (ReflectionData reflection : reflections) {
            sum += reflection.gain;
        }
        return sum;
    }
}
