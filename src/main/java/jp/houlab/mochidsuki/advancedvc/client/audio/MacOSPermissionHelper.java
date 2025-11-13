package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * macOS環境でのマイク許可要求ヘルパー
 * macOS 10.14 Mojave以降では、マイクアクセスに明示的な許可が必要
 *
 * 技術的制限：
 * macOS Sequoia (15.6)以降の厳格なセキュリティポリシーにより、
 * MODのコードから直接システムダイアログを表示することは不可能です。
 *
 * 解決策：
 * プロジェクトルートの launch_minecraft_macos.sh スクリプトを使用して
 * Minecraft Launcherを起動してください。
 */
public class MacOSPermissionHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 現在のOSがmacOSかどうかを判定
     * @return macOSの場合true
     */
    public static boolean isMacOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("mac") || osName.contains("darwin");
    }

    /**
     * macOS環境でマイク許可を確認する
     *
     * 注意：macOS Sequoia以降では、MODのコードからシステムダイアログを表示できません。
     * launch_minecraft_macos.sh スクリプトを使用してMinecraftを起動してください。
     *
     * @return 許可が得られた場合true、拒否された場合false
     */
    public static boolean requestMicrophonePermission() {
        if (!isMacOS()) {
            LOGGER.debug("Not macOS, skipping permission check");
            return true;
        }

        LOGGER.info("Checking microphone permission on macOS...");

        try {
            // Java Sound APIでマイクをテスト
            LOGGER.info("Testing microphone access with Java Sound API...");
            AudioFormat format = new AudioFormat(48000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            LOGGER.info("Getting microphone line...");
            TargetDataLine testLine = (TargetDataLine) AudioSystem.getLine(info);

            LOGGER.info("Opening microphone...");
            testLine.open(format);

            LOGGER.info("Starting microphone...");
            testLine.start();

            LOGGER.info("Microphone opened and started successfully");

            // 少量のデータを読み取って動作確認（データ内容はチェックしない）
            byte[] buffer = new byte[960];
            int bytesRead = testLine.read(buffer, 0, buffer.length);
            LOGGER.info("Read {} bytes from microphone", bytesRead);

            testLine.stop();
            testLine.close();

            // マイクが正常に開けて、データが読めれば許可ありと判定
            // データの内容（0かどうか）はチェックしない（無音環境でも許可されている可能性がある）
            if (bytesRead > 0) {
                LOGGER.info("✓ Microphone permission granted (successfully read {} bytes)", bytesRead);
                return true;
            } else {
                LOGGER.warn("Microphone opened but no data was read - this is unusual");
                showMacOSPermissionInstructions();
                return false;
            }

        } catch (LineUnavailableException e) {
            LOGGER.error("✗ Microphone permission denied or microphone unavailable: {}", e.getMessage());
            LOGGER.error("This usually means macOS has not granted microphone permission to the application");
            showMacOSPermissionInstructions();
            return false;
        } catch (Exception e) {
            LOGGER.error("✗ Unexpected error while checking microphone permission", e);
            showMacOSPermissionInstructions();
            return false;
        }
    }


    /**
     * macOSでマイク許可が必要な旨をユーザーに通知
     */
    public static void showMacOSPermissionWarning() {
        if (!isMacOS()) {
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // チャットメッセージでユーザーに通知
                mc.player.sendSystemMessage(Component.literal("§c[Advanced VC] マイク許可が必要です"));
                mc.player.sendSystemMessage(Component.literal("§e[Advanced VC] システム環境設定 > プライバシーとセキュリティ > マイク"));
                mc.player.sendSystemMessage(Component.literal("§e[Advanced VC] から Minecraft にマイクアクセスを許可してください"));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to show permission warning message", e);
        }

        LOGGER.warn("=====================================");
        LOGGER.warn("macOS Microphone Permission Required");
        LOGGER.warn("=====================================");
        LOGGER.warn("Advanced VCはマイクへのアクセス許可が必要です。");
        LOGGER.warn("");
        LOGGER.warn("以下の手順で許可してください：");
        LOGGER.warn("1. システム環境設定を開く");
        LOGGER.warn("2. 「プライバシーとセキュリティ」を選択");
        LOGGER.warn("3. 左側のメニューから「マイク」を選択");
        LOGGER.warn("4. 右側のリストから「Minecraft」を見つけてチェックを入れる");
        LOGGER.warn("5. Minecraftを再起動");
        LOGGER.warn("=====================================");
    }

    /**
     * macOSでマイク許可の手動設定手順を表示し、システム環境設定を開く
     */
    public static void showMacOSPermissionInstructions() {
        if (!isMacOS()) {
            return;
        }

        LOGGER.error("=====================================");
        LOGGER.error("macOS マイク許可が必要です");
        LOGGER.error("=====================================");
        LOGGER.error("マイクへのアクセスが許可されていません。");
        LOGGER.error("");
        LOGGER.error("手動設定が必要です：");
        LOGGER.error("1. Minecraftを完全に終了");
        LOGGER.error("2. システム環境設定 > プライバシーとセキュリティ > マイク");
        LOGGER.error("3. リストから「Minecraft」、「java」、または「Minecraft Launcher」を探す");
        LOGGER.error("4. 該当アプリにチェックを入れる");
        LOGGER.error("5. Minecraft Launcherから再起動");
        LOGGER.error("=====================================");
        LOGGER.error("");
        LOGGER.error("詳細な手順: プロジェクトルートの README_MACOS.md を参照");
        LOGGER.error("");
        LOGGER.error("技術的背景：");
        LOGGER.error("macOS Sequoia (15.6)以降の厳格なセキュリティポリシーにより、");
        LOGGER.error("MODのコードから自動的にマイク許可を取得することは不可能です。");
        LOGGER.error("Simple Voice Chat (SVC)も同じ制限に直面しています。");
        LOGGER.error("=====================================");

        // Minecraftのチャットにも通知
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§c§l[Advanced VC] マイク許可が必要です！"));
                mc.player.sendSystemMessage(Component.literal("§e[Advanced VC] システム環境設定 > プライバシー > マイク"));
                mc.player.sendSystemMessage(Component.literal("§e[Advanced VC] で Minecraft にチェックを入れてください"));
                mc.player.sendSystemMessage(Component.literal("§e[Advanced VC] 詳細: README_MACOS.md"));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to show in-game message", e);
        }
    }

    /**
     * マイク許可状態を確認（非侵襲的チェック）
     * @return 許可されている可能性が高い場合true
     */
    public static boolean checkMicrophonePermissionStatus() {
        if (!isMacOS()) {
            return true;
        }

        try {
            // TargetDataLineが利用可能かどうかを確認
            // この時点ではまだシステムダイアログは表示されない
            TargetDataLine.Info info = new TargetDataLine.Info(TargetDataLine.class, null);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            LOGGER.debug("Failed to check microphone availability", e);
            return false;
        }
    }
}
