package jp.houlab.mochidsuki.advancedvc.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * PAミキサー制御パケット
 * クライアント → サーバー
 */
public class MixerControlPacket {

    private final UUID playerId;
    private final int channelId;
    private final double volume;
    private final double sendLevel;
    private final boolean compressorEnabled;
    private final double eqLow;
    private final double eqMid;
    private final double eqHigh;

    public MixerControlPacket(UUID playerId, int channelId, double volume, double sendLevel,
                              boolean compressorEnabled, double eqLow, double eqMid, double eqHigh) {
        this.playerId = playerId;
        this.channelId = channelId;
        this.volume = volume;
        this.sendLevel = sendLevel;
        this.compressorEnabled = compressorEnabled;
        this.eqLow = eqLow;
        this.eqMid = eqMid;
        this.eqHigh = eqHigh;
    }

    // チャンネル設定更新パケット作成
    public static MixerControlPacket update(UUID playerId, int channelId, double volume, double sendLevel,
                                           boolean compressorEnabled, double eqLow, double eqMid, double eqHigh) {
        return new MixerControlPacket(playerId, channelId, volume, sendLevel, compressorEnabled, eqLow, eqMid, eqHigh);
    }

    // Encode to network
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeInt(channelId);
        buf.writeDouble(volume);
        buf.writeDouble(sendLevel);
        buf.writeBoolean(compressorEnabled);
        buf.writeDouble(eqLow);
        buf.writeDouble(eqMid);
        buf.writeDouble(eqHigh);
    }

    // Decode from network
    public static MixerControlPacket decode(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int channelId = buf.readInt();
        double volume = buf.readDouble();
        double sendLevel = buf.readDouble();
        boolean compressorEnabled = buf.readBoolean();
        double eqLow = buf.readDouble();
        double eqMid = buf.readDouble();
        double eqHigh = buf.readDouble();
        return new MixerControlPacket(playerId, channelId, volume, sendLevel, compressorEnabled, eqLow, eqMid, eqHigh);
    }

    // Handle packet (server-side)
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // TODO: ミキサー設定をサーバーに保存または他のクライアントに同期
                // 現時点では、クライアントサイドのDSP処理に使用されるため、
                // サーバーサイドでの処理は必要に応じて実装
            }
        });
        context.setPacketHandled(true);
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public int getChannelId() { return channelId; }
    public double getVolume() { return volume; }
    public double getSendLevel() { return sendLevel; }
    public boolean isCompressorEnabled() { return compressorEnabled; }
    public double getEqLow() { return eqLow; }
    public double getEqMid() { return eqMid; }
    public double getEqHigh() { return eqHigh; }
}
