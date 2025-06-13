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

    private static final ResourceLocation ANIMATION = new ResourceLocation(TungSahurMod.MODID, "animations/tung_sahur.animation.json");

    @Override
    public ResourceLocation getModelResource(TungSahurEntity entity) {
        int stage = entity.getEvolutionStage();
        return switch (stage) {
            case 1 -> STAGE_2_MODEL;
            case 2 -> STAGE_3_MODEL;
            default -> STAGE_1_MODEL;
        };
    }

    @Override
    public ResourceLocation getTextureResource(TungSahurEntity entity) {
        // レンダラーでテクスチャは処理されるため、ここでは基本テクスチャを返す
        return new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TungSahurEntity entity) {
        return ANIMATION;
    }
}