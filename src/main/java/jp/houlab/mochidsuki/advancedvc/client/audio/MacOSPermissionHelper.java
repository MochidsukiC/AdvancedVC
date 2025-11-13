package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * macOS環境でのマイク許可要求ヘルパー
 * macOS 10.14 Mojave以降では、マイクアクセスに明示的な許可が必要
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
     * macOS環境でマイク許可を要求する
     * Java Sound APIを使用してマイクを開こうとすることで、システムダイアログを表示
     *
     * @return 許可が得られた場合true、拒否された場合false
     */
    public static boolean requestMicrophonePermission() {
        if (!isMacOS()) {
            LOGGER.debug("Not macOS, skipping permission check");
            return true; // macOS以外は常に許可されているとみなす
        }

        LOGGER.info("Checking microphone permission on macOS...");

        try {
            // マイクを開いてみる（システムダイアログが表示される）
            LOGGER.info("Attempting to open microphone to trigger permission dialog...");
            TargetDataLine testLine = AudioSystem.getTargetDataLine(null);
            LOGGER.info("Got TargetDataLine: {}", testLine);

            testLine.open();
            LOGGER.info("Successfully opened microphone");

            testLine.close();
            LOGGER.info("Closed test microphone");

            LOGGER.info("Microphone permission granted");
            return true;

        } catch (LineUnavailableException e) {
            LOGGER.error("Microphone permission denied or microphone unavailable", e);
            LOGGER.error("LineUnavailableException message: {}", e.getMessage());

            // ユーザーにシステム設定を確認するよう案内
            showMacOSPermissionWarning();
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to check microphone permission", e);
            LOGGER.error("Exception type: {}, message: {}", e.getClass().getName(), e.getMessage());
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
