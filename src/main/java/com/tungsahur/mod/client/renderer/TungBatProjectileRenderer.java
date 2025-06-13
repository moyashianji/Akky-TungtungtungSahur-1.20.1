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
import net.minecraft.core.particles.ParticleTypes;
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

        // より滑らかで迫力のある回転アニメーション
        float totalTime = entity.tickCount + partialTick;
        float speed = (float) entity.getDeltaMovement().length();

        // 速度に応じた回転速度
        float rotationSpeed = 20.0F + speed * 40.0F;

        // 複軸回転でより動的に
        poseStack.mulPose(Axis.YP.rotationDegrees(totalTime * rotationSpeed));
        poseStack.mulPose(Axis.XP.rotationDegrees(totalTime * rotationSpeed * 0.7F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(totalTime * rotationSpeed * 0.3F));

        // 飛行中の振動効果
        float wobble = (float) Math.sin(totalTime * 0.8F) * 0.1F;
        poseStack.translate(wobble, wobble * 0.5F, 0);

        // スケール調整（少し大きくして迫力アップ）
        float scale = 1.4F + (float) Math.sin(totalTime * 0.5F) * 0.1F;
        poseStack.scale(scale, scale, scale);

        // バットアイテムをレンダリング
        this.itemRenderer.renderStatic(batStack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer,
                entity.level(), entity.getId());

        poseStack.popPose();

        // クライアント側でのパーティクル軌跡
        if (entity.level().isClientSide && entity.tickCount % 2 == 0) {
            spawnClientTrailParticles(entity);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void spawnClientTrailParticles(TungBatProjectile entity) {
        // 炎の軌跡
        entity.level().addParticle(ParticleTypes.FLAME,
                entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.2,
                entity.getY() + (entity.level().random.nextDouble() - 0.5) * 0.2,
                entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.2,
                0, 0, 0);

        // 煙の軌跡
        if (entity.tickCount % 3 == 0) {
            entity.level().addParticle(ParticleTypes.SMOKE,
                    entity.getX(), entity.getY(), entity.getZ(),
                    -entity.getDeltaMovement().x * 0.1,
                    -entity.getDeltaMovement().y * 0.1,
                    -entity.getDeltaMovement().z * 0.1);
        }

        // 高速時の追加パーティクル
        double speed = entity.getDeltaMovement().length();
        if (speed > 0.5) {
            entity.level().addParticle(ParticleTypes.CRIT,
                    entity.getX() - entity.getDeltaMovement().x * 0.5,
                    entity.getY() - entity.getDeltaMovement().y * 0.5,
                    entity.getZ() - entity.getDeltaMovement().z * 0.5,
                    0, 0, 0);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TungBatProjectile entity) {
        return null;
    }
}