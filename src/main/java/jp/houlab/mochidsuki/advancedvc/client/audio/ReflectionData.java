package jp.houlab.mochidsuki.advancedvc.client.audio;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * 個別の反射音データ
 * Ray Tracingで計算された1つの反射音の詳細情報を保持
 */
public class ReflectionData {
    // ===== 基本情報 =====

    /** 到達時間（ミリ秒） */
    public final float delayMs;

    /** ゲイン（0.0-1.0） */
    public final float gain;

    /** 反射点の3D位置（OpenAL Sourceの配置位置） */
    public final Vec3 reflectionPoint;

    /** リスナーへの方向ベクトル */
    public final Vec3 directionToListener;

    /** 反射次数（1 = 1次反射, 2 = 2次反射） */
    public final int reflectionOrder;


    // ===== 詳細情報 =====

    /** 反射したブロック */
    public final Block reflectionBlock;

    /** 反射面の法線ベクトル */
    public final Vec3 reflectionNormal;

    /** 総経路長（メートル） */
    public final float totalPathLength;


    // ===== 周波数特性 =====

    /** 低周波（250Hz）ゲイン */
    public final float gain250Hz;

    /** 中周波（1kHz）ゲイン */
    public final float gain1000Hz;

    /** 高周波（4kHz）ゲイン */
    public final float gain4000Hz;


    // ===== 優先度計算用 =====

    /** 優先度スコア（Sourceプール割り当て用） */
    public float priority;


    /**
     * コンストラクタ
     */
    public ReflectionData(
            float delayMs,
            float gain,
            Vec3 reflectionPoint,
            Vec3 directionToListener,
            int reflectionOrder,
            Block reflectionBlock,
            Vec3 reflectionNormal,
            float totalPathLength,
            float gain250Hz,
            float gain1000Hz,
            float gain4000Hz
    ) {
        this.delayMs = delayMs;
        this.gain = gain;
        this.reflectionPoint = reflectionPoint;
        this.directionToListener = directionToListener;
        this.reflectionOrder = reflectionOrder;
        this.reflectionBlock = reflectionBlock;
        this.reflectionNormal = reflectionNormal;
        this.totalPathLength = totalPathLength;
        this.gain250Hz = gain250Hz;
        this.gain1000Hz = gain1000Hz;
        this.gain4000Hz = gain4000Hz;

        // 優先度はデフォルト0（後で計算）
        this.priority = 0.0f;
    }

    /**
     * 優先度を計算
     * ゲインが高く、リスナーに近いほど優先度が高い
     * @param listenerDistance リスナーからの距離（m）
     */
    public void calculatePriority(double listenerDistance) {
        this.priority = gain / (float)(1.0 + listenerDistance * 0.1);
    }

    /**
     * デバッグ用文字列
     */
    @Override
    public String toString() {
        return String.format("Reflection[order=%d, delay=%.1fms, gain=%.2f, distance=%.1fm, priority=%.3f]",
                reflectionOrder, delayMs, gain, totalPathLength, priority);
    }
}
