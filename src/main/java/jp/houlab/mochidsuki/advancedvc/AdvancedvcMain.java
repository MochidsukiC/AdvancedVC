package jp.houlab.mochidsuki.advancedvc;

import com.mojang.logging.LogUtils;
import jp.houlab.mochidsuki.advancedvc.api.AcousticPropertiesLoader;
import jp.houlab.mochidsuki.advancedvc.block.*;
import jp.houlab.mochidsuki.advancedvc.block.entity.ModBlockEntities;
import jp.houlab.mochidsuki.advancedvc.client.audio.ClientAudioEngine;
import jp.houlab.mochidsuki.advancedvc.item.BandToolItem;
import jp.houlab.mochidsuki.advancedvc.item.WalkieTalkieItem;
import jp.houlab.mochidsuki.advancedvc.network.NetworkManager;
import jp.houlab.mochidsuki.advancedvc.server.ServerAudioRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Advanced VC (Advanced Voice Chat) Main Class
 * 高度音響VCシステムのメインクラス
 */
@Mod(AdvancedvcMain.MODID)
public class AdvancedvcMain {

    public static final String MODID = "advancedvc";
    private static final Logger LOGGER = LogUtils.getLogger();

    // ===== Deferred Registers =====
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ===== ブロック登録 =====
    // 音響ブロック
    public static final RegistryObject<Block> MICROPHONE_BLOCK = BLOCKS.register("microphone",
            () -> new MicrophoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> SPEAKER_BLOCK = BLOCKS.register("speaker",
            () -> new SpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> SOUNDPROOF_BLOCK = BLOCKS.register("soundproof_block",
            () -> new SoundproofBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(2.0f)
                    .sound(SoundType.WOOL)));

    public static final RegistryObject<Block> ABSORBENT_BLOCK = BLOCKS.register("absorbent_block",
            () -> new AbsorbentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(2.0f)
                    .sound(SoundType.WOOL)));

    // ===== アイテム登録 =====
    // ブロックアイテム
    public static final RegistryObject<Item> MICROPHONE_ITEM = ITEMS.register("microphone",
            () -> new BlockItem(MICROPHONE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPEAKER_ITEM = ITEMS.register("speaker",
            () -> new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> SOUNDPROOF_BLOCK_ITEM = ITEMS.register("soundproof_block",
            () -> new BlockItem(SOUNDPROOF_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> ABSORBENT_BLOCK_ITEM = ITEMS.register("absorbent_block",
            () -> new BlockItem(ABSORBENT_BLOCK.get(), new Item.Properties()));

    // ツールアイテム
    public static final RegistryObject<Item> WALKIE_TALKIE = ITEMS.register("walkie_talkie",
            () -> new WalkieTalkieItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BAND_TOOL = ITEMS.register("band_tool",
            () -> new BandToolItem(new Item.Properties().stacksTo(1)));

    // ===== クリエイティブタブ =====
    public static final RegistryObject<CreativeModeTab> ADVANCED_VC_TAB = CREATIVE_MODE_TABS.register("advanced_vc_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> WALKIE_TALKIE.get().getDefaultInstance())
                    .title(net.minecraft.network.chat.Component.literal("Advanced VC"))
                    .displayItems((parameters, output) -> {
                        // アイテム追加
                        output.accept(WALKIE_TALKIE.get());
                        output.accept(BAND_TOOL.get());
                        output.accept(MICROPHONE_ITEM.get());
                        output.accept(SPEAKER_ITEM.get());
                        output.accept(SOUNDPROOF_BLOCK_ITEM.get());
                        output.accept(ABSORBENT_BLOCK_ITEM.get());
                    })
                    .build());

    public AdvancedvcMain() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 共通セットアップ
        modEventBus.addListener(this::commonSetup);

        // レジストリ登録
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // イベントバス登録
        MinecraftForge.EVENT_BUS.register(this);

        // クリエイティブタブ追加
        modEventBus.addListener(this::addCreative);

        // 設定登録
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info("Advanced VC initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Advanced VC common setup");

        // ネットワークチャネル登録
        event.enqueueWork(NetworkManager::register);

        // 音響特性のロード
        event.enqueueWork(AcousticPropertiesLoader::loadAcousticProperties);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 必要に応じて他のタブにもアイテムを追加
    }

    /**
     * サーバー起動時の処理
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Advanced VC: Server started, initializing audio router");

        // サーバーサイド・オーディオルーターを起動
        ServerAudioRouter router = ServerAudioRouter.getInstance();
        router.start(event.getServer(), Config.udpPort);
    }

    /**
     * サーバー停止時の処理
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Advanced VC: Server stopping, shutting down audio router");

        // サーバーサイド・オーディオルーターを停止
        ServerAudioRouter router = ServerAudioRouter.getInstance();
        router.stop();
    }

    /**
     * クライアントサイドのイベントハンドラー
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Advanced VC: Client setup");
        }
    }

    /**
     * クライアントサイドのゲームイベント
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {

        /**
         * サーバーに接続した時
         */
        @SubscribeEvent
        public static void onClientPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            if (!Config.enableVoiceChat) {
                LOGGER.info("Voice chat is disabled in config");
                return;
            }

            if (!Config.autoStart) {
                LOGGER.info("Auto-start is disabled, voice chat not started automatically");
                return;
            }

            LOGGER.info("Advanced VC: Player logged in, starting client audio engine");

            // クライアントオーディオエンジンを起動
            Minecraft mc = Minecraft.getInstance();
            if (mc.getCurrentServer() != null) {
                String serverAddress = mc.getCurrentServer().ip;
                ClientAudioEngine.getInstance().start(serverAddress, Config.udpPort);
            }
        }

        /**
         * サーバーから切断した時
         */
        @SubscribeEvent
        public static void onClientPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            LOGGER.info("Advanced VC: Player logged out, stopping client audio engine");

            // クライアントオーディオエンジンを停止
            ClientAudioEngine engine = ClientAudioEngine.getInstance();
            if (engine.isRunning()) {
                engine.stop();
            }
        }
    }
}
