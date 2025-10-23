package jp.houlab.mochidsuki.advancedvc.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 制御データパケット（Forgeチャネル経由 - TCP）
 * 状態同期、モード切替、設定変更などに使用
 */
public class ControlPacket {

    public enum ControlType {
        HANDSHAKE,              // UDPポートと認証トークンのハンドシェイク
        MUTE_STATE,             // ミュート状態の同期
        MODE_SWITCH,            // モード切替
        VOLUME_CHANGE,          // 声量変更
        FREQUENCY_SET,          // ウォーキートーキー周波数設定
        BAND_SESSION_START,     // バンドセッション開始
        BAND_SESSION_END,       // バンドセッション終了
        MIXER_CONTROL          // PAミキサー制御
    }

    private final ControlType type;
    private final UUID playerId;
    private final int intValue;
    private final String stringValue;
    private final byte[] data;

    public ControlPacket(ControlType type, UUID playerId, int intValue, String stringValue, byte[] data) {
        this.type = type;
        this.playerId = playerId;
        this.intValue = intValue;
        this.stringValue = stringValue;
        this.data = data;
    }

    // 便利なコンストラクタ
    public static ControlPacket handshake(UUID playerId, int udpPort, String token) {
        return new ControlPacket(ControlType.HANDSHAKE, playerId, udpPort, token, null);
    }

    public static ControlPacket muteState(UUID playerId, boolean muted) {
        return new ControlPacket(ControlType.MUTE_STATE, playerId, muted ? 1 : 0, null, null);
    }

    public static ControlPacket modeSwitch(UUID playerId, int modeOrdinal) {
        return new ControlPacket(ControlType.MODE_SWITCH, playerId, modeOrdinal, null, null);
    }

    public static ControlPacket volumeChange(UUID playerId, int volumeLevelOrdinal) {
        return new ControlPacket(ControlType.VOLUME_CHANGE, playerId, volumeLevelOrdinal, null, null);
    }

    public static ControlPacket frequencySet(UUID playerId, int frequency) {
        return new ControlPacket(ControlType.FREQUENCY_SET, playerId, frequency, null, null);
    }

    public static ControlPacket bandSessionStart(UUID conductorId) {
        return new ControlPacket(ControlType.BAND_SESSION_START, conductorId, 0, null, null);
    }

    public static ControlPacket bandSessionEnd(UUID conductorId) {
        return new ControlPacket(ControlType.BAND_SESSION_END, conductorId, 0, null, null);
    }

    public static ControlPacket mixerControl(UUID playerId, byte[] mixerData) {
        return new ControlPacket(ControlType.MIXER_CONTROL, playerId, 0, null, mixerData);
    }

    // Encode to network
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUUID(playerId);
        buf.writeInt(intValue);
        buf.writeUtf(stringValue != null ? stringValue : "");
        buf.writeBoolean(data != null);
        if (data != null) {
            buf.writeInt(data.length);
            buf.writeBytes(data);
        }
    }

    // Decode from network
    public static ControlPacket decode(FriendlyByteBuf buf) {
        ControlType type = buf.readEnum(ControlType.class);
        UUID playerId = buf.readUUID();
        int intValue = buf.readInt();
        String stringValue = buf.readUtf();
        byte[] data = null;
        if (buf.readBoolean()) {
            int dataLength = buf.readInt();
            data = new byte[dataLength];
            buf.readBytes(data);
        }
        return new ControlPacket(type, playerId, intValue, stringValue.isEmpty() ? null : stringValue, data);
    }

    // Handle packet
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // パケット処理はここで実装
            // サーバーサイドまたはクライアントサイドで適切に処理
            handlePacket(context);
        });
        context.setPacketHandled(true);
    }

    private void handlePacket(NetworkEvent.Context context) {
        // TODO: 実装 - 各パケットタイプに応じた処理
        // この部分は後で、AudioServerやAudioEngineと連携して実装
    }

    // Getters
    public ControlType getType() { return type; }
    public UUID getPlayerId() { return playerId; }
    public int getIntValue() { return intValue; }
    public String getStringValue() { return stringValue; }
    public byte[] getData() { return data; }
}
