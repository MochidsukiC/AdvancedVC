package jp.houlab.mochidsuki.advancedvc.network.packet;

import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * プレイヤー位置同期パケット
 * サーバー → クライアント
 *
 * 音響シミュレーションの精度向上のため、近くのプレイヤーの正確な位置を同期
 */
public class PlayerPositionSyncPacket {

    private final Map<UUID, Vec3> playerPositions;

    public PlayerPositionSyncPacket(Map<UUID, Vec3> playerPositions) {
        this.playerPositions = playerPositions;
    }

    // 単一プレイヤー用の便利コンストラクタ
    public static PlayerPositionSyncPacket single(UUID playerId, Vec3 position) {
        Map<UUID, Vec3> positions = new HashMap<>();
        positions.put(playerId, position);
        return new PlayerPositionSyncPacket(positions);
    }

    // 複数プレイヤー用
    public static PlayerPositionSyncPacket multi(Map<UUID, Vec3> playerPositions) {
        return new PlayerPositionSyncPacket(playerPositions);
    }

    // Encode to network
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(playerPositions.size());
        for (Map.Entry<UUID, Vec3> entry : playerPositions.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeDouble(entry.getValue().x);
            buf.writeDouble(entry.getValue().y);
            buf.writeDouble(entry.getValue().z);
        }
    }

    // Decode from network
    public static PlayerPositionSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        Map<UUID, Vec3> positions = new HashMap<>();
        for (int i = 0; i < count; i++) {
            UUID playerId = buf.readUUID();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            positions.put(playerId, new Vec3(x, y, z));
        }
        return new PlayerPositionSyncPacket(positions);
    }

    // Handle packet (client-side)
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // クライアントサイドでのみ実行
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientAudioEngine engine = ClientAudioEngine.getInstance();
                for (Map.Entry<UUID, Vec3> entry : playerPositions.entrySet()) {
                    engine.updatePlayerPosition(entry.getKey(), entry.getValue());
                }
            });
        });
        context.setPacketHandled(true);
    }

    // Getters
    public Map<UUID, Vec3> getPlayerPositions() { return playerPositions; }
}
