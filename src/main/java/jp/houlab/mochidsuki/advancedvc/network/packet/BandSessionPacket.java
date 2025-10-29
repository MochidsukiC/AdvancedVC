package jp.houlab.mochidsuki.advancedvc.network.packet;

import jp.houlab.mochidsuki.advancedvc.server.ServerAudioRouter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * バンドセッション制御パケット
 * クライアント → サーバー
 */
public class BandSessionPacket {

    public enum Action {
        START,      // セッション開始
        END,        // セッション終了
        JOIN,       // セッション参加
        LEAVE       // セッション離脱
    }

    private final Action action;
    private final UUID conductorId;

    public BandSessionPacket(Action action, UUID conductorId) {
        this.action = action;
        this.conductorId = conductorId;
    }

    // セッション開始パケット
    public static BandSessionPacket start(UUID conductorId) {
        return new BandSessionPacket(Action.START, conductorId);
    }

    // セッション終了パケット
    public static BandSessionPacket end(UUID conductorId) {
        return new BandSessionPacket(Action.END, conductorId);
    }

    // セッション参加パケット
    public static BandSessionPacket join(UUID playerId, UUID conductorId) {
        return new BandSessionPacket(Action.JOIN, conductorId);
    }

    // セッション離脱パケット
    public static BandSessionPacket leave(UUID playerId) {
        return new BandSessionPacket(Action.LEAVE, playerId);
    }

    // Encode to network
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUUID(conductorId);
    }

    // Decode from network
    public static BandSessionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID conductorId = buf.readUUID();
        return new BandSessionPacket(action, conductorId);
    }

    // Handle packet (server-side)
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerAudioRouter router = ServerAudioRouter.getInstance();

                switch (action) {
                    case START:
                        // プレイヤーをコンダクターとしてセッション開始
                        List<UUID> initialMembers = new ArrayList<>();
                        initialMembers.add(player.getUUID());
                        router.startBandSession(player.getUUID(), initialMembers);
                        break;
                    case END:
                        // セッション終了
                        router.endBandSession();
                        break;
                    case JOIN:
                        // セッションに参加 (TODO: ServerAudioRouterにメンバー追加APIが必要)
                        // router.addMemberToBandSession(player.getUUID());
                        break;
                    case LEAVE:
                        // セッションから離脱 (TODO: ServerAudioRouterにメンバー削除APIが必要)
                        // router.removeMemberFromBandSession(player.getUUID());
                        break;
                }
            }
        });
        context.setPacketHandled(true);
    }

    // Getters
    public Action getAction() { return action; }
    public UUID getConductorId() { return conductorId; }
}
