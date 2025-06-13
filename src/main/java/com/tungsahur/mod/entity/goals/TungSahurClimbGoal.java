package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// 壁登りゴール
public class TungSahurClimbGoal extends Goal {
    private final TungSahurEntity tungSahur;

    public TungSahurClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.tungSahur.horizontalCollision && this.tungSahur.getTarget() != null;
    }

    @Override
    public void tick() {
        if (this.tungSahur.horizontalCollision) {
            this.tungSahur.setDeltaMovement(
                    this.tungSahur.getDeltaMovement().x,
                    0.2D, // 上昇速度
                    this.tungSahur.getDeltaMovement().z
            );
        }
    }
}
