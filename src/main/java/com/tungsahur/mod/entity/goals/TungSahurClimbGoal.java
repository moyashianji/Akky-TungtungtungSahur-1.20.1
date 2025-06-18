// TungSahurClimbGoal.java - 蜘蛛のような壁登りゴール
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

    public TungSahurClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

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
        if (this.target == null || !this.target.isAlive()) return false;

        double heightDifference = this.target.getY() - this.tungSahur.getY();
        double horizontalDistance = Math.sqrt(
                Math.pow(this.target.getX() - this.tungSahur.getX(), 2) +
                        Math.pow(this.target.getZ() - this.tungSahur.getZ(), 2)
        );

        // 登り続ける条件
        return (heightDifference > 1.0D || this.isClimbing) &&
                horizontalDistance <= 20.0D &&
                (this.tungSahur.horizontalCollision || this.isClimbing);
    }

    @Override
    public void start() {
        this.isClimbing = true;
        this.climbTimer = 0;
        this.climbDirection = findBestClimbDirection();
        this.tungSahur.setClimbing(true);
        this.lastClimbPosition = this.tungSahur.position();

        // 登り開始音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.8F, 0.8F);

        // 登り開始パーティクル
        spawnClimbStartParticles();
    }

    @Override
    public void stop() {
        this.isClimbing = false;
        this.climbDirection = null;
        this.climbTimer = 0;
        this.tungSahur.setClimbing(false);
        this.tungSahur.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.target == null || !this.isClimbing) return;

        this.climbTimer++;

        // 登り続ける処理
        performClimbing();

        // 登り中のパーティクル効果（控えめに）
        if (this.climbTimer % 10 == 0) {
            spawnClimbingParticles();
        }

        // 登り中の音効果
        if (this.climbTimer % 15 == 0) {
            this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.4F, 1.2F);
        }

        // スタック検知と対処
        if (this.climbTimer % 20 == 0) {
            checkForStuckAndAdjust();
        }
    }

    private boolean canFindClimbableWall() {
        BlockPos pos = this.tungSahur.blockPosition();

        // 周囲4方向の壁をチェック
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = pos.relative(direction);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            if (!wallState.isAir() && wallState.isSolidRender(this.tungSahur.level(), wallPos)) {
                return true;
            }
        }
        return false;
    }

    private Direction findBestClimbDirection() {
        BlockPos pos = this.tungSahur.blockPosition();
        Vec3 toTarget = this.target.position().subtract(this.tungSahur.position()).normalize();

        Direction bestDirection = null;
        double bestAlignment = -2.0D;

        // ターゲット方向に最も近い壁を探す
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = pos.relative(direction);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            if (!wallState.isAir() && wallState.isSolidRender(this.tungSahur.level(), wallPos)) {
                Vec3 directionVec = Vec3.atLowerCornerOf(direction.getNormal());
                double alignment = directionVec.dot(toTarget);

                if (alignment > bestAlignment) {
                    bestAlignment = alignment;
                    bestDirection = direction;
                }
            }
        }

        return bestDirection;
    }

    private void performClimbing() {
        if (this.climbDirection == null) {
            this.climbDirection = findBestClimbDirection();
            if (this.climbDirection == null) {
                this.stop();
                return;
            }
        }

        // 壁に向かって移動力を加える
        Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
        double climbSpeed = 0.15D + (this.tungSahur.getEvolutionStage() * 0.05D);

        // 上昇速度の計算
        double upwardSpeed = climbSpeed;

        // ターゲットが真上にいる場合は上昇速度を上げる
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        if (heightDiff > 5.0D) {
            upwardSpeed *= 1.5D;
        }

        // 横方向への軽い移動（壁に沿って）
        double horizontalSpeed = climbSpeed * 0.3D;

        Vec3 currentVelocity = this.tungSahur.getDeltaMovement();
        Vec3 climbVelocity = new Vec3(
                wallDirection.x * horizontalSpeed,
                upwardSpeed,
                wallDirection.z * horizontalSpeed
        );

        this.tungSahur.setDeltaMovement(climbVelocity);

        // ターゲットの方向を向く
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 壁がなくなったら方向を再計算
        BlockPos wallPos = this.tungSahur.blockPosition().relative(this.climbDirection);
        BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

        if (wallState.isAir() || !wallState.isSolidRender(this.tungSahur.level(), wallPos)) {
            this.climbDirection = findBestClimbDirection();
        }
    }

    private void checkForStuckAndAdjust() {
        Vec3 currentPos = this.tungSahur.position();
        double distanceMoved = currentPos.distanceTo(this.lastClimbPosition);

        // 移動距離が少ない場合はスタックと判定
        if (distanceMoved < 0.1D) {
            // 方向を変更して登り直す
            this.climbDirection = findAlternativeClimbDirection();

            // 少しジャンプして位置を調整
            if (this.tungSahur.onGround() || this.climbTimer % 40 == 0) {
                Vec3 jumpVec = new Vec3(
                        (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.4,
                        0.3D,
                        (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.4
                );
                this.tungSahur.setDeltaMovement(this.tungSahur.getDeltaMovement().add(jumpVec));
            }
        }

        this.lastClimbPosition = currentPos;
    }

    private Direction findAlternativeClimbDirection() {
        BlockPos pos = this.tungSahur.blockPosition();

        // 現在の方向以外で登れる壁を探す
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction == this.climbDirection) continue;

            BlockPos wallPos = pos.relative(direction);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            if (!wallState.isAir() && wallState.isSolidRender(this.tungSahur.level(), wallPos)) {
                return direction;
            }
        }

        return this.climbDirection; // 代替が見つからない場合は現在の方向を維持
    }

    private void spawnClimbStartParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 登り開始の爪痕パーティクル
            for (int i = 0; i < 8; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.0;
                double y = this.tungSahur.getY() + this.tungSahur.getRandom().nextDouble() * 1.5;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.0;

                serverLevel.sendParticles(ParticleTypes.CRIT,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
            }

            // 壁の削りカスパーティクル
            if (this.climbDirection != null) {
                BlockPos wallPos = this.tungSahur.blockPosition().relative(this.climbDirection);
                BlockState wallState = this.tungSahur.level().getBlockState(wallPos);


      }
        }
    }

    private void spawnClimbingParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 登り中の爪痕パーティクル
            double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.6;
            double y = this.tungSahur.getY() + this.tungSahur.getRandom().nextDouble() * this.tungSahur.getBbHeight();
            double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.6;

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 1, 0.0, 0.1, 0.0, 0.01);

            // 時々火花パーティクル
            if (this.climbTimer % 30 == 0) {
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        x, y, z, 2, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}