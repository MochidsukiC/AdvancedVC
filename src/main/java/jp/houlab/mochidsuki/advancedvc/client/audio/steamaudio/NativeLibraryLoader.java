package jp.houlab.mochidsuki.advancedvc.client.audio.steamaudio;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * ネイティブライブラリローダー
 * Steam Audioのネイティブライブラリ（phonon.dll等）をJARから抽出してロードする
 */
public class NativeLibraryLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loaded = false;

    /**
     * Steam Audioネイティブライブラリをロード
     * @return ロード成功時true、失敗時false
     */
    public static synchronized boolean loadSteamAudio() {
        if (loaded) {
            return true;
        }

        try {
            // プラットフォーム検出
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();

            String platformDir;
            String[] libraryNames;

            if (os.contains("win")) {
                platformDir = "natives/windows";
                libraryNames = new String[]{"phonon.dll", "GPUUtilities.dll", "TrueAudioNext.dll"};
            } else if (os.contains("mac") || os.contains("darwin")) {
                platformDir = "natives/macos";
                libraryNames = new String[]{"libphonon.dylib"};
            } else if (os.contains("linux")) {
                platformDir = "natives/linux";
                libraryNames = new String[]{"libphonon.so"};
            } else {
                LOGGER.error("Unsupported platform: {} {}", os, arch);
                return false;
            }

            LOGGER.info("Detected platform: {} {}", os, arch);
            LOGGER.info("Loading Steam Audio libraries from: {}", platformDir);

            // 一時ディレクトリを作成
            Path tempDir = Files.createTempDirectory("steamaudio_natives");
            tempDir.toFile().deleteOnExit();

            // 各ライブラリを抽出してロード
            for (String libraryName : libraryNames) {
                String resourcePath = "/" + platformDir + "/" + libraryName;
                File tempFile = extractLibrary(resourcePath, tempDir, libraryName);

                if (tempFile != null) {
                    // ライブラリをロード
                    System.load(tempFile.getAbsolutePath());
                    LOGGER.info("Loaded native library: {}", libraryName);
                } else {
                    LOGGER.error("Failed to extract library: {}", libraryName);
                    return false;
                }
            }

            loaded = true;
            LOGGER.info("Steam Audio native libraries loaded successfully");
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load Steam Audio native libraries", e);
            return false;
        }
    }

    /**
     * JARからネイティブライブラリを抽出
     * @param resourcePath リソースパス
     * @param tempDir 一時ディレクトリ
     * @param fileName ファイル名
     * @return 抽出されたファイル
     */
    private static File extractLibrary(String resourcePath, Path tempDir, String fileName) {
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.error("Library not found in JAR: {}", resourcePath);
                return null;
            }

            Path outputPath = tempDir.resolve(fileName);
            Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);

            File outputFile = outputPath.toFile();
            outputFile.deleteOnExit();

            return outputFile;

        } catch (IOException e) {
            LOGGER.error("Failed to extract library: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * ライブラリがロード済みか確認
     * @return ロード済みならtrue
     */
    public static boolean isLoaded() {
        return loaded;
    }
}
