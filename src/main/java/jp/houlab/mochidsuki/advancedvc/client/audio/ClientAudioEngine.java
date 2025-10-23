package jp.houlab.mochidsuki.advancedvc.client.audio;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.common.VoiceMode;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import jp.houlab.mochidsuki.advancedvc.network.UDPNetworkManager;
import jp.houlab.mochidsuki.advancedvc.network.packet.VoicePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * クライアントサイド・オーディオエンジン
 * マイク入力、VAD、エンコード、送信、受信、デコード、再生を統合管理
 */
public class ClientAudioEngine {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ClientAudioEngine instance;

    // コンポーネント
    private MicrophoneCapture microphone;
    private VoiceActivityDetector vad;
    private OpusCodec.Encoder highQualityEncoder;
    private OpusCodec.Encoder lowQualityEncoder;
    private OpusCodec.Decoder decoder;
    private AudioPlayer audioPlayer;
    private UDPNetworkManager udpNetwork;
    private AcousticSimulationEngine acousticSimulation;

    // 状態
    private final AtomicBoolean running = new AtomicBoolean(false);
    private VoiceMode currentMode = VoiceMode.SIMULATION;
    private VolumeLevel currentVolume = VolumeLevel.NORMAL;
    private int walkieTalkieFrequency = 0;
    private boolean pttPressed = false;
    private boolean muted = false;

    // シーケンス番号
    private short sequenceNumber = 0;

    // 処理スレッド
    private Thread processingThread;

    private ClientAudioEngine() {
    }

    public static ClientAudioEngine getInstance() {
        if (instance == null) {
            instance = new ClientAudioEngine();
        }
        return instance;
    }

    /**
     * オーディオエンジンを開始
     */
    public void start(String serverHost, int serverPort) {
        if (running.get()) {
            LOGGER.warn("Client audio engine is already running");
            return;
        }

        LOGGER.info("Starting client audio engine...");

        // コンポーネント初期化
        microphone = new MicrophoneCapture();
        vad = new VoiceActivityDetector();
        highQualityEncoder = OpusCodec.createHighQualityEncoder();
        lowQualityEncoder = OpusCodec.createLowQualityEncoder();
        decoder = OpusCodec.createDecoder();
        audioPlayer = new AudioPlayer();
        udpNetwork = new UDPNetworkManager(0); // クライアントはランダムポート
        acousticSimulation = new AcousticSimulationEngine();

        // UDP設定
        udpNetwork.setServerAddress(serverHost, serverPort);

        // パケット受信リスナー
        udpNetwork.addPacketListener("main", this::handleReceivedPacket);

        // 起動
        microphone.start();
        audioPlayer.start();
        udpNetwork.start();

        running.set(true);

        // 処理スレッド開始
        processingThread = new Thread(this::processingLoop, "Audio-Processing");
        processingThread.setDaemon(true);
        processingThread.start();

        LOGGER.info("Client audio engine started");
    }

    /**
     * オーディオエンジンを停止
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        LOGGER.info("Stopping client audio engine...");

        running.set(false);

        if (microphone != null) {
            microphone.stop();
        }
        if (audioPlayer != null) {
            audioPlayer.stop();
        }
        if (udpNetwork != null) {
            udpNetwork.stop();
        }

        if (processingThread != null) {
            try {
                processingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.info("Client audio engine stopped");
    }

    /**
     * 処理ループ（マイク入力 → VAD → エンコード → 送信）
     */
    private void processingLoop() {
        while (running.get()) {
            try {
                // マイクからフレーム取得
                short[] samples = microphone.getNextFrame(50);
                if (samples == null) {
                    continue;
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    continue;
                }

                UUID playerId = mc.player.getUUID();

                // ミュート中は送信しない
                if (muted) {
                    continue;
                }

                // シミュレーションモード（VAD）
                if (currentMode == VoiceMode.SIMULATION) {
                    if (vad.process(samples)) {
                        sendVoicePacket(samples, VoiceMode.SIMULATION, playerId);
                    }
                }

                // デバイスモード（PTT）
                if (pttPressed && walkieTalkieFrequency > 0) {
                    sendVoicePacket(samples, VoiceMode.DEVICE, playerId);
                }

                // バンドモード
                if (currentMode == VoiceMode.BAND) {
                    sendVoicePacket(samples, VoiceMode.BAND, playerId);
                }

            } catch (Exception e) {
                if (running.get()) {
                    LOGGER.error("Error in processing loop", e);
                }
            }
        }
    }

    /**
     * 音声パケットを送信
     */
    private void sendVoicePacket(short[] samples, VoiceMode mode, UUID playerId) {
        // エンコーダー選択
        OpusCodec.Encoder encoder = (mode == VoiceMode.DEVICE) ? lowQualityEncoder : highQualityEncoder;
        if (encoder == null) {
            return;
        }

        // エンコード
        byte[] encodedData = encoder.encode(samples);
        if (encodedData.length == 0) {
            return;
        }

        // パケット構築
        VoicePacket.Builder builder = new VoicePacket.Builder()
                .packetType(getPacketTypeForMode(mode))
                .senderId(playerId)
                .sequenceNumber(sequenceNumber++)
                .mode(mode)
                .volumeLevel(currentVolume)
                .audioData(encodedData);

        if (mode == VoiceMode.DEVICE) {
            builder.frequency(walkieTalkieFrequency);
        }

        VoicePacket packet = builder.build();

        // 送信
        udpNetwork.sendToServer(packet);
    }

    /**
     * 受信パケット処理
     */
    private void handleReceivedPacket(VoicePacket packet) {
        try {
            if (decoder == null) {
                return;
            }

            // デコード
            short[] samples = decoder.decode(packet.getAudioData());
            if (samples.length == 0) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }

            // モードに応じた再生
            if (packet.getMode() == VoiceMode.DEVICE) {
                // デバイスモード: 非ポジショナル再生
                // ウォーキートーキー風の音質劣化を適用
                short[] filtered = DSPProcessor.applyWalkieTalkieFilter(samples);
                audioPlayer.addNonPositionalAudio(filtered);
            } else if (packet.getMode() == VoiceMode.SIMULATION) {
                // シミュレーションモード: 音響シミュレーション + ポジショナル再生
                Vec3 sourcePos;
                if (packet.isFromSpeaker()) {
                    // スピーカーブロックからの音声
                    sourcePos = new Vec3(packet.getSpeakerX(), packet.getSpeakerY(), packet.getSpeakerZ());
                } else {
                    // プレイヤーからの音声
                    // TODO: 他プレイヤーの座標を適切に取得
                    // 現在は簡易的に自分の位置を使用（実際にはサーバーから座標情報が必要）
                    sourcePos = mc.player.position().add(10, 0, 0); // 仮の位置
                }

                Vec3 listenerPos = mc.player.position();
                float initialVolume = packet.getVolumeLevel().getAmplitude();

                // クライアントサイド音響シミュレーション実行
                AcousticSimulationEngine.DSPParameters dspParams =
                        acousticSimulation.simulate(sourcePos, listenerPos, initialVolume);

                LOGGER.debug("Acoustic simulation: {}", dspParams);

                // DSP処理を適用
                short[] processed = DSPProcessor.applyDSP(samples, dspParams);

                audioPlayer.addPositionalAudio(packet.getSenderId(), processed, sourcePos);
            } else {
                // バンドモード: DSP処理なしでポジショナル再生
                Vec3 position = mc.player.position(); // 仮
                audioPlayer.addPositionalAudio(packet.getSenderId(), samples, position);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling received packet", e);
        }
    }

    /**
     * モードに対応するパケットタイプを取得
     */
    private byte getPacketTypeForMode(VoiceMode mode) {
        return switch (mode) {
            case SIMULATION -> AudioConstants.PACKET_TYPE_SIMULATION;
            case DEVICE -> AudioConstants.PACKET_TYPE_DEVICE;
            case BAND -> AudioConstants.PACKET_TYPE_BAND;
        };
    }

    // ===== Public API =====

    public void setVoiceMode(VoiceMode mode) {
        this.currentMode = mode;
        LOGGER.info("Voice mode changed to: {}", mode);
    }

    public void setVolumeLevel(VolumeLevel level) {
        this.currentVolume = level;
        LOGGER.info("Volume level changed to: {}", level.getDisplayName());
    }

    public void setWalkieTalkieFrequency(int frequency) {
        this.walkieTalkieFrequency = frequency;
        LOGGER.info("Walkie-talkie frequency set to: {}", frequency);
    }

    public void setPTTPressed(boolean pressed) {
        this.pttPressed = pressed;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        LOGGER.info("Muted: {}", muted);
    }

    public VoiceMode getCurrentMode() {
        return currentMode;
    }

    public VolumeLevel getCurrentVolume() {
        return currentVolume;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isMuted() {
        return muted;
    }
}
