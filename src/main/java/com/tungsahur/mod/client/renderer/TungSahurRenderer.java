package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TungSahurRenderer extends GeoEntityRenderer<TungSahurEntity> {

    // 3つの形態のテクスチャ
    private static final ResourceLocation STAGE_1_TEXTURE = new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
    private static final ResourceLocation STAGE_2_TEXTURE = new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage2.png");
    private static final ResourceLocation STAGE_3_TEXTURE = new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage3.png");

    public TungSahurRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TungSahurModel());
        this.shadowRadius = 0.8F;
    }

    @Override
    public ResourceLocation getTextureLocation(TungSahurEntity entity) {
        int stage = entity.getEvolutionStage();
        return switch (stage) {
            case 1 -> STAGE_2_TEXTURE;
            case 2 -> STAGE_3_TEXTURE;
            default -> STAGE_1_TEXTURE;
        };
    }

    @Override
    public void render(TungSahurEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {

        // スケール適用
        float scale = entity.getScaleFactor();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        // 見られている時の効果
        if (entity.isBeingWatched()) {
            // 軽微な振動効果
            float shake = (float) Math.sin(entity.tickCount * 0.5F) * 0.01F;
            poseStack.translate(shake, 0, shake);
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected float getDeathMaxRotation(TungSahurEntity entityLivingBaseIn) {
        return 0.0F;
    }
}