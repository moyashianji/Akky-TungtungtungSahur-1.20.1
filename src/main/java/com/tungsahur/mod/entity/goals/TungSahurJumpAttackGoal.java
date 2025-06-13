// TungSahurJumpAttackGoal.java - ジャンプ攻撃にパーティクル追加
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TungSahurJumpAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int jumpCooldown = 0;
    private int jumpChargeTime = 0;
    private boolean isJumping = false;

    public TungSahurJumpAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        double distance = this.tungSahur.distanceTo(this.target);
        return distance > 4.0D && distance < 12.0D && this.jumpCooldown <= 0 &&
                this.tungSahur.onGround() && this.tungSahur.getEvolutionStage() >= 1;
    }

    @Override
    public void start() {
        this.jumpChargeTime = 20;
        this.isJumping = false;
        spawnChargeStartParticles();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        if (this.jumpChargeTime > 0) {
            this.jumpChargeTime--;
            this.tungSahur.getNavigation().stop();

            // チャージ中の震える演出 + パーティクル
            if (this.jumpChargeTime % 4 == 0) {
                this.tungSahur.setPos(
                        this.tungSahur.getX() + (this.tungSahur.getRandom().nextFloat() - 0.5F) * 0.1F,
                        this.tungSahur.getY(),
                        this.tungSahur.getZ() + (this.tungSahur.getRandom().nextFloat() - 0.5F) * 0.1F
                );
                spawnChargeParticles();
            }
        } else if (!isJumping) {
            performJumpAttack();
            isJumping = true;
        }

        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        }
    }

    private void spawnChargeStartParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 足元に魔法陣風のパーティクル
            for (int ring = 1; ring <= 3; ring++) {
                for (int i = 0; i < 8 * ring; i++) {
                    double angle = (i * 2 * Math.PI) / (8 * ring);
                    double radius = ring * 0.8;
                    double x = tungSahur.getX() + Math.cos(angle) * radius;
                    double z = tungSahur.getZ() + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                            x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }
        }
    }

    private void spawnChargeParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 脚部周りのエネルギー蓄積
            for (int i = 0; i < 6; i++) {
                double angle = tungSahur.tickCount * 0.3 + i * Math.PI / 3;
                double radius = 1.2;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 0.2;

                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 1, 0.0, 0.2, 0.0, 0.0);
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }

            // 地面のクラック風パーティクル
            for (int i = 0; i < 10; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 3.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 3.0;

                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        tungSahur.getX() + offsetX, tungSahur.getY(), tungSahur.getZ() + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.0);
            }

            // 充電音
            if (jumpChargeTime % 8 == 0) {
                serverLevel.playSound(null, tungSahur.blockPosition(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.5F, 2.0F);
            }
        }
    }

    private void performJumpAttack() {
        double dx = this.target.getX() - this.tungSahur.getX();
        double dy = this.target.getY() - this.tungSahur.getY();
        double dz = this.target.getZ() - this.tungSahur.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // ジャンプの勢いを計算
        double jumpPower = 0.8D + this.tungSahur.getEvolutionStage() * 0.2D;

        this.tungSahur.setDeltaMovement(
                (dx / distance) * jumpPower,
                0.6D + (dy > 0 ? dy * 0.1D : 0),
                (dz / distance) * jumpPower
        );

        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // ジャンプ開始の爆発的パーティクル
            spawnJumpLaunchParticles(serverLevel);

            // ジャンプ音強化
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.2F);
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.5F);
        }

        this.jumpCooldown = 100;
        this.stop();
    }

    private void spawnJumpLaunchParticles(ServerLevel serverLevel) {
        // 足元の爆発
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                tungSahur.getX(), tungSahur.getY(), tungSahur.getZ(),
                5, 0.5, 0.2, 0.5, 0.0);

        // 放射状の衝撃波
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            double velocityX = Math.cos(angle) * 1.2;
            double velocityZ = Math.sin(angle) * 1.2;

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    tungSahur.getX(), tungSahur.getY() + 0.1, tungSahur.getZ(),
                    1, velocityX, 0.2, velocityZ, 0.1);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    tungSahur.getX(), tungSahur.getY() + 0.1, tungSahur.getZ(),
                    1, velocityX * 0.5, 0.4, velocityZ * 0.5, 0.0);
        }

        // 地面のクレーター風パーティクル
        for (int ring = 1; ring <= 4; ring++) {
            for (int i = 0; i < 6 * ring; i++) {
                double angle = (i * 2 * Math.PI) / (6 * ring);
                double radius = ring * 0.8;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.3, 0.0, 0.05);
            }
        }

        // 上昇する軌跡パーティクル
        for (int i = 0; i < 15; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.6;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.6;

            serverLevel.sendParticles(ParticleTypes.DRIPPING_LAVA,
                    tungSahur.getX() + offsetX, tungSahur.getY() + 1.0, tungSahur.getZ() + offsetZ,
                    1, 0.0, 0.8, 0.0, 0.0);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.jumpChargeTime > 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.jumpChargeTime = 0;
        this.isJumping = false;
    }
}
