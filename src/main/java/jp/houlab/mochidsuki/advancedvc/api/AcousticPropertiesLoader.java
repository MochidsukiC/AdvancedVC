package jp.houlab.mochidsuki.advancedvc.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 音響特性JSONローダー
 */
public class AcousticPropertiesLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /**
     * 音響特性JSONを読み込んでAPIに登録
     */
    public static void loadAcousticProperties() {
        try {
            // リソースからJSONを読み込み
            InputStream stream = AcousticPropertiesLoader.class.getResourceAsStream(
                    "/data/advancedvc/acoustic_properties.json"
            );

            if (stream == null) {
                LOGGER.warn("Acoustic properties JSON not found, using defaults");
                return;
            }

            JsonObject root = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
            JsonObject blocks = root.getAsJsonObject("blocks");

            AdvancedProximityChatAPI api = AdvancedProximityChatAPI.getInstance();
            int loadedCount = 0;

            // 各ブロックの音響特性を登録
            for (String blockId : blocks.keySet()) {
                JsonObject props = blocks.getAsJsonObject(blockId);
                float absorption = props.get("absorption").getAsFloat();
                float reflection = props.get("reflection").getAsFloat();

                // ブロックを取得
                ResourceLocation blockLocation = new ResourceLocation(blockId);
                Block block = ForgeRegistries.BLOCKS.getValue(blockLocation);

                if (block != null) {
                    api.registerSoundOcclusionBlock(block, absorption, reflection);
                    loadedCount++;
                    LOGGER.debug("Loaded acoustic properties for {}: absorption={}, reflection={}",
                            blockId, absorption, reflection);
                } else {
                    LOGGER.warn("Block not found: {}", blockId);
                }
            }

            LOGGER.info("Loaded acoustic properties for {} blocks", loadedCount);

            stream.close();
        } catch (Exception e) {
            LOGGER.error("Failed to load acoustic properties", e);
        }
    }
}
