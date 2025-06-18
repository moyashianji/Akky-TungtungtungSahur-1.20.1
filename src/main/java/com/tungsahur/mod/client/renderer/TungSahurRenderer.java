// TungSahurRenderer.java - 修正版レンダラー
package com.tungsahur.mod.client.renderer;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.client.renderer.layers.SahurItemLayer;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.TungSahurMod;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

import javax.annotation.Nullable;

public class TungSahurRenderer extends GeoEntityRenderer<TungSahurEntity> {

    public TungSahurRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TungSahurModel());
        this.shadowRadius = 0.8f;

        // SahurItemLayerを追加してバットの表示を処理
        this.addRenderLayer(new SahurItemLayer<>(this));
    }

    @Override
    public RenderType getRenderType(TungSahurEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, TungSahurEntity entity, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, float red,
                          float green, float blue, float alpha) {

        // バットが装備されているかチェックし、されていなければ強制的に装備
        ensureBatIsEquipped(entity);

        // 進化段階に応じたスケール調整
        float scale = entity.getScaleFactor();
        poseStack.scale(scale, scale, scale);

        // 見られている時の震え効果
        if (entity.isBeingWatched()) {
            float shake = (float) Math.sin(entity.tickCount * 0.5F) * 0.01F;
            poseStack.translate(shake, 0, shake);
        }

        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * バットが装備されているかを確認し、されていなければ強制的に装備
     */
    private void ensureBatIsEquipped(TungSahurEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();

        // バットが装備されていない、または正しいバットでない場合
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            ItemStack batStack = createBatForEntity(entity);
            entity.setItemSlot(EquipmentSlot.MAINHAND, batStack);
        }
    }

    /**
     * エンティティに応じたバットアイテムを作成
     */
    private ItemStack createBatForEntity(TungSahurEntity entity) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        // 進化段階に応じてバットを強化
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        int evolutionStage = entity.getEvolutionStage();
        batStack.getTag().putInt("TungSahurStage", evolutionStage);
        batStack.getTag().putBoolean("TungSahurOwned", true);
        batStack.getTag().putBoolean("ForceDisplay", true);
        batStack.getTag().putBoolean("AlwaysVisible", true);
        batStack.getTag().putBoolean("ForceRender", true);

        // 進化段階に応じた強化
        switch (evolutionStage) {
            case 1:
                batStack.getTag().putInt("Damage", 15);
                batStack.getTag().putString("BatType", "Enhanced");
                batStack.getTag().putInt("BloodLevel", 1);
                break;
            case 2:
                batStack.getTag().putInt("Damage", 25);
                batStack.getTag().putString("BatType", "Legendary");
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putInt("BloodLevel", 3);
                break;
            default:
                batStack.getTag().putInt("Damage", 8);
                batStack.getTag().putString("BatType", "Normal");
                break;
        }

        return batStack;
    }

    @Override
    protected float getDeathMaxRotation(TungSahurEntity entityLivingBaseIn) {
        return 0.0F;
    }

    /**
     * 死亡時にもバットを表示し続ける
     */
    @Override
    public void render(TungSahurEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        // レンダリング前にバットの装備を確認
        ensureBatIsEquipped(entity);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}