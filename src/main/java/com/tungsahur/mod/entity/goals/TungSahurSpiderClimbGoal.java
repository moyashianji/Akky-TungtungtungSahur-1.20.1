// TungSahurSpiderClimbGoal.java - 蜘蛛と同じ仕組みの壁登り
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TungSahurSpiderClimbGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int climbTicks = 0;
    private boolean wasClimbing = false;

    public TungSahurSpiderClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        // 蜘蛛と同じように、移動フラグのみ使用
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;

        // ターゲットが高い位置にいる場合のみ壁登りを試行
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        if (heightDiff < 2.0D) return false;

        // 距離チェック
        double distance = this.tungSahur.distanceTo(this.target);
        if (distance > 16.0D) return false;

        TungSahurMod.LOGGER.info("壁登りGoal開始: 高度差={}, 距離={}", heightDiff, distance);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.climbTicks > 400) return false; // 20秒で終了

        // ターゲットの高さに到達したら終了
        if (this.tungSahur.getY() >= this.target.getY() - 1.0D) return false;

        return true;
    }

    @Override
    public void start() {
        this.climbTicks = 0;
        this.wasClimbing = false;

        // 開始音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                1.0F, 0.8F);

        TungSahurMod.LOGGER.info("壁登りGoal開始！位置: {}", this.tungSahur.position());
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.climbTicks++;

        // 蜘蛛と同じように、ターゲットに向かって移動するだけ
        // 実際の壁登りはtick()メソッドのsetClimbing(this.horizontalCollision)で自動的に行われる
        this.tungSahur.getNavigation().moveTo(this.target, 1.0D);

        // 壁登り状態の変化をデバッグ
        boolean currentlyClimbing = this.tungSahur.isClimbing();
        if (currentlyClimbing != this.wasClimbing) {
            TungSahurMod.LOGGER.info("壁登り状態変化: {} -> {}, 位置: {}",
                    this.wasClimbing, currentlyClimbing, this.tungSahur.position());
            this.wasClimbing = currentlyClimbing;
        }

        // デバッグ情報
        if (this.climbTicks % 20 == 0) {
            TungSahurMod.LOGGER.info("壁登り中: Tick={}, 位置={}, 登攀中={}, 水平衝突={}",
                    this.climbTicks, this.tungSahur.position(),
                    this.tungSahur.isClimbing(), this.tungSahur.horizontalCollision);
        }

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
    }

    @Override
    public void stop() {
        // 終了音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE,
                0.8F, 1.0F);

        TungSahurMod.LOGGER.info("壁登りGoal終了: 最終位置={}, 登攀状態={}",
                this.tungSahur.position(), this.tungSahur.isClimbing());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}