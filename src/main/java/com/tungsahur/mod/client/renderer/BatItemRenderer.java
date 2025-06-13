package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BatItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final ItemRenderer itemRenderer;

    public BatItemRenderer() {
        super(null, null);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {

        poseStack.pushPose();

        // コンテキスト別の特殊レンダリング
        switch (displayContext) {
            case GUI -> renderGuiEffects(poseStack, stack);
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND ->
                    renderFirstPersonEffects(poseStack, stack, displayContext);
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
                    renderThirdPersonEffects(poseStack, stack, displayContext);
            case GROUND -> renderGroundEffects(poseStack, stack);

            case HEAD -> renderHeadEffects(poseStack, stack);
            default -> {}
        }

        // 基本アイテムレンダリング
        this.itemRenderer.renderStatic(stack, displayContext, packedLight, packedOverlay,
                poseStack, buffer, null, 0);

        poseStack.popPose();

        // クライアント側パーティクル効果
        spawnClientParticles(stack, displayContext);
    }

    private void renderGuiEffects(PoseStack poseStack, ItemStack stack) {
        long time = System.currentTimeMillis();

        // GUI での回転
        float rotation = (time % 8000) / 8000.0F * 360.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 0.1F));

        // 浮遊効果
        float bob = (float) Math.sin(time * 0.003F) * 0.02F;
        poseStack.translate(0, bob, 0);

        // 恐怖レベルに応じた振動
        if (hasProperty(stack, "fear_level") && getProperty(stack, "fear_level") > 0.5F) {
            float shake = (float) Math.sin(time * 0.05F) * 0.01F;
            poseStack.translate(shake, shake * 0.5F, 0);
        }
    }

    private void renderFirstPersonEffects(PoseStack poseStack, ItemStack stack, ItemDisplayContext context) {
        long time = System.currentTimeMillis();

        // 一人称視点での威圧感演出
        float shake = (float) Math.sin(time * 0.02F) * 0.001F;

        // チャージ状態での強化演出
        if (hasProperty(stack, "charged") && getProperty(stack, "charged") > 0.5F) {
            shake *= 3.0F;

            // チャージ中のスケール変化
            float scale = (float) (1.0F + Math.sin(time * 0.03F) * 0.02F);
            poseStack.scale(scale, scale, scale);
        }

        poseStack.translate(shake, shake * 0.5F, 0);

        // 血痕レベルでの色調変化（微細な位置調整で表現）
        float bloodLevel = getProperty(stack, "blood_level");
        if (bloodLevel > 0.3F) {
            float bloodShake = (float) Math.sin(time * 0.01F) * bloodLevel * 0.001F;
            poseStack.translate(bloodShake, 0, 0);
        }
    }

    private void renderThirdPersonEffects(PoseStack poseStack, ItemStack stack, ItemDisplayContext context) {
        long time = System.currentTimeMillis();

        // 三人称視点での迫力演出
        float shake = (float) Math.sin(time * 0.015F) * 0.002F;

        // 攻撃準備状態での演出強化
        if (hasProperty(stack, "charged") && getProperty(stack, "charged") > 0.5F) {
            shake *= 4.0F;

            // 威圧的なスケール変化
            float scale = (float) (1.02F + Math.sin(time * 0.025F) * 0.03F);
            poseStack.scale(scale, scale, scale);
        }

        poseStack.translate(shake, shake, shake * 0.5F);
    }

    private void renderGroundEffects(PoseStack poseStack, ItemStack stack) {
        long time = System.currentTimeMillis();

        // 地面での不気味な演出
        float rotation = (time % 10000) / 10000.0F * 360.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 0.2F));

        // 浮遊感
        float hover = (float) Math.sin(time * 0.002F) * 0.05F;
        poseStack.translate(0, hover, 0);

        // 恐怖オーラでの振動
        if (hasProperty(stack, "fear_level") && getProperty(stack, "fear_level") > 0.3F) {
            float shake = (float) Math.sin(time * 0.04F) * 0.01F;
            poseStack.translate(shake, 0, shake);
        }
    }

    private void renderFrameEffects(PoseStack poseStack, ItemStack stack) {
        // アイテムフレーム内での展示効果
        long time = System.currentTimeMillis();

        // ゆっくりとした回転
        float rotation = (time % 20000) / 20000.0F * 360.0F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation * 0.1F));

        // 微細な脈動
        float pulse = (float) (1.0F + Math.sin(time * 0.001F) * 0.01F);
        poseStack.scale(pulse, pulse, pulse);
    }

    private void renderHeadEffects(PoseStack poseStack, ItemStack stack) {
        // 頭装備時の効果
        long time = System.currentTimeMillis();

        // 重厚感のある配置
        poseStack.translate(0, -0.1, 0);

        // 微細な振動で威圧感
        float shake = (float) Math.sin(time * 0.01F) * 0.001F;
        poseStack.translate(shake, 0, shake);
    }

    private void spawnClientParticles(ItemStack stack, ItemDisplayContext context) {
        if (!Minecraft.getInstance().level.isClientSide) return;

        // パーティクル効果は控えめに（クライアント負荷軽減）
        if (Minecraft.getInstance().level.getGameTime() % 20 != 0) return;

        // 恐怖レベルに応じたパーティクル
        float fearLevel = getProperty(stack, "fear_level");
        if (fearLevel > 0.5F && context != ItemDisplayContext.GUI) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                double x = minecraft.player.getX() + (minecraft.level.random.nextDouble() - 0.5) * 2.0;
                double y = minecraft.player.getY() + 1.0;
                double z = minecraft.player.getZ() + (minecraft.level.random.nextDouble() - 0.5) * 2.0;

                minecraft.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 0.0, 0.05, 0.0);
            }
        }
    }

    // ヘルパーメソッド
    private boolean hasProperty(ItemStack stack, String property) {
        // ItemPropertiesからプロパティ値を取得
        return true; // 簡略化
    }

    private float getProperty(ItemStack stack, String property) {
        // ItemPropertiesからプロパティ値を取得
        return 0.0F; // 簡略化（実際の実装では適切な値を返す）
    }
}