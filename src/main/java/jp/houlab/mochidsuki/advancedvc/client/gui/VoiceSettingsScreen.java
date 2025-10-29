package jp.houlab.mochidsuki.advancedvc.client.gui;

import jp.houlab.mochidsuki.advancedvc.Config;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import jp.houlab.mochidsuki.advancedvc.client.audio.MicrophoneCapture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import java.util.ArrayList;
import java.util.List;

/**
 * ボイスチャット設定画面
 */
public class VoiceSettingsScreen extends Screen {

    private final Screen lastScreen;
    private final List<String> inputDevices = new ArrayList<>();
    private final List<String> outputDevices = new ArrayList<>();

    private int selectedInputIndex = 0;
    private int selectedOutputIndex = 0;
    private double vadThreshold = Config.vadThreshold;
    private double inputVolume = 1.0;
    private double outputVolume = 1.0;

    private Button inputDeviceButton;
    private Button outputDeviceButton;
    private VadThresholdSlider vadSlider;
    private VolumeSlider inputVolumeSlider;
    private VolumeSlider outputVolumeSlider;

    public VoiceSettingsScreen(Screen lastScreen) {
        super(Component.literal("VC設定"));
        this.lastScreen = lastScreen;
        loadAudioDevices();
    }

    /**
     * 利用可能なオーディオデバイスを読み込み
     */
    private void loadAudioDevices() {
        inputDevices.clear();
        outputDevices.clear();

        // デフォルトデバイスを追加
        inputDevices.add("デフォルト");
        outputDevices.add("デフォルト");

        // 利用可能なミキサーを取得
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);

                // 入力デバイス（マイク）
                if (mixer.getTargetLineInfo().length > 0) {
                    inputDevices.add(mixerInfo.getName());
                }

                // 出力デバイス（スピーカー）
                if (mixer.getSourceLineInfo().length > 0) {
                    outputDevices.add(mixerInfo.getName());
                }
            } catch (Exception e) {
                // スキップ
            }
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 60;
        int buttonWidth = 200;
        int spacing = 30;

        // タイトル
        // (描画はrenderメソッドで行う)

        // 入力デバイス選択
        this.inputDeviceButton = Button.builder(
                Component.literal("入力: " + inputDevices.get(selectedInputIndex)),
                button -> {
                    selectedInputIndex = (selectedInputIndex + 1) % inputDevices.size();
                    button.setMessage(Component.literal("入力: " + inputDevices.get(selectedInputIndex)));
                })
                .bounds(centerX - buttonWidth / 2, startY, buttonWidth, 20)
                .build();
        this.addRenderableWidget(inputDeviceButton);

        // 出力デバイス選択
        this.outputDeviceButton = Button.builder(
                Component.literal("出力: " + outputDevices.get(selectedOutputIndex)),
                button -> {
                    selectedOutputIndex = (selectedOutputIndex + 1) % outputDevices.size();
                    button.setMessage(Component.literal("出力: " + outputDevices.get(selectedOutputIndex)));
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, 20)
                .build();
        this.addRenderableWidget(outputDeviceButton);

        // VAD閾値スライダー
        this.vadSlider = new VadThresholdSlider(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, 20, vadThreshold);
        this.addRenderableWidget(vadSlider);

        // 入力ボリュームスライダー
        this.inputVolumeSlider = new VolumeSlider(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, 20,
                Component.literal("入力音量"), inputVolume);
        this.addRenderableWidget(inputVolumeSlider);

        // 出力ボリュームスライダー
        this.outputVolumeSlider = new VolumeSlider(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, 20,
                Component.literal("出力音量"), outputVolume);
        this.addRenderableWidget(outputVolumeSlider);

        // マイクテストボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("マイクテスト"),
                button -> testMicrophone())
                .bounds(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth / 2 - 5, 20)
                .build());

        // リセットボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("リセット"),
                button -> resetToDefaults())
                .bounds(centerX + 5, startY + spacing * 5, buttonWidth / 2 - 5, 20)
                .build());

        // 完了ボタン
        this.addRenderableWidget(Button.builder(
                Component.literal("完了"),
                button -> {
                    applySettings();
                    this.minecraft.setScreen(lastScreen);
                })
                .bounds(centerX - buttonWidth / 2, startY + spacing * 6, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // タイトル描画
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // ラベル描画
        guiGraphics.drawString(this.font, "オーディオデバイス", this.width / 2 - 100, 45, 0xAAAAAA);
        guiGraphics.drawString(this.font, "音声検知設定", this.width / 2 - 100, 135, 0xAAAAAA);
        guiGraphics.drawString(this.font, "音量設定", this.width / 2 - 100, 165, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 設定を適用
     */
    private void applySettings() {
        ClientAudioEngine engine = ClientAudioEngine.getInstance();

        // VAD閾値を更新・保存
        vadThreshold = vadSlider.getThreshold();
        Config.saveVadThreshold(vadThreshold);

        // 音量を更新
        inputVolume = inputVolumeSlider.getVolume();
        outputVolume = outputVolumeSlider.getVolume();

        // ClientAudioEngineに適用
        if (engine.isRunning()) {
            engine.updateVadThreshold(vadThreshold);
            engine.updateInputVolume(inputVolume);
            engine.updateOutputVolume(outputVolume);
        }

        // TODO: デバイス変更の実装（エンジンの再起動が必要）
        // TODO: 音量設定の永続化（現在は一時的な設定のみ）
    }

    /**
     * デフォルト値にリセット
     */
    private void resetToDefaults() {
        selectedInputIndex = 0;
        selectedOutputIndex = 0;
        vadThreshold = 0.02;
        inputVolume = 1.0;
        outputVolume = 1.0;

        // UIを更新
        inputDeviceButton.setMessage(Component.literal("入力: " + inputDevices.get(selectedInputIndex)));
        outputDeviceButton.setMessage(Component.literal("出力: " + outputDevices.get(selectedOutputIndex)));
        vadSlider.setThreshold(vadThreshold);
        inputVolumeSlider.setVolume(inputVolume);
        outputVolumeSlider.setVolume(outputVolume);
    }

    /**
     * マイクテスト
     */
    private void testMicrophone() {
        // TODO: マイクテスト機能の実装
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * VAD閾値スライダー
     */
    private static class VadThresholdSlider extends AbstractSliderButton {
        private double threshold;

        public VadThresholdSlider(int x, int y, int width, int height, double initialThreshold) {
            super(x, y, width, height, Component.empty(), (initialThreshold - 0.001) / 0.099);
            this.threshold = initialThreshold;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("VAD閾値: %.3f", threshold)));
        }

        @Override
        protected void applyValue() {
            this.threshold = 0.001 + (this.value * 0.099); // 0.001 ~ 0.1
            updateMessage();
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
            this.value = (threshold - 0.001) / 0.099;
            updateMessage();
        }
    }

    /**
     * ボリュームスライダー
     */
    private static class VolumeSlider extends AbstractSliderButton {
        private final Component label;
        private double volume;

        public VolumeSlider(int x, int y, int width, int height, Component label, double initialVolume) {
            super(x, y, width, height, Component.empty(), initialVolume);
            this.label = label;
            this.volume = initialVolume;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format("%s: %d%%", label.getString(), (int)(volume * 100))));
        }

        @Override
        protected void applyValue() {
            this.volume = this.value;
            updateMessage();
        }

        public double getVolume() {
            return volume;
        }

        public void setVolume(double volume) {
            this.volume = volume;
            this.value = volume;
            updateMessage();
        }
    }
}
