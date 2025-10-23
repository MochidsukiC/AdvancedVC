package jp.houlab.mochidsuki.advancedvc.client.gui;

import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import jp.houlab.mochidsuki.advancedvc.network.NetworkManager;
import jp.houlab.mochidsuki.advancedvc.network.packet.SpeakerConnectionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * スピーカー接続設定GUI
 */
public class SpeakerConnectionScreen extends Screen {
    private final BlockPos speakerPos;
    private EditBox micXInput;
    private EditBox micYInput;
    private EditBox micZInput;
    private Button amplificationButton;
    private VolumeLevel currentAmplification = VolumeLevel.LOUD;

    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 140;

    public SpeakerConnectionScreen(BlockPos speakerPos) {
        super(Component.literal("スピーカー設定"));
        this.speakerPos = speakerPos;
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // マイク座標入力フィールド
        micXInput = createCoordinateInput(leftPos + 70, topPos + 30);
        micYInput = createCoordinateInput(leftPos + 70, topPos + 55);
        micZInput = createCoordinateInput(leftPos + 70, topPos + 80);

        this.addRenderableWidget(micXInput);
        this.addRenderableWidget(micYInput);
        this.addRenderableWidget(micZInput);

        // 増幅レベル切り替えボタン
        amplificationButton = Button.builder(
                Component.literal("増幅: " + currentAmplification.getDisplayName()),
                button -> {
                    currentAmplification = currentAmplification.next();
                    button.setMessage(Component.literal("増幅: " + currentAmplification.getDisplayName()));
                }
        ).bounds(leftPos + 10, topPos + 105, 180, 20).build();
        this.addRenderableWidget(amplificationButton);

        // 接続ボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("接続"),
                button -> {
                    try {
                        int x = Integer.parseInt(micXInput.getValue());
                        int y = Integer.parseInt(micYInput.getValue());
                        int z = Integer.parseInt(micZInput.getValue());
                        BlockPos micPos = new BlockPos(x, y, z);

                        // パケットをサーバーに送信してスピーカーを接続
                        SpeakerConnectionPacket packet = SpeakerConnectionPacket.connect(speakerPos, micPos, currentAmplification);
                        NetworkManager.CHANNEL.sendToServer(packet);

                        this.onClose();
                    } catch (NumberFormatException e) {
                        // エラー処理
                    }
                }
        ).bounds(leftPos + 10, topPos + 130, 80, 20).build());

        // 切断ボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("切断"),
                button -> {
                    // パケットをサーバーに送信してスピーカーを切断
                    SpeakerConnectionPacket packet = SpeakerConnectionPacket.disconnect(speakerPos);
                    NetworkManager.CHANNEL.sendToServer(packet);
                    this.onClose();
                }
        ).bounds(leftPos + 110, topPos + 130, 80, 20).build());
    }

    private EditBox createCoordinateInput(int x, int y) {
        EditBox editBox = new EditBox(this.font, x, y, 100, 20, Component.literal("Coordinate"));
        editBox.setMaxLength(10);
        editBox.setValue("0");
        editBox.setFilter(s -> s.matches("-?\\d*")); // 負の数も許可
        return editBox;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // 背景
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xC0101010);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + GUI_WIDTH - 2, topPos + GUI_HEIGHT - 2, 0xFF383838);

        // タイトル
        graphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 8, 0xFFFFFF);

        // ラベル
        graphics.drawString(this.font, "スピーカー位置:", leftPos + 10, topPos + 18, 0xAAAAAA);
        graphics.drawString(this.font, speakerPos.toShortString(), leftPos + 100, topPos + 18, 0xFFFFFF);

        graphics.drawString(this.font, "マイク X:", leftPos + 10, topPos + 35, 0xFFFFFF);
        graphics.drawString(this.font, "マイク Y:", leftPos + 10, topPos + 60, 0xFFFFFF);
        graphics.drawString(this.font, "マイク Z:", leftPos + 10, topPos + 85, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
