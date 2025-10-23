package jp.houlab.mochidsuki.advancedvc.server;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.common.VoiceMode;
import jp.houlab.mochidsuki.advancedvc.network.UDPNetworkManager;
import jp.houlab.mochidsuki.advancedvc.network.packet.VoicePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * サーバーサイド・オーディオルーター
 * 音声パケットの受信、フィルタリング、ルーティングを処理
 */
public class ServerAudioRouter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ServerAudioRouter instance;

    private MinecraftServer server;
    private UDPNetworkManager udpNetwork;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // プレイヤー状態管理
    private final ConcurrentHashMap<UUID, PlayerVoiceState> playerStates = new ConcurrentHashMap<>();

    // バンドセッション管理
    private BandSession activeBandSession = null;

    private ServerAudioRouter() {
    }

    public static ServerAudioRouter getInstance() {
        if (instance == null) {
            instance = new ServerAudioRouter();
        }
        return instance;
    }

    /**
     * オーディオルーターを開始
     */
    public void start(MinecraftServer server, int udpPort) {
        if (running.get()) {
            LOGGER.warn("Server audio router is already running");
            return;
        }

        this.server = server;

        LOGGER.info("Starting server audio router on port {}...", udpPort);

        // UDP初期化
        udpNetwork = new UDPNetworkManager(udpPort);
        udpNetwork.addPacketListener("router", this::handleReceivedPacket);
        udpNetwork.start();

        running.set(true);

        LOGGER.info("Server audio router started");
    }

    /**
     * オーディオルーターを停止
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        LOGGER.info("Stopping server audio router...");

        running.set(false);

        if (udpNetwork != null) {
            udpNetwork.stop();
        }

        playerStates.clear();

        LOGGER.info("Server audio router stopped");
    }

    /**
     * 受信パケット処理
     */
    private void handleReceivedPacket(VoicePacket packet) {
        try {
            // モードに応じてルーティング
            switch (packet.getMode()) {
                case SIMULATION -> routeSimulationMode(packet);
                case DEVICE -> routeDeviceMode(packet);
                case BAND -> routeBandMode(packet);
            }
        } catch (Exception e) {
            LOGGER.error("Error routing packet", e);
        }
    }

    /**
     * シミュレーションモードのルーティング
     * 声量に基づく距離フィルタリングのみ実行
     */
    private void routeSimulationMode(VoicePacket packet) {
        ServerPlayer sender = server.getPlayerList().getPlayer(packet.getSenderId());
        if (sender == null) {
            return;
        }

        Vec3 senderPos = sender.position();
        float maxDistance = packet.getVolumeLevel().getMaxDistance();

        // 範囲内のプレイヤーを取得
        List<String> nearbyPlayerIds = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(packet.getSenderId())) {
                continue; // 自分自身には送らない
            }

            Vec3 playerPos = player.position();
            double distance = senderPos.distanceTo(playerPos);

            if (distance <= maxDistance) {
                nearbyPlayerIds.add(player.getUUID().toString());
            }
        }

        // クリーンな音声パケットをブロードキャスト
        if (!nearbyPlayerIds.isEmpty()) {
            udpNetwork.broadcast(packet, nearbyPlayerIds);
        }
    }

    /**
     * デバイスモードのルーティング
     * 同じ周波数を設定しているプレイヤーにブロードキャスト
     */
    private void routeDeviceMode(VoicePacket packet) {
        int frequency = packet.getFrequency();
        if (frequency <= 0) {
            return;
        }

        List<String> recipientIds = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(packet.getSenderId())) {
                continue; // 自分自身には送らない
            }

            PlayerVoiceState state = playerStates.get(player.getUUID());
            if (state != null && state.walkieTalkieFrequency == frequency) {
                recipientIds.add(player.getUUID().toString());
            }
        }

        // TODO: デバイス特有の音質劣化DSP適用

        if (!recipientIds.isEmpty()) {
            udpNetwork.broadcast(packet, recipientIds);
        }
    }

    /**
     * バンドモードのルーティング
     */
    private void routeBandMode(VoicePacket packet) {
        if (activeBandSession == null) {
            return;
        }

        // TODO: バンドモードの同期ロジック実装
        // 指揮者ベースの同期ハブモデル

        List<String> sessionMemberIds = activeBandSession.getMemberIds();
        udpNetwork.broadcast(packet, sessionMemberIds);
    }

    /**
     * プレイヤー状態を登録/更新
     */
    public void updatePlayerState(UUID playerId, PlayerVoiceState state) {
        playerStates.put(playerId, state);
    }

    /**
     * プレイヤー状態を削除
     */
    public void removePlayerState(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * バンドセッションを開始
     */
    public void startBandSession(UUID conductorId, List<UUID> memberIds) {
        activeBandSession = new BandSession(conductorId, memberIds);
        LOGGER.info("Band session started with conductor: {}", conductorId);
    }

    /**
     * バンドセッションを終了
     */
    public void endBandSession() {
        activeBandSession = null;
        LOGGER.info("Band session ended");
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * プレイヤーの音声状態
     */
    public static class PlayerVoiceState {
        public boolean muted = false;
        public VoiceMode currentMode = VoiceMode.SIMULATION;
        public int walkieTalkieFrequency = 0;

        public PlayerVoiceState() {
        }
    }

    /**
     * バンドセッション
     */
    private static class BandSession {
        private final UUID conductorId;
        private final List<UUID> memberIds;

        public BandSession(UUID conductorId, List<UUID> memberIds) {
            this.conductorId = conductorId;
            this.memberIds = new ArrayList<>(memberIds);
        }

        public List<String> getMemberIds() {
            return memberIds.stream().map(UUID::toString).toList();
        }
    }
}
