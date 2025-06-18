// TungSahurSmartMoveToTargetGoal.java - 強化版インテリジェント移動ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

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
    private int consecutiveFailures = 0;
    private Vec3 lastPosition = Vec3.ZERO;

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
        this.consecutiveFailures = 0;
        this.lastPosition = this.tungSahur.position();
        updatePath();
    }

    @Override
    public void stop() {
        this.target = null;
        this.path = null;
        this.tungSahur.getNavigation().stop();
        this.consecutiveFailures = 0;
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        double distance = this.tungSahur.distanceTo(this.target);
        boolean shouldUpdatePath = false;

        // パス再計算の条件チェック
        this.lastPathRecalc++;
        if (this.lastPathRecalc > 40) { // 2秒ごと
            shouldUpdatePath = true;
        }

        // ターゲットが大きく移動した場合
        double targetMoveDistance = Math.sqrt(
                Math.pow(this.target.getX() - this.targetX, 2) +
                        Math.pow(this.target.getY() - this.targetY, 2) +
                        Math.pow(this.target.getZ() - this.targetZ, 2)
        );

        if (targetMoveDistance > 3.0D) {
            shouldUpdatePath = true;
        }

        // スタック検出とジャンプ処理
        handleStuckDetection();

        // 障害物回避処理
        handleObstacleAvoidance();

        if (shouldUpdatePath) {
            updatePath();
        }

        // 速度調整
        adjustMovementSpeed(distance);
    }

    /**
     * スタック検出とジャンプ処理
     */
    private void handleStuckDetection() {
        Vec3 currentPos = this.tungSahur.position();
        double movementDistance = this.lastPosition.distanceTo(currentPos);

        if (movementDistance < 0.1D) {
            this.ticksStuck++;

            if (this.ticksStuck > 20) { // 1秒間動かない
                // 前方の障害物をチェック
                Vec3 lookDirection = this.tungSahur.getLookAngle();
                BlockPos frontPos = this.tungSahur.blockPosition().offset(
                        (int)(lookDirection.x * 2), 0, (int)(lookDirection.z * 2)
                );

                // 段差がある場合はジャンプ
                if (canJumpOverObstacle(frontPos)) {
                    performSmartJump();
                } else {
                    // ジャンプできない場合は迂回パスを探す
                    findAlternatePath();
                }

                this.ticksStuck = 0;
            }
        } else {
            this.ticksStuck = 0;
        }

        this.lastPosition = currentPos;
    }

    /**
     * 障害物回避処理
     */
    private void handleObstacleAvoidance() {
        if (this.tungSahur.horizontalCollision) {
            // 水平衝突時の処理
            Vec3 lookDirection = this.tungSahur.getLookAngle();
            BlockPos frontPos = this.tungSahur.blockPosition().offset(
                    (int)lookDirection.x, 0, (int)lookDirection.z
            );

            // 1ブロック高の障害物なら自動ジャンプ
            if (canJumpOverObstacle(frontPos)) {
                performSmartJump();
            } else {
                // より高い障害物の場合は登攀を試行
                if (this.tungSahur.canClimbWalls()) {
                    this.tungSahur.setClimbing(true);
                }
            }
        }
    }

    /**
     * ジャンプ可能な障害物かチェック
     */
    private boolean canJumpOverObstacle(BlockPos obstaclePos) {
        // 障害物の高さをチェック
        int heightCheck = 0;
        for (int y = 0; y < 3; y++) {
            if (!this.tungSahur.level().getBlockState(obstaclePos.above(y)).isAir()) {
                heightCheck++;
            } else {
                break;
            }
        }

        // 1-2ブロックの高さならジャンプ可能
        return heightCheck <= 2 && heightCheck > 0;
    }

    /**
     * スマートジャンプの実行
     */
    private void performSmartJump() {
        if (this.tungSahur.onGround()) {
            // ジャンプ力を調整（進化段階に応じて）
            double jumpPower = 0.42D + (this.tungSahur.getEvolutionStage() * 0.1D);

            Vec3 currentVelocity = this.tungSahur.getDeltaMovement();
            Vec3 forwardDirection = this.tungSahur.getLookAngle().scale(0.2D);

            this.tungSahur.setDeltaMovement(
                    currentVelocity.x + forwardDirection.x,
                    jumpPower,
                    currentVelocity.z + forwardDirection.z
            );

            this.tungSahur.hasImpulse = true;
        }
    }

    /**
     * 代替パスの探索
     */
    private void findAlternatePath() {
        if (this.target == null) return;

        PathNavigation navigation = this.tungSahur.getNavigation();

        // 左右に迂回するパスを試行
        Vec3 toTarget = this.target.position().subtract(this.tungSahur.position()).normalize();
        Vec3 leftDirection = new Vec3(-toTarget.z, 0, toTarget.x).scale(3.0D);
        Vec3 rightDirection = new Vec3(toTarget.z, 0, -toTarget.x).scale(3.0D);

        // 左側迂回を試行
        Vec3 leftPos = this.tungSahur.position().add(leftDirection);
        Path leftPath = navigation.createPath(leftPos.x, leftPos.y, leftPos.z, 1);

        if (leftPath != null && leftPath.canReach()) {
            navigation.moveTo(leftPath, this.speedModifier);
            return;
        }

        // 右側迂回を試行
        Vec3 rightPos = this.tungSahur.position().add(rightDirection);
        Path rightPath = navigation.createPath(rightPos.x, rightPos.y, rightPos.z, 1);

        if (rightPath != null && rightPath.canReach()) {
            navigation.moveTo(rightPath, this.speedModifier);
            return;
        }

        // 後退してからアプローチ
        Vec3 backwardPos = this.tungSahur.position().subtract(toTarget.scale(2.0D));
        Path backwardPath = navigation.createPath(backwardPos.x, backwardPos.y, backwardPos.z, 1);

        if (backwardPath != null && backwardPath.canReach()) {
            navigation.moveTo(backwardPath, this.speedModifier * 0.8D);
        }
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
            // 直接ターゲットに向かう
            this.path = navigation.createPath(this.target, 1);
        } else {
            // 距離を保つための位置を計算
            Vec3 direction = this.tungSahur.position().subtract(this.target.position()).normalize();
            Vec3 keepDistancePos = this.target.position().add(direction.scale(targetDistance));

            this.path = navigation.createPath(keepDistancePos.x, keepDistancePos.y, keepDistancePos.z, 1);
        }

        if (this.path != null && this.path.canReach()) {
            navigation.moveTo(this.path, this.speedModifier);
            this.consecutiveFailures = 0;
        } else {
            this.consecutiveFailures++;

            // 連続失敗時は代替手段を使用
            if (this.consecutiveFailures > 3) {
                findAlternatePath();
            }
        }
    }

    private void adjustMovementSpeed(double distance) {
        double baseSpeed = this.speedModifier;

        // 距離に応じた速度調整
        if (distance > 15.0D) {
            baseSpeed *= 1.2D; // 遠い場合は高速移動
        } else if (distance < 6.0D) {
            baseSpeed *= 0.8D; // 近い場合は慎重に
        }

        // 進化段階による調整
        baseSpeed *= (1.0D + this.tungSahur.getEvolutionStage() * 0.1D);

        // 見られている時の速度低下
        if (this.tungSahur.isBeingWatched()) {
            baseSpeed *= 0.3D;
        }

        // ナビゲーションの速度を更新
        if (this.path != null) {
            this.tungSahur.getNavigation().setSpeedModifier(baseSpeed);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}