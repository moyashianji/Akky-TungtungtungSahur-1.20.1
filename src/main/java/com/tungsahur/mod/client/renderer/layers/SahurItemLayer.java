// SahurItemLayer.java - 修正版アイテムレイヤー
package com.tungsahur.mod.client.renderer.layers;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
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
    private final GeoEntityRenderer<T> renderer;

    public SahurItemLayer(GeoEntityRenderer<T> renderer) {
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
        if (mainHandItem.isEmpty() || !mainHandItem.is(ModItems.TUNG_SAHUR_BAT.get())) {
            mainHandItem = createBatForEntity(animatable);
            // エンティティにバットを装備させる
            animatable.setItemSlot(EquipmentSlot.MAINHAND, mainHandItem);
        }

        // 手に持つアイテムの判定
        return switch (bone.getName()) {
            case "right_hand", "rightArm", "right_arm", "arm_right", "armRight" -> {
                // バットの表示を強制
                if (!mainHandItem.isEmpty() && mainHandItem.is(ModItems.TUNG_SAHUR_BAT.get())) {
                    enhanceBatForDisplay(mainHandItem, animatable);
                    yield mainHandItem;
                }
                yield mainHandItem;
            }
            case "left_hand", "leftArm", "left_arm", "arm_left", "armLeft" -> {
                // オフハンドアイテム
                if (!offhandItem.isEmpty() && !(offhandItem.getItem() instanceof ShieldItem)) {
                    yield offhandItem;
                }
                yield ItemStack.EMPTY;
            }
            case "head", "helmet" -> {
                // ヘッドアイテム
                yield headItem;
            }
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * エンティティに応じたバットアイテムを作成
     */
    private ItemStack createBatForEntity(T animatable) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        int evolutionStage = animatable.getEvolutionStage();
        batStack.getTag().putInt("TungSahurStage", evolutionStage);
        batStack.getTag().putBoolean("TungSahurOwned", true);
        batStack.getTag().putBoolean("ForceDisplay", true);
        batStack.getTag().putBoolean("AlwaysVisible", true);

        return batStack;
    }

    /**
     * バットの表示を強化
     */
    private void enhanceBatForDisplay(ItemStack batStack, T animatable) {
        if (batStack.hasTag()) {
            CompoundTag tag = batStack.getTag();
            tag.putBoolean("TungSahurOwned", true);
            tag.putBoolean("ForceDisplay", true);
            tag.putBoolean("AlwaysVisible", true);
            tag.putBoolean("ForceRender", true);
            tag.putInt("TungSahurStage", animatable.getEvolutionStage());

            // 進化段階に応じた視覚的強化
            switch (animatable.getEvolutionStage()) {
                case 1 -> {
                    tag.putBoolean("Bloodstained", true);
                    tag.putInt("BloodLevel", 1);
                }
                case 2 -> {
                    tag.putBoolean("Cursed", true);
                    tag.putBoolean("DarkEnergy", true);
                    tag.putInt("BloodLevel", 3);
                }
            }
        }
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
        return switch (bone.getName()) {
            case "right_hand", "rightArm", "right_arm", "arm_right", "armRight" -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case "left_hand", "leftArm", "left_arm", "arm_left", "armLeft" -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            case "head", "helmet" -> ItemDisplayContext.HEAD;
            default -> ItemDisplayContext.NONE;
        };
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                      MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        if (stack.isEmpty()) return;

        // バットの特殊レンダリング処理
        if (stack.is(ModItems.TUNG_SAHUR_BAT.get())) {
            poseStack.pushPose();

            // 進化段階に応じたエフェクト
            int stage = animatable.getEvolutionStage();

            // 基本的な位置調整
            switch (bone.getName()) {
                case "right_hand", "rightArm", "right_arm", "arm_right", "armRight" -> {
                    poseStack.translate(0.0, -0.1, 0.0);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                    poseStack.mulPose(Axis.YP.rotationDegrees(0));

                    // 進化段階による特殊効果
                    if (stage >= 1) {
                        // 威圧感のある振動
                        float shake = (float) Math.sin(animatable.tickCount * 0.1F) * 0.005F;
                        poseStack.translate(shake, shake, 0);
                    }

                    if (stage >= 2) {
                        // さらに強い振動とサイズ調整
                        float pulse = 1.0F + (float) Math.sin(animatable.tickCount * 0.05F) * 0.03F;
                        poseStack.scale(pulse, pulse, pulse);
                    }
                }
            }

            poseStack.popPose();
        }

        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
    }
}