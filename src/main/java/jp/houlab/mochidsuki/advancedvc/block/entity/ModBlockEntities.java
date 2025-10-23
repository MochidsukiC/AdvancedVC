package jp.houlab.mochidsuki.advancedvc.block.entity;

import jp.houlab.mochidsuki.advancedvc.AdvancedvcMain;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BlockEntity登録
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdvancedvcMain.MODID);

    public static final RegistryObject<BlockEntityType<MicrophoneBlockEntity>> MICROPHONE =
            BLOCK_ENTITIES.register("microphone", () ->
                    BlockEntityType.Builder.of(
                            MicrophoneBlockEntity::new,
                            AdvancedvcMain.MICROPHONE_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            BLOCK_ENTITIES.register("speaker", () ->
                    BlockEntityType.Builder.of(
                            SpeakerBlockEntity::new,
                            AdvancedvcMain.SPEAKER_BLOCK.get()
                    ).build(null));
}
