// SahurItemLayer.java - DynamicGeoEntityRenderer対応版
package com.tungsahur.mod.client.renderer.layers;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;

import javax.annotation.Nullable;

public class SahurItemLayer<T extends TungSahurEntity & GeoAnimatable> extends BlockAndItemGeoLayer<T> {
    private final DynamicGeoEntityRenderer<T> renderer;

    public SahurItemLayer(DynamicGeoEntityRenderer<T> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Nullable
    @Override
    protected ItemStack getStackForBone(GeoBone bone, T animatable) {
        // ボーン名に基づいてアイテムを決定
        String boneName = bone.getName();
        System.out.println("getStackForBone呼び出し: ボーン名=" + boneName);

        // itemMainHandボーンのみでアイテムを表示（重複を防ぐ）
        if (boneName.equals("itemMainHand")) {
            ItemStack mainHandItem = animatable.getMainHandItem();
            System.out.println("itemMainHandボーン: メインハンドアイテム=" + mainHandItem.getDisplayName().getString());
            return mainHandItem;
        }

        // 他のボーンではアイテムを表示しない
        return null;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
        String boneName = bone.getName();

        // 全てのアイテムでNONEを返して、元のモデル表示設定を完全に無効化
        return ItemDisplayContext.NONE;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                      MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {

        String boneName = bone.getName();
        System.out.println("renderStackForBone: ボーン=" + boneName + ", アイテム=" + stack.getDisplayName().getString());

        // 完全に独自の変形のみを適用するため、親クラスの変形を回避
        poseStack.pushPose();

        // バット専用の調整
        if (stack.is(ModItems.TUNG_SAHUR_BAT.get())) {
            adjustBatTransformOnly(poseStack, boneName, animatable);

            // 独自レンダリング - 親クラスのレンダリングを使わずに直接レンダリング
            renderBatDirectly(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
        } else {
            // 一般的なアイテムの調整
            adjustGeneralItemTransform(poseStack, boneName, stack);
            // 一般アイテムのみ親クラスのレンダリングを使用
            super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    /**
     * バット専用の直接レンダリング
     */
    private void renderBatDirectly(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                   MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        // MinecraftのItemRendererを直接使用してバットをレンダリング
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.renderer.entity.ItemRenderer itemRenderer = minecraft.getItemRenderer();

        // バットを独自の変形でレンダリング
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, packedOverlay, poseStack, bufferSource, animatable.level(), 0);
    }

    /**
     * バット専用の変形調整（元のモデル表示を完全に無視）
     */
    private void adjustBatTransformOnly(PoseStack poseStack, String boneName, T animatable) {
        System.out.println("バット独自調整中のボーン名: " + boneName);

        if (boneName.equals("itemMainHand")) {
            System.out.println("itemMainHandボーンでバット独自調整実行中");

            // 日数に応じたスケール調整（少し小さくする）
            float entityScale = animatable.getScaleFactor();
            float itemScale = calculateItemScale(animatable.getMainHandItem(), animatable, entityScale) * 0.8f; // スケールを少し小さく
            poseStack.scale(itemScale, itemScale, itemScale);

            // 完全に独自の変形（元のモデル設定を完全無視）
            // バットをもっと下の位置に配置
            poseStack.translate(-0.1, -0.4, 0.1); // バット全体をもっと下に移動

            // バットを上下逆にするためにX軸で180度回転を追加
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F)); // 上下逆転
            // 持ち手のもっと先端を持つためにバットをさらに移動
            poseStack.translate(0.0, -0.5, 0.0); // バットをもっと下に移動して持ち手の先端を手の位置に
            poseStack.mulPose(Axis.XP.rotationDegrees(20.0F)); // 前方斜め下向き
            poseStack.mulPose(Axis.YP.rotationDegrees(10.0F)); // 少し外向き
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F)); // Z軸回転なし

            // 日数に応じた威圧的な調整
            int dayNumber = animatable.getDayNumber();
            System.out.println("日数: " + dayNumber);
            if (dayNumber >= 2) {
                poseStack.mulPose(Axis.XP.rotationDegrees(10.0F)); // より下向き
                poseStack.translate(0.02, -0.05, 0.0);
            }
            if (dayNumber >= 3) {
                poseStack.mulPose(Axis.XP.rotationDegrees(15.0F)); // さらに下向き
                poseStack.mulPose(Axis.YP.rotationDegrees(5.0F)); // より外向き
                poseStack.translate(0.02, -0.05, 0.0);
            }
        }
    }

    /**
     * 一般的なアイテムの変形調整
     */
    private void adjustGeneralItemTransform(PoseStack poseStack, String boneName, ItemStack stack) {
        System.out.println("一般アイテム調整中のボーン名: " + boneName);

        if (boneName.equals("itemMainHand")) {
            System.out.println("itemMainHandボーンで一般アイテム調整実行中");

            // 参考コードと同様の基本変形
            poseStack.scale(1.0F, 1.0F, 1.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
    }

    /**
     * アイテムのスケール計算
     */
    private float calculateItemScale(ItemStack stack, T animatable, float entityScale) {
        if (stack.is(ModItems.TUNG_SAHUR_BAT.get())) {
            // バットは日数に応じてサイズを調整
            int dayNumber = animatable.getDayNumber();
            float baseBatScale = 1.0f;

            switch (dayNumber) {
                case 2:
                    baseBatScale = 1.2f; // 2日目は少し大きく
                    break;
                case 3:
                    baseBatScale = 1.4f; // 3日目は更に大きく
                    break;
                default:
                    baseBatScale = 1.0f; // 1日目は標準サイズ
                    break;
            }

            return baseBatScale * entityScale;
        }

        // その他のアイテムは標準スケール
        return 1.0f * entityScale;
    }
}