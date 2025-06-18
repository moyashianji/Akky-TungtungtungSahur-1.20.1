// TungBatProjectileRenderer.java - 完全対応版
package com.tungsahur.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class TungBatProjectileRenderer extends EntityRenderer<TungBatProjectile> {
    private final ItemRenderer itemRenderer;

    // 日数別バットスタック（キャッシュ用）
    private final ItemStack day1BatStack;
    private final ItemStack day2BatStack;
    private final ItemStack day3BatStack;

    public TungBatProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();

        // 日数別バットアイテムの事前準備
        this.day1BatStack = createDayBatStack(1);
        this.day2BatStack = createDayBatStack(2);
        this.day3BatStack = createDayBatStack(3);

        TungSahurMod.LOGGER.debug("TungBatProjectileRenderer初期化完了");
    }

    @Override
    public void render(TungBatProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        // 日数に応じたバットアイテムの選択
        ItemStack batStack = getBatStackForDay(entity.getThrowerDayNumber());

        // より滑らかで迫力のある回転アニメーション
        float totalTime = entity.tickCount + partialTick;
        float speed = (float) entity.getDeltaMovement().length();

        // 日数に応じた回転速度調整
        float baseRotationSpeed = switch (entity.getThrowerDayNumber()) {
            case 1 -> 15.0F;
            case 2 -> 20.0F;
            case 3 -> 25.0F;
            default -> 15.0F;
        };

        float rotationSpeed = baseRotationSpeed + speed * 40.0F;

        // 複軸回転でより動的に（日数が高いほど複雑）
        applyDaySpecificRotation(poseStack, totalTime, rotationSpeed, entity.getThrowerDayNumber());

        // 飛行中の振動効果
        applyFlightEffects(poseStack, totalTime, entity);

        // 日数に応じたスケール調整
        float scale = calculateScale(totalTime, entity);
        poseStack.scale(scale, scale, scale);

        // ホーミング中の特殊エフェクト
        if (entity.isHoming()) {
            applyHomingEffects(poseStack, totalTime);
        }

        // バットアイテムをレンダリング
        this.itemRenderer.renderStatic(batStack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer,
                entity.level(), entity.getId());

        poseStack.popPose();

        // クライアント側でのパーティクル軌跡
        if (entity.level().isClientSide && entity.tickCount % getParticleFrequency(entity) == 0) {
            spawnClientTrailParticles(entity);
        }

        // デバッグ情報の表示
        if (TungSahurMod.isDebugMode() && entity.tickCount % 20 == 0) {
            logRenderDebugInfo(entity);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 日数に応じたバットスタックの取得
     */
    private ItemStack getBatStackForDay(int dayNumber) {
        return switch (dayNumber) {
            case 2 -> day2BatStack;
            case 3 -> day3BatStack;
            default -> day1BatStack;
        };
    }

    /**
     * 日数別バットスタックの作成
     */
    private ItemStack createDayBatStack(int dayNumber) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        tag.putInt("DayNumber", dayNumber);
        tag.putBoolean("EntityBat", true);
        tag.putBoolean("Projectile", true);

        switch (dayNumber) {
            case 1:
                tag.putString("DisplayName", "Flying Bat (Day 1)");
                break;
            case 2:
                tag.putString("DisplayName", "Enhanced Flying Bat (Day 2)");
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 2);
                break;
            case 3:
                tag.putString("DisplayName", "Ultimate Flying Bat (Day 3)");
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 3);
                tag.putBoolean("Homing", true);
                break;
        }

        return batStack;
    }

    /**
     * 日数に応じた回転効果
     */
    private void applyDaySpecificRotation(PoseStack poseStack, float totalTime, float rotationSpeed, int dayNumber) {
        switch (dayNumber) {
            case 1:
                // 1日目：基本的な回転
                poseStack.mulPose(Axis.YP.rotationDegrees(totalTime * rotationSpeed));
                poseStack.mulPose(Axis.XP.rotationDegrees(totalTime * rotationSpeed * 0.7F));
                break;

            case 2:
                // 2日目：より複雑な回転
                poseStack.mulPose(Axis.YP.rotationDegrees(totalTime * rotationSpeed));
                poseStack.mulPose(Axis.XP.rotationDegrees(totalTime * rotationSpeed * 0.8F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(totalTime * rotationSpeed * 0.3F));
                break;

            case 3:
                // 3日目：非常に複雑で魔法的な回転
                poseStack.mulPose(Axis.YP.rotationDegrees(totalTime * rotationSpeed));
                poseStack.mulPose(Axis.XP.rotationDegrees(totalTime * rotationSpeed * 0.9F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(totalTime * rotationSpeed * 0.5F));

                // 追加の魔法的回転
                float magicRotation = (float) Math.sin(totalTime * 0.3F) * 15.0F;
                poseStack.mulPose(Axis.YP.rotationDegrees(magicRotation));
                break;
        }
    }

    /**
     * 飛行中の振動効果
     */
    private void applyFlightEffects(PoseStack poseStack, float totalTime, TungBatProjectile entity) {
        float baseWobble = (float) Math.sin(totalTime * 0.8F) * 0.1F;
        float speedMultiplier = (float) entity.getDeltaMovement().length();

        // 日数に応じた振動の強さ調整
        float wobbleStrength = switch (entity.getThrowerDayNumber()) {
            case 1 -> 1.0F;
            case 2 -> 0.8F; // より安定
            case 3 -> 0.5F; // 非常に安定
            default -> 1.0F;
        };

        float finalWobble = baseWobble * wobbleStrength * speedMultiplier;
        poseStack.translate(finalWobble, finalWobble * 0.5F, 0);

        // 3日目のホーミングモードでは振動を抑制
        if (entity.getThrowerDayNumber() >= 3 && entity.isHoming()) {
            poseStack.translate(-finalWobble * 0.5F, -finalWobble * 0.25F, 0);
        }
    }

    /**
     * スケール計算
     */
    private float calculateScale(float totalTime, TungBatProjectile entity) {
        float baseScale = switch (entity.getThrowerDayNumber()) {
            case 1 -> 1.2F;
            case 2 -> 1.4F;
            case 3 -> 1.6F;
            default -> 1.2F;
        };

        // 脈動効果
        float pulseScale = (float) Math.sin(totalTime * 0.5F) * 0.1F;

        // ホーミング時は少し大きく
        if (entity.isHoming()) {
            baseScale *= 1.1F;
            pulseScale *= 1.5F; // より強い脈動
        }

        return baseScale + pulseScale;
    }

    /**
     * ホーミング時の特殊エフェクト
     */
    private void applyHomingEffects(PoseStack poseStack, float totalTime) {
        // 魔法的なオーラ効果（スケールの微調整）
        float homingPulse = (float) Math.sin(totalTime * 2.0F) * 0.05F;
        poseStack.scale(1.0F + homingPulse, 1.0F + homingPulse, 1.0F + homingPulse);

        // 軽い光沢効果（位置の微調整）
        float shimmer = (float) Math.cos(totalTime * 1.5F) * 0.02F;
        poseStack.translate(shimmer, shimmer, 0);
    }

    /**
     * パーティクル生成頻度の計算
     */
    private int getParticleFrequency(TungBatProjectile entity) {
        return switch (entity.getThrowerDayNumber()) {
            case 1 -> 3; // 普通の頻度
            case 2 -> 2; // 高い頻度
            case 3 -> 1; // 非常に高い頻度
            default -> 3;
        };
    }

    /**
     * クライアント側のパーティクル軌跡
     */
    private void spawnClientTrailParticles(TungBatProjectile entity) {
        RandomSource random = entity.level().getRandom();
        Vec3 pos = entity.position();
        Vec3 velocity = entity.getDeltaMovement();

        // 基本軌跡パーティクル
        for (int i = 0; i < getParticleCount(entity); i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.3;
            double offsetY = (random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (random.nextDouble() - 0.5) * 0.3;

            entity.level().addParticle(ParticleTypes.CRIT,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    -velocity.x * 0.1, -velocity.y * 0.1, -velocity.z * 0.1);
        }

        // 日数に応じた特別なパーティクル
        spawnDaySpecificParticles(entity, random, pos);

        // ホーミング時の特別なパーティクル
        if (entity.isHoming()) {
            spawnHomingParticles(entity, random, pos);
        }
    }

    /**
     * パーティクル数の計算
     */
    private int getParticleCount(TungBatProjectile entity) {
        int baseCount = switch (entity.getThrowerDayNumber()) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 1;
        };

        if (entity.isHoming()) {
            baseCount += 1;
        }

        return baseCount;
    }

    /**
     * 日数に応じた特別なパーティクル
     */
    private void spawnDaySpecificParticles(TungBatProjectile entity, RandomSource random, Vec3 pos) {
        switch (entity.getThrowerDayNumber()) {
            case 2:
                if (random.nextFloat() < 0.4F) {
                    entity.level().addParticle(ParticleTypes.FLAME,
                            pos.x, pos.y, pos.z,
                            0, 0, 0);
                }
                if (random.nextFloat() < 0.2F) {
                    entity.level().addParticle(ParticleTypes.LAVA,
                            pos.x, pos.y, pos.z,
                            0, 0, 0);
                }
                break;

            case 3:
                if (random.nextFloat() < 0.5F) {
                    entity.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x, pos.y, pos.z,
                            0, 0, 0);
                }
                if (random.nextFloat() < 0.3F) {
                    entity.level().addParticle(ParticleTypes.WITCH,
                            pos.x, pos.y, pos.z,
                            0, 0, 0);
                }
                if (random.nextFloat() < 0.2F) {
                    entity.level().addParticle(ParticleTypes.ENCHANTED_HIT,
                            pos.x, pos.y, pos.z,
                            0, 0, 0);
                }
                break;
        }
    }

    /**
     * ホーミング時の特別なパーティクル
     */
    private void spawnHomingParticles(TungBatProjectile entity, RandomSource random, Vec3 pos) {
        if (random.nextFloat() < 0.6F) {
            entity.level().addParticle(ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z,
                    0, 0, 0);
        }

        if (random.nextFloat() < 0.3F) {
            entity.level().addParticle(ParticleTypes.PORTAL,
                    pos.x, pos.y, pos.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1);
        }
    }

    /**
     * デバッグ情報の出力
     */
    private void logRenderDebugInfo(TungBatProjectile entity) {
        TungSahurMod.LOGGER.debug("TungBatProjectile レンダリング情報:");
        TungSahurMod.LOGGER.debug("  - 日数: {}", entity.getThrowerDayNumber());
        TungSahurMod.LOGGER.debug("  - ホーミング: {}", entity.isHoming());
        TungSahurMod.LOGGER.debug("  - 速度: {}", entity.getDeltaMovement().length());
        TungSahurMod.LOGGER.debug("  - 生存時間: {}tick", entity.tickCount);
        TungSahurMod.LOGGER.debug("  - ダメージ倍率: {}", entity.getDamageMultiplier());
    }

    @Override
    public ResourceLocation getTextureLocation(TungBatProjectile entity) {
        // プロジェクタイルのテクスチャは使用しない（アイテムレンダリングを使用）
        return new ResourceLocation(TungSahurMod.MODID, "textures/entity/tung_bat_projectile.png");
    }

    /**
     * レンダリングの可視性チェック
     */
    @Override
    public boolean shouldRender(TungBatProjectile entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        // 基本の可視性チェック
        if (!super.shouldRender(entity, frustum, x, y, z)) {
            return false;
        }

        // 距離による詳細度調整
        double distanceSquared = entity.distanceToSqr(x, y, z);
        if (distanceSquared > 256.0D) { // 16ブロック以上
            // 遠距離では簡略化
            return entity.tickCount % 2 == 0;
        }

        return true;
    }
}