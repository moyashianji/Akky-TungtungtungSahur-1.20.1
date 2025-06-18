// ClientSetup.java - 完全修正版
package com.tungsahur.mod.client;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.renderer.TungBatProjectileRenderer;
import com.tungsahur.mod.client.renderer.TungSahurRenderer;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TungSahurMod.LOGGER.info("TungSahur クライアントセットアップ開始");

            // エンティティレンダラー登録
            registerEntityRenderers();

            // アイテムプロパティ登録（バット表示強化）
            registerEnhancedItemProperties();

            // TungSahur専用バットプロパティ登録
            registerTungSahurBatProperties();

            // 強制表示プロパティ追加
            registerForceDisplayProperties();

            TungSahurMod.LOGGER.info("TungSahur クライアントセットアップ完了 - バット表示強化");
        });
    }

    /**
     * エンティティレンダラーの登録
     */
    private static void registerEntityRenderers() {
        try {
            // Tung Sahurエンティティレンダラー（バット表示レイヤー付き）
            EntityRenderers.register(ModEntities.TUNG_SAHUR.get(), TungSahurRenderer::new);
            TungSahurMod.LOGGER.info("TungSahurRenderer正常に登録されました");

            // Tung Batプロジェクタイルレンダラー
            EntityRenderers.register(ModEntities.TUNG_BAT_PROJECTILE.get(), TungBatProjectileRenderer::new);
            TungSahurMod.LOGGER.info("TungBatProjectileRenderer正常に登録されました");

            TungSahurMod.LOGGER.debug("エンティティレンダラー登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("エンティティレンダラー登録中にエラー発生: ", e);
        }
    }

    /**
     * 強化されたアイテムプロパティの登録
     */
    private static void registerEnhancedItemProperties() {
        try {
            // バットの基本表示状態プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "always_visible"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        // TungSahurが持っている場合は常に1.0（表示）
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity) {
                            return 1.0F;
                        }
                        // プレイヤーが持っている場合も表示
                        if (livingEntity instanceof Player) {
                            return 0.8F;
                        }
                        return 0.5F;
                    });

            TungSahurMod.LOGGER.debug("基本アイテムプロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("アイテムプロパティ登録中にエラー発生: ", e);
        }
    }

    /**
     * TungSahur専用バットプロパティ登録
     */
    private static void registerTungSahurBatProperties() {
        try {
            // 強制表示プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "force_display"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag()) {
                            boolean forceDisplay = itemStack.getTag().getBoolean("ForceDisplay");
                            if (forceDisplay) return 1.0F;
                        }

                        // TungSahurエンティティの場合は常に表示
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity) {
                            return 1.0F;
                        }

                        return 0.0F;
                    });

            // 可視性強化プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "visibility_enhanced"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                            // TungSahurの進化段階に応じて可視性を強化
                            int stage = tungSahur.getEvolutionStage();
                            return 0.5F + (stage * 0.25F); // 0.5, 0.75, 1.0
                        }
                        return 0.0F;
                    });

            TungSahurMod.LOGGER.debug("TungSahur専用バットプロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("TungSahur専用プロパティ登録中にエラー発生: ", e);
        }
    }

    /**
     * 強制表示プロパティの追加
     */
    private static void registerForceDisplayProperties() {
        try {
            // 強制レンダリングプロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "force_render"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag()) {
                            boolean forceRender = itemStack.getTag().getBoolean("ForceRender");
                            boolean alwaysVisible = itemStack.getTag().getBoolean("AlwaysVisible");
                            boolean tungSahurOwned = itemStack.getTag().getBoolean("TungSahurOwned");

                            if (tungSahurOwned || forceRender || alwaysVisible) {
                                return 1.0F;
                            }
                        }
                        return 0.0F;
                    });

            // 装備状態プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "equipped"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (livingEntity != null) {
                            ItemStack mainHand = livingEntity.getMainHandItem();
                            ItemStack offHand = livingEntity.getOffhandItem();

                            return (mainHand == itemStack || offHand == itemStack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });

            // レンダリング優先度プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "render_priority"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag()) {
                            boolean tungSahurOwned = itemStack.getTag().getBoolean("TungSahurOwned");
                            boolean forceRender = itemStack.getTag().getBoolean("ForceRender");
                            boolean alwaysVisible = itemStack.getTag().getBoolean("AlwaysVisible");

                            // 複数の条件で高優先度を設定
                            int priority = 0;
                            if (tungSahurOwned) priority += 3;
                            if (forceRender) priority += 2;
                            if (alwaysVisible) priority += 1;

                            return Math.min(priority / 6.0F, 1.0F);
                        }
                        return 0.0F;
                    });

            // デバッグ用プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "debug_info"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                            // デバッグ情報として進化段階を10で割った値を返す
                            return tungSahur.getEvolutionStage() / 10.0F;
                        }
                        return 0.0F;
                    });

            TungSahurMod.LOGGER.debug("強制表示プロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("強制表示プロパティ登録中にエラー発生: ", e);
        }
    }
}