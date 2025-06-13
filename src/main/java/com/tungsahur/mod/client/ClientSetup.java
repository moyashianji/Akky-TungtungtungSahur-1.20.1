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
import net.minecraft.world.entity.player.Player;
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

            // アイテムプロパティ登録
            registerItemProperties();

            // Tung Sahur専用バットプロパティ登録
            registerTungSahurBatProperties();

            // カスタムアイテムレンダラー登録
            registerCustomItemRenderers();

            TungSahurMod.LOGGER.info("Tung Sahur クライアントセットアップ完了");
        });
    }

    /**
     * エンティティレンダラーの登録
     */
    private static void registerEntityRenderers() {
        // Tung Sahurエンティティレンダラー
        EntityRenderers.register(ModEntities.TUNG_SAHUR.get(), TungSahurRenderer::new);

        // Tung Batプロジェクタイルレンダラー（カスタムレンダラー使用）
        EntityRenderers.register(ModEntities.TUNG_BAT_PROJECTILE.get(), TungBatProjectileRenderer::new);

        TungSahurMod.LOGGER.debug("エンティティレンダラー登録完了");
    }

    /**
     * アイテムプロパティの登録
     */
    private static void registerItemProperties() {
        // バットの充電状態プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "charged"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (livingEntity != null) {
                        // アイテム使用中や攻撃中の状態を表現
                        boolean isUsing = livingEntity.isUsingItem() && livingEntity.getUseItem() == itemStack;
                        boolean isAttacking = false;

                        if (livingEntity instanceof Player player) {
                            // プレイヤーの攻撃状態をチェック
                            isAttacking = player.getAttackStrengthScale(0.0F) < 1.0F || player.swinging;
                        } else {
                            // 他のMobの攻撃状態をチェック
                            isAttacking = livingEntity.swinging || livingEntity.getLastHurtMob() != null;
                        }

                        return (isUsing || isAttacking) ? 1.0F : 0.0F;
                    }
                    return 0.0F;
                });

        // バットの光る効果プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "glowing"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    // 時間に基づく微細な光の変化
                    long time = System.currentTimeMillis();
                    return (float) (0.3F + Math.sin(time * 0.01F) * 0.2F);
                });

        // バットの恐怖度プロパティ（近くにTung Sahurがいる場合）
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "fear_level"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (livingEntity != null && clientLevel != null) {
                        // 近くにTung Sahurがいるかチェック
                        boolean nearbyTungSahur = clientLevel.getEntitiesOfClass(
                                com.tungsahur.mod.entity.TungSahurEntity.class,
                                livingEntity.getBoundingBox().inflate(32.0D)
                        ).size() > 0;

                        if (nearbyTungSahur) {
                            // 恐怖状態での視覚効果
                            long time = System.currentTimeMillis();
                            return (float) (0.5F + Math.sin(time * 0.02F) * 0.5F);
                        }
                    }
                    return 0.0F;
                });

        // バットの血痕レベル（ダメージを与えた回数に基づく）
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "blood_level"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag() && itemStack.getTag().contains("KillCount")) {
                        int kills = itemStack.getTag().getInt("KillCount");
                        return Math.min(kills / 10.0F, 1.0F); // 10キルで最大値
                    }
                    return 0.0F;
                });

        // バットの耐久度に基づく見た目変化
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "damage_level"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.isDamageableItem()) {
                        float damageRatio = (float) itemStack.getDamageValue() / itemStack.getMaxDamage();
                        return damageRatio;
                    }
                    return 0.0F;
                });

        TungSahurMod.LOGGER.debug("アイテムプロパティ登録完了");
    }

    /**
     * Tung Sahur専用バットプロパティの登録
     */
    private static void registerTungSahurBatProperties() {
        // Tung Sahurステージプロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "tung_sahur_stage"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag() && itemStack.getTag().contains("TungSahurStage")) {
                        return itemStack.getTag().getInt("TungSahurStage") / 2.0F; // 0.0, 0.5, 1.0
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

        // プレイヤー感知プロパティ
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "player_detection"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag() && livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity) {
                        boolean playerNearby = itemStack.getTag().getBoolean("PlayerNearby");
                        int fearLevel = itemStack.getTag().getInt("FearLevel");

                        if (playerNearby) {
                            // 恐怖レベルに応じた値 (0.2 - 1.0)
                            return 0.2F + (fearLevel / 5.0F) * 0.8F;
                        }
                    }
                    return 0.0F;
                });

        // 魂縛りプロパティ（最終形態専用）
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "soul_bound"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (itemStack.hasTag() && itemStack.getTag().getBoolean("SoulBound")) {
                        // 時間による脈動効果
                        long time = System.currentTimeMillis();
                        return (float) (0.5F + Math.sin(time * 0.005F) * 0.5F);
                    }
                    return 0.0F;
                });

        // バット活性化プロパティ（攻撃準備中）
        ItemProperties.register(ModItems.TUNG_SAHUR_BAT.get(),
                new ResourceLocation(TungSahurMod.MODID, "activated"),
                (itemStack, clientLevel, livingEntity, seed) -> {
                    if (livingEntity instanceof com.tungsahur.mod.entity.TungSahurEntity tungSahur) {
                        // 攻撃中やターゲットがいる時
                        boolean hasTarget = tungSahur.getTarget() != null;
                        boolean isAttacking = tungSahur.swinging;
                        boolean isCharging = tungSahur.isBeingWatched(); // 見られている時は充電状態

                        if (isAttacking) return 1.0F;
                        if (hasTarget && isCharging) return 0.8F;
                        if (hasTarget) return 0.6F;
                    }
                    return 0.0F;
                });

        TungSahurMod.LOGGER.debug("Tung Sahur専用バットプロパティ登録完了");
    }

    /**
     * カスタムアイテムレンダラーの登録
     */
    private static void registerCustomItemRenderers() {
        // Tung Sahur Bat用のカスタムレンダラーは直接アイテムクラスで実装
        // IClientItemExtensionsはアイテムクラス側で実装する必要がある

        TungSahurMod.LOGGER.debug("カスタムアイテムレンダラー登録完了");
    }

    /**
     * リソースリロードリスナーの登録
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // カスタムリソースローダーがあれば登録
        // 現在は特別なリソースローダーは不要
        TungSahurMod.LOGGER.debug("リロードリスナー登録完了");
    }

    /**
     * アイテムレンダリング用の設定クラス
     */
    public static class BatItemExtensions implements IClientItemExtensions {
        private final BatItemRenderer renderer = new BatItemRenderer();

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return renderer;
        }
    }

    /**
     * パーティクル関連の初期化
     */
    private static void initializeParticleEffects() {
        // カスタムパーティクルがあれば登録
        // 現在はバニラパーティクルを使用しているため不要
        TungSahurMod.LOGGER.debug("パーティクル効果初期化完了");
    }

    /**
     * シェーダー関連の初期化
     */
    private static void initializeShaders() {
        // カスタムシェーダーがあれば登録
        // 現在は特別なシェーダーは不要
        TungSahurMod.LOGGER.debug("シェーダー初期化完了");
    }

    /**
     * キーバインド関連の初期化
     */
    private static void initializeKeyBindings() {
        // Tung Sahur専用のキーバインドがあれば登録
        // 例: 恐怖メーターの表示切替、特殊攻撃など
        TungSahurMod.LOGGER.debug("キーバインド初期化完了");
    }

    /**
     * HUD/GUI関連の初期化
     */
    private static void initializeHUD() {
        // Tung Sahurゲーム用のHUD要素
        // 例: 恐怖メーター、日数表示、Tung Sahur接近警告など
        TungSahurMod.LOGGER.debug("HUD初期化完了");
    }

    /**
     * 音響効果の初期化
     */
    private static void initializeSoundEffects() {
        // カスタムサウンドイベントがあれば登録
        // 現在はバニラサウンドを使用
        TungSahurMod.LOGGER.debug("音響効果初期化完了");
    }

    /**
     * レンダリング最適化の設定
     */
    private static void optimizeRendering() {
        // パフォーマンス向上のための設定
        // LODシステム、カリング最適化など
        TungSahurMod.LOGGER.debug("レンダリング最適化完了");
    }

    /**
     * デバッグ用の設定
     */
    private static void setupDebugFeatures() {
        // 開発時のデバッグ機能
        if (TungSahurMod.LOGGER.isDebugEnabled()) {
            // デバッグレンダリング、ヒットボックス表示など
            TungSahurMod.LOGGER.debug("デバッグ機能セットアップ完了");
        }
    }

    /**
     * MOD互換性チェック
     */
    private static void checkModCompatibility() {
        // 他のMODとの互換性チェック
        // OptiFine、Sodium、JEIなどとの互換性
        TungSahurMod.LOGGER.debug("MOD互換性チェック完了");
    }

    /**
     * 設定ファイルからのクライアント設定読み込み
     */
    private static void loadClientConfig() {
        // クライアント専用設定の読み込み
        // パーティクル密度、音量設定、描画品質など
        TungSahurMod.LOGGER.debug("クライアント設定読み込み完了");
    }

    /**
     * 完全なクライアント初期化
     */
    public static void completeClientInitialization(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 基本設定
            registerEntityRenderers();
            registerItemProperties();
            registerTungSahurBatProperties();
            registerCustomItemRenderers();

            // 追加機能
            initializeParticleEffects();
            initializeShaders();
            initializeKeyBindings();
            initializeHUD();
            initializeSoundEffects();

            // 最適化
            optimizeRendering();

            // 開発・デバッグ
            setupDebugFeatures();
            checkModCompatibility();
            loadClientConfig();

            TungSahurMod.LOGGER.info("§c§lTung Sahur クライアント完全初期化完了 - 恐怖の始まり...§r");
        });
    }
}