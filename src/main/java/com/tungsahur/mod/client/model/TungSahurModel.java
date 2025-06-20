// TungSahurModel.java - 日数対応版
package com.tungsahur.mod.client.model;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TungSahurModel extends GeoModel<TungSahurEntity> {

    // 3つの日数のモデル（1日目をデフォルトに）
    private static final ResourceLocation DAY_1_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage1.geo.json");
    private static final ResourceLocation DAY_2_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage2.geo.json");
    private static final ResourceLocation DAY_3_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage3.geo.json");

    // 統一されたアニメーションファイル
    private static final ResourceLocation ANIMATION = new ResourceLocation(TungSahurMod.MODID, "animations/tung_sahur.animation.json");

    public TungSahurModel() {
        TungSahurMod.LOGGER.info("TungSahurModel初期化完了 - 日数ベースモデル対応");
    }

    @Override
    public ResourceLocation getModelResource(TungSahurEntity entity) {
        int dayNumber = entity.getDayNumber();

        ResourceLocation modelPath = switch (dayNumber) {
            case 2 -> DAY_2_MODEL;  // 2日目（中くらいサイズ）
            case 3 -> DAY_3_MODEL;  // 3日目（大きいサイズ）
            default -> DAY_1_MODEL; // 1日目（普通サイズ）- デフォルト
        };


        return modelPath;
    }

    @Override
    public ResourceLocation getTextureResource(TungSahurEntity entity) {
        int dayNumber = entity.getDayNumber();

        ResourceLocation texturePath = switch (dayNumber) {
            case 2 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage2.png");
            case 3 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage3.png");
            default -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
        };



        return texturePath;
    }

    @Override
    public ResourceLocation getAnimationResource(TungSahurEntity entity) {
        // 初回のみアニメーションパスをログ出力
        if (entity.tickCount <= 1) {
            TungSahurMod.LOGGER.info("TungSahur アニメーション: {}", ANIMATION);
        }

        return ANIMATION;
    }


}