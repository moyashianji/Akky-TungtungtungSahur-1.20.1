// TungSahurJumpscareOverlay.java - エンティティモデル表示ジャンプスケア
package com.tungsahur.mod.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TungSahurMod.MODID, value = Dist.CLIENT)
public class TungSahurJumpscareOverlay {

    // ジャンプスケアの状態管理
    private static boolean isJumpscareActive = false;
    private static long jumpscareStartTime = 0;
    private static final long JUMPSCARE_DURATION = 1500; // 1.5秒間（一瞬）
    private static TungSahurEntity jumpscareEntity = null;

    // アニメーション用の値
    private static float entityScale = 1.0F;
    private static float entityRotationY = 0.0F;
    private static float screenFlashIntensity = 0.0F;

    /**
     * ジャンプスケアを開始
     */
    public static void startJumpscare(TungSahurEntity entity) {
        TungSahurMod.LOGGER.info("=== ジャンプスケア開始 ===");
        TungSahurMod.LOGGER.info("エンティティ: {}", entity);
        TungSahurMod.LOGGER.info("エンティティnull?: {}", entity == null);

        isJumpscareActive = true;
        jumpscareStartTime = System.currentTimeMillis();
        jumpscareEntity = entity;

        // 初期値設定
        entityScale = 0.1F; // 小さく開始
        entityRotationY = 0.0F;
        screenFlashIntensity = 1.0F; // 最大の明度で開始

        TungSahurMod.LOGGER.info("ジャンプスケア状態: active={}, startTime={}", isJumpscareActive, jumpscareStartTime);
        TungSahurMod.LOGGER.info("TungSahurジャンプスケア開始完了 - 持続時間: {}ms", JUMPSCARE_DURATION);
        TungSahurMod.LOGGER.info("====================");
    }

    /**
     * ジャンプスケアが現在アクティブかどうか
     */
    public static boolean isJumpscareActive() {
        if (isJumpscareActive && System.currentTimeMillis() - jumpscareStartTime > JUMPSCARE_DURATION) {
            isJumpscareActive = false;
            jumpscareEntity = null;
            TungSahurMod.LOGGER.info("TungSahurジャンプスケア終了");
        }
        return isJumpscareActive;
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!isJumpscareActive() || jumpscareEntity == null) {
            return;
        }

        TungSahurMod.LOGGER.debug("GUI オーバーレイ描画中: overlay={}", event.getOverlay());

        // 最前面に描画（全てのGUIの上）
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
            TungSahurMod.LOGGER.debug("ジャンプスケアエンティティ描画開始");
            renderJumpscareEntity(event.getGuiGraphics());
        }
    }

    /**
     * ジャンプスケアエンティティのメイン描画処理
     */
    private static void renderJumpscareEntity(GuiGraphics guiGraphics) {
        TungSahurMod.LOGGER.debug("=== renderJumpscareEntity開始 ===");

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        TungSahurMod.LOGGER.debug("画面サイズ: {}x{}", screenWidth, screenHeight);

        long elapsedTime = System.currentTimeMillis() - jumpscareStartTime;
        float progress = (float) elapsedTime / JUMPSCARE_DURATION;

        TungSahurMod.LOGGER.debug("経過時間: {}ms, 進行度: {}", elapsedTime, progress);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        try {
            // アニメーション値を計算
            updateAnimationValues(progress);

            // 背景フラッシュ効果
            renderBackgroundFlash(guiGraphics, screenWidth, screenHeight);

            // TungSahurエンティティを画面中央に描画
            renderTungSahurEntity(guiGraphics, screenWidth, screenHeight, progress);

            TungSahurMod.LOGGER.debug("ジャンプスケア描画完了");

        } catch (Exception e) {
            TungSahurMod.LOGGER.error("ジャンプスケアエンティティ描画エラー: ", e);
        } finally {
            poseStack.popPose();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        TungSahurMod.LOGGER.debug("=== renderJumpscareEntity完了 ===");
    }

    /**
     * アニメーション値を更新
     */
    private static void updateAnimationValues(float progress) {
        // エンティティスケールアニメーション（顔面アップ用）
        if (progress < 0.2f) {
            // 0-20%: 顔面が徐々にフェードイン
            entityScale = Mth.lerp(progress / 0.2f, 3.0F, 5.0F);
        } else if (progress < 0.8f) {
            // 20-80%: 最大サイズで維持（顔面アップ）
            entityScale = 5.0F;
        } else {
            // 80-100%: フェードアウト
            entityScale = Mth.lerp((progress - 0.8f) / 0.2f, 5.0F, 4.0F);
        }

        // 回転アニメーション（微細な揺れ）
        entityRotationY = (float) Math.sin(System.currentTimeMillis() * 0.02) * 2.0F;

        // 画面フラッシュ強度（黒色）
        if (progress < 0.3f) {
            screenFlashIntensity = Mth.lerp(progress / 0.3f, 1.0F, 0.0F); // 黒から透明へ
        } else {
            screenFlashIntensity = 0.0F;
        }
    }
    /**
     * 背景フラッシュ効果の描画
     */
    private static void renderBackgroundFlash(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (screenFlashIntensity > 0.0F) {
            // 黒いフラッシュ（フェードイン効果）
            int flashAlpha = (int)(screenFlashIntensity * 255);
            guiGraphics.fill(0, 0, screenWidth, screenHeight,
                    flashAlpha << 24 | 0x000000); // 黒色フラッシュ
        }
    }
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    /**
     * TungSahurエンティティの描画（renderEntityInInventoryFollowsAngleを使用）
     */
    private static void renderTungSahurEntity(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float progress) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        try {
            TungSahurMod.LOGGER.debug("TungSahurエンティティ描画開始");

            // 画面中央の座標計算
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            TungSahurMod.LOGGER.debug("画面中央: ({}, {})", centerX, centerY);

            // エンティティサイズ計算（顔面アップ用）
            int entitySize = (int)(60 * entityScale); // より大きなサイズで顔面アップ

            TungSahurMod.LOGGER.debug("エンティティサイズ: {} (scale: {})", entitySize, entityScale);

            // 位置調整（かなり下に表示）
            int entityX = centerX;
            int entityY = centerY + (int)(screenHeight * 2.5f); // 画面下部寄りに配置

            // 軽微な画面揺れ効果
            float shake = (float) Math.sin(System.currentTimeMillis() * 0.15) * 3.0F;
            entityX += shake;
            entityY += shake * 0.3f;

            TungSahurMod.LOGGER.debug("エンティティ描画位置: ({}, {})", entityX, entityY);

            // エンティティの向きを設定（正面顔アップ用に調整）
            jumpscareEntity.setYRot(0.0F); // Y軸回転は0度
            jumpscareEntity.setXRot(0.0F); // X軸回転も0度にリセット

            // マウス位置（視線方向）を正面顔用に調整
            float lookAtX = 0.0F; // 正面
            float lookAtY = 0.0F; // 水平視線

            // 赤黒い不気味な発光効果
            float glowPulse = (float) Math.sin(System.currentTimeMillis() * 0.008) * 0.4F + 0.6F; // ゆっくりと変動
            float darkGlow = (float) Math.sin(System.currentTimeMillis() * 0.012) * 0.3F + 0.4F; // 暗い部分の変動

            float red = 1.2F * glowPulse;           // 赤を強調して発光
            float green = 0.1F * darkGlow;         // 緑を抑えて暗く
            float blue = 0.1F * darkGlow;          // 青を抑えて暗く
            float alpha = 1.0F - Math.max(0, (progress - 0.8F) * 5.0F); // 最後でフェードアウト

            TungSahurMod.LOGGER.debug("色調設定: R={}, G={}, B={}, A={}", red, green, blue, alpha);

            RenderSystem.setShaderColor(red, green, blue, alpha);

            // ★修正：renderEntityInInventoryFollowsAngleを使用
            TungSahurMod.LOGGER.debug("renderEntityInInventoryFollowsAngle 呼び出し開始");
            TungSahurMod.LOGGER.debug("パラメータ: entity={}, x={}, y={}, size={}, lookX={}, lookY={}",
                    jumpscareEntity, entityX, entityY, entitySize, lookAtX, lookAtY);

            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    guiGraphics,
                    entityX,           // X座標
                    entityY,           // Y座標
                    entitySize,        // エンティティサイズ
                    lookAtX,           // マウスX（視線方向）
                    lookAtY,           // マウスY（視線方向）
                    jumpscareEntity    // 表示するエンティティ
            );

            TungSahurMod.LOGGER.debug("renderEntityInInventoryFollowsAngle 呼び出し完了");

            // 追加の恐怖効果
            renderAdditionalScareEffects(guiGraphics, centerX, centerY, entitySize, progress);

        } catch (Exception e) {
            TungSahurMod.LOGGER.error("TungSahurエンティティ描画エラー: ", e);
            e.printStackTrace();
        } finally {
            poseStack.popPose();
        }
    }    private static void renderAdditionalScareEffects(GuiGraphics guiGraphics, int centerX, int centerY,
                                                     int entitySize, float progress) {

        // 周囲を暗くする効果
        if (progress > 0.2f) {
            int darknessAlpha = (int)((progress - 0.2f) * 200);

            // 中央以外を暗くする
            int radius = entitySize + 50;

            // 上部
            guiGraphics.fill(0, 0, guiGraphics.guiWidth(), centerY - radius,
                    darknessAlpha << 24 | 0x000000);
            // 下部
            guiGraphics.fill(0, centerY + radius, guiGraphics.guiWidth(), guiGraphics.guiHeight(),
                    darknessAlpha << 24 | 0x000000);
            // 左部
            guiGraphics.fill(0, centerY - radius, centerX - radius, centerY + radius,
                    darknessAlpha << 24 | 0x000000);
            // 右部
            guiGraphics.fill(centerX + radius, centerY - radius, guiGraphics.guiWidth(), centerY + radius,
                    darknessAlpha << 24 | 0x000000);
        }

        // 光る目の効果（エンティティの上に重ねて描画）
        if (progress > 0.4f && progress < 0.9f) {
            renderGlowingEyes(guiGraphics, centerX, centerY, entitySize);
        }
    }

    /**
     * 光る目の効果
     */
    private static void renderGlowingEyes(GuiGraphics guiGraphics, int centerX, int centerY, int entitySize) {
        // 目の位置計算（エンティティの顔部分）
        int eyeSize = Math.max(3, entitySize / 20);
        int eyeOffset = entitySize / 8;

        int leftEyeX = centerX - eyeOffset;
        int rightEyeX = centerX + eyeOffset;
        int eyeY = centerY - entitySize / 3; // 顔の位置

        // 光る効果の強度（点滅）
        float glowIntensity = 0.8f + (float)Math.sin(System.currentTimeMillis() * 0.2) * 0.2f;
        int eyeAlpha = (int)(glowIntensity * 255);

        // 赤い光る目
        int eyeColor = eyeAlpha << 24 | 0xFF0000; // 赤色

        // グロー効果（周りを薄く光らせる）
        int glowSize = eyeSize + 2;
        int glowColor = (eyeAlpha / 2) << 24 | 0xFF3333; // 薄い赤

        // グロー描画
        guiGraphics.fill(leftEyeX - 1, eyeY - 1, leftEyeX + glowSize, eyeY + glowSize, glowColor);
        guiGraphics.fill(rightEyeX - 1, eyeY - 1, rightEyeX + glowSize, eyeY + glowSize, glowColor);

        // 目の本体描画
        guiGraphics.fill(leftEyeX, eyeY, leftEyeX + eyeSize, eyeY + eyeSize, eyeColor);
        guiGraphics.fill(rightEyeX, eyeY, rightEyeX + eyeSize, eyeY + eyeSize, eyeColor);
    }
}