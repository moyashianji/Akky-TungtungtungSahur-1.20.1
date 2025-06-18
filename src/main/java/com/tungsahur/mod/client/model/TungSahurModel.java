// TungSahurModel.java - 3段階モデル対応版
package com.tungsahur.mod.client.model;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TungSahurModel extends GeoModel<TungSahurEntity> {

    // 3つの形態のモデル
    private static final ResourceLocation STAGE_1_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage1.geo.json");
    private static final ResourceLocation STAGE_2_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage2.geo.json");
    private static final ResourceLocation STAGE_3_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage3.geo.json");

    // 統一されたアニメーションファイル
    private static final ResourceLocation ANIMATION = new ResourceLocation(TungSahurMod.MODID, "animations/tung_sahur.animation.json");

    @Override
    public ResourceLocation getModelResource(TungSahurEntity entity) {
        int stage = entity.getEvolutionStage();
        return switch (stage) {
            case 1 -> STAGE_2_MODEL;  // 2日目
            case 2 -> STAGE_3_MODEL;  // 3日目
            default -> STAGE_1_MODEL; // 1日目
        };
    }

    @Override
    public ResourceLocation getTextureResource(TungSahurEntity entity) {
        // 進化段階に応じたテクスチャを返す
        int stage = entity.getEvolutionStage();
        return switch (stage) {
            case 1 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage2.png");
            case 2 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage3.png");
            default -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
        };
    }

    @Override
    public ResourceLocation getAnimationResource(TungSahurEntity entity) {
        // 統一されたアニメーションファイルを使用
        return ANIMATION;
    }
}