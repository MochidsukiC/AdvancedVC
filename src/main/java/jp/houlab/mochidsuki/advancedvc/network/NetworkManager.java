package jp.houlab.mochidsuki.advancedvc.network;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import jp.houlab.mochidsuki.advancedvc.network.packet.ControlPacket;
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

        LOGGER.info("Network channels registered");
    }
}
