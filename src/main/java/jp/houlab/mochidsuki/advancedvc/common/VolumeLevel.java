package jp.houlab.mochidsuki.advancedvc.common;

/**
 * 5段階の声量設定
 * シミュレーションモード: 初期音圧と最大到達距離
 * バンドモード: PAミキサーへの入力ゲイン
 * デバイスモード: 無視される
 */
public enum VolumeLevel {
    WHISPER("独り言", 0.3f, 5.0f),      // 囁き - 5m
    QUIET("囁き", 0.5f, 10.0f),          // 小声 - 10m
    NORMAL("会話", 1.0f, 30.0f),         // 通常会話 - 30m (デフォルト)
    LOUD("演説", 1.5f, 60.0f),           // 大声 - 60m
    SHOUT("叫び", 2.0f, 100.0f);         // 叫び - 100m

    private final String displayName;
    private final float amplitude;      // 初期音圧（音量倍率）
    private final float maxDistance;    // 最大到達距離（ブロック単位）

    VolumeLevel(String displayName, float amplitude, float maxDistance) {
        this.displayName = displayName;
        this.amplitude = amplitude;
        this.maxDistance = maxDistance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getAmplitude() {
        return amplitude;
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    /**
     * 次の声量レベルに切り替える（循環）
     */
    public VolumeLevel next() {
        VolumeLevel[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * 前の声量レベルに切り替える（循環）
     */
    public VolumeLevel previous() {
        VolumeLevel[] values = values();
        return values[(this.ordinal() - 1 + values.length) % values.length];
    }
}
