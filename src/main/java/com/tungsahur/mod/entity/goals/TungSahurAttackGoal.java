// TungSahurAttackGoal.java - 基本攻撃にパーティクル追加
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;

public class TungSahurAttackGoal extends MeleeAttackGoal {
    private final TungSahurEntity tungSahur;
    private int attackDelay = 0;
    private int chargeTime = 0;

    public TungSahurAttackGoal(TungSahurEntity tungSahur, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(tungSahur, speedModifier, followingTargetEvenIfNotSeen);
        this.tungSahur = tungSahur;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && tungSahur.getTarget() instanceof Player;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
        double attackRange = this.getAttackReachSqr(target);

        if (distToTargetSqr <= attackRange && this.attackDelay <= 0) {
            if (chargeTime <= 0) {
                chargeTime = 15; // チャージ開始
                spawnChargeParticles();
            } else {
                chargeTime--;
                if (chargeTime <= 0) {
                    performStylishAttack(target);
                    this.resetAttackCooldown();
                    this.attackDelay = 40;
                }
            }
        }

        if (this.attackDelay > 0) {
            this.attackDelay--;
        }
    }

    private void spawnChargeParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 赤い螺旋状のパーティクル
            for (int i = 0; i < 3; i++) {
                double angle = (tungSahur.tickCount + i * 120) * 0.2;
                double radius = 1.5 + Math.sin(tungSahur.tickCount * 0.1) * 0.5;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 1.0 + Math.sin(angle * 3) * 0.3;

                serverLevel.sendParticles(ParticleTypes.FLAME,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.02);
                serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }

            // 地面からの煙
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double x = tungSahur.getX() + Math.cos(angle) * 2.0;
                double z = tungSahur.getZ() + Math.sin(angle) * 2.0;
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        x, tungSahur.getY() + 0.1, z, 3, 0.2, 0.0, 0.2, 0.05);
            }
        }
    }

    private void performStylishAttack(LivingEntity target) {
        // 攻撃実行
        this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(target);

        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 衝撃波パーティクル
            spawnImpactWave(serverLevel);

            // 血しぶき風パーティクル
            spawnBloodSplatter(serverLevel, target);

            // 攻撃音強化
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.5F, 1.5F);
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.3F, 0.8F);
        }
    }

    private void spawnImpactWave(ServerLevel serverLevel) {
        // 円形衝撃波
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 0; i < 16 * ring; i++) {
                double angle = (i * 2 * Math.PI) / (16 * ring);
                double radius = ring * 1.5;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.CRIT,
                        x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.2, 0.0, 0.1);
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        x, tungSahur.getY() + 0.5, z, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }
    }

    private void spawnBloodSplatter(ServerLevel serverLevel, LivingEntity target) {
        // 血しぶき風の赤いパーティクル
        for (int i = 0; i < 20; i++) {
            double velocityX = (serverLevel.random.nextDouble() - 0.5) * 0.8;
            double velocityY = serverLevel.random.nextDouble() * 0.8 + 0.2;
            double velocityZ = (serverLevel.random.nextDouble() - 0.5) * 0.8;

            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                    1, velocityX, velocityY, velocityZ, 0.0);
        }
    }
}