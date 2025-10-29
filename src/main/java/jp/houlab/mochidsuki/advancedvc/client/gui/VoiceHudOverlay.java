package jp.houlab.mochidsuki.advancedvc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.houlab.mochidsuki.advancedvc.client.VoiceStatus;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * ボイスチャット用HUDオーバーレイ
 * 左下に声量レベルとマイク状態を表示
 */
public class VoiceHudOverlay implements IGuiOverlay {

    private static final int HUD_X = 10;  // 左端からのオフセット
    private static final int HUD_Y_OFFSET = 50; // 下端からのオフセット

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // デバッグ: ConfigでVC機能が無効の場合は表示しない
        if (!jp.houlab.mochidsuki.advancedvc.Config.enableVoiceChat) {
            return;
        }

        ClientAudioEngine engine = ClientAudioEngine.getInstance();
        Font font = mc.font;

        // Y座標（画面下部から）
        int hudY = screenHeight - HUD_Y_OFFSET;

        // マイク状態を取得（エンジンが動いていなくても状態を表示）
        VoiceStatus status = getVoiceStatus(engine);

        // 声量レベルを取得（0.0 ~ 1.0）
        float voiceLevel = engine.isRunning() ? engine.getCurrentVoiceLevel() : 0.0f;

        RenderSystem.enableBlend();

        // マイク状態表示
        String statusText = status.getDisplayName();
        int statusColor = status.getColor();
        guiGraphics.drawString(font, statusText, HUD_X, hudY, statusColor, true);

        // 声量レベルバー表示
        int barX = HUD_X + font.width(statusText) + 10;
        int barY = hudY;
        int barWidth = 80;
        int barHeight = 8;

        // バーの背景（黒）
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

        // バーの前景（レベルに応じて色変更）
        int levelWidth = (int) (barWidth * voiceLevel);
        int levelColor = getVoiceLevelColor(voiceLevel);
        if (levelWidth > 0) {
            guiGraphics.fill(barX, barY, barX + levelWidth, barY + barHeight, levelColor);
        }

        // バーの枠線
        guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFFFFFFFF); // 上
        guiGraphics.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFFFFFFFF); // 下
        guiGraphics.fill(barX - 1, barY, barX, barY + barHeight, 0xFFFFFFFF); // 左
        guiGraphics.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFFFFFFFF); // 右

        RenderSystem.disableBlend();
    }

    /**
     * マイク状態を取得
     */
    private VoiceStatus getVoiceStatus(ClientAudioEngine engine) {
        // 接続失敗状態を優先的にチェック
        if (engine.isConnectionFailed() || !engine.isServerConnected()) {
            return VoiceStatus.CONNECTION_FAILED;
        }
        if (engine.isMuted()) {
            return VoiceStatus.MUTED;
        }
        if (engine.isSpeaking()) {
            return VoiceStatus.SPEAKING;
        }
        return VoiceStatus.IDLE;
    }

    /**
     * 声量レベルに応じた色を取得
     */
    private int getVoiceLevelColor(float level) {
        if (level < 0.3f) {
            return 0xFF00FF00; // 緑
        } else if (level < 0.7f) {
            return 0xFFFFFF00; // 黄色
        } else {
            return 0xFFFF0000; // 赤
        }
    }
}
