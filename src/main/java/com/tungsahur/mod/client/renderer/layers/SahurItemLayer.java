package com.tungsahur.mod.client.renderer.layers;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.DynamicGeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.nbt.CompoundTag;

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
        ItemStack mainHandItem = animatable.getMainHandItem();
        ItemStack offhandItem = animatable.getOffhandItem();
        ItemStack headItem = animatable.getItemBySlot(EquipmentSlot.HEAD);

        // ヘッドアーマーの特殊処理
        if (headItem.getItem() instanceof ArmorItem armorItem) {
            if (armorItem.getEquipmentSlot().getName().equals("head")) {
                headItem = ItemStack.EMPTY;
            }
        }

        // sahurが常にバットを持っているように強制
        if (mainHandItem.isEmpty()) {
            mainHandItem = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
            // エンティティにバットを装備させる
            animatable.setItemSlot(EquipmentSlot.MAINHAND, mainHandItem);
        }

        switch (bone.getName()) {
            case "itemMainHand":
                return mainHandItem;
            case "itemOffHand":
                return offhandItem;
            case "itemMainHand2":
            case "itemMainHand3":
                return mainHandItem;
            case "itemOffHand2":
            case "itemOffHand3":
                return offhandItem;
            case "Head":
            case "armorHead":
                return headItem;
            default:
                return null;
        }
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
        switch (bone.getName()) {
            case "itemOffHand":
            case "itemMainHand":
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case "itemOffHand2":
            case "itemOffHand3":
            case "itemMainHand2":
            case "itemMainHand3":
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case "Head":
            case "armorHead":
                return ItemDisplayContext.HEAD;
            default:
                return ItemDisplayContext.NONE;
        }
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        ItemStack mainHandItem = animatable.getMainHandItem();
        ItemStack offhandItem = animatable.getOffhandItem();
        ItemStack headItem = animatable.getItemBySlot(EquipmentSlot.HEAD);

        // バットの進化段階に応じたスケールファクター
        float scaleFactor = getScaleFactorForEvolution(animatable);

        if (stack == mainHandItem || stack == offhandItem) {
            poseStack.scale(scaleFactor, scaleFactor, scaleFactor);

            // バット専用の回転設定
            if (stack.is(ModItems.TUNG_SAHUR_BAT.get())) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(15.0F)); // 少し角度を付ける

                // 進化段階に応じた位置調整
                adjustBatPosition(poseStack, animatable);
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));

                if (stack.getItem() instanceof ShieldItem) {
                    if (stack == offhandItem) {
                        poseStack.translate(0.0F, -0.25F, 0.0F);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                    }
                }
            }
        }
        else if(stack == headItem){
            scaleFactor = 0.625F;
            poseStack.translate(0.0F, 0.25F, 0.0F);
            poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        }

        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
    }

    /**
     * 進化段階に応じたスケールファクターを取得
     */
    private float getScaleFactorForEvolution(T animatable) {
        int evolutionStage = animatable.getEvolutionStage();
        return switch (evolutionStage) {
            case 1 -> 1.2F; // 少し大きく
            case 2 -> 1.5F; // さらに大きく
            default -> 1.0F; // 通常サイズ
        };
    }

    /**
     * バットの位置を進化段階に応じて調整
     */
    private void adjustBatPosition(PoseStack poseStack, T animatable) {
        int evolutionStage = animatable.getEvolutionStage();

        switch (evolutionStage) {
            case 1:
                // Stage 2: 少し前に出す
                poseStack.translate(0.0F, 0.1F, 0.2F);
                break;
            case 2:
                // Stage 3: さらに前に出して威圧感を演出
                poseStack.translate(0.0F, 0.2F, 0.4F);
                break;
            default:
                // Stage 1: デフォルト位置
                break;
        }
    }
}