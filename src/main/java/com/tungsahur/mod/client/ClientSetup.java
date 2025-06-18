// ClientSetup.java - 完全対応版
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

            try {
                // エンティティレンダラー登録
                registerEntityRenderers();

                // アイテムプロパティ登録（バット表示強化）
                registerEnhancedItemProperties();

                // TungSahur専用バットプロパティ登録
                registerTungSahurBatProperties();

                // 日数別バットプロパティ登録
                registerDaySpecificProperties();

                // デバッグ用プロパティ登録
                registerDebugProperties();

                TungSahurMod.LOGGER.info("TungSahur クライアントセットアップ完了 - 全機能対応");

            } catch (Exception e) {
                TungSahurMod.LOGGER.error("クライアントセットアップ中にエラー発生: ", e);
                throw new RuntimeException("TungSahur クライアントセットアップ失敗", e);
            }
        });
    }

    /**
     * エンティティレンダラーの登録
     */
    private static void registerEntityRenderers() {
        try {
            // Tung Sahurエンティティレンダラー（日数対応版）
            EntityRenderers.register(ModEntities.TUNG_SAHUR.get(), TungSahurRenderer::new);
            TungSahurMod.LOGGER.info("TungSahurRenderer正常に登録されました");

            // Tung Batプロジェクタイルレンダラー（強化版）
            EntityRenderers.register(ModEntities.TUNG_BAT_PROJECTILE.get(), TungBatProjectileRenderer::new);
            TungSahurMod.LOGGER.info("TungBatProjectileRenderer正常に登録されました");

            TungSahurMod.LOGGER.debug("エンティティレンダラー登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("エンティティレンダラー登録中にエラー発生: ", e);
            throw e;
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

            // バットの使用状態プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "in_use"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                            if (tungSahur.isCurrentlyAttacking()) return 1.0F;
                            if (tungSahur.isCurrentlyThrowing()) return 0.8F;
                            if (tungSahur.isCurrentlyJumping()) return 0.6F;
                        }
                        return 0.0F;
                    });

            TungSahurMod.LOGGER.debug("基本アイテムプロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("アイテムプロパティ登録中にエラー発生: ", e);
            throw e;
        }
    }

    /**
     * TungSahur専用バットプロパティの登録
     */
    private static void registerTungSahurBatProperties() {
        try {
            // エンティティバット識別プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "entity_bat"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag() && itemStack.getTag().getBoolean("EntityBat")) {
                            return 1.0F;
                        }
                        return 0.0F;
                    });

            // 耐久度表示プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "durability"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag() && itemStack.getTag().getBoolean("Unbreakable")) {
                            return 1.0F; // 無限耐久
                        }
                        return itemStack.getDamageValue() / (float) itemStack.getMaxDamage();
                    });

            // エンチャント光沢プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "enchanted"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag() && itemStack.getTag().getBoolean("Enchanted")) {
                            return 1.0F;
                        }
                        return itemStack.isEnchanted() ? 1.0F : 0.0F;
                    });

            TungSahurMod.LOGGER.debug("TungSahur専用バットプロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("TungSahurバットプロパティ登録中にエラー発生: ", e);
            throw e;
        }
    }

    /**
     * 日数別バットプロパティの登録
     */
    private static void registerDaySpecificProperties() {
        try {
            // 日数プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "day_number"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (itemStack.hasTag()) {
                            int dayNumber = itemStack.getTag().getInt("DayNumber");
                            return dayNumber / 10.0F; // 0.1, 0.2, 0.3として返す
                        }

                        // エンティティから日数を取得
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                            return tungSahur.getDayNumber() / 10.0F;
                        }

                        return 0.1F; // デフォルトは1日目
                    });

            // 1日目専用プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "day_1"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        int dayNumber = getDayNumberFromItem(itemStack, livingEntity);
                        return dayNumber == 1 ? 1.0F : 0.0F;
                    });

            // 2日目専用プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "day_2"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        int dayNumber = getDayNumberFromItem(itemStack, livingEntity);
                        return dayNumber == 2 ? 1.0F : 0.0F;
                    });

            // 3日目専用プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "day_3"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        int dayNumber = getDayNumberFromItem(itemStack, livingEntity);
                        return dayNumber == 3 ? 1.0F : 0.0F;
                    });

            TungSahurMod.LOGGER.debug("日数別バットプロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("日数別バットプロパティ登録中にエラー発生: ", e);
            throw e;
        }
    }

    /**
     * デバッグ用プロパティの登録
     */
    private static void registerDebugProperties() {
        try {
            // デバッグモード表示プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "debug_mode"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        return TungSahurMod.isDebugMode() ? 1.0F : 0.0F;
                    });

            // エンティティ状態表示プロパティ
            ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                    new ResourceLocation(TungSahurMod.MODID, "entity_state"),
                    (itemStack, clientLevel, livingEntity, seed) -> {
                        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                            // 状態を数値化して返す
                            if (tungSahur.isCurrentlyAttacking()) return 0.8F;
                            if (tungSahur.isCurrentlyThrowing()) return 0.6F;
                            if (tungSahur.isCurrentlyJumping()) return 0.4F;
                            if (tungSahur.isWallClimbing()) return 0.2F;
                            return 0.1F; // idle状態
                        }
                        return 0.0F;
                    });

            TungSahurMod.LOGGER.debug("デバッグ用プロパティ登録完了");
        } catch (Exception e) {
            TungSahurMod.LOGGER.error("デバッグ用プロパティ登録中にエラー発生: ", e);
            // デバッグプロパティのエラーは致命的ではないので続行
            TungSahurMod.LOGGER.warn("デバッグ用プロパティの一部が登録されませんでした");
        }
    }

    /**
     * アイテムまたはエンティティから日数を取得するヘルパーメソッド
     */
    private static int getDayNumberFromItem(ItemStack itemStack, LivingEntity livingEntity) {
        // アイテムタグから取得
        if (itemStack.hasTag()) {
            int dayNumber = itemStack.getTag().getInt("DayNumber");
            if (dayNumber > 0) {
                return dayNumber;
            }
        }

        // エンティティから取得
        if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
            return tungSahur.getDayNumber();
        }

        // デフォルト
        return 1;
    }



    /**
     * レンダリング統計情報の出力
     */
    public static void logRenderingStats() {
        TungSahurMod.LOGGER.info("=== TungSahur レンダリング統計 ===");
        TungSahurMod.LOGGER.info("登録済みエンティティレンダラー数: 2");
        TungSahurMod.LOGGER.info("登録済みアイテムプロパティ数: 10+");
        TungSahurMod.LOGGER.info("デバッグモード: {}", TungSahurMod.isDebugMode());
        TungSahurMod.LOGGER.info("==================================");
    }
}