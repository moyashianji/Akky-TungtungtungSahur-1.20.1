// TungSahurThrowGoal.java - 投擲攻撃ゴール（2日目以降）
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
    private int chargeTime = 0;
    private int aimTime = 0;
    private boolean isCharging = false;

    public TungSahurThrowGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // 2日目以降のみ使用可能
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // 中距離から遠距離での使用
        return distance >= 6.0D && distance <= 20.0D &&
                this.tungSahur.canThrow() &&
                this.tungSahur.hasLineOfSight(this.target);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        double distance = this.tungSahur.distanceTo(this.target);
        return distance >= 4.0D && distance <= 25.0D && this.tungSahur.hasLineOfSight(this.target);
    }

    @Override
    public void start() {
        this.chargeTime = 20 + this.tungSahur.getRandom().nextInt(20); // 1-2秒のチャージ
        this.aimTime = 0;
        this.isCharging = true;

        // チャージ開始音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.CROSSBOW_LOADING_START, SoundSource.HOSTILE, 0.8F, 0.8F);

        // チャージ開始パーティクル
        spawnChargeParticles();
    }

    @Override
    public void stop() {
        this.chargeTime = 0;
        this.aimTime = 0;
        this.isCharging = false;
        this.tungSahur.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.isCharging) {
            // チャージ中は移動停止
            this.tungSahur.getNavigation().stop();

            this.chargeTime--;
            this.aimTime++;

            // チャージ中のパーティクル効果
            if (this.aimTime % 5 == 0) {
                spawnChargeParticles();
            }

            // チャージ完了時の処理
            if (this.chargeTime <= 0) {
                executeThrow();
                this.isCharging = false;
            }
        }
    }

    private void executeThrow() {
        if (this.target != null && this.tungSahur.canThrow()) {
            // 投擲実行
            this.tungSahur.performThrowAttack(this.target);

            // 投擲音
            this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.2F);

            // 投擲時のパーティクル
            spawnThrowParticles();
        }
    }

    private void spawnChargeParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // チャージ中の赤いパーティクル
            for (int i = 0; i < 3; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;
                double y = this.tungSahur.getY() + this.tungSahur.getBbHeight() * 0.7 +
                        (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;

                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }

            // 手元のエネルギーパーティクル
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.2, this.tungSahur.getZ(),
                    2, 0.3, 0.1, 0.3, 0.1);
        }
    }

    private void spawnThrowParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 投擲時の煙パーティクル
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.0, this.tungSahur.getZ(),
                    8, 0.5, 0.3, 0.5, 0.1);

            // 威力を示すパーティクル
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.2, this.tungSahur.getZ(),
                    6, 0.4, 0.2, 0.4, 0.2);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}