// TungSahurMeleeAttackGoal.java - 近接攻撃ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class TungSahurMeleeAttackGoal extends MeleeAttackGoal {
    private final TungSahurEntity tungSahur;
    private int attackDelay = 0;

    public TungSahurMeleeAttackGoal(TungSahurEntity tungSahur, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(tungSahur, speedModifier, followingTargetEvenIfNotSeen);
        this.tungSahur = tungSahur;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.tungSahur.getTarget();
        if (target == null) return false;

        double distance = this.tungSahur.distanceTo(target);
        return distance <= 3.0D && this.tungSahur.canAttack() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.tungSahur.getTarget();
        if (target == null) return false;

        double distance = this.tungSahur.distanceTo(target);
        return distance <= 4.0D && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        this.attackDelay = 0;
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = this.tungSahur.getTarget();
        if (target != null) {
            this.tungSahur.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (this.attackDelay > 0) {
                this.attackDelay--;
            }
        }
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distanceToTargetSqr) {
        double attackReach = this.getAttackReachSqr(target);

        if (distanceToTargetSqr <= attackReach && this.attackDelay <= 0) {
            this.resetAttackCooldown();
            this.tungSahur.performMeleeAttack(target);
            this.attackDelay = 10; // 攻撃後の短いディレイ
        }
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        // 攻撃範囲を進化段階に応じて調整
        double baseReach = this.tungSahur.getBbWidth() * 2.0F * this.tungSahur.getBbWidth() * 2.0F + target.getBbWidth();
        return baseReach + (this.tungSahur.getEvolutionStage() * 0.5D);
    }

    @Override
    protected int adjustedTickDelay(int tickDelay) {
        // 進化段階に応じて攻撃速度調整
        int adjustment = this.tungSahur.getEvolutionStage() * 2;
        return Math.max(1, tickDelay - adjustment);
    }
}