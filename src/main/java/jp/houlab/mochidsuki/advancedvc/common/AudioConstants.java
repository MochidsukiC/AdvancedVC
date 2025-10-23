package jp.houlab.mochidsuki.advancedvc.common;

/**
 * オーディオシステムの定数定義
 */
public class AudioConstants {

    // === サンプリング設定 ===
    public static final int SAMPLE_RATE = 48000;              // 48kHz（高品質）
    public static final int FRAME_SIZE = 960;                  // 20ms @ 48kHz
    public static final int CHANNELS = 1;                      // モノラル

    // === Opusコーデック設定 ===
    // シミュレーション/バンドモード（高品質）
    public static final int OPUS_BITRATE_HIGH = 64000;        // 64kbps
    public static final int OPUS_COMPLEXITY_HIGH = 10;         // 最高品質

    // デバイスモード（低品質、ウォーキートーキー風）
    public static final int OPUS_BITRATE_LOW = 8000;          // 8kbps（劣化音質）
    public static final int OPUS_COMPLEXITY_LOW = 5;           // 低複雑度

    // === ネットワーク設定 ===
    public static final int DEFAULT_UDP_PORT = 24455;          // デフォルトUDPポート
    public static final int MAX_PACKET_SIZE = 1400;            // MTU考慮
    public static final int NETWORK_BUFFER_SIZE = 8192;        // 受信バッファサイズ

    // === 遅延/バッファ設定 ===
    public static final int JITTER_BUFFER_MS = 40;            // ジッターバッファ
    public static final int BAND_MODE_TARGET_LATENCY_MS = 25; // バンドモード目標遅延

    // === VAD（音声アクティビティ検出）設定 ===
    public static final float VAD_THRESHOLD = 0.01f;          // 音声検出閾値（振幅）
    public static final int VAD_FRAME_COUNT = 3;              // 連続フレーム数

    // === 距離減衰設定 ===
    public static final float ATTENUATION_FACTOR = 0.15f;     // 距離減衰係数
    public static final float MIN_AUDIBLE_VOLUME = 0.01f;     // 最小可聴音量

    // === デバイス通信設定 ===
    public static final int WALKIE_TALKIE_MIN_FREQ = 1;       // 最小周波数
    public static final int WALKIE_TALKIE_MAX_FREQ = 999;     // 最大周波数
    public static final int WALKIE_TALKIE_FILTER_LOW = 300;   // ローカットフィルター（Hz）
    public static final int WALKIE_TALKIE_FILTER_HIGH = 3400; // ハイカットフィルター（Hz）

    // === パケット識別子 ===
    public static final byte PACKET_TYPE_SIMULATION = 0x01;
    public static final byte PACKET_TYPE_DEVICE = 0x02;
    public static final byte PACKET_TYPE_BAND = 0x03;
    public static final byte PACKET_TYPE_CONTROL = 0x10;
}
