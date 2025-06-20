package com.tungsahur.mod;

import com.mojang.logging.LogUtils;
import com.tungsahur.mod.commands.TungSahurCommands;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.events.BedSleepEvent;
import com.tungsahur.mod.events.DayCounterEvents;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
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



    }


    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TUNG_SAHUR_SPAWN_EGG);
        }
    }

    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TUNG_SAHUR.get(), TungSahurEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // 1つの設定で手動スポーンと自然スポーンの両方を処理
        event.register(ModEntities.TUNG_SAHUR.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, world, reason, pos, random) -> {
                    // 手動スポーン（コマンドやスポーンエッグ）の場合
                    if (reason == MobSpawnType.COMMAND || reason == MobSpawnType.SPAWN_EGG) {
                        // 既存の条件を使用
                        return TungSahurEntity.checkTungSahurSpawnRules(entityType, world, reason, pos, random);
                    }

                    // 自然スポーンの場合
                    if (reason == MobSpawnType.NATURAL) {
                        // 平和モード以外でのみスポーン
                        if (world.getDifficulty() == Difficulty.PEACEFUL) return false;

                        // 夜間のみスポーン
                        if (world.getLevel().isDay()) return false;

                        // 暗い場所でのみスポーン（ゾンビと同じ条件）
                        boolean darkEnough = Monster.isDarkEnoughToSpawn(world, pos, random);

                        // 基本的なモブスポーン条件
                        boolean basicRules = Mob.checkMobSpawnRules(entityType, world, reason, pos, random);

                        return darkEnough && basicRules;
                    }

                    // その他のスポーン理由の場合は既存の条件
                    return TungSahurEntity.checkTungSahurSpawnRules(entityType, world, reason, pos, random);
                },
                SpawnPlacementRegisterEvent.Operation.REPLACE); // REPLACEのみ使用

        TungSahurMod.LOGGER.info("TungSahur統合スポーン設定登録完了");
    }
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TungSahurCommands.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Tung Sahur クライアントセットアップ完了");
        }
    }

    /**
     * デバッグモードの状態を確認するメソッド
     * 開発環境やシステムプロパティで制御可能
     */
    public static boolean isDebugMode() {
        // システムプロパティでデバッグモードを制御
        String debugProperty = System.getProperty("tungsahur.debug");
        if ("true".equals(debugProperty)) {
            return true;
        }

        // 開発環境の場合はデバッグモード
        boolean isDevelopment = !net.minecraftforge.fml.loading.FMLEnvironment.production;
        if (isDevelopment) {
            return true;
        }

        // ログレベルがDEBUG以下の場合
        try {
            org.apache.logging.log4j.Level currentLevel = ((org.apache.logging.log4j.core.Logger) LOGGER).getLevel();
            if (currentLevel != null && currentLevel.isLessSpecificThan(org.apache.logging.log4j.Level.DEBUG)) {
                return true;
            }
        } catch (Exception e) {
            // ログレベル取得に失敗した場合は無視
        }

        return false;
    }

    /**
     * デバッグモードを強制的に有効/無効にするメソッド（開発時用）
     */
    public static void setDebugMode(boolean enabled) {
        System.setProperty("tungsahur.debug", String.valueOf(enabled));
        LOGGER.info("TungSahur デバッグモード: {}", enabled ? "有効" : "無効");
    }

    /**
     * デバッグ情報を出力するメソッド
     */
    public static void logDebugInfo(String message, Object... args) {
        if (isDebugMode()) {
            LOGGER.debug("[DEBUG] " + message, args);
        }
    }

    /**
     * 警告レベルのデバッグ情報を出力するメソッド
     */
    public static void logDebugWarn(String message, Object... args) {
        if (isDebugMode()) {
            LOGGER.warn("[DEBUG-WARN] " + message, args);
        }
    }

    /**
     * エラーレベルのデバッグ情報を出力するメソッド
     */
    public static void logDebugError(String message, Throwable throwable) {
        if (isDebugMode()) {
            LOGGER.error("[DEBUG-ERROR] " + message, throwable);
        }
    }
}