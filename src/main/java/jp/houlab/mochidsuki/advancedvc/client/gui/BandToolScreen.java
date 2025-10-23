package jp.houlab.mochidsuki.advancedvc.client.gui;

import jp.houlab.mochidsuki.advancedvc.network.NetworkManager;
import jp.houlab.mochidsuki.advancedvc.network.packet.BandSessionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * バンドツール（PAミキサー）GUI
 */
public class BandToolScreen extends Screen {

    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 200;

    private List<MixerChannel> channels = new ArrayList<>();

    public BandToolScreen() {
        super(Component.literal("PAミキサー"));
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // 4チャンネルのミキサー
        for (int i = 0; i < 4; i++) {
            MixerChannel channel = new MixerChannel("CH " + (i + 1));
            channels.add(channel);

            int channelX = leftPos + 10 + (i * 70);
            int channelY = topPos + 30;

            // ボリュームスライダー
            this.addRenderableWidget(new VolumeSlider(
                    channelX, channelY, 60, 20,
                    Component.literal("Vol"), channel
            ));

            // コンプレッサー
            this.addRenderableWidget(Button.builder(
                    Component.literal("Comp"),
                    button -> {
                        channel.compressorEnabled = !channel.compressorEnabled;
                        button.setMessage(Component.literal(channel.compressorEnabled ? "Comp: ON" : "Comp: OFF"));
                    }
            ).bounds(channelX, channelY + 30, 60, 15).build());

            // EQ
            this.addRenderableWidget(Button.builder(
                    Component.literal("EQ"),
                    button -> {
                        // TODO: EQ詳細設定ダイアログ
                    }
            ).bounds(channelX, channelY + 50, 60, 15).build());

            // Send (Delay/Reverb)
            this.addRenderableWidget(new SendSlider(
                    channelX, channelY + 70, 60, 20,
                    Component.literal("Send"), channel
            ));
        }

        // マスターセクション
        int masterX = leftPos + 10;
        int masterY = topPos + 130;

        this.addRenderableWidget(Button.builder(
                Component.literal("セッション開始"),
                button -> {
                    // バンドセッション開始パケット送信
                    if (Minecraft.getInstance().player != null) {
                        BandSessionPacket packet = BandSessionPacket.start(Minecraft.getInstance().player.getUUID());
                        NetworkManager.CHANNEL.sendToServer(packet);
                    }
                }
        ).bounds(masterX, masterY, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("セッション終了"),
                button -> {
                    // バンドセッション終了パケット送信
                    if (Minecraft.getInstance().player != null) {
                        BandSessionPacket packet = BandSessionPacket.end(Minecraft.getInstance().player.getUUID());
                        NetworkManager.CHANNEL.sendToServer(packet);
                    }
                }
        ).bounds(masterX + 110, masterY, 100, 20).build());

        // 閉じるボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("閉じる"),
                button -> this.onClose()
        ).bounds(leftPos + GUI_WIDTH - 70, topPos + GUI_HEIGHT - 30, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // 背景
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xC0101010);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + GUI_WIDTH - 2, topPos + GUI_HEIGHT - 2, 0xFF2A2A2A);

        // タイトル
        graphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 8, 0xFFFFFF);

        // チャンネルラベル
        for (int i = 0; i < channels.size(); i++) {
            int channelX = leftPos + 10 + (i * 70);
            int channelY = topPos + 20;
            graphics.drawString(this.font, channels.get(i).name, channelX, channelY, 0xFFFFFF);
        }

        // セクション区切り線
        graphics.fill(leftPos + 5, topPos + 120, leftPos + GUI_WIDTH - 5, topPos + 122, 0xFF555555);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * ミキサーチャンネル
     */
    private static class MixerChannel {
        String name;
        double volume = 0.8;
        double sendLevel = 0.0;
        boolean compressorEnabled = false;
        double eqLow = 0.5;
        double eqMid = 0.5;
        double eqHigh = 0.5;

        public MixerChannel(String name) {
            this.name = name;
        }
    }

    /**
     * ボリュームスライダー
     */
    private static class VolumeSlider extends AbstractSliderButton {
        private final MixerChannel channel;

        public VolumeSlider(int x, int y, int width, int height, Component message, MixerChannel channel) {
            super(x, y, width, height, message, channel.volume);
            this.channel = channel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("Vol: %d%%", (int)(value * 100))));
        }

        @Override
        protected void applyValue() {
            channel.volume = value;
        }
    }

    /**
     * Sendスライダー
     */
    private static class SendSlider extends AbstractSliderButton {
        private final MixerChannel channel;

        public SendSlider(int x, int y, int width, int height, Component message, MixerChannel channel) {
            super(x, y, width, height, message, channel.sendLevel);
            this.channel = channel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("Send: %d%%", (int)(value * 100))));
        }

        @Override
        protected void applyValue() {
            channel.sendLevel = value;
        }
    }
}
