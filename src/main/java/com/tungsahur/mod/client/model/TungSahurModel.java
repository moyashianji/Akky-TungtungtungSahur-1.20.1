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

        // デバッグ用：モデルパスをログに出力（初回とモデル変更時のみ）
        if (entity.tickCount <= 1 || (entity.tickCount % 100 == 0 && TungSahurMod.isDebugMode())) {
            TungSahurMod.LOGGER.debug("TungSahur Day {} -> モデル: {}", dayNumber, modelPath);
        }

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

        // デバッグ用：テクスチャパスをログに出力（初回とテクスチャ変更時のみ）
        if (entity.tickCount <= 1 || (entity.tickCount % 100 == 0 && TungSahurMod.isDebugMode())) {
            TungSahurMod.LOGGER.debug("TungSahur Day {} -> テクスチャ: {}", dayNumber, texturePath);
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

    /**
     * 特定の状態に応じたモデル調整
     */
    public ResourceLocation getModelForState(TungSahurEntity entity) {
        // 特殊状態での代替モデル（将来の拡張用）
        if (entity.isCurrentlyJumping()) {
            // ジャンプ中専用モデル（あれば）
            ResourceLocation jumpModel = new ResourceLocation(TungSahurMod.MODID,
                    "geo/tung_sahur_day" + entity.getDayNumber() + "_jumping.geo.json");
            return jumpModel;
        }

        if (entity.isWallClimbing()) {
            // 壁登り中専用モデル（あれば）
            ResourceLocation climbModel = new ResourceLocation(TungSahurMod.MODID,
                    "geo/tung_sahur_day" + entity.getDayNumber() + "_climbing.geo.json");
            return climbModel;
        }

        // 通常モデルを返す
        return getModelResource(entity);
    }

    /**
     * 特定の状態に応じたテクスチャ調整
     */
    public ResourceLocation getTextureForState(TungSahurEntity entity) {
        String basePath = "textures/entity/tung_sahur_day" + entity.getDayNumber();

        // 状態に応じたテクスチャ変更
        if (entity.isBeingWatched()) {
            // 見られている時は目が光る
            return new ResourceLocation(TungSahurMod.MODID, basePath + "_watched.png");
        }

        if (entity.isCurrentlyAttacking()) {
            // 攻撃中は少し赤っぽく
            return new ResourceLocation(TungSahurMod.MODID, basePath + "_attacking.png");
        }

        if (entity.isWallClimbing()) {
            // 壁登り中は集中した表情
            return new ResourceLocation(TungSahurMod.MODID, basePath + "_climbing.png");
        }

        // 通常テクスチャを返す
        return getTextureResource(entity);
    }

    /**
     * 日数による詳細なモデル切り替え
     */
    public boolean shouldUseAlternativeModel(TungSahurEntity entity) {
        // 特定の条件下で代替モデルを使用するかの判定

        // 体力が低い時
        if (entity.getHealth() < entity.getMaxHealth() * 0.3F) {
            return true;
        }

        // 長時間見られている時
        if (entity.isBeingWatched() && entity.tickCount % 40 < 20) {
            return true; // 点滅効果
        }

        return false;
    }

    /**
     * スケールファクターの取得（レンダラーとの連携用）
     */
    public float getModelScale(TungSahurEntity entity) {
        float baseScale = entity.getScaleFactor();

        // 状態による微調整
        if (entity.isCurrentlyJumping()) {
            baseScale *= 1.05F; // ジャンプ中は少し大きく
        }

        if (entity.isBeingWatched()) {
            baseScale *= 0.98F; // 見られている時は少し縮む
        }

        return baseScale;
    }

    /**
     * アニメーションの詳細制御
     */
    public boolean shouldOverrideAnimation(TungSahurEntity entity, String animationName) {
        // 特定の条件下でアニメーションをオーバーライドするか判定

        // 見られている時のidleアニメーション変更
        if (entity.isBeingWatched() && "idle".equals(animationName)) {
            return true; // 別のidleアニメーションを使用
        }

        // 壁登り中の特殊アニメーション
        if (entity.isWallClimbing() && ("walk".equals(animationName) || "sprint".equals(animationName))) {
            return true; // climbingアニメーションを優先
        }

        return false;
    }

    /**
     * モデル読み込みエラー時のフォールバック
     */
    public ResourceLocation getFallbackModel(TungSahurEntity entity) {
        // エラー時には1日目モデルを使用
        TungSahurMod.LOGGER.warn("TungSahurモデル読み込みエラー、フォールバックモデルを使用: Day {}", entity.getDayNumber());
        return DAY_1_MODEL;
    }

    /**
     * テクスチャ読み込みエラー時のフォールバック
     */
    public ResourceLocation getFallbackTexture(TungSahurEntity entity) {
        // エラー時には1日目テクスチャを使用
        TungSahurMod.LOGGER.warn("TungSahurテクスチャ読み込みエラー、フォールバックテクスチャを使用: Day {}", entity.getDayNumber());
        return new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_sahur_stage1.png");
    }
}