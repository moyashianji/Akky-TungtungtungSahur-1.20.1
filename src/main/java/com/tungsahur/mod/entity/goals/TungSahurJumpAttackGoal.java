// TungSahurJumpAttackGoal.java - ジャンプ攻撃ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class TungSahurJumpAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int preparationTime = 0;
    private int jumpTime = 0;
    private boolean isPreparing = false;
    private boolean isJumping = false;
    private boolean hasLanded = false;
    private int cooldownAfterJump = 0;

    // ジャンプ攻撃の各フェーズの時間
    private static final int PREPARATION_DURATION = 30; // 1.5秒
    private static final int JUMP_DURATION = 40; // 2秒
    private static final int LANDING_EFFECT_RADIUS = (int) 4.0D;

    public TungSahurJumpAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // 3日目のみ使用可能
        if (this.tungSahur.getDayNumber() < 3) return false;

        // クールダウン中は使用不可
        if (!this.tungSahur.canJumpAttack()) return false;
        if (this.cooldownAfterJump > 0) return false;

        // 見られている時は使用しない
        if (this.tungSahur.isBeingWatched()) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // 中距離での使用（近すぎても遠すぎても使わない）
        boolean inRange = distance >= 4.0D && distance <= 12.0D;
        boolean hasLineOfSight = this.tungSahur.hasLineOfSight(this.target);

        // 高低差がある場合により積極的に使用
        double heightDiff = Math.abs(this.target.getY() - this.tungSahur.getY());
        boolean heightAdvantage = heightDiff >= 2.0D;

        // 使用頻度制御（強力な攻撃なので頻繁には使わない）
        boolean randomCheck = this.tungSahur.getRandom().nextFloat() < (heightAdvantage ? 0.4F : 0.2F);

        return inRange && hasLineOfSight && randomCheck;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getDayNumber() < 3) return false;

        // 準備中またはジャンプ中は継続
        if (this.isPreparing || this.isJumping) return true;

        // 通常時は距離をチェック
        double distance = this.tungSahur.distanceTo(this.target);
        return distance <= 15.0D;
    }

    @Override
    public void start() {
        this.preparationTime = PREPARATION_DURATION;
        this.jumpTime = 0;
        this.isPreparing = true;
        this.isJumping = false;
        this.hasLanded = false;

        // 準備開始のサウンド
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE,
                0.8F, 0.7F);

        TungSahurMod.LOGGER.debug("TungSahurジャンプ攻撃開始: ターゲット距離={}",
                this.tungSahur.distanceTo(this.target));
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        if (this.isPreparing) {
            handlePreparation();
        } else if (this.isJumping) {
            handleJumping();
        }
    }

    private void handlePreparation() {
        // ターゲットを注視
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 移動を停止
        this.tungSahur.getNavigation().stop();

        this.preparationTime--;

        // 準備中のパーティクル効果
        if (this.preparationTime % 3 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnPreparationParticles(serverLevel);
        }

        // 地面を削る音（準備中期）
        if (this.preparationTime == 15) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.HORSE_STEP, SoundSource.HOSTILE,
                    1.0F, 0.8F);

            if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
                spawnGroundScrapeParticles(serverLevel);
            }
        }

        if (this.preparationTime <= 0) {
            // 準備完了、ジャンプ実行
            executeJump();
        }
    }

    private void executeJump() {
        this.isPreparing = false;
        this.isJumping = true;
        this.jumpTime = JUMP_DURATION;
        this.tungSahur.setCurrentlyJumping(true);

        // ジャンプ攻撃の実際の実行
        this.tungSahur.performJumpAttack(this.target);

        // ジャンプ音とパーティクル
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE,
                1.2F, 0.9F);

        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnJumpParticles(serverLevel);
        }

        TungSahurMod.LOGGER.debug("TungSahurジャンプ実行完了");
    }

    private void handleJumping() {
        this.jumpTime--;

        // 空中での軌道パーティクル
        if (this.jumpTime % 4 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnTrailParticles(serverLevel);
        }

        // 着地判定
        if (!this.hasLanded && this.tungSahur.onGround() && this.jumpTime < JUMP_DURATION - 10) {
            handleLanding();
        }

        if (this.jumpTime <= 0) {
            // ジャンプ攻撃終了
            stop();
        }
    }

    private void handleLanding() {
        this.hasLanded = true;

        // 着地時の範囲攻撃
        performLandingAttack();

        // 着地音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                1.0F, 0.8F);

        // 着地パーティクル
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnLandingParticles(serverLevel);
        }

        TungSahurMod.LOGGER.debug("TungSahur着地攻撃実行");
    }

    private void performLandingAttack() {
        // 着地地点周辺の範囲攻撃
        AABB attackArea = this.tungSahur.getBoundingBox().inflate(LANDING_EFFECT_RADIUS);
        List<LivingEntity> nearbyEntities = this.tungSahur.level().getEntitiesOfClass(LivingEntity.class, attackArea);

        for (LivingEntity entity : nearbyEntities) {
            if (entity == this.tungSahur) continue;
            if (entity.isDeadOrDying()) continue;

            double distance = this.tungSahur.distanceTo(entity);
            if (distance <= LANDING_EFFECT_RADIUS) {
                // 距離に応じたダメージ計算
                float maxDamage = 8.0F;
                float damage = (float) (maxDamage * (1.0 - distance / LANDING_EFFECT_RADIUS));
                damage = Math.max(2.0F, damage); // 最低ダメージ

                // ダメージ適用
                entity.hurt(this.tungSahur.damageSources().mobAttack(this.tungSahur), damage);

                // 強力なノックバック
                Vec3 direction = entity.position().subtract(this.tungSahur.position()).normalize();
                double knockbackStrength = 1.5D * (1.0 - distance / LANDING_EFFECT_RADIUS);
                Vec3 knockback = direction.scale(knockbackStrength);

                entity.setDeltaMovement(entity.getDeltaMovement().add(
                        knockback.x, Math.max(0.4D, knockback.y), knockback.z));
                entity.hurtMarked = true;

                // プレイヤーには効果を付与しない（要求仕様）
                TungSahurMod.LOGGER.debug("着地攻撃ヒット: {} に {}ダメージ",
                        entity.getClass().getSimpleName(), damage);
            }
        }
    }

    private void spawnPreparationParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 集中パーティクル
        for (int i = 0; i < 3; i++) {
            double x = tungPos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;
            double y = tungPos.y + 0.1;
            double z = tungPos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
        }

        // 身体周りのエネルギー
        Vec3 bodyPos = tungPos.add(0, this.tungSahur.getBbHeight() * 0.5, 0);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                bodyPos.x, bodyPos.y, bodyPos.z,
                2, 0.5, 0.5, 0.5, 0.1);
    }

    private void spawnGroundScrapeParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 地面削りパーティクル
        for (int i = 0; i < 8; i++) {
            double x = tungPos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;
            double y = tungPos.y + 0.1;
            double z = tungPos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;

            serverLevel.sendParticles(ParticleTypes.POOF,
                    x, y, z, 1, 0.2, 0.1, 0.2, 0.05);
        }
    }

    private void spawnJumpParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // ジャンプ時の爆発的パーティクル
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                tungPos.x, tungPos.y, tungPos.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // 煙雲
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                tungPos.x, tungPos.y, tungPos.z,
                10, 1.0, 0.5, 1.0, 0.1);
    }

    private void spawnTrailParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 飛行軌跡
        serverLevel.sendParticles(ParticleTypes.FLAME,
                tungPos.x, tungPos.y + this.tungSahur.getBbHeight() * 0.5, tungPos.z,
                2, 0.3, 0.3, 0.3, 0.02);

        serverLevel.sendParticles(ParticleTypes.SMOKE,
                tungPos.x, tungPos.y + this.tungSahur.getBbHeight() * 0.3, tungPos.z,
                1, 0.2, 0.2, 0.2, 0.01);
    }

    private void spawnLandingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 着地時の大規模パーティクル
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                tungPos.x, tungPos.y, tungPos.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // 衝撃波パーティクル
        for (int i = 0; i < 20; i++) {
            double angle = (i / 20.0) * 2 * Math.PI;
            double radius = LANDING_EFFECT_RADIUS;
            double x = tungPos.x + Math.cos(angle) * radius;
            double z = tungPos.z + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    x, tungPos.y + 0.1, z,
                    1, 0.1, 0.1, 0.1, 0.1);
        }


 }

    @Override
    public void stop() {
        this.target = null;
        this.preparationTime = 0;
        this.jumpTime = 0;
        this.isPreparing = false;
        this.isJumping = false;
        this.hasLanded = false;
        this.tungSahur.setCurrentlyJumping(false);

        // ジャンプ攻撃後のクールダウン設定
        this.cooldownAfterJump = 200 + this.tungSahur.getRandom().nextInt(100); // 10-15秒

        TungSahurMod.LOGGER.debug("TungSahurジャンプ攻撃終了: クールダウン={}tick", this.cooldownAfterJump);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // クールダウン更新（エンティティのtickで呼び出される）
    public void updateCooldown() {
        if (this.cooldownAfterJump > 0) {
            this.cooldownAfterJump--;
        }
    }
}