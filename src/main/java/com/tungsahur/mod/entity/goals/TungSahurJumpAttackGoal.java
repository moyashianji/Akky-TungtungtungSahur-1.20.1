// TungSahurJumpAttackGoal.java - ジャンプ攻撃ゴール（2日目以降）
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class TungSahurJumpAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int prepareTime = 0;
    private int landingTime = 0;
    private boolean isPreparingJump = false;
    private boolean hasLanded = false;

    public TungSahurJumpAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // 2日目以降のみ使用可能
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // 中距離でのジャンプ攻撃
        return distance >= 4.0D && distance <= 10.0D &&
                this.tungSahur.canJump() &&
                this.tungSahur.onGround() &&
                this.tungSahur.hasLineOfSight(this.target);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        // ジャンプ中または着地処理中は続行
        return this.isPreparingJump || !this.tungSahur.onGround() || this.hasLanded;
    }

    @Override
    public void start() {
        this.prepareTime = 15 + this.tungSahur.getRandom().nextInt(10); // 0.75-1.25秒の準備時間
        this.landingTime = 0;
        this.isPreparingJump = true;
        this.hasLanded = false;

        // 準備開始音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 1.4F);

        // 準備パーティクル
        spawnPrepareParticles();
    }

    @Override
    public void stop() {
        this.prepareTime = 0;
        this.landingTime = 0;
        this.isPreparingJump = false;
        this.hasLanded = false;
        this.tungSahur.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.isPreparingJump) {
            // ジャンプ準備中
            this.tungSahur.getNavigation().stop();
            this.prepareTime--;

            // 準備中のパーティクル効果
            if (this.prepareTime % 3 == 0) {
                spawnPrepareParticles();
            }

            // 準備完了時のジャンプ実行
            if (this.prepareTime <= 0) {
                executeJump();
                this.isPreparingJump = false;
            }
        } else if (!this.tungSahur.onGround()) {
            // ジャンプ中のパーティクル
            spawnJumpTrailParticles();
        } else if (!this.hasLanded) {
            // 着地処理
            handleLanding();
            this.hasLanded = true;
            this.landingTime = 20; // 着地後の処理時間
        } else if (this.landingTime > 0) {
            // 着地後の処理
            this.landingTime--;
            if (this.landingTime <= 0) {
                // ゴール終了
            }
        }
    }

    private void executeJump() {
        if (this.target != null && this.tungSahur.canJump()) {
            // ジャンプ実行
            this.tungSahur.performJumpAttack(this.target);

            // ジャンプ音
            this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 0.8F, 1.0F);

            // ジャンプ時のパーティクル
            spawnJumpParticles();
        }
    }

    private void handleLanding() {
        // 着地音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.0F, 0.8F);

        // 着地パーティクル
        spawnLandingParticles();

        // 着地ダメージ判定
        checkLandingDamage();
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
                    double dx = entity.getX() - this.tungSahur.getX();
                    double dz = entity.getZ() - this.tungSahur.getZ();
                    double length = Math.sqrt(dx * dx + dz * dz);

                    if (length > 0) {
                        double knockbackStrength = 1.0D + (this.tungSahur.getEvolutionStage() * 0.3D);
                        entity.setDeltaMovement(
                                entity.getDeltaMovement().add(
                                        (dx / length) * knockbackStrength,
                                        0.3D,
                                        (dz / length) * knockbackStrength
                                )
                        );
                    }
                }
            }
        }
    }

    private void spawnPrepareParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 足元の震えるパーティクル
            for (int i = 0; i < 4; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;
                double y = this.tungSahur.getY() + 0.1;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0;

                serverLevel.sendParticles(ParticleTypes.POOF,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
            }

            // エネルギーチャージパーティクル
            serverLevel.sendParticles(ParticleTypes.FIREWORK,
                    this.tungSahur.getX(), this.tungSahur.getY() + 0.5, this.tungSahur.getZ(),
                    2, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void spawnJumpParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // ジャンプ時の爆発パーティクル
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    3, 0.5, 0.1, 0.5, 0.0);

            // 煙パーティクル
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    6, 0.8, 0.2, 0.8, 0.1);
        }
    }

    private void spawnJumpTrailParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // ジャンプ中の軌跡パーティクル
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void spawnLandingParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 着地時の衝撃波パーティクル
            for (int i = 0; i < 12; i++) {
                double angle = (i / 12.0) * Math.PI * 2;
                double radius = 2.0D + (this.tungSahur.getEvolutionStage() * 0.5D);

                double x = this.tungSahur.getX() + Math.cos(angle) * radius;
                double y = this.tungSahur.getY() + 0.1;
                double z = this.tungSahur.getZ() + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.POOF,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.1);
            }

            // 中央の爆発パーティクル
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.tungSahur.getX(), this.tungSahur.getY() + 0.5, this.tungSahur.getZ(),
                    2, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}