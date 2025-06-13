package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class TungBatProjectileRenderer extends EntityRenderer<TungBatProjectile> {
    private final ItemRenderer itemRenderer;
    private final ItemStack batStack;

    public TungBatProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
    }

    @Override
    public void render(TungBatProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // 回転アニメーション
        float rotation = entity.tickCount + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation * 15.0F));

        // スケール調整
        poseStack.scale(1.2F, 1.2F, 1.2F);

        // バットアイテムをレンダリング
        this.itemRenderer.renderStatic(batStack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer,
                entity.level(), entity.getId());

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TungBatProjectile entity) {
        // アイテムレンダラーが使用するため、ここでは不要
        return null;
    }
}