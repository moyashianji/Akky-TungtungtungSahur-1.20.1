package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// 投擲攻撃ゴール
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
        this.throwChargeTime = 30; // 1.5秒のチャージ時間
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.throwChargeTime > 0) {
            this.throwChargeTime--;
            this.tungSahur.getNavigation().stop();
        } else {
            this.tungSahur.performThrowAttack(this.target);
            this.throwCooldown = 120; // 6秒のクールダウン
            this.stop();
        }

        // クールダウン処理
        if (this.throwCooldown > 0) {
            this.throwCooldown--;
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
