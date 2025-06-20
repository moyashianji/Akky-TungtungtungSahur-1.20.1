// TungSahurThrowGoal.java - 投擲攻撃ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TungSahurThrowGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int chargeTime = 0;
    private int aimTime = 0;
    private boolean isCharging = false;
    private boolean isAiming = false;
    private int cooldownAfterThrow = 0;

    public TungSahurThrowGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.LOOK)); // Flag.MOVEを除去
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // 2日目以降のみ使用可能
        if (this.tungSahur.getDayNumber() < 2) return false;

        // クールダウン中は使用不可
        if (!this.tungSahur.canThrow()) return false;

        // 個別クールダウンチェック
        if (cooldownAfterThrow > 0) return false;

        // ほぼ全ての条件を撤廃
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getDayNumber() < 2) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // チャージ中またはエイム中は継続
        if (this.isCharging || this.isAiming) return true;

        // それ以外は距離と視線をチェック
        return distance >= 3.0D && distance <= 25.0D &&
                this.tungSahur.hasLineOfSight(this.target);
    }

    @Override
    public void start() {
        // 日数に応じたチャージ時間調整
        int baseChargeTime = switch (this.tungSahur.getDayNumber()) {
            case 2 -> 25; // 1.25秒
            case 3 -> 20; // 1秒
            default -> 30; // 1.5秒
        };

        this.chargeTime = baseChargeTime;
        this.aimTime = 10; // 0.5秒のエイム時間
        this.isCharging = true;
        this.isAiming = false;

        // 投擲開始の音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.CROSSBOW_LOADING_START, SoundSource.HOSTILE,
                0.6F, 1.2F);

        TungSahurMod.LOGGER.debug("TungSahur投擲攻撃開始: Day={}, チャージ時間={}",
                this.tungSahur.getDayNumber(), this.chargeTime);
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // ターゲットを注視
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 移動を停止
        this.tungSahur.getNavigation().stop();

        if (this.isCharging) {
            handleCharging();
        } else if (this.isAiming) {
            handleAiming();
        }
    }

    private void handleCharging() {
        this.chargeTime--;

        // チャージ中のパーティクル効果
        if (this.chargeTime % 5 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnChargingParticles(serverLevel);
        }

        // チャージ中の音（途中で1回だけ）
        if (this.chargeTime == 10) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.HOSTILE,
                    0.5F, 1.1F);
        }

        if (this.chargeTime <= 0) {
            // チャージ完了、エイムフェーズに移行
            this.isCharging = false;
            this.isAiming = true;
            this.tungSahur.setCurrentlyThrowing(true);

            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.CROSSBOW_LOADING_END, SoundSource.HOSTILE,
                    0.7F, 1.0F);
        }
    }

    private void handleAiming() {
        this.aimTime--;

        // エイム中のパーティクル（集中）
        if (this.aimTime % 2 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnAimingParticles(serverLevel);
        }

        if (this.aimTime <= 0) {
            // 投擲実行
            executeThrow();
        }
    }

    private void executeThrow() {
        if (!this.tungSahur.canThrow() || this.target == null) {
            stop();
            return;
        }

        // 実際の投擲攻撃実行
        this.tungSahur.performThrowAttack(this.target);

        // 投擲後のクールダウン設定
        int throwCooldown = switch (this.tungSahur.getDayNumber()) {
            case 2 -> 80 + this.tungSahur.getRandom().nextInt(40); // 4-6秒
            case 3 -> 60 + this.tungSahur.getRandom().nextInt(40); // 3-5秒
            default -> 100; // 5秒
        };

        this.cooldownAfterThrow = throwCooldown;

        TungSahurMod.LOGGER.debug("TungSahur投擲実行完了: クールダウン={}tick", throwCooldown);

        stop();
    }

    private void spawnChargingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 体の周りに集中パーティクル
        for (int i = 0; i < 2; i++) {
            double x = tungPos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;
            double y = tungPos.y + this.tungSahur.getBbHeight() * 0.7 +
                    (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;
            double z = tungPos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
        }

        // 手の位置に特別なパーティクル
        Vec3 handPos = tungPos.add(0, this.tungSahur.getBbHeight() * 0.8, 0);
        serverLevel.sendParticles(ParticleTypes.WITCH,
                handPos.x, handPos.y, handPos.z,
                1, 0.05, 0.05, 0.05, 0.01);
    }

    private void spawnAimingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();
        Vec3 targetPos = this.target.position();

        // ターゲットに向かう軌道を表示
        Vec3 direction = targetPos.subtract(tungPos).normalize();

        for (int i = 1; i <= 3; i++) {
            Vec3 particlePos = tungPos.add(direction.scale(i * 2.0));
            particlePos = particlePos.add(0, this.tungSahur.getBbHeight() * 0.8, 0);

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    particlePos.x, particlePos.y, particlePos.z,
                    1, 0.1, 0.1, 0.1, 0.02);
        }

        // エイム集中パーティクル
        Vec3 eyePos = tungPos.add(0, this.tungSahur.getEyeHeight(), 0);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                eyePos.x, eyePos.y, eyePos.z,
                1, 0.05, 0.05, 0.05, 0.01);
    }

    @Override
    public void stop() {
        this.target = null;
        this.chargeTime = 0;
        this.aimTime = 0;
        this.isCharging = false;
        this.isAiming = false;
        this.tungSahur.setCurrentlyThrowing(false);

        TungSahurMod.LOGGER.debug("TungSahur投擲ゴール停止");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // クールダウン更新（エンティティのtickで呼び出される）
    public void updateCooldown() {
        if (this.cooldownAfterThrow > 0) {
            this.cooldownAfterThrow--;
        }
    }
}