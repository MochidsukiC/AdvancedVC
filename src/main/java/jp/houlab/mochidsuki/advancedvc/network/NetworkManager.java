package jp.houlab.mochidsuki.advancedvc.network;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import jp.houlab.mochidsuki.advancedvc.network.packet.BandSessionPacket;
import jp.houlab.mochidsuki.advancedvc.network.packet.ControlPacket;
import jp.houlab.mochidsuki.advancedvc.network.packet.MixerControlPacket;
import jp.houlab.mochidsuki.advancedvc.network.packet.PlayerPositionSyncPacket;
import jp.houlab.mochidsuki.advancedvc.network.packet.SpeakerConnectionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * Forgeネットワークチャネルマネージャー
 * 制御データ（ミュート、モード切替、周波数設定等）の信頼性の高い送信に使用
 */
public class NetworkManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AdvancedvcMain.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        // ControlPacketを登録
        CHANNEL.registerMessage(
                packetId++,
                ControlPacket.class,
                ControlPacket::encode,
                ControlPacket::decode,
                ControlPacket::handle
        );

        // SpeakerConnectionPacketを登録
        CHANNEL.registerMessage(
                packetId++,
                SpeakerConnectionPacket.class,
                SpeakerConnectionPacket::encode,
                SpeakerConnectionPacket::decode,
                SpeakerConnectionPacket::handle
        );

        // BandSessionPacketを登録
        CHANNEL.registerMessage(
                packetId++,
                BandSessionPacket.class,
                BandSessionPacket::encode,
                BandSessionPacket::decode,
                BandSessionPacket::handle
        );

        // MixerControlPacketを登録
        CHANNEL.registerMessage(
                packetId++,
                MixerControlPacket.class,
                MixerControlPacket::encode,
                MixerControlPacket::decode,
                MixerControlPacket::handle
        );

        // PlayerPositionSyncPacketを登録
        CHANNEL.registerMessage(
                packetId++,
                PlayerPositionSyncPacket.class,
                PlayerPositionSyncPacket::encode,
                PlayerPositionSyncPacket::decode,
                PlayerPositionSyncPacket::handle
        );

        LOGGER.info("Network channels registered");
    }
}
