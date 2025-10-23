package jp.houlab.mochidsuki.advancedvc;

import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Advanced VC 設定
 */
@Mod.EventBusSubscriber(modid = AdvancedvcMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===== ネットワーク設定 =====
    private static final ForgeConfigSpec.IntValue UDP_PORT = BUILDER
            .comment("UDP port for voice communication")
            .defineInRange("udpPort", AudioConstants.DEFAULT_UDP_PORT, 1024, 65535);

    // ===== オーディオ設定 =====
    private static final ForgeConfigSpec.IntValue SAMPLE_RATE = BUILDER
            .comment("Audio sample rate (Hz)")
            .defineInRange("sampleRate", AudioConstants.SAMPLE_RATE, 8000, 48000);

    private static final ForgeConfigSpec.IntValue OPUS_BITRATE_HIGH = BUILDER
            .comment("Opus encoder bitrate for high quality mode (bps)")
            .defineInRange("opusBitrateHigh", AudioConstants.OPUS_BITRATE_HIGH, 16000, 128000);

    private static final ForgeConfigSpec.IntValue OPUS_BITRATE_LOW = BUILDER
            .comment("Opus encoder bitrate for low quality mode (bps)")
            .defineInRange("opusBitrateLow", AudioConstants.OPUS_BITRATE_LOW, 6000, 32000);

    // ===== VAD設定 =====
    private static final ForgeConfigSpec.DoubleValue VAD_THRESHOLD = BUILDER
            .comment("Voice activity detection threshold (0.0 - 1.0)")
            .defineInRange("vadThreshold", (double) AudioConstants.VAD_THRESHOLD, 0.0, 1.0);

    // ===== 距離設定 =====
    private static final ForgeConfigSpec.DoubleValue ATTENUATION_FACTOR = BUILDER
            .comment("Distance attenuation factor")
            .defineInRange("attenuationFactor", (double) AudioConstants.ATTENUATION_FACTOR, 0.0, 1.0);

    // ===== クライアント設定 =====
    private static final ForgeConfigSpec.BooleanValue ENABLE_VOICE_CHAT = BUILDER
            .comment("Enable voice chat (client-side)")
            .define("enableVoiceChat", true);

    private static final ForgeConfigSpec.BooleanValue AUTO_START = BUILDER
            .comment("Automatically start voice chat when joining a server")
            .define("autoStart", true);

    // ===== デバッグ設定 =====
    private static final ForgeConfigSpec.BooleanValue DEBUG_MODE = BUILDER
            .comment("Enable debug logging")
            .define("debugMode", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Static config values
    public static int udpPort;
    public static int sampleRate;
    public static int opusBitrateHigh;
    public static int opusBitrateLow;
    public static float vadThreshold;
    public static float attenuationFactor;
    public static boolean enableVoiceChat;
    public static boolean autoStart;
    public static boolean debugMode;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        udpPort = UDP_PORT.get();
        sampleRate = SAMPLE_RATE.get();
        opusBitrateHigh = OPUS_BITRATE_HIGH.get();
        opusBitrateLow = OPUS_BITRATE_LOW.get();
        vadThreshold = VAD_THRESHOLD.get().floatValue();
        attenuationFactor = ATTENUATION_FACTOR.get().floatValue();
        enableVoiceChat = ENABLE_VOICE_CHAT.get();
        autoStart = AUTO_START.get();
        debugMode = DEBUG_MODE.get();
    }
}
