package jp.houlab.mochidsuki.advancedvc.network.packet;

import jp.houlab.mochidsuki.advancedvc.block.entity.SpeakerBlockEntity;
import jp.houlab.mochidsuki.advancedvc.common.VolumeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * スピーカーとマイクの接続パケット
 * クライアント → サーバー
 */
public class SpeakerConnectionPacket {

    private final BlockPos speakerPos;
    private final BlockPos microphonePos;
    private final int amplificationLevel; // VolumeLevel ordinal
    private final boolean connect; // true: 接続, false: 切断

    public SpeakerConnectionPacket(BlockPos speakerPos, BlockPos microphonePos, int amplificationLevel, boolean connect) {
        this.speakerPos = speakerPos;
        this.microphonePos = microphonePos;
        this.amplificationLevel = amplificationLevel;
        this.connect = connect;
    }

    // 接続パケット作成
    public static SpeakerConnectionPacket connect(BlockPos speakerPos, BlockPos microphonePos, VolumeLevel amplificationLevel) {
        return new SpeakerConnectionPacket(speakerPos, microphonePos, amplificationLevel.ordinal(), true);
    }

    // 切断パケット作成
    public static SpeakerConnectionPacket disconnect(BlockPos speakerPos) {
        return new SpeakerConnectionPacket(speakerPos, BlockPos.ZERO, 0, false);
    }

    // Encode to network
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(speakerPos);
        buf.writeBlockPos(microphonePos);
        buf.writeInt(amplificationLevel);
        buf.writeBoolean(connect);
    }

    // Decode from network
    public static SpeakerConnectionPacket decode(FriendlyByteBuf buf) {
        BlockPos speakerPos = buf.readBlockPos();
        BlockPos microphonePos = buf.readBlockPos();
        int amplificationLevel = buf.readInt();
        boolean connect = buf.readBoolean();
        return new SpeakerConnectionPacket(speakerPos, microphonePos, amplificationLevel, connect);
    }

    // Handle packet (server-side)
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level level = player.level();
                BlockEntity blockEntity = level.getBlockEntity(speakerPos);

                if (blockEntity instanceof SpeakerBlockEntity speakerBE) {
                    if (connect) {
                        // 接続
                        speakerBE.connectToMicrophone(microphonePos);
                        VolumeLevel volumeLevel = VolumeLevel.values()[amplificationLevel];
                        speakerBE.setAmplification(volumeLevel);
                    } else {
                        // 切断
                        speakerBE.disconnect();
                    }
                    speakerBE.setChanged();
                }
            }
        });
        context.setPacketHandled(true);
    }

    // Getters
    public BlockPos getSpeakerPos() { return speakerPos; }
    public BlockPos getMicrophonePos() { return microphonePos; }
    public int getAmplificationLevel() { return amplificationLevel; }
    public boolean isConnect() { return connect; }
}
