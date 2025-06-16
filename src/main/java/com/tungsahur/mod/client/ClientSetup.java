// ClientSetup.java - バット表示強化プロパティ版
package com.tungsahur.mod.client;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.renderer.BatItemRenderer;
import com.tungsahur.mod.client.renderer.TungBatProjectileRenderer;
import com.tungsahur.mod.client.renderer.TungSahurRenderer;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // エンティティレンダラー登録
            registerEntityRenderers();

            // アイテムプロパティ登録（バット表示強化）
            registerEnhancedItemProperties();

            // TungSahur専用バットプロパティ登録
            registerTungSahurBatProperties();

            // 強制表示プロパティ追加
            registerForceDisplayProperties();

            TungSahurMod.LOGGER.info("Tung Sahur クライアントセットアップ完了 - バット表示強化");
        });
    }

    /**
     * エンティティレンダラーの登録
     */
    private static void registerEntityRenderers() {
        // Tung Sahurエンティティレンダラー（バット表示レイヤー付き）
        EntityRenderers.register(ModEntities.TUNG_SAHUR.get(), TungSahurRenderer::new);

        // Tung Batプロジェクタイルレンダラー
        EntityRenderers.register(ModEntities.TUNG_BAT_PROJECTILE.get(), TungBatProjectileRenderer::new);

        TungSahurMod.LOGGER.debug("エンティティレンダラー登録完了");
    }

    /**
     * 強化されたアイテムプロパティの登録
     */
    private static void registerEnhancedItemProperties() {
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

        TungSahurMod.LOGGER.debug("強化アイテムプロパティ登録完了");
    }

    /**
     * TungSahur専用バットプロパティの登録
     */
    private static void registerTungSahurBatProperties() {
        // TungSahurステージプロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "tung_sahur_stage"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag()) {
                        return itemStack.getTag().getInt("TungSahurStage") / 2.0F; // 0.0, 0.5, 1.0
                    }
                    return 0.0F;
                });

        // 形態タイプ判定プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "form_type"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag()) {
                        String formType = itemStack.getTag().getString("FormType");
                        return switch (formType) {
                            case "Basic" -> 0.0F;
                            case "Bloodstained" -> 0.5F;
                            case "SoulBound" -> 1.0F;
                            default -> 0.0F;
                        };
                    }
                    return 0.0F;
                });

        // 血染めレベルプロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "bloodstained"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag()) {
                        boolean bloodstained = itemStack.getTag().getBoolean("Bloodstained");
                        int bloodLevel = itemStack.getTag().getInt("BloodLevel");
                        return bloodstained ? (bloodLevel / 3.0F) : 0.0F;
                    }
                    return 0.0F;
                });

        // 呪いプロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "cursed"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag()) {
                        boolean cursed = itemStack.getTag().getBoolean("Cursed");
                        boolean darkEnergy = itemStack.getTag().getBoolean("DarkEnergy");
                        boolean soulBound = itemStack.getTag().getBoolean("SoulBound");

                        if (soulBound) return 1.0F;
                        if (darkEnergy) return 0.7F;
                        if (cursed) return 0.4F;
                    }
                    return 0.0F;
                });

        // TungSahur所有プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "tung_sahur_owned"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag()) {
                        boolean tungSahurOwned = itemStack.getTag().getBoolean("TungSahurOwned");
                        if (tungSahurOwned && livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity) {
                            return 1.0F;
                        }
                    }
                    return 0.0F;
                });

        TungSahurMod.LOGGER.debug("TungSahur専用バットプロパティ登録完了");
    }

    /**
     * 強制表示プロパティの登録
     */
    private static void registerForceDisplayProperties() {
        // 強制表示フラグ
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

        // デバッグ表示プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "debug_visible"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    // デバッグモードでは常に表示
                    if (TungSahurMod.LOGGER.isDebugEnabled()) {
                        return 1.0F;
                    }
                    return 0.0F;
                });

        TungSahurMod.LOGGER.debug("強制表示プロパティ登録完了");
    }

    /**
     * リソースリロードリスナーの登録
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        TungSahurMod.LOGGER.debug("リロードリスナー登録完了");
    }

    /**
     * アイテムレンダリング用の設定クラス（強化版）
     */
    public static class BatItemExtensions implements IClientItemExtensions {
        private final BatItemRenderer renderer = new BatItemRenderer();

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return renderer;
        }
    }

    /**
     * バット表示強化のためのヘルパーメソッド
     */
    public static boolean shouldForceBatDisplay(ItemStack stack, LivingEntity entity) {
        // TungSahurエンティティの場合は常に表示
        if (entity instanceof com.tungsahur.mod.entity.TungSahurEntity) {
            return true;
        }

        // NBTタグによる強制表示チェック
        if (stack.hasTag()) {
            return stack.getTag().getBoolean("ForceRender") ||
                    stack.getTag().getBoolean("AlwaysVisible") ||
                    stack.getTag().getBoolean("TungSahurOwned") ||
                    stack.getTag().getBoolean("ForceDisplay");
        }

        return false;
    }

    /**
     * バットの可視性を確認するデバッグメソッド
     */
    public static void debugBatVisibility(ItemStack stack, LivingEntity entity) {
        if (!TungSahurMod.LOGGER.isDebugEnabled()) return;

        System.out.println("=== Bat Visibility Debug ===");
        System.out.println("Entity: " + (entity != null ? entity.getClass().getSimpleName() : "null"));
        System.out.println("Stack: " + stack.getItem().toString());
        System.out.println("Has Tag: " + stack.hasTag());

        if (stack.hasTag()) {
            System.out.println("ForceRender: " + stack.getTag().getBoolean("ForceRender"));
            System.out.println("AlwaysVisible: " + stack.getTag().getBoolean("AlwaysVisible"));
            System.out.println("TungSahurOwned: " + stack.getTag().getBoolean("TungSahurOwned"));
            System.out.println("ForceDisplay: " + stack.getTag().getBoolean("ForceDisplay"));
            System.out.println("TungSahurStage: " + stack.getTag().getInt("TungSahurStage"));
        }

        System.out.println("Should Force Display: " + shouldForceBatDisplay(stack, entity));
        System.out.println("============================");
    }
}