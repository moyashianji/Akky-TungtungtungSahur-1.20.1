
// TungSahurThrowGoal.java - 投擲攻撃にパーティクル追加
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TungSahurThrowGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int throwCooldown = 0;
    private int throwChargeTime = 0;

    public TungSahurThrowGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        double distance = this.tungSahur.distanceTo(this.target);
        return distance > 8.0D && distance < 32.0D && this.throwCooldown <= 0 &&
                this.tungSahur.getEvolutionStage() >= 1;
    }

    @Override
    public void start() {
        this.throwChargeTime = 30;
        spawnPrepareThrowParticles();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.throwChargeTime > 0) {
            this.throwChargeTime--;
            this.tungSahur.getNavigation().stop();

            // チャージ中のパーティクル
            if (this.throwChargeTime % 3 == 0) {
                spawnChargeUpParticles();
            }
        } else {
            performStylishThrow();
            this.throwCooldown = 120;
            this.stop();
        }

        if (this.throwCooldown > 0) {
            this.throwCooldown--;
        }
    }

    private void spawnPrepareThrowParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 準備段階の光の柱
            for (int i = 0; i < 15; i++) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        tungSahur.getX(), tungSahur.getY() + i * 0.3, tungSahur.getZ(),
                        1, 0.1, 0.0, 0.1, 0.0);
            }
        }
    }

    private void spawnChargeUpParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 腕周りの電撃エフェクト
            double armX = tungSahur.getX() - Math.sin(Math.toRadians(tungSahur.getYRot() + 90)) * 0.8;
            double armZ = tungSahur.getZ() + Math.cos(Math.toRadians(tungSahur.getYRot() + 90)) * 0.8;
            double armY = tungSahur.getY() + 1.5;

            for (int i = 0; i < 5; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.6;
                double offsetY = (serverLevel.random.nextDouble() - 0.5) * 0.6;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.6;

                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        armX + offsetX, armY + offsetY, armZ + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.1);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        armX + offsetX, armY + offsetY, armZ + offsetZ,
                        1, 0.0, 0.1, 0.0, 0.0);
            }

            // エネルギー収束パーティクル
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = 2.0 - (30 - throwChargeTime) * 0.06;
                double x = armX + Math.cos(angle) * radius;
                double z = armZ + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.WITCH,
                        x, armY, z, 1,
                        (armX - x) * 0.1, 0.0, (armZ - z) * 0.1, 0.0);
            }
        }
    }

    private void performStylishThrow() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 投擲瞬間の爆発エフェクト
            spawnThrowExplosion(serverLevel);

            // 投擲音強化
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.0F, 1.2F);
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.7F, 0.8F);
        }

        this.tungSahur.performThrowAttack(this.target);
    }

    private void spawnThrowExplosion(ServerLevel serverLevel) {
        double armX = tungSahur.getX() - Math.sin(Math.toRadians(tungSahur.getYRot() + 90)) * 0.8;
        double armZ = tungSahur.getZ() + Math.cos(Math.toRadians(tungSahur.getYRot() + 90)) * 0.8;
        double armY = tungSahur.getY() + 1.5;

        // 爆発の中心
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                armX, armY, armZ, 3, 0.2, 0.2, 0.2, 0.0);

        // 放射状のパーティクル
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10;
            double velocityX = Math.cos(angle) * 0.8;
            double velocityZ = Math.sin(angle) * 0.8;
            double velocityY = tungSahur.level().random.nextDouble() * 0.4 + 0.2;

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    armX, armY, armZ, 1, velocityX, velocityY, velocityZ, 0.1);
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    armX, armY, armZ, 1, velocityX * 0.5, velocityY, velocityZ * 0.5, 0.0);
        }

        // 衝撃波リング
        for (int ring = 1; ring <= 2; ring++) {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                double x = armX + Math.cos(angle) * ring * 1.5;
                double z = armZ + Math.sin(angle) * ring * 1.5;

                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                        x, armY, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.throwChargeTime > 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.throwChargeTime = 0;
    }
}