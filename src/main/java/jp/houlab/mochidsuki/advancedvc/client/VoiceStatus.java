package jp.houlab.mochidsuki.advancedvc.client;

/**
 * マイク状態
 */
public enum VoiceStatus {
    IDLE("待機中", 0xFFAAAAAA),           // グレー
    SPEAKING("発話中", 0xFF00FF00),      // 緑
    MUTED("ミュート中", 0xFFFF0000),     // 赤
    CONNECTION_FAILED("接続失敗", 0xFFFF8800); // オレンジ

    private final String displayName;
    private final int color;

    VoiceStatus(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }
}
