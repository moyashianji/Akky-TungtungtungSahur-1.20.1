// TungSahurRenderer.java - 点滅バグ修正版 + SahurItemLayer追加
package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.client.renderer.layers.SahurItemLayer; // 追加
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;

public class TungSahurRenderer extends DynamicGeoEntityRenderer<TungSahurEntity> {

    // デバッグ用フラグ
    private static final boolean DEBUG_RENDERING = false;
    private static final boolean ENABLE_DISTANCE_OPTIMIZATION = false; // 点滅問題解決のため無効化

    public TungSahurRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TungSahurModel());
        this.shadowRadius = 0.7F; // 基本影サイズ

        // SahurItemLayerを追加してバットを表示
        this.addRenderLayer(new SahurItemLayer<>(this));

        TungSahurMod.LOGGER.info("TungSahurRenderer初期化完了 - SahurItemLayer追加");
    }

    @Override
    public void render(TungSahurEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        // レンダリング前の準備
        ensureSingleBatIsEquipped(entity);

        // 日数に応じたスケール適用
        float scaleFactor = entity.getScaleFactor();
        poseStack.pushPose();
        poseStack.scale(scaleFactor, scaleFactor, scaleFactor);

        // 影サイズもスケールに合わせて調整
        this.shadowRadius = 0.7F * scaleFactor;

        // デバッグ情報をログに出力（デバッグモード時のみ）
        if (DEBUG_RENDERING && entity.tickCount % 100 == 0) {
            logRenderingDebugInfo(entity, scaleFactor);
        }

        try {
            // メインレンダリング実行（レイヤーも含む）
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } catch (Exception e) {
            // レンダリングエラーをキャッチして安全に処理
            TungSahurMod.LOGGER.error("TungSahurレンダリングエラー: {}", e.getMessage());
            if (DEBUG_RENDERING) {
                e.printStackTrace();
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * バットアイテムが正しく装備されていることを確認
     */
    private void ensureSingleBatIsEquipped(TungSahurEntity entity) {
        ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);

        // メインハンドにバットが装備されていない場合
        if (mainHandItem.isEmpty() || !mainHandItem.is(ModItems.TUNG_SAHUR_BAT.get())) {
            ItemStack batStack = createDaySpecificBat(entity.getDayNumber());
            entity.setItemInHand(InteractionHand.MAIN_HAND, batStack);

            if (DEBUG_RENDERING) {
                TungSahurMod.LOGGER.debug("TungSahur {} にバットを装備: Day{}",
                        entity.getId(), entity.getDayNumber());
            }
        }

        // オフハンドが空でない場合はクリア
        if (!offHandItem.isEmpty()) {
            entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * 日数に応じたバットの作成
     */
    private ItemStack createDaySpecificBat(int dayNumber) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        // 日数に応じたカスタマイズ
        switch (dayNumber) {
            case 1:
                tag.putString("DisplayName", "§7TungSahur's Bat (Day 1)");
                tag.putInt("HideFlags", 63);
                break;
            case 2:
                tag.putString("DisplayName", "§cTungSahur's Enhanced Bat (Day 2)");
                tag.putInt("HideFlags", 63);
                tag.putBoolean("Enchanted", true); // 光る効果
                break;
            case 3:
                tag.putString("DisplayName", "§5TungSahur's Ultimate Bat (Day 3)");
                tag.putInt("HideFlags", 63);
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 999); // 特別なモデル
                break;
            default:
                tag.putString("DisplayName", "§8TungSahur's Mysterious Bat");
                tag.putInt("HideFlags", 63);
                break;
        }

        // エンティティ用バットの設定
        tag.putBoolean("EntityBat", true);
        tag.putBoolean("Unbreakable", true);
        tag.putInt("DayNumber", dayNumber);

        return batStack;
    }

    @Override
    public ResourceLocation getTextureLocation(TungSahurEntity entity) {
        // モデルクラスにテクスチャ選択を委譲
        return ((TungSahurModel) this.model).getTextureResource(entity);
    }

    /**
     * エンティティの明るさ調整
     */
    @Override
    protected int getBlockLightLevel(TungSahurEntity entity, BlockPos pos) {
        // 基本明度を取得
        int baseLightLevel = super.getBlockLightLevel(entity, pos);

        // 日数が高いほど暗闇でも少し明るく見える（ただし適度に）
        int dayBonus = Math.min(2, entity.getDayNumber() - 1); // 最大2まで

        // 見られている時は少し暗くする
        int watchingPenalty = entity.isBeingWatched() ? 1 : 0;

        int finalLightLevel = Math.max(0, Math.min(15, baseLightLevel + dayBonus - watchingPenalty));

        return finalLightLevel;
    }

    /**
     * スカイライトレベルの調整
     */
    @Override
    protected int getSkyLightLevel(TungSahurEntity entity, BlockPos pos) {
        int baseSkyLight = super.getSkyLightLevel(entity, pos);

        // 夜間はTungSahurを少し見えやすくする
        if (entity.level().isNight()) {
            return Math.min(15, baseSkyLight + 1);
        }

        return baseSkyLight;
    }

    /**
     * 修正された可視性チェック（点滅バグ修正）
     */
    @Override
    public boolean shouldRender(TungSahurEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {

        // 基本の可視性チェック
        boolean basicVisibility = super.shouldRender(entity, frustum, x, y, z);

        if (!basicVisibility) {
            return false;
        }

        // エンティティが無効な場合は描画しない
        if (entity.isRemoved() || !entity.isAlive()) {
            return false;
        }

        // 距離による最適化を無効化（点滅問題の解決）
        if (!ENABLE_DISTANCE_OPTIMIZATION) {
            return true;
        }

        // 以下は従来の距離最適化ロジック（現在は無効）
        double distanceSquared = entity.distanceToSqr(x, y, z);

        // 極遠距離でのみレンダリング頻度を調整（より控えめに）
        if (distanceSquared > 2304.0D) { // 48ブロック以上（従来の32から拡大）
            // より穏やかな間引き（5フレームに1回から2フレームに1回に変更）
            return entity.tickCount % 2 == 0;
        }

        // 中距離以下では常に描画
        return true;
    }

    /**
     * カリング範囲の調整
     */
    @Override
    public boolean shouldShowName(TungSahurEntity entity) {
        // デバッグモード時のみ名前表示
        if (DEBUG_RENDERING) {
            return entity.hasCustomName();
        }

        // 通常は名前を表示しない（恐怖演出のため）
        return false;
    }

    /**
     * デバッグ情報のログ出力
     */
    private void logRenderingDebugInfo(TungSahurEntity entity, float scaleFactor) {
        TungSahurMod.LOGGER.debug("=== TungSahurレンダリング情報 ===");
        TungSahurMod.LOGGER.debug("  エンティティID: {}", entity.getId());
        TungSahurMod.LOGGER.debug("  日数: {}", entity.getDayNumber());
        TungSahurMod.LOGGER.debug("  スケール: {}", scaleFactor);
        TungSahurMod.LOGGER.debug("  影サイズ: {}", this.shadowRadius);
        TungSahurMod.LOGGER.debug("  生存状態: {}", entity.isAlive());
        TungSahurMod.LOGGER.debug("  削除済み: {}", entity.isRemoved());
        TungSahurMod.LOGGER.debug("  見られている: {}", entity.isBeingWatched());
        TungSahurMod.LOGGER.debug("  メインハンド: {}", entity.getMainHandItem().getDisplayName().getString());
        TungSahurMod.LOGGER.debug("  位置: {}, {}, {}", entity.getX(), entity.getY(), entity.getZ());
        TungSahurMod.LOGGER.debug("  Tick数: {}", entity.tickCount);
        TungSahurMod.LOGGER.debug("===============================");
    }
}