package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;

// 基本攻撃ゴール
public class TungSahurAttackGoal extends MeleeAttackGoal {
    private final TungSahurEntity tungSahur;
    private int attackDelay = 0;

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
            this.resetAttackCooldown();
            this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
            this.attackDelay = 40; // 2秒のクールダウン
        }

        if (this.attackDelay > 0) {
            this.attackDelay--;
        }
    }
}

