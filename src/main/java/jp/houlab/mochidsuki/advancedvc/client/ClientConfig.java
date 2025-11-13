package jp.houlab.mochidsuki.advancedvc.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * クライアント側の設定永続化
 * Forge の TOML 共通設定とは独立して、UI 操作で変更される値を保存する。
 */
public class ClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "advancedvc-client.json";

    private static ClientConfig INSTANCE;

    // 保存対象フィールド（初期値）
    public boolean enableVoiceChat = true;
    public boolean autoStart = true;
    public double vadThreshold = 0.01; // 表示/UI 用のしきい値
    public double inputVolume = 1.0;
    public double outputVolume = 1.0;
    public String inputDevice = "デフォルト";
    public String outputDevice = "デフォルト";
    public String volumeLevel = "NORMAL"; // 声量レベル（WHISPER, QUIET, NORMAL, LOUD, SHOUT）
    public boolean visualizeRays = false; // レイ可視化（デバッグ表示）
    // Steam Audio使用（Phase 1: バイノーラル再生）
    // ARM64環境ではSteam Audio自体のバグによりクラッシュするため、OpenAL EFXを使用
    // iplAudioBufferAllocateは成功し、dataポインタも設定されるが、
    // iplBinauralEffectApplyでdataポインタの間接参照時にNULLポインタでクラッシュ
    // これはSteam Audio v4.7.0のARM64実装のバグと判断
    public boolean useSteamAudio = !isArmArchitecture();

    /**
     * システムアーキテクチャがARMかどうかを判定
     * @return ARMアーキテクチャの場合true
     */
    private static boolean isArmArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.contains("arm") || arch.contains("aarch");
    }


    public static synchronized ClientConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new ClientConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    private Path configPath() {
        // 通常 Forge は config/ ディレクトリを用いる
        return Paths.get("config").resolve(FILE_NAME);
    }

    public void load() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                // 初回は作成
                save();
                return;
            }
            try (Reader r = Files.newBufferedReader(path)) {
                ClientConfig loaded = GSON.fromJson(r, ClientConfig.class);
                if (loaded != null) {
                    this.enableVoiceChat = loaded.enableVoiceChat;
                    this.autoStart = loaded.autoStart;
                    this.vadThreshold = loaded.vadThreshold;
                    this.inputVolume = loaded.inputVolume;
                    this.outputVolume = loaded.outputVolume;
                    this.inputDevice = loaded.inputDevice;
                    this.outputDevice = loaded.outputDevice;
                    this.volumeLevel = loaded.volumeLevel != null ? loaded.volumeLevel : "NORMAL";
                    this.visualizeRays = loaded.visualizeRays;
                    this.useSteamAudio = loaded.useSteamAudio;
                }
            }

            // ARM環境では設定ファイルの値に関わらず強制的にSteam Audioを無効化
            if (isArmArchitecture()) {
                if (this.useSteamAudio) {
                    LOGGER.warn("Steam Audio is not supported on ARM64 architecture. Forcing useSteamAudio=false and using OpenAL EFX instead.");
                    this.useSteamAudio = false;
                    save(); // 設定を更新して保存
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load client config, using defaults", e);
        }
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save client config", e);
        }
    }
}
