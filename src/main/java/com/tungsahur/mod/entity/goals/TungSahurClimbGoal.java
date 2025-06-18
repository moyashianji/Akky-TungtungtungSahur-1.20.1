// TungSahurClimbGoal.java - 完全版壁登りゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TungSahurClimbGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private boolean isClimbing = false;
    private Direction climbDirection = null;
    private int climbTimer = 0;
    private Vec3 lastClimbPosition = Vec3.ZERO;
    private int stuckTimer = 0;

    public TungSahurClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.tungSahur.canClimbWalls()) return false;

        // ターゲットが高い位置にいるかチェック
        double heightDifference = this.target.getY() - this.tungSahur.getY();
        if (heightDifference <= 2.0D) return false;

        // 壁に接触している且つターゲットが近くにいる
        double horizontalDistance = Math.sqrt(
                Math.pow(this.target.getX() - this.tungSahur.getX(), 2) +
                        Math.pow(this.target.getZ() - this.tungSahur.getZ(), 2)
        );

        return this.tungSahur.horizontalCollision &&
                horizontalDistance <= 16.0D &&
                heightDifference <= 20.0D &&
                canFindClimbableWall();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive() || !this.tungSahur.canClimbWalls()) {
            return false;
        }

        double heightDifference = this.target.getY() - this.tungSahur.getY();
        double horizontalDistance = Math.sqrt(
                Math.pow(this.target.getX() - this.tungSahur.getX(), 2) +
                        Math.pow(this.target.getZ() - this.tungSahur.getZ(), 2)
        );

        // 目標に到達したか、登攀が完了した場合は停止
        return heightDifference > 1.0D &&
                horizontalDistance <= 20.0D &&
                this.climbTimer < 200 && // 最大10秒間
                this.stuckTimer < 60; // 3秒間動けない場合は停止
    }

    @Override
    public void start() {
        this.isClimbing = true;
        this.climbTimer = 0;
        this.stuckTimer = 0;
        this.lastClimbPosition = this.tungSahur.position();
        this.climbDirection = findBestClimbDirection();
        this.tungSahur.setClimbing(true);

        // 登攀開始音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.5F, 1.2F);
    }

    @Override
    public void stop() {
        this.isClimbing = false;
        this.climbDirection = null;
        this.tungSahur.setClimbing(false);
        this.tungSahur.getNavigation().stop();

        // 登攀終了時のパーティクル
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.0, this.tungSahur.getZ(),
                    8, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @Override
    public void tick() {
        if (!this.isClimbing || this.target == null) return;

        this.climbTimer++;

        // スタック検出
        double moveDistance = this.tungSahur.position().distanceTo(this.lastClimbPosition);
        if (moveDistance < 0.1D) {
            this.stuckTimer++;
        } else {
            this.stuckTimer = 0;
            this.lastClimbPosition = this.tungSahur.position();
        }

        // 壁登りの実行
        performClimbing();

        // 登攀中のパーティクル効果
        if (this.climbTimer % 10 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
    }

        // 目標の方向を向く
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
    }

    private void performClimbing() {
        if (this.climbDirection == null) {
            this.climbDirection = findBestClimbDirection();
            if (this.climbDirection == null) return;
        }

        // 登攀速度（進化段階に応じて調整）
        double climbSpeed = 0.15D + (this.tungSahur.getEvolutionStage() * 0.05D);

        // 上方向の移動
        Vec3 upwardMotion = new Vec3(0, climbSpeed, 0);

        // 壁に沿った水平移動
        Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
        Vec3 horizontalMotion = wallDirection.scale(climbSpeed * 0.5D);

        // ターゲットに向かう方向調整
        if (this.target != null) {
            Vec3 toTarget = this.target.position().subtract(this.tungSahur.position()).normalize();

            // Y軸を除いた水平方向
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0, toTarget.z).normalize();
            horizontalMotion = horizontalToTarget.scale(climbSpeed * 0.3D);
        }

        // 最終的な移動ベクトル
        Vec3 climbMotion = upwardMotion.add(horizontalMotion);

        // 壁に向かって少し押し付ける力を追加
        Vec3 wallStickMotion = Vec3.atLowerCornerOf(this.climbDirection.getNormal()).scale(-0.1D);
        climbMotion = climbMotion.add(wallStickMotion);

        this.tungSahur.setDeltaMovement(climbMotion);
        this.tungSahur.hasImpulse = true;

        // 定期的に登攀可能性をチェック
        if (this.climbTimer % 20 == 0) {
            if (!canContinueClimbing()) {
                // 登攀不可能になった場合はジャンプで脱出
                performClimbJump();
            }
        }
    }

    private boolean canFindClimbableWall() {
        BlockPos entityPos = this.tungSahur.blockPosition();

        // 周囲4方向の壁をチェック
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = entityPos.relative(direction);
            if (isClimbableWall(wallPos)) {
                return true;
            }
        }

        return false;
    }

    private Direction findBestClimbDirection() {
        BlockPos entityPos = this.tungSahur.blockPosition();
        Direction bestDirection = null;
        double bestScore = -1.0D;

        // 各方向をチェックして最適な登攀方向を決定
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = entityPos.relative(direction);

            if (isClimbableWall(wallPos)) {
                double score = calculateClimbScore(direction);
                if (score > bestScore) {
                    bestScore = score;
                    bestDirection = direction;
                }
            }
        }

        return bestDirection;
    }

    private double calculateClimbScore(Direction direction) {
        if (this.target == null) return 0.0D;

        // ターゲットへの方向との一致度を計算
        Vec3 toTarget = this.target.position().subtract(this.tungSahur.position()).normalize();
        Vec3 wallDirection = Vec3.atLowerCornerOf(direction.getNormal());

        // 壁の方向とターゲットへの方向の類似度
        double similarity = wallDirection.dot(toTarget);

        // 壁の高さをチェック（高い壁ほど有利）
        BlockPos entityPos = this.tungSahur.blockPosition();
        BlockPos wallPos = entityPos.relative(direction);
        int wallHeight = getWallHeight(wallPos);

        return similarity + (wallHeight * 0.1D);
    }

    private boolean isClimbableWall(BlockPos pos) {
        BlockState blockState = this.tungSahur.level().getBlockState(pos);

        // 固体ブロックかつ、上部に少なくとも2ブロック分の空間がある
        if (!blockState.isSolid()) return false;

        // 上部の空間チェック
        for (int y = 1; y <= 3; y++) {
            if (!this.tungSahur.level().getBlockState(pos.above(y)).isAir()) {
                return y >= 2; // 最低2ブロック分の空間が必要
            }
        }

        return true;
    }

    private int getWallHeight(BlockPos wallBase) {
        int height = 0;

        for (int y = 0; y < 20; y++) {
            BlockPos checkPos = wallBase.above(y);
            if (this.tungSahur.level().getBlockState(checkPos).isSolid()) {
                height++;
            } else {
                break;
            }
        }

        return height;
    }

    private boolean canContinueClimbing() {
        if (this.climbDirection == null) return false;

        BlockPos entityPos = this.tungSahur.blockPosition();
        BlockPos wallPos = entityPos.relative(this.climbDirection);

        // 現在の位置の壁が登攀可能か
        if (!isClimbableWall(wallPos)) return false;

        // 上方向に空間があるか
        BlockPos abovePos = entityPos.above();
        return this.tungSahur.level().getBlockState(abovePos).isAir() &&
                this.tungSahur.level().getBlockState(abovePos.above()).isAir();
    }

    private void performClimbJump() {
        // 登攀終了時の強力なジャンプ
        double jumpPower = 0.6D + (this.tungSahur.getEvolutionStage() * 0.2D);

        Vec3 jumpDirection = Vec3.ZERO;
        if (this.target != null) {
            jumpDirection = this.target.position().subtract(this.tungSahur.position()).normalize();
            jumpDirection = new Vec3(jumpDirection.x * 0.3D, 0, jumpDirection.z * 0.3D);
        }

        this.tungSahur.setDeltaMovement(
                jumpDirection.x,
                jumpPower,
                jumpDirection.z
        );

        this.tungSahur.hasImpulse = true;

        // ジャンプ音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.5F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}