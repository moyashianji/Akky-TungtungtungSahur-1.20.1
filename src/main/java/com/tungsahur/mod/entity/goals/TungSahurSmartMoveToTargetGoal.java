// TungSahurSmartMoveToTargetGoal.java - インテリジェントな移動ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

public class TungSahurSmartMoveToTargetGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private final double speedModifier;
    private LivingEntity target;
    private Path path;
    private int delayCounter = 0;
    private double targetX, targetY, targetZ;
    private int ticksStuck = 0;
    private int lastPathRecalc = 0;

    public TungSahurSmartMoveToTargetGoal(TungSahurEntity tungSahur, double speedModifier) {
        this.tungSahur = tungSahur;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.target.isAlive()) {
            return false;
        }

        double distance = this.tungSahur.distanceTo(this.target);

        // 近接攻撃範囲外の場合のみ移動
        return distance > 3.5D && distance < 32.0D;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) {
            return false;
        }

        double distance = this.tungSahur.distanceTo(this.target);

        // 近すぎる場合や遠すぎる場合は停止
        return distance > 2.5D && distance < 40.0D &&
                !this.tungSahur.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.delayCounter = 0;
        this.ticksStuck = 0;
        this.lastPathRecalc = 0;
        updatePath();
    }

    @Override
    public void stop() {
        this.target = null;
        this.tungSahur.getNavigation().stop();
        this.path = null;
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        double distance = this.tungSahur.distanceTo(this.target);

        // 距離に応じた戦略的な移動
        if (distance <= 3.5D) {
            // 近すぎる場合は少し離れる
            moveAwayFromTarget();
        } else if (distance >= 8.0D && this.tungSahur.getEvolutionStage() >= 1) {
            // 遠距離では投擲攻撃のために停止する場合もある
            if (this.tungSahur.canThrow() && this.tungSahur.hasLineOfSight(this.target)) {
                this.tungSahur.getNavigation().stop();
                return;
            }
        }

        // パス更新の判定
        this.delayCounter++;
        this.lastPathRecalc++;

        boolean shouldUpdatePath = false;

        // 定期的なパス更新
        if (this.delayCounter >= 10) {
            this.delayCounter = 0;
            shouldUpdatePath = true;
        }

        // ターゲットが大きく移動した場合
        if (this.lastPathRecalc >= 20) {
            double dx = this.target.getX() - this.targetX;
            double dy = this.target.getY() - this.targetY;
            double dz = this.target.getZ() - this.targetZ;
            double distanceMoved = dx * dx + dy * dy + dz * dz;

            if (distanceMoved > 4.0D) {
                shouldUpdatePath = true;
            }
        }

        // スタック検出
        if (this.tungSahur.getDeltaMovement().horizontalDistanceSqr() < 0.01D) {
            this.ticksStuck++;
            if (this.ticksStuck > 20) {
                shouldUpdatePath = true;
                this.ticksStuck = 0;

                // スタック時のジャンプ試行
                if (this.tungSahur.onGround() && this.tungSahur.getRandom().nextFloat() < 0.3F) {
                    this.tungSahur.setDeltaMovement(
                            this.tungSahur.getDeltaMovement().add(0, 0.4D, 0)
                    );
                }
            }
        } else {
            this.ticksStuck = 0;
        }

        if (shouldUpdatePath) {
            updatePath();
        }

        // 速度調整
        adjustMovementSpeed(distance);
    }

    private void updatePath() {
        if (this.target == null) return;

        this.targetX = this.target.getX();
        this.targetY = this.target.getY();
        this.targetZ = this.target.getZ();
        this.lastPathRecalc = 0;

        PathNavigation navigation = this.tungSahur.getNavigation();

        // 戦略的な移動先の決定
        double distance = this.tungSahur.distanceTo(this.target);
        double targetDistance;

        if (this.tungSahur.getEvolutionStage() >= 1) {
            // 進化段階が高い場合は中距離を維持
            if (distance < 5.0D) {
                targetDistance = 5.0D;
            } else if (distance > 12.0D) {
                targetDistance = 8.0D;
            } else {
                targetDistance = distance * 0.8D; // やや近づく
            }
        } else {
            // 初期段階では近接戦重視
            targetDistance = 3.0D;
        }

        // パスの生成
        if (targetDistance < distance) {
            // ターゲットに近づく
            this.path = navigation.createPath(this.target, 0);
        } else {
            // 適切な距離を維持
            this.path = navigation.createPath(this.targetX, this.targetY, this.targetZ, 0);
        }

        if (this.path != null) {
            navigation.moveTo(this.path, this.speedModifier);
        }
    }

    private void moveAwayFromTarget() {
        // ターゲットから離れる方向に移動
        double dx = this.tungSahur.getX() - this.target.getX();
        double dz = this.tungSahur.getZ() - this.target.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length > 0) {
            double moveDistance = 3.0D;
            double newX = this.tungSahur.getX() + (dx / length) * moveDistance;
            double newZ = this.tungSahur.getZ() + (dz / length) * moveDistance;

            this.tungSahur.getNavigation().moveTo(newX, this.tungSahur.getY(), newZ, this.speedModifier * 0.8D);
        }
    }

    private void adjustMovementSpeed(double distance) {
        double speedMultiplier = this.speedModifier;

        // 距離に応じた速度調整
        if (distance > 12.0D) {
            // 遠距離では高速移動
            speedMultiplier *= 1.3D;
        } else if (distance < 5.0D) {
            // 近距離では慎重に移動
            speedMultiplier *= 0.8D;
        }

        // 進化段階による調整
        speedMultiplier *= (1.0D + this.tungSahur.getEvolutionStage() * 0.15D);

        // 現在のナビゲーションの速度を更新
        if (this.tungSahur.getNavigation().isInProgress()) {
            this.tungSahur.getNavigation().setSpeedModifier(speedMultiplier);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}