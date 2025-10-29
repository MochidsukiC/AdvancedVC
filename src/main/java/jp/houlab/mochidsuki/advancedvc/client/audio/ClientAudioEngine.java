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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    // 接続状態
    private final AtomicBoolean connectionFailed = new AtomicBoolean(false);
    private String lastServerHost = null;
    private int lastServerPort = 0;

    // プレイヤー位置キャッシュ（音響シミュレーション用）
    private final Map<UUID, Vec3> playerPositions = new ConcurrentHashMap<>();

    // シーケンス番号
    private short sequenceNumber = 0;

    // 処理スレッド
    private Thread processingThread;
    private Thread reconnectionThread;

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
        // サーバー情報を保存（再接続用）
        lastServerHost = serverHost;
        lastServerPort = serverPort;

        // 初回接続を試みる
        if (startInternal(serverHost, serverPort)) {
            connectionFailed.set(false);
            LOGGER.info("Client audio engine started successfully");
        } else {
            connectionFailed.set(true);
            LOGGER.warn("Failed to start client audio engine, will retry every 5 seconds");
            startReconnectionLoop();
        }
    }

    /**
     * 内部起動処理
     */
    private boolean startInternal(String serverHost, int serverPort) {
        if (running.get()) {
            LOGGER.debug("Client audio engine is already running");
            return true;
        }

        try {
            LOGGER.info("===== Starting Client Audio Engine =====");
            LOGGER.info("Target server: {}:{}", serverHost, serverPort);

            // コンポーネント初期化
            LOGGER.info("Initializing audio components...");
            microphone = new MicrophoneCapture();
            vad = new VoiceActivityDetector();
            highQualityEncoder = OpusCodec.createHighQualityEncoder();
            lowQualityEncoder = OpusCodec.createLowQualityEncoder();
            decoder = OpusCodec.createDecoder();
            audioPlayer = new AudioPlayer();

            if (highQualityEncoder == null || lowQualityEncoder == null || decoder == null) {
                throw new IllegalStateException("Failed to create Opus codecs");
            }

            LOGGER.info("Creating UDP network manager...");
            udpNetwork = new UDPNetworkManager(0); // クライアントはランダムポート
            acousticSimulation = new AcousticSimulationEngine();

            // UDP設定
            LOGGER.info("Setting server address: {}:{}", serverHost, serverPort);
            udpNetwork.setServerAddress(serverHost, serverPort);

            // パケット受信リスナー
            udpNetwork.addPacketListener("main", this::handleReceivedPacket);

            // 起動
            LOGGER.info("Starting microphone capture...");
            microphone.start();

            LOGGER.info("Starting audio player...");
            audioPlayer.start();

            LOGGER.info("Starting UDP network...");
            udpNetwork.start();

            if (!udpNetwork.isRunning()) {
                throw new IllegalStateException("UDP network failed to start");
            }

            running.set(true);

            // 処理スレッド開始
            LOGGER.info("Starting audio processing thread...");
            processingThread = new Thread(this::processingLoop, "Audio-Processing");
            processingThread.setDaemon(true);
            processingThread.start();

            LOGGER.info("===== Client Audio Engine Started Successfully =====");
            return true;
        } catch (Exception e) {
            LOGGER.error("===== Failed to Start Client Audio Engine =====", e);
            LOGGER.error("Error type: {}", e.getClass().getName());
            LOGGER.error("Error message: {}", e.getMessage());
            cleanupFailedStart();
            return false;
        }
    }

    /**
     * 起動失敗時のクリーンアップ
     */
    private void cleanupFailedStart() {
        if (microphone != null) {
            try {
                microphone.stop();
            } catch (Exception e) {
                LOGGER.debug("Error stopping microphone during cleanup", e);
            }
        }
        if (audioPlayer != null) {
            try {
                audioPlayer.stop();
            } catch (Exception e) {
                LOGGER.debug("Error stopping audio player during cleanup", e);
            }
        }
        if (udpNetwork != null) {
            try {
                udpNetwork.stop();
            } catch (Exception e) {
                LOGGER.debug("Error stopping UDP network during cleanup", e);
            }
        }
    }

    /**
     * 再接続ループを開始
     */
    private void startReconnectionLoop() {
        if (reconnectionThread != null && reconnectionThread.isAlive()) {
            LOGGER.debug("Reconnection thread is already running");
            return; // 既に再接続ループが動いている
        }

        LOGGER.info("===== Starting Auto-Reconnection Loop =====");
        LOGGER.info("Will attempt to reconnect to {}:{} every 5 seconds", lastServerHost, lastServerPort);

        reconnectionThread = new Thread(() -> {
            int attemptCount = 0;
            while (connectionFailed.get() && lastServerHost != null) {
                try {
                    attemptCount++;
                    LOGGER.info("----- Reconnection Attempt #{} -----", attemptCount);
                    Thread.sleep(5000); // 5秒待機

                    LOGGER.info("Attempting to reconnect to {}:{}...", lastServerHost, lastServerPort);
                    if (startInternal(lastServerHost, lastServerPort)) {
                        connectionFailed.set(false);
                        LOGGER.info("***** Successfully reconnected to server after {} attempts *****", attemptCount);
                        break;
                    } else {
                        LOGGER.warn("Reconnection attempt #{} failed, will retry in 5 seconds", attemptCount);
                    }
                } catch (InterruptedException e) {
                    LOGGER.info("Reconnection loop interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Unexpected error in reconnection loop", e);
                }
            }
            LOGGER.info("===== Auto-Reconnection Loop Ended =====");
        }, "Audio-Reconnection");
        reconnectionThread.setDaemon(true);
        reconnectionThread.start();
    }

    /**
     * オーディオエンジンを停止
     */
    public void stop() {
        if (!running.get() && !connectionFailed.get()) {
            return;
        }

        LOGGER.info("Stopping client audio engine...");

        running.set(false);
        connectionFailed.set(false); // 再接続ループを停止

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

        // 再接続スレッドを停止
        if (reconnectionThread != null && reconnectionThread.isAlive()) {
            reconnectionThread.interrupt();
            try {
                reconnectionThread.join(1000);
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
                    // サーバーから同期された位置情報を使用
                    sourcePos = getPlayerPosition(packet.getSenderId());
                    if (sourcePos.equals(Vec3.ZERO)) {
                        // 位置情報がまだ同期されていない場合はスキップ
                        return;
                    }
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

    /**
     * プレイヤー位置を更新（ネットワークパケットから受信）
     */
    public void updatePlayerPosition(UUID playerId, Vec3 position) {
        playerPositions.put(playerId, position);
    }

    /**
     * プレイヤー位置を取得（音響シミュレーション用）
     */
    private Vec3 getPlayerPosition(UUID playerId) {
        return playerPositions.getOrDefault(playerId, Vec3.ZERO);
    }

    // ===== UI Support Methods =====

    /**
     * 現在の声量レベル（0.0～1.0）を取得
     */
    public float getCurrentVoiceLevel() {
        if (vad == null || !running.get()) {
            return 0.0f;
        }
        return vad.getCurrentLevel();
    }

    /**
     * サーバー接続状態を取得
     */
    public boolean isServerConnected() {
        return udpNetwork != null && running.get() && !connectionFailed.get();
    }

    /**
     * 接続失敗状態かを取得
     */
    public boolean isConnectionFailed() {
        return connectionFailed.get();
    }

    /**
     * 発話中かを取得
     */
    public boolean isSpeaking() {
        if (vad == null || !running.get() || muted) {
            return false;
        }
        return vad.isVoiceDetected();
    }

    /**
     * VAD閾値を更新
     */
    public void updateVadThreshold(double threshold) {
        if (vad != null) {
            vad.setThreshold(threshold);
            LOGGER.info("VAD threshold updated to: {}", threshold);
        }
    }

    /**
     * 入力音量を更新
     */
    public void updateInputVolume(double volume) {
        if (microphone != null) {
            microphone.setInputGain(volume);
            LOGGER.info("Input volume updated to: {}", volume);
        }
    }

    /**
     * 出力音量を更新
     */
    public void updateOutputVolume(double volume) {
        if (audioPlayer != null) {
            audioPlayer.setOutputGain(volume);
            LOGGER.info("Output volume updated to: {}", volume);
        }
    }
}
