package jp.houlab.mochidsuki.advancedvc.client.gui;

import jp.houlab.mochidsuki.advancedvc.Config;
import jp.houlab.mochidsuki.advancedvc.client.ClientConfig;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import jp.houlab.mochidsuki.advancedvc.client.audio.MicrophoneCapture;
import jp.houlab.mochidsuki.advancedvc.client.audio.AudioPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
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
    private double vadThreshold = ClientConfig.get().vadThreshold;
    private double inputVolume = ClientConfig.get().inputVolume;
    private double outputVolume = ClientConfig.get().outputVolume;

    private Button inputDeviceButton;
    private Button outputDeviceButton;
    private VadThresholdSlider vadSlider;
    private VolumeSlider inputVolumeSlider;
    private VolumeSlider outputVolumeSlider;
    private volatile boolean micTesting = false;
    private Thread micTestThread;
    private boolean settingsApplied = false; // 設定適用済みフラグ

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

                boolean supportsInput = false;
                boolean supportsOutput = false;

                // Mixerがサポートするラインタイプを確認
                Line.Info[] targetLines = mixer.getTargetLineInfo();
                Line.Info[] sourceLines = mixer.getSourceLineInfo();

                // 入力デバイス（マイク）: TargetDataLineをサポートしているか
                for (Line.Info lineInfo : targetLines) {
                    if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                        supportsInput = true;
                        break;
                    }
                }

                // 出力デバイス（スピーカー）: SourceDataLineをサポートしているか
                for (Line.Info lineInfo : sourceLines) {
                    if (lineInfo.getLineClass().equals(SourceDataLine.class)) {
                        supportsOutput = true;
                        break;
                    }
                }

                // 対応するリストに追加
                if (supportsInput) {
                    inputDevices.add(mixerInfo.getName());
                }
                if (supportsOutput) {
                    outputDevices.add(mixerInfo.getName());
                }
            } catch (Exception e) {
                // スキップ
            }
        }
        // 保存済みデバイスに合わせてインデックスを初期化
        String savedIn = ClientConfig.get().inputDevice;
        String savedOut = ClientConfig.get().outputDevice;
        if (savedIn != null) {
            int idx = inputDevices.indexOf(savedIn);
            if (idx >= 0) selectedInputIndex = idx;
        }
        if (savedOut != null) {
            int idx = outputDevices.indexOf(savedOut);
            if (idx >= 0) selectedOutputIndex = idx;
        }
    }

    @Override
    protected void init() {
        // 設定適用フラグをリセット（画面が開かれるたびにリセット）
        settingsApplied = false;

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
        // 既に適用済みの場合は何もしない（複数回呼び出し防止）
        if (settingsApplied) {
            return;
        }
        settingsApplied = true;

        ClientAudioEngine engine = ClientAudioEngine.getInstance();

        // VAD閾値を更新・保存
        vadThreshold = vadSlider.getThreshold();
        ClientConfig cfg = ClientConfig.get();

        // 現在の設定を保存
        String oldInputDevice = cfg.inputDevice;
        String oldOutputDevice = cfg.outputDevice;

        cfg.vadThreshold = vadThreshold;

        // 音量を更新
        inputVolume = inputVolumeSlider.getVolume();
        outputVolume = outputVolumeSlider.getVolume();
        cfg.inputVolume = inputVolume;
        cfg.outputVolume = outputVolume;

        // デバイス選択を保存（適用には再初期化が必要）
        cfg.inputDevice = inputDevices.get(selectedInputIndex);
        cfg.outputDevice = outputDevices.get(selectedOutputIndex);

        // 保存
        cfg.save();

        // ClientAudioEngineに適用
        if (engine.isRunning()) {
            engine.updateVadThreshold(vadThreshold);
            engine.updateInputVolume(inputVolume);
            engine.updateOutputVolume(outputVolume);

            // デバイスが変更された場合のみ再初期化
            boolean deviceChanged = !java.util.Objects.equals(oldInputDevice, cfg.inputDevice) ||
                                   !java.util.Objects.equals(oldOutputDevice, cfg.outputDevice);
            if (deviceChanged) {
                engine.applySelectedAudioDevicesFromConfig();
            }
        }

        // 注意: デバイス変更は次回エンジン起動時に反映されます
    }

    /**
     * デフォルト値にリセット
     */
    private void resetToDefaults() {
        selectedInputIndex = 0;
        selectedOutputIndex = 0;
        vadThreshold = 0.01;
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
        if (micTesting) {
            micTesting = false; // ループを止める
            return;
        }

        // エンジン稼働中はテストを抑止（デバイス占有の可能性）
        if (ClientAudioEngine.getInstance().isRunning()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("マイクテストは音声エンジン停止時のみ利用できます"), true);
            }
            return;
        }

        // 選択デバイスで簡易ループバック（3秒）
        final String inName = inputDevices.get(selectedInputIndex);
        final String outName = outputDevices.get(selectedOutputIndex);

        micTesting = true;
        micTestThread = new Thread(() -> {
            MicrophoneCapture testMic = new MicrophoneCapture();
            AudioPlayer testPlayer = new AudioPlayer();
            try {
                if (!"デフォルト".equals(inName)) testMic.setPreferredMixerName(inName);
                if (!"デフォルト".equals(outName)) testPlayer.setPreferredMixerName(outName);

                testMic.start();
                testPlayer.start();

                long end = System.currentTimeMillis() + 3000; // 3秒間
                while (micTesting && System.currentTimeMillis() < end) {
                    short[] s = testMic.getNextFrame(50);
                    if (s != null) testPlayer.addNonPositionalAudio(s);
                }
            } finally {
                try { testMic.stop(); } catch (Exception ignored) {}
                try { testPlayer.stop(); } catch (Exception ignored) {}
                micTesting = false;
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("マイクテスト完了"), true);
                }
            }
        }, "AVC-MicTest");
        micTestThread.setDaemon(true);
        micTestThread.start();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // ESCなどで閉じた場合も設定を適用・保存する
        try {
            applySettings();
        } catch (Exception ignored) {
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(lastScreen);
        }
    }

    @Override
    public void removed() {
        // 画面が閉じられる全経路で最終的に保存を保証
        try {
            applySettings();
        } catch (Exception ignored) {
        }
        super.removed();
    }

    // ==== リアルタイム更新コールバック ====
    private void onVadChanged(double newThreshold) {
        this.vadThreshold = newThreshold;
        ClientConfig cfg = ClientConfig.get();
        cfg.vadThreshold = newThreshold;
        cfg.save();
        ClientAudioEngine engine = ClientAudioEngine.getInstance();
        if (engine.isRunning()) {
            engine.updateVadThreshold(newThreshold);
        }
    }

    private void onInputVolumeChanged(double newVolume) {
        this.inputVolume = newVolume;
        ClientConfig cfg = ClientConfig.get();
        cfg.inputVolume = newVolume;
        cfg.save();
        ClientAudioEngine engine = ClientAudioEngine.getInstance();
        if (engine.isRunning()) {
            engine.updateInputVolume(newVolume);
        }
    }

    private void onOutputVolumeChanged(double newVolume) {
        this.outputVolume = newVolume;
        ClientConfig cfg = ClientConfig.get();
        cfg.outputVolume = newVolume;
        cfg.save();
        ClientAudioEngine engine = ClientAudioEngine.getInstance();
        if (engine.isRunning()) {
            engine.updateOutputVolume(newVolume);
        }
    }

    /**
     * VAD閾値スライダー
     */
    private class VadThresholdSlider extends AbstractSliderButton {
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
            // Realtime apply
            VoiceSettingsScreen.this.onVadChanged(this.threshold);
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
     * ボリュームスライダー（0～400%対応）
     */
    private class VolumeSlider extends AbstractSliderButton {
        private final Component label;
        private double volume;
        private static final double MAX_VOLUME = 4.0; // 最大400%

        public VolumeSlider(int x, int y, int width, int height, Component label, double initialVolume) {
            // valueは0.0～1.0の範囲なので、initialVolume / MAX_VOLUMEで正規化
            super(x, y, width, height, Component.empty(), Math.min(initialVolume / MAX_VOLUME, 1.0));
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
            // valueは0.0～1.0なので、MAX_VOLUMEを掛けて0.0～4.0に変換
            this.volume = this.value * MAX_VOLUME;
            updateMessage();
            // Realtime apply
            if (VoiceSettingsScreen.this.inputVolumeSlider == this) {
                VoiceSettingsScreen.this.onInputVolumeChanged(this.volume);
            } else if (VoiceSettingsScreen.this.outputVolumeSlider == this) {
                VoiceSettingsScreen.this.onOutputVolumeChanged(this.volume);
            }
        }

        public double getVolume() {
            return volume;
        }

        public void setVolume(double volume) {
            this.volume = volume;
            // valueは0.0～1.0の範囲なので、MAX_VOLUMEで割って正規化
            this.value = Math.min(volume / MAX_VOLUME, 1.0);
            updateMessage();
        }
    }
}
