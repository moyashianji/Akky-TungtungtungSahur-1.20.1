package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.model.TungSahurModel;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

        // バットが装備されていない場合は強制的に装備
        ensureBatIsVisible(entity);

        poseStack.pushPose();

        // スケール適用
        float scale = entity.getScaleFactor();
        poseStack.scale(scale, scale, scale);

        // 見られている時の効果
        if (entity.isBeingWatched()) {
            float shake = (float) Math.sin(entity.tickCount * 0.5F) * 0.01F;
            poseStack.translate(shake, 0, shake);
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    /**
     * バットが見えるように強制的に確認
     */
    private void ensureBatIsVisible(TungSahurEntity entity) {
        ItemStack mainHand = entity.getMainHandItem();

        // クライアント側でもバットが装備されていることを確認
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            // クライアント側でバットを設定（表示用）
            ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

            // 進化段階に応じてバットを強化
            enhanceBatForDisplay(batStack, entity.getEvolutionStage());

            // 強制的に装備（クライアント側表示用）
            entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, batStack);
        }
    }

    /**
     * 表示用バット強化
     */
    private void enhanceBatForDisplay(ItemStack batStack, int evolutionStage) {
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        batStack.getTag().putInt("TungSahurStage", evolutionStage);

        switch (evolutionStage) {
            case 1 -> {
                batStack.getTag().putBoolean("Bloodstained", true);
                batStack.getTag().putInt("KillCount", 10);
                batStack.getTag().putInt("BloodLevel", 1);
            }
            case 2 -> {
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putInt("KillCount", 25);
                batStack.getTag().putBoolean("DarkEnergy", true);
                batStack.getTag().putInt("BloodLevel", 3);
                batStack.getTag().putBoolean("SoulBound", true);
            }
        }
    }

    @Override
    protected float getDeathMaxRotation(TungSahurEntity entityLivingBaseIn) {
        return 0.0F;
    }
}
