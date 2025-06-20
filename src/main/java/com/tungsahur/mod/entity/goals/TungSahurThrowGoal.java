// TungSahurThrowGoal.java - シンプル投擲Goal（移動阻害なし）
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TungSahurThrowGoal extends Goal {
    private final TungSahurEntity tungSahur;

    public TungSahurThrowGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        // フラグを設定しない（移動を阻害しない）
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        // このGoalは実際には使用されない（エンティティ側で直接管理）
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        // 何もしない
    }

    @Override
    public void tick() {
        // 何もしない
    }

    @Override
    public void stop() {
        // 何もしない
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    // クールダウン更新（後方互換性のため）
    public void updateCooldown() {
        // 何もしない（エンティティ側で管理）
    }

    // デバッグ情報
    public boolean isInChargePhase() {
        return tungSahur.isCurrentlyThrowing();
    }

    public boolean isInAimPhase() {
        return tungSahur.isCurrentlyThrowing();
    }
}