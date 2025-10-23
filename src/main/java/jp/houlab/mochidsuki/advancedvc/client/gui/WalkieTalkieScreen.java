package jp.houlab.mochidsuki.advancedvc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.item.WalkieTalkieItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * ウォーキートーキー周波数設定GUI
 */
public class WalkieTalkieScreen extends Screen {
    private final ItemStack walkieTalkie;
    private EditBox frequencyInput;
    private int currentFrequency;

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 100;

    public WalkieTalkieScreen(ItemStack walkieTalkie) {
        super(Component.literal("ウォーキートーキー設定"));
        this.walkieTalkie = walkieTalkie;
        this.currentFrequency = WalkieTalkieItem.getFrequency(walkieTalkie);
    }

    @Override
    protected void init() {
        super.init();

        int leftPos = (this.width - GUI_WIDTH) / 2;
        int topPos = (this.height - GUI_HEIGHT) / 2;

        // 周波数入力フィールド
        frequencyInput = new EditBox(
                this.font,
                leftPos + 38,
                topPos + 35,
                100,
                20,
                Component.literal("Frequency")
        );
        frequencyInput.setMaxLength(3);
        frequencyInput.setValue(String.valueOf(currentFrequency));
        frequencyInput.setFilter(s -> s.matches("\\d*")); // 数字のみ
        this.addRenderableWidget(frequencyInput);

        // OKボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("設定"),
                button -> {
                    try {
                        int freq = Integer.parseInt(frequencyInput.getValue());
                        if (freq >= AudioConstants.WALKIE_TALKIE_MIN_FREQ &&
                            freq <= AudioConstants.WALKIE_TALKIE_MAX_FREQ) {
                            WalkieTalkieItem.setFrequency(walkieTalkie, freq);
                            ClientAudioEngine.getInstance().setWalkieTalkieFrequency(freq);
                            this.onClose();
                        } else {
                            frequencyInput.setValue(String.valueOf(currentFrequency));
                        }
                    } catch (NumberFormatException e) {
                        frequencyInput.setValue(String.valueOf(currentFrequency));
                    }
                }
        ).bounds(leftPos + 20, topPos + 65, 60, 20).build());

        // キャンセルボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("キャンセル"),
                button -> this.onClose()
        ).bounds(leftPos + 96, topPos + 65, 60, 20).build());

        // プリセットボタン
        int presetX = leftPos + 20;
        int presetY = topPos + 15;
        this.addRenderableWidget(createPresetButton("1", 1, presetX, presetY));
        this.addRenderableWidget(createPresetButton("10", 10, presetX + 30, presetY));
        this.addRenderableWidget(createPresetButton("100", 100, presetX + 65, presetY));
        this.addRenderableWidget(createPresetButton("500", 500, presetX + 105, presetY));
    }

    private Button createPresetButton(String label, int frequency, int x, int y) {
        return Button.builder(
                Component.literal(label),
                button -> frequencyInput.setValue(String.valueOf(frequency))
        ).bounds(x, y, 25, 15).build();
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 5, 0xFFFFFF);

        // ラベル
        graphics.drawString(this.font, "周波数:", leftPos + 38, topPos + 25, 0xFFFFFF);
        graphics.drawString(this.font, "Hz", leftPos + 142, topPos + 40, 0xFFFFFF);

        // 範囲表示
        String rangeText = String.format("(%d - %d)",
                AudioConstants.WALKIE_TALKIE_MIN_FREQ,
                AudioConstants.WALKIE_TALKIE_MAX_FREQ);
        graphics.drawCenteredString(this.font, rangeText, this.width / 2, topPos + GUI_HEIGHT - 10, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
