package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.mojang.logging.LogUtils;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;

/**
 * Steam Audio初期化テスト
 * Phase 1: ライブラリロード、Context作成、HRTF作成、BinauralEffect作成の基本動作確認
 */
public class SteamAudioInitTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Steam Audioの基本初期化テスト
     * @return テスト成功時true
     */
    public static boolean runBasicInitTest() {
        LOGGER.info("=== Steam Audio初期化テスト開始 ===");

        // Step 1: ネイティブライブラリのロード
        LOGGER.info("Step 1: ネイティブライブラリをロード中...");
        if (!NativeLibraryLoader.loadSteamAudio()) {
            LOGGER.error("ネイティブライブラリのロードに失敗しました");
            return false;
        }
        LOGGER.info("✅ ネイティブライブラリのロード成功");

        Pointer context = null;
        Pointer hrtf = null;
        Pointer binauralEffect = null;

        try {
            // Step 2: Contextの作成
            LOGGER.info("Step 2: Steam Audio Contextを作成中...");
            SteamAudioLibrary.IPLContextSettings contextSettings = new SteamAudioLibrary.IPLContextSettings();
            contextSettings.version = SteamAudioLibrary.STEAMAUDIO_VERSION;
            contextSettings.logCallback = null;
            contextSettings.allocateCallback = null;
            contextSettings.freeCallback = null;
            contextSettings.write();

            PointerByReference contextRef = new PointerByReference();
            int result = SteamAudioLibrary.INSTANCE.iplContextCreate(contextSettings, contextRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("Context作成に失敗しました。エラーコード: {}", result);
                return false;
            }

            context = contextRef.getValue();
            if (context == null) {
                LOGGER.error("Contextがnullです");
                return false;
            }
            LOGGER.info("✅ Context作成成功: {}", context);

            // Step 3: オーディオ設定の準備
            LOGGER.info("Step 3: オーディオ設定を準備中...");
            SteamAudioLibrary.IPLAudioSettings audioSettings = new SteamAudioLibrary.IPLAudioSettings();
            audioSettings.samplingRate = 48000;  // 48kHz
            audioSettings.frameSize = 1024;      // 1024サンプル/フレーム
            audioSettings.write();
            LOGGER.info("✅ オーディオ設定: {}Hz, {}サンプル/フレーム",
                       audioSettings.samplingRate, audioSettings.frameSize);

            // Step 4: HRTFの作成
            LOGGER.info("Step 4: HRTFを作成中...");
            SteamAudioLibrary.IPLHRTFSettings hrtfSettings = new SteamAudioLibrary.IPLHRTFSettings();
            hrtfSettings.type = SteamAudioLibrary.IPLHRTFType.IPL_HRTFTYPE_DEFAULT;
            hrtfSettings.sofaFileName = null;
            hrtfSettings.sofaData = null;
            hrtfSettings.sofaDataSize = 0;
            hrtfSettings.write();

            PointerByReference hrtfRef = new PointerByReference();
            result = SteamAudioLibrary.INSTANCE.iplHRTFCreate(context, audioSettings, hrtfSettings, hrtfRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("HRTF作成に失敗しました。エラーコード: {}", result);
                return false;
            }

            hrtf = hrtfRef.getValue();
            if (hrtf == null) {
                LOGGER.error("HRTFがnullです");
                return false;
            }
            LOGGER.info("✅ HRTF作成成功: {}", hrtf);

            // Step 5: バイノーラルエフェクトの作成
            LOGGER.info("Step 5: バイノーラルエフェクトを作成中...");
            SteamAudioLibrary.IPLBinauralEffectSettings effectSettings = new SteamAudioLibrary.IPLBinauralEffectSettings();
            effectSettings.hrtf = hrtf;
            effectSettings.write();

            PointerByReference effectRef = new PointerByReference();
            result = SteamAudioLibrary.INSTANCE.iplBinauralEffectCreate(context, audioSettings, effectSettings, effectRef);

            if (result != SteamAudioLibrary.IPLerror.IPL_STATUS_SUCCESS) {
                LOGGER.error("バイノーラルエフェクト作成に失敗しました。エラーコード: {}", result);
                return false;
            }

            binauralEffect = effectRef.getValue();
            if (binauralEffect == null) {
                LOGGER.error("バイノーラルエフェクトがnullです");
                return false;
            }
            LOGGER.info("✅ バイノーラルエフェクト作成成功: {}", binauralEffect);

            LOGGER.info("=== Steam Audio初期化テスト成功 ===");
            return true;

        } catch (Exception e) {
            LOGGER.error("初期化テスト中に例外が発生しました", e);
            return false;

        } finally {
            // Step 6: クリーンアップ
            LOGGER.info("Step 6: リソースをクリーンアップ中...");
            try {
                if (binauralEffect != null) {
                    PointerByReference effectRef = new PointerByReference(binauralEffect);
                    SteamAudioLibrary.INSTANCE.iplBinauralEffectRelease(effectRef);
                    LOGGER.info("✅ バイノーラルエフェクトをリリース");
                }

                if (hrtf != null) {
                    PointerByReference hrtfRef = new PointerByReference(hrtf);
                    SteamAudioLibrary.INSTANCE.iplHRTFRelease(hrtfRef);
                    LOGGER.info("✅ HRTFをリリース");
                }

                if (context != null) {
                    PointerByReference contextRef = new PointerByReference(context);
                    SteamAudioLibrary.INSTANCE.iplContextRelease(contextRef);
                    LOGGER.info("✅ Contextをリリース");
                }
            } catch (Exception e) {
                LOGGER.error("クリーンアップ中に例外が発生しました", e);
            }
        }
    }

    /**
     * より詳細な初期化情報を出力するテスト
     * @return テスト成功時true
     */
    public static boolean runDetailedInitTest() {
        LOGGER.info("=== Steam Audio詳細初期化テスト開始 ===");

        // プラットフォーム情報
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        String javaVersion = System.getProperty("java.version");

        LOGGER.info("実行環境:");
        LOGGER.info("  OS: {}", os);
        LOGGER.info("  アーキテクチャ: {}", arch);
        LOGGER.info("  Java: {}", javaVersion);
        LOGGER.info("  Steam Audio バージョン: {}.{}.{} (0x{:08X})",
                   SteamAudioLibrary.STEAMAUDIO_VERSION_MAJOR,
                   SteamAudioLibrary.STEAMAUDIO_VERSION_MINOR,
                   SteamAudioLibrary.STEAMAUDIO_VERSION_PATCH,
                   SteamAudioLibrary.STEAMAUDIO_VERSION);

        // 基本初期化テストを実行
        return runBasicInitTest();
    }
}
