
        package com.tungsahur.mod;

import com.mojang.logging.LogUtils;
import com.tungsahur.mod.commands.TungSahurCommands;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.events.BedSleepEvent;
import com.tungsahur.mod.events.DayCounterEvents;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(TungSahurMod.MODID)
public class TungSahurMod {
    public static final String MODID = "tungsahurmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Creative Mode Tab
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> TUNG_SAHUR_TAB = CREATIVE_MODE_TABS.register("tung_sahur_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(ModItems.TUNG_SAHUR_BAT.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.TUNG_SAHUR_BAT.get());
                output.accept(ModItems.TUNG_SAHUR_SPAWN_EGG.get());
            }).build());

    public TungSahurMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // レジスタ登録
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // イベント登録
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);

        // Forge Event Bus登録
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new BedSleepEvent());
        MinecraftForge.EVENT_BUS.register(new DayCounterEvents());

        // コンフィグ登録
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Tung Tung Tung Sahur Mod - 恐怖の始まり...");
        LOGGER.info("新しいゲームフローシステムが初期化されました");

        // 新しいゲームシステムの初期化ログ
        LOGGER.info("=== ゲームシステム概要 ===");
        LOGGER.info("- /tungsahur start: ゲーム開始");
        LOGGER.info("- /tungsahur reset: ゲームリセット");
        LOGGER.info("- ゲーム中は睡眠不可、3日目の夜終了後に睡眠可能");
        LOGGER.info("========================");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.TUNG_SAHUR_BAT);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TUNG_SAHUR_SPAWN_EGG);
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TUNG_SAHUR.get(), TungSahurEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.TUNG_SAHUR.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /**
     * コマンド登録
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TungSahurCommands.register(event.getDispatcher());
        LOGGER.info("TungSahurコマンドが登録されました");
    }

    // クライアント専用イベント
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Tung Sahur Mod クライアント初期化完了");
        }
    }

    // === 新しいゲームシステム用のユーティリティメソッド ===

    /**
     * ゲーム状態の初期化（サーバー起動時）
     */
    public static void initializeGameSystems() {
        LOGGER.info("ゲームシステムを初期化中...");

        // イベントシステムのリセット
        DayCounterEvents.resetGameState();

        LOGGER.info("ゲームシステム初期化完了");
    }

    /**
     * デバッグ情報の取得
     */
    public static void logDebugInfo() {
        LOGGER.info("=== TungSahur Mod デバッグ情報 ===");
        LOGGER.info("Mod ID: {}", MODID);
        LOGGER.info("Day Counter Events: {}", DayCounterEvents.getDebugInfo());
        LOGGER.info("Bed Sleep Event: システム正常動作中");
        LOGGER.info("===========================");
    }

    /**
     * モッドの完全リセット（開発・デバッグ用）
     */
    public static void performCompleteReset() {
        LOGGER.warn("完全リセットを実行中...");

        // イベントシステムのリセット
        DayCounterEvents.resetGameState();

        // 睡眠システムの統計をリセット
        BedSleepEvent.logSleepPreventionStatistics();

        LOGGER.info("完全リセット完了");
    }
}