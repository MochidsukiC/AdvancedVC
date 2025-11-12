package jp.houlab.mochidsuki.advancedvc.network;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.common.AudioConstants;
import jp.houlab.mochidsuki.advancedvc.network.packet.VoicePacket;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * UDP通信マネージャー
 * 低遅延の音声パケット送受信を専用UDPソケットで処理
 */
public class UDPNetworkManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int port;
    private DatagramSocket socket;
    private Thread receiveThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // パケット受信リスナー
    private final ConcurrentHashMap<String, Consumer<VoicePacket>> packetListeners = new ConcurrentHashMap<>();

    // クライアント側: サーバーアドレス
    private InetAddress serverAddress;
    private int serverPort;

    // サーバー側: クライアントアドレスマップ（UUID -> SocketAddress）
    private final ConcurrentHashMap<String, SocketAddress> clientAddresses = new ConcurrentHashMap<>();

    public UDPNetworkManager(int port) {
        this.port = port;
    }

    /**
     * UDPソケットを開始
     */
    public void start() throws SocketException {
        if (running.get()) {
            LOGGER.warn("UDP Network Manager is already running");
            return;
        }

        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(AudioConstants.NETWORK_BUFFER_SIZE);
        socket.setSendBufferSize(AudioConstants.NETWORK_BUFFER_SIZE);
        running.set(true);

        // 受信スレッド開始
        receiveThread = new Thread(this::receiveLoop, "UDP-Voice-Receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();

        LOGGER.info("UDP Network Manager started on port {} (bound to {})",
                socket.getLocalPort(), socket.getLocalAddress());
    }

    /**
     * UDPソケットを停止
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (receiveThread != null) {
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        packetListeners.clear();
        clientAddresses.clear();

        LOGGER.info("UDP Network Manager stopped");
    }

    /**
     * クライアント側: サーバーアドレスを設定
     */
    public void setServerAddress(String host, int port) {
        try {
            this.serverAddress = InetAddress.getByName(host);
            this.serverPort = port;
            LOGGER.info("Server address set to {}:{}", host, port);
        } catch (UnknownHostException e) {
            LOGGER.error("Failed to resolve server address: {}", host, e);
        }
    }

    /**
     * サーバー側: クライアントアドレスを登録
     */
    public void registerClientAddress(String playerId, SocketAddress address) {
        clientAddresses.put(playerId, address);
        LOGGER.debug("Registered client address for player {}: {}", playerId, address);
    }

    /**
     * 音声パケットを送信（クライアント→サーバー）
     */
    public void sendToServer(VoicePacket packet) {
        if (serverAddress == null) {
            LOGGER.warn("Server address not set, cannot send packet");
            return;
        }

        try {
            byte[] data = packet.serialize();
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(datagramPacket);
            LOGGER.debug("Sent UDP packet to server {}:{} - {} bytes, packet type: {}",
                    serverAddress.getHostAddress(), serverPort, data.length, packet.getPacketType());
        } catch (IOException e) {
            LOGGER.error("Failed to send packet to server {}:{}", serverAddress, serverPort, e);
        }
    }

    /**
     * 音声パケットを送信（サーバー→特定クライアント）
     */
    public void sendToClient(String playerId, VoicePacket packet) {
        SocketAddress clientAddress = clientAddresses.get(playerId);
        if (clientAddress == null) {
            LOGGER.debug("Client address not found for player {}", playerId);
            return;
        }

        try {
            byte[] data = packet.serialize();
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, clientAddress);
            socket.send(datagramPacket);
        } catch (IOException e) {
            LOGGER.error("Failed to send packet to client {}", playerId, e);
        }
    }

    /**
     * 音声パケットをブロードキャスト（サーバー→複数クライアント）
     */
    public void broadcast(VoicePacket packet, Iterable<String> playerIds) {
        byte[] data = packet.serialize();
        int successCount = 0;
        int failCount = 0;

        for (String playerId : playerIds) {
            SocketAddress clientAddress = clientAddresses.get(playerId);
            if (clientAddress != null) {
                try {
                    DatagramPacket datagramPacket = new DatagramPacket(data, data.length, clientAddress);
                    socket.send(datagramPacket);
                    successCount++;
                    LOGGER.debug("Sent packet to client {} at {}", playerId, clientAddress);
                } catch (IOException e) {
                    failCount++;
                    LOGGER.error("Failed to broadcast packet to client {}", playerId, e);
                }
            } else {
                failCount++;
                LOGGER.debug("Client address not found for player {}", playerId);
            }
        }

        LOGGER.debug("Broadcast complete: {} successful, {} failed", successCount, failCount);
    }

    /**
     * パケット受信リスナーを登録
     */
    public void addPacketListener(String listenerId, Consumer<VoicePacket> listener) {
        packetListeners.put(listenerId, listener);
    }

    /**
     * パケット受信リスナーを削除
     */
    public void removePacketListener(String listenerId) {
        packetListeners.remove(listenerId);
    }

    /**
     * 受信ループ
     */
    private void receiveLoop() {
        byte[] buffer = new byte[AudioConstants.MAX_PACKET_SIZE];
        LOGGER.info("UDP receive loop started, listening for packets...");

        while (running.get()) {
            try {
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagramPacket);

                // パケットをデシリアライズ
                byte[] receivedData = new byte[datagramPacket.getLength()];
                System.arraycopy(datagramPacket.getData(), 0, receivedData, 0, datagramPacket.getLength());

                VoicePacket voicePacket;
                try {
                    voicePacket = VoicePacket.deserialize(receivedData);
                } catch (Exception e) {
                    LOGGER.error("Failed to deserialize packet: {} bytes from {}",
                            datagramPacket.getLength(), datagramPacket.getSocketAddress(), e);
                    continue;
                }

                // サーバー側: クライアントアドレスを自動登録
                String senderId = voicePacket.getSenderId().toString();
                SocketAddress previousAddress = clientAddresses.putIfAbsent(senderId, datagramPacket.getSocketAddress());
                if (previousAddress == null) {
                    LOGGER.info("Registered new client address for {}: {}", senderId, datagramPacket.getSocketAddress());
                }

                // リスナーに通知
                for (Consumer<VoicePacket> listener : packetListeners.values()) {
                    try {
                        listener.accept(voicePacket);
                    } catch (Exception e) {
                        LOGGER.error("Error in packet listener", e);
                    }
                }

            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.error("Error receiving UDP packet", e);
                }
            }
        }
    }

    /**
     * UDPネットワークが動作中かを確認
     */
    public boolean isRunning() {
        return running.get() && socket != null && !socket.isClosed();
    }
}
