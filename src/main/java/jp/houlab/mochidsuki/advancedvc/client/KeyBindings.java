package jp.houlab.mochidsuki.advancedvc.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import jp.houlab.mochidsuki.advancedvc.client.gui.VoiceSettingsScreen;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 音声制御用キーバインディング
 */
@Mod.EventBusSubscriber(modid = AdvancedvcMain.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyBindings {

    // キーマッピング定義
    public static final KeyMapping KEY_PTT = new KeyMapping(
            "key.advancedvc.ptt",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // デフォルト: V
            "key.categories.advancedvc"
    );

    public static final KeyMapping KEY_MUTE = new KeyMapping(
            "key.advancedvc.mute",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, // デフォルト: M
            "key.categories.advancedvc"
    );

    public static final KeyMapping KEY_VOLUME_UP = new KeyMapping(
            "key.advancedvc.volume_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_ADD, // デフォルト: テンキー+
            "key.categories.advancedvc"
    );

    public static final KeyMapping KEY_VOLUME_DOWN = new KeyMapping(
            "key.advancedvc.volume_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_SUBTRACT, // デフォルト: テンキー-
            "key.categories.advancedvc"
    );

    public static final KeyMapping KEY_SETTINGS = new KeyMapping(
            "key.advancedvc.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // デフォルト: K
            "key.categories.advancedvc"
    );

    /**
     * キーマッピング登録イベント
     */
    @Mod.EventBusSubscriber(modid = AdvancedvcMain.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class RegistrationEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KEY_PTT);
            event.register(KEY_MUTE);
            event.register(KEY_VOLUME_UP);
            event.register(KEY_VOLUME_DOWN);
            event.register(KEY_SETTINGS);
        }
    }

    /**
     * キー入力処理
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        ClientAudioEngine engine = ClientAudioEngine.getInstance();

        // PTTキー
        if (KEY_PTT.matches(event.getKey(), event.getScanCode())) {
            boolean pressed = event.getAction() == GLFW.GLFW_PRESS || event.getAction() == GLFW.GLFW_REPEAT;
            engine.setPTTPressed(pressed);
        }

        // ミュートキー（トグル）
        if (KEY_MUTE.consumeClick()) {
            boolean currentMute = engine.isMuted();
            engine.setMuted(!currentMute);

            String status = !currentMute ? "ミュート" : "ミュート解除";
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("音声: " + status),
                    true
            );
        }

        // 声量アップ
        if (KEY_VOLUME_UP.consumeClick()) {
            VolumeLevel current = engine.getCurrentVolume();
            VolumeLevel next = current.next();
            engine.setVolumeLevel(next);

            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("声量: " + next.getDisplayName()),
                    true
            );
        }

        // 声量ダウン
        if (KEY_VOLUME_DOWN.consumeClick()) {
            VolumeLevel current = engine.getCurrentVolume();
            VolumeLevel previous = current.previous();
            engine.setVolumeLevel(previous);

            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("声量: " + previous.getDisplayName()),
                    true
            );
        }

        // 設定画面を開く
        if (KEY_SETTINGS.consumeClick()) {
            mc.setScreen(new VoiceSettingsScreen(mc.screen));
        }
    }
}
