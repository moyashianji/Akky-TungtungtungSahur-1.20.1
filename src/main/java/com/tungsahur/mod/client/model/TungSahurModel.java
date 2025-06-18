// TungSahurModel.java - Stage1デフォルト対応版
package com.tungsahur.mod.client.model;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TungSahurModel extends GeoModel<TungSahurEntity> {

    // 3つの形態のモデル（stage1をデフォルトに）
    private static final ResourceLocation STAGE_1_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage1.geo.json");
    private static final ResourceLocation STAGE_2_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage2.geo.json");
    private static final ResourceLocation STAGE_3_MODEL = new ResourceLocation(TungSahurMod.MODID, "geo/tung_sahur_stage3.geo.json");

    // 統一されたアニメーションファイル
    private static final ResourceLocation ANIMATION = new ResourceLocation(TungSahurMod.MODID, "animations/tung_sahur.animation.json");

    public TungSahurModel() {
        TungSahurMod.LOGGER.info("TungSahurModel初期化完了 - Stage1デフォルト対応");
    }

    @Override
    public ResourceLocation getModelResource(TungSahurEntity entity) {
        int stage = entity.getEvolutionStage();

        ResourceLocation modelPath = switch (stage) {
            case 2 -> STAGE_3_MODEL;  // 3日目（最終形態）
            case 1 -> STAGE_2_MODEL;  // 2日目（強化形態）
            default -> STAGE_1_MODEL; // 1日目（基本形態）- デフォルト
        };

        // デバッグ用：モデルパスをログに出力（初回のみ）
        if (entity.tickCount <= 1) {
            TungSahurMod.LOGGER.info("TungSahur Stage {} -> モデル: {}", stage, modelPath);
        }

        return modelPath;
    }

    @Override
    public ResourceLocation getTextureResource(TungSahurEntity entity) {
        int stage = entity.getEvolutionStage();

        ResourceLocation texturePath = switch (stage) {
            case 2 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage3.png");
            case 1 -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage2.png");
            default -> new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
        };

        // デバッグ用：テクスチャパスをログに出力（初回のみ）
        if (entity.tickCount <= 1) {
            TungSahurMod.LOGGER.info("TungSahur Stage {} -> テクスチャ: {}", stage, texturePath);
        }

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