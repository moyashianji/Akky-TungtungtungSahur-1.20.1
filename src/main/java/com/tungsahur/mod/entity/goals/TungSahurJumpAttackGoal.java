// TungSahurJumpAttackGoal.java - 完全版ジャンプ攻撃ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.EnumSet;
import java.util.List;

public class TungSahurJumpAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int prepareTime = 0;
    private int landingTime = 0;
    private boolean isPreparingJump = false;
    private boolean hasLanded = false;
    private double lastGroundY = 0;
    private int airTime = 0;

    public TungSahurJumpAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // Stage1以降のみ使用可能（修正: 0->1に変更）
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        // ジャンプ攻撃可能状態かチェック
        if (!this.tungSahur.canJump()) return false;

        double distance = this.tungSahur.distanceTo(this.target);
        double heightDiff = this.target.getY() - this.tungSahur.getY();

        // 中距離でのジャンプ攻撃（高低差も考慮）
        return distance >= 4.0D && distance <= 12.0D &&
                this.tungSahur.onGround() &&
                this.tungSahur.hasLineOfSight(this.target) &&
                heightDiff >= -2.0D && heightDiff <= 8.0D; // 高低差制限
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        // ジャンプ準備中、空中、または着地処理中は続行
        return this.isPreparingJump || !this.tungSahur.onGround() || this.hasLanded;
    }

    @Override
    public void start() {
        // 進化段階に応じた準備時間
        this.prepareTime = 15 - (this.tungSahur.getEvolutionStage() * 3);
        this.prepareTime = Math.max(5, this.prepareTime); // 最低5tick

        this.landingTime = 0;
        this.isPreparingJump = true;
        this.hasLanded = false;
        this.lastGroundY = this.tungSahur.getY();
        this.airTime = 0;

        // 移動停止
        this.tungSahur.getNavigation().stop();

        // ジャンプ状態設定
        this.tungSahur.setJumping(true);

        // 準備音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 1.5F);

        // 準備パーティクル
        spawnPrepareParticles();
    }

    @Override
    public void stop() {
        this.prepareTime = 0;
        this.landingTime = 0;
        this.isPreparingJump = false;
        this.hasLanded = false;
        this.airTime = 0;
        this.tungSahur.getNavigation().stop();
        this.tungSahur.setJumping(false);
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.isPreparingJump) {
            // ジャンプ準備中
            handleJumpPreparation();
        } else if (!this.tungSahur.onGround()) {
            // 空中処理
            handleAirborne();
        } else if (this.hasLanded) {
            // 着地処理
            handleLanding();
        }
    }

    private void handleJumpPreparation() {
        // 準備中は移動停止
        this.tungSahur.getNavigation().stop();

        this.prepareTime--;

        // 準備中のパーティクル効果
        if (this.prepareTime % 3 == 0) {
            spawnPrepareParticles();
        }

        // 地面を掘るようなエフェクト
        if (this.prepareTime % 5 == 0 && this.prepareTime > 0) {
            spawnGroundScrapeParticles();
        }

        // ジャンプ実行
        if (this.prepareTime <= 0) {
            executeJump();
            this.isPreparingJump = false;
        }
    }

    private void handleAirborne() {
        this.airTime++;

        // 空中でのパーティクル軌跡
        if (this.airTime % 3 == 0) {
            spawnAirTrailParticles();
        }

        // 目標に向かって微調整（進化段階が高いほど精密）
        if (this.tungSahur.getEvolutionStage() >= 2) {
            adjustAirborneTrajectory();
        }

        // 着地判定
        if (this.tungSahur.onGround() && this.airTime > 5) {
            this.hasLanded = true;
            this.landingTime = 20; // 1秒間の着地処理
            handleImpact();
        }
    }

    private void handleLanding() {
        this.landingTime--;

        // 着地処理中のパーティクル
        if (this.landingTime % 4 == 0) {
            spawnLandingParticles();
        }

        // 着地処理完了
        if (this.landingTime <= 0) {
            this.hasLanded = false;
        }
    }

    private void executeJump() {
        if (this.target != null && this.tungSahur.canJump()) {
            // TungSahurEntityのジャンプ攻撃メソッドを使用
            this.tungSahur.performJumpAttack(this.target);

            // 追加のジャンプ音
            float pitch = 1.0F + (this.tungSahur.getEvolutionStage() * 0.2F);
            this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 0.8F, pitch);

            // ジャンプ時のパーティクル
            spawnJumpParticles();
        }
    }

    private void adjustAirborneTrajectory() {
        if (this.target == null) return;

        // 現在の速度を取得
        double currentVelX = this.tungSahur.getDeltaMovement().x;
        double currentVelZ = this.tungSahur.getDeltaMovement().z;
        double currentVelY = this.tungSahur.getDeltaMovement().y;

        // ターゲットへの方向を計算
        double dx = this.target.getX() - this.tungSahur.getX();
        double dz = this.target.getZ() - this.tungSahur.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance > 0.5D) {
            // 微調整の強さ（進化段階に応じて）
            double adjustmentStrength = 0.02D + (this.tungSahur.getEvolutionStage() * 0.01D);

            // 正規化された方向ベクトル
            double dirX = dx / distance;
            double dirZ = dz / distance;

            // 現在の速度に微調整を加える
            double newVelX = currentVelX + (dirX * adjustmentStrength);
            double newVelZ = currentVelZ + (dirZ * adjustmentStrength);

            // 速度制限
            double maxSpeed = 1.5D;
            double currentSpeed = Math.sqrt(newVelX * newVelX + newVelZ * newVelZ);
            if (currentSpeed > maxSpeed) {
                newVelX = (newVelX / currentSpeed) * maxSpeed;
                newVelZ = (newVelZ / currentSpeed) * maxSpeed;
            }

            this.tungSahur.setDeltaMovement(newVelX, currentVelY, newVelZ);
        }
    }

    private void handleImpact() {
        // 着地音
        float volume = 1.0F + (this.tungSahur.getEvolutionStage() * 0.3F);
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, volume, 0.8F);

        // 着地パーティクル
        spawnImpactParticles();

        // 着地ダメージ判定
        checkLandingDamage();

        // 進化段階に応じた追加効果
        applyLandingEffects();
    }

    private void checkLandingDamage() {
        // 着地地点周辺の敵にダメージ
        double radius = 2.0D + (this.tungSahur.getEvolutionStage() * 0.5D);
        AABB area = new AABB(
                this.tungSahur.getX() - radius, this.tungSahur.getY() - 1.0, this.tungSahur.getZ() - radius,
                this.tungSahur.getX() + radius, this.tungSahur.getY() + 2.0, this.tungSahur.getZ() + radius
        );

        List<LivingEntity> nearbyEntities = this.tungSahur.level().getEntitiesOfClass(LivingEntity.class, area);

        for (LivingEntity entity : nearbyEntities) {
            if (entity != this.tungSahur && !entity.isAlliedTo(this.tungSahur)) {
                double distance = this.tungSahur.distanceTo(entity);
                if (distance <= radius) {
                    // ダメージ量（距離に応じて減衰）
                    float damage = (float) (6.0D + (this.tungSahur.getEvolutionStage() * 2.0D));
                    damage *= (1.0D - (distance / radius) * 0.5D); // 距離による減衰

                    entity.hurt(this.tungSahur.damageSources().mobAttack(this.tungSahur), damage);

                    // ノックバック効果
                    double knockbackStrength = 1.0D + (this.tungSahur.getEvolutionStage() * 0.3D);
                    double knockbackX = entity.getX() - this.tungSahur.getX();
                    double knockbackZ = entity.getZ() - this.tungSahur.getZ();
                    entity.knockback(knockbackStrength, -knockbackX, -knockbackZ);

                    // 状態異常効果
                    applyLandingDebuffs(entity);
                }
            }
        }
    }

    private void applyLandingEffects() {
        switch (this.tungSahur.getEvolutionStage()) {
            case 1:
                // Stage1: 基本的な震動効果
                if (this.tungSahur.level() instanceof ServerLevel serverLevel) {


                }
                break;

            case 2:
                // Stage2: より強力な地震効果
                if (this.tungSahur.level() instanceof ServerLevel serverLevel) {


                    // 暗い炎
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            this.tungSahur.getX(), this.tungSahur.getY() + 0.5, this.tungSahur.getZ(),
                            15, 1.5, 0.5, 1.5, 0.1);
                }

                // 追加音響効果
                this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                        SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 0.5F, 0.5F);
                break;
        }
    }

    private void applyLandingDebuffs(LivingEntity entity) {
        switch (this.tungSahur.getEvolutionStage()) {
            case 1:
                // Stage1: 軽微な混乱
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                break;

            case 2:
                // Stage2: より強力なデバフ
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));

                // プレイヤーには追加で恐怖効果
                if (entity instanceof net.minecraft.world.entity.player.Player) {
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                }
                break;
        }
    }

    private void spawnPrepareParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 準備中の警告パーティクル
            int particleCount = 3 + this.tungSahur.getEvolutionStage();

            for (int i = 0; i < particleCount; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;
                double y = this.tungSahur.getY() + 0.1;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;

                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
            }

            // 身体周りのエネルギー
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.0, this.tungSahur.getZ(),
                    2, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void spawnGroundScrapeParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {


        }
    }

    private void spawnJumpParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // ジャンプ時の爆発的パーティクル
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            // 煙雲
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    15, 1.0, 0.1, 1.0, 0.1);
        }
    }

    private void spawnAirTrailParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 空中での軌跡パーティクル
            int trailCount = 2 + this.tungSahur.getEvolutionStage();

            for (int i = 0; i < trailCount; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;
                double y = this.tungSahur.getY() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;

                if (this.tungSahur.getEvolutionStage() >= 2) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                } else {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private void spawnImpactParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 着地衝撃パーティクル
            int impactCount = 20 + (this.tungSahur.getEvolutionStage() * 10);

            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);


      }
    }

    private void spawnLandingParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 着地後の継続パーティクル
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.tungSahur.getX(), this.tungSahur.getY() + 0.5, this.tungSahur.getZ(),
                    3, 0.5, 0.2, 0.5, 0.05);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * 他のゴールがジャンプ攻撃の状態を確認できるメソッド
     */
    public boolean isJumpAttacking() {
        return this.isPreparingJump || !this.tungSahur.onGround() || this.hasLanded;
    }

    /**
     * 緊急時にジャンプ攻撃を中断するメソッド
     */
    public void cancelJumpAttack() {
        if (this.isPreparingJump) {
            this.isPreparingJump = false;
            this.tungSahur.setJumping(false);
        }
    }

    /**
     * ジャンプ準備中かどうか
     */
    public boolean isPreparingJump() {
        return this.isPreparingJump;
    }
}