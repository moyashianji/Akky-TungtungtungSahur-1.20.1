// TungSahurRenderer.java - スケール対応完全修正版
package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
            TungSahurMod.LOGGER.debug("TungSahurレンダリング中: Stage={}, Scale={}, 影サイズ={}",
                    entity.getEvolutionStage(), scaleFactor, this.shadowRadius);
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

        // メインハンドにバットがない場合、進化段階に応じたバットを装備
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            ItemStack evolutionBat = createEvolutionBat(entity.getEvolutionStage());
            entity.setItemInHand(InteractionHand.MAIN_HAND, evolutionBat);
        }

        // オフハンドは常に空にする（バット2本持ち防止）
        if (!offHand.isEmpty()) {
            entity.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * 進化段階に応じたバットアイテムを作成
     */
    private ItemStack createEvolutionBat(int evolutionStage) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        // 基本タグ設定
        tag.putInt("EvolutionStage", evolutionStage);
        tag.putBoolean("EntityBat", true);
        tag.putString("CustomModelData", "tung_sahur_bat_stage" + evolutionStage);

        // 進化段階に応じた強化
        switch (evolutionStage) {
            case 0 -> { // 1日目
                tag.putInt("Damage", 8);
                tag.putString("BatType", "Basic");
                tag.putInt("CustomModelData", 100);
            }
            case 1 -> { // 2日目
                tag.putInt("Damage", 15);
                tag.putString("BatType", "Enhanced");
                tag.putInt("BloodLevel", 1);
                tag.putInt("CustomModelData", 101);
            }
            case 2 -> { // 3日目以降
                tag.putInt("Damage", 25);
                tag.putString("BatType", "Legendary");
                tag.putBoolean("Cursed", true);
                tag.putInt("BloodLevel", 3);
                tag.putInt("Enchantment", 1);
                tag.putInt("CustomModelData", 102);
            }
        }

        return batStack;
    }

    @Override
    protected float getDeathMaxRotation(TungSahurEntity entityLivingBaseIn) {
        return 0.0F; // 死亡時の回転を無効
    }

    /**
     * 死亡時にもバットを表示し続ける
     */
    @Override
    public ResourceLocation getTextureLocation(TungSahurEntity entity) {
        // デバッグ用：テクスチャパスをログに出力（初回のみ）
        ResourceLocation texture = super.getTextureLocation(entity);
        if (entity.tickCount == 1) {
            TungSahurMod.LOGGER.info("使用中のテクスチャ: {} (Stage: {})", texture, entity.getEvolutionStage());
        }
        return texture;
    }


}