// TungSahurMod.java - 完全修正版（レンダラー登録の二重回避）
package com.tungsahur.mod;

import com.mojang.logging.LogUtils;
import com.tungsahur.mod.commands.TungSahurCommands;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.events.BedSleepEvent;
import com.tungsahur.mod.events.DayCounterEvents;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.SpawnPlacements;
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

        LOGGER.info("TungSahurMod初期化開始");

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

        LOGGER.info("TungSahurMod初期化完了");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Tung Tung Tung Sahur Mod - 恐怖の始まり...");

        event.enqueueWork(() -> {
            // サーバー側の共通セットアップ
            LOGGER.info("TungSahur共通セットアップ完了");
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.TUNG_SAHUR_SPAWN_EGG);
            LOGGER.debug("TungSahurスポーンエッグをクリエイティブタブに追加");
        }
    }

    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        try {
            event.put(ModEntities.TUNG_SAHUR.get(), TungSahurEntity.createAttributes().build());
            LOGGER.info("TungSahurEntityの属性登録完了");
        } catch (Exception e) {
            LOGGER.error("TungSahurEntity属性登録中にエラー発生: ", e);
        }
    }

    @SubscribeEvent
    public void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        try {
            event.register(ModEntities.TUNG_SAHUR.get(),
                    SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    TungSahurEntity::checkTungSahurSpawnRules,
                    SpawnPlacementRegisterEvent.Operation.REPLACE);
            LOGGER.info("TungSahurEntityのスポーン配置ルール登録完了");
        } catch (Exception e) {
            LOGGER.error("TungSahurEntityスポーン配置登録中にエラー発生: ", e);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TungSahurCommands.register(event.getDispatcher());
        LOGGER.info("TungSahurコマンド登録完了");
    }

    // クライアント側のイベントハンドラ（ClientSetup.javaとの二重登録を回避）
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // ClientSetup.javaで処理されるため、ここでは最小限のログのみ
            LOGGER.info("TungSahur メインクラス側クライアントセットアップ完了");
        }
    }
}