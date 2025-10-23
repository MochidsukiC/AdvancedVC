package jp.houlab.mochidsuki.advancedvc.server;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.network.UDPNetworkManager;
import jp.houlab.mochidsuki.advancedvc.network.packet.VoicePacket;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * バンドモード同期マネージャー
 * 指揮者ベースの同期ハブモデルを実装
 */
public class BandModeManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID conductorId;
    private final List<UUID> memberIds;
    private final UDPNetworkManager networkManager;

    // 同期用バッファ: プレイヤーID → パケットキュー
    private final ConcurrentHashMap<UUID, LinkedBlockingQueue<VoicePacket>> playerBuffers = new ConcurrentHashMap<>();

    // 指揮者のタイムスタンプ（マスタークロック）
    private long conductorTimestamp = 0;

    // バッファサイズ設定
    private static final int BUFFER_SIZE = 10; // パケット数
    private static final long SYNC_INTERVAL_MS = 20; // 同期間隔（50Hz）

    private volatile boolean running = false;
    private Thread syncThread;

    public BandModeManager(UUID conductorId, List<UUID> memberIds, UDPNetworkManager networkManager) {
        this.conductorId = conductorId;
        this.memberIds = new ArrayList<>(memberIds);
        this.networkManager = networkManager;

        // 各メンバーのバッファを初期化
        for (UUID memberId : memberIds) {
            playerBuffers.put(memberId, new LinkedBlockingQueue<>(BUFFER_SIZE));
        }
    }

    /**
     * 同期開始
     */
    public void start() {
        if (running) {
            LOGGER.warn("Band mode manager is already running");
            return;
        }

        running = true;
        syncThread = new Thread(this::syncLoop, "BandMode-Sync");
        syncThread.setDaemon(true);
        syncThread.start();

        LOGGER.info("Band mode synchronization started with conductor: {}", conductorId);
    }

    /**
     * 同期停止
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (syncThread != null) {
            try {
                syncThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        playerBuffers.clear();
        LOGGER.info("Band mode synchronization stopped");
    }

    /**
     * パケット受信
     */
    public void receivePacket(VoicePacket packet) {
        UUID senderId = packet.getSenderId();

        if (!memberIds.contains(senderId)) {
            return; // セッションメンバーでない
        }

        LinkedBlockingQueue<VoicePacket> buffer = playerBuffers.get(senderId);
        if (buffer != null) {
            if (!buffer.offer(packet)) {
                // バッファ満杯の場合は古いパケットを削除
                buffer.poll();
                buffer.offer(packet);
                LOGGER.debug("Buffer overflow for player {}, dropped oldest packet", senderId);
            }

            // 指揮者のパケットの場合、タイムスタンプを更新
            if (senderId.equals(conductorId)) {
                conductorTimestamp = System.currentTimeMillis();
            }
        }
    }

    /**
     * 同期ループ
     * 指揮者のタイミングに合わせて全メンバーのパケットをミキシング・配信
     */
    private void syncLoop() {
        while (running) {
            try {
                long startTime = System.currentTimeMillis();

                // 各プレイヤーからパケットを収集
                Map<UUID, VoicePacket> currentPackets = new HashMap<>();

                for (UUID memberId : memberIds) {
                    LinkedBlockingQueue<VoicePacket> buffer = playerBuffers.get(memberId);
                    if (buffer != null) {
                        VoicePacket packet = buffer.poll();
                        if (packet != null) {
                            currentPackets.put(memberId, packet);
                        }
                    }
                }

                // パケットがある場合、全メンバーにブロードキャスト
                if (!currentPackets.isEmpty()) {
                    broadcastMixedAudio(currentPackets);
                }

                // 同期間隔を保つ
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = SYNC_INTERVAL_MS - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in sync loop", e);
            }
        }
    }

    /**
     * ミキシング済み音声をブロードキャスト
     */
    private void broadcastMixedAudio(Map<UUID, VoicePacket> packets) {
        // 各メンバーに他のメンバーの音声を送信
        for (UUID receiverId : memberIds) {
            for (Map.Entry<UUID, VoicePacket> entry : packets.entrySet()) {
                UUID senderId = entry.getKey();
                VoicePacket packet = entry.getValue();

                // 自分自身のパケットは送信しない
                if (!senderId.equals(receiverId)) {
                    networkManager.sendToClient(receiverId.toString(), packet);
                }
            }
        }
    }

    /**
     * メンバーを追加
     */
    public void addMember(UUID playerId) {
        if (!memberIds.contains(playerId)) {
            memberIds.add(playerId);
            playerBuffers.put(playerId, new LinkedBlockingQueue<>(BUFFER_SIZE));
            LOGGER.info("Added member to band session: {}", playerId);
        }
    }

    /**
     * メンバーを削除
     */
    public void removeMember(UUID playerId) {
        memberIds.remove(playerId);
        playerBuffers.remove(playerId);
        LOGGER.info("Removed member from band session: {}", playerId);
    }

    public UUID getConductorId() {
        return conductorId;
    }

    public List<UUID> getMemberIds() {
        return new ArrayList<>(memberIds);
    }

    public boolean isRunning() {
        return running;
    }
}
