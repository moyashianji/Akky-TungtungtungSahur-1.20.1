// TungSahurRenderer.java - 完全対応版
package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TungSahurRenderer extends GeoEntityRenderer<TungSahurEntity> {

    public TungSahurRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TungSahurModel());
        this.shadowRadius = 0.7F; // 基本影サイズ
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

        // デバッグ情報をログに出力（100tick毎）
        if (entity.tickCount % 100 == 0) {
            TungSahurMod.LOGGER.debug("TungSahurレンダリング中: Day={}, Scale={}, 影サイズ={}",
                    entity.getDayNumber(), scaleFactor, this.shadowRadius);
        }

        // 親クラスのレンダリング実行
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    /**
     * バット1本のみ装備を保証する（2本持ち防止）
     */
    private void ensureSingleBatIsEquipped(TungSahurEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();

        // メインハンドにバットがない場合、日数に応じたバットを装備
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            ItemStack dayBat = createDayBat(entity.getDayNumber());
            entity.setItemInHand(InteractionHand.MAIN_HAND, dayBat);
        }

        // オフハンドは常に空にする（バット2本持ち防止）
        if (!offHand.isEmpty()) {
            entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * 日数に応じたバットアイテムを作成
     */
    private ItemStack createDayBat(int dayNumber) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        // 基本タグ設定
        tag.putInt("DayNumber", dayNumber);
        tag.putBoolean("EntityBat", true);
        tag.putString("CustomModelData", "tungsahur_day_" + dayNumber);
        tag.putBoolean("Unbreakable", true); // エンティティ用バットは壊れない

        // 日数に応じた見た目調整
        switch (dayNumber) {
            case 1:
                tag.putString("DisplayName", "TungSahur's Bat (Day 1)");
                tag.putInt("HideFlags", 63); // すべてのフラグを非表示
                break;
            case 2:
                tag.putString("DisplayName", "TungSahur's Enhanced Bat (Day 2)");
                tag.putInt("HideFlags", 63);
                tag.putBoolean("Enchanted", true); // 光る効果
                break;
            case 3:
                tag.putString("DisplayName", "TungSahur's Ultimate Bat (Day 3)");
                tag.putInt("HideFlags", 63);
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 999); // 特別なモデル
                break;
        }

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
        // 日数が高いほど暗闇でも少し明るく見える
        int baseLightLevel = super.getBlockLightLevel(entity, pos);
        int dayBonus = entity.getDayNumber() - 1; // 0, 1, 2のボーナス

        return Math.min(15, baseLightLevel + dayBonus);
    }




    /**
     * パフォーマンス最適化のための可視性チェック
     */
    @Override
    public boolean shouldRender(TungSahurEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        // 基本の可視性チェック
        if (!super.shouldRender(entity, frustum, x, y, z)) {
            return false;
        }

        // 距離による詳細度調整
        double distanceSquared = entity.distanceToSqr(x, y, z);
        if (distanceSquared > 1024.0D) { // 32ブロック以上
            // 遠距離では簡略化されたレンダリング
            return entity.tickCount % 3 == 0; // 3フレームに1回のみ更新
        }

        return true;
    }
}