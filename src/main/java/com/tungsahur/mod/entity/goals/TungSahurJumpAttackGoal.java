package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// ジャンプ攻撃ゴール
class TungSahurJumpAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int jumpCooldown = 0;
    private int jumpChargeTime = 0;

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
        this.jumpChargeTime = 20; // 1秒のチャージ
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        if (this.jumpChargeTime > 0) {
            this.jumpChargeTime--;
            this.tungSahur.getNavigation().stop();
            // チャージ中は震える演出
            if (this.jumpChargeTime % 4 == 0) {
                this.tungSahur.setPos(
                        this.tungSahur.getX() + (this.tungSahur.getRandom().nextFloat() - 0.5F) * 0.1F,
                        this.tungSahur.getY(),
                        this.tungSahur.getZ() + (this.tungSahur.getRandom().nextFloat() - 0.5F) * 0.1F
                );
            }
        } else {
            performJumpAttack();
        }

        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
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

        // ジャンプ音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.2F);

        this.jumpCooldown = 100; // 5秒のクールダウン
        this.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.target.isAlive() && this.jumpChargeTime > 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.jumpChargeTime = 0;
    }
}
