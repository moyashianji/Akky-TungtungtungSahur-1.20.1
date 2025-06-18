// TungSahurWallClimbGoal.java - 壁登りゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TungSahurWallClimbGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private Direction climbDirection;
    private int climbTicks = 0;
    private int maxClimbHeight = 0;
    private BlockPos startClimbPos;
    private boolean isActivelyClimbing = false;

    public TungSahurWallClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // 見られている時は壁登りしない
        if (this.tungSahur.isBeingWatched()) return false;

        // 既に壁に衝突している場合のみ
        if (!this.tungSahur.horizontalCollision) return false;

        // ターゲットが高い位置にいる場合
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        if (heightDiff < 1.5D) return false;

        // 壁登り可能な方向を検出
        this.climbDirection = findClimbableDirection();
        if (this.climbDirection == null) return false;

        // ターゲットまでの距離が適切な範囲内
        double horizontalDistance = this.tungSahur.distanceTo(this.target);
        return horizontalDistance <= 16.0D;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られている時は中止
        if (this.tungSahur.isBeingWatched()) return false;

        // 最大登攀高度に達した場合は終了
        if (this.climbTicks > this.maxClimbHeight * 10) return false;

        // ターゲットが近くに来た場合は終了
        double distance = this.tungSahur.distanceTo(this.target);
        if (distance <= 3.0D) return false;

        // まだ壁にいるかチェック
        return this.tungSahur.horizontalCollision || this.isActivelyClimbing;
    }

    @Override
    public void start() {
        this.climbTicks = 0;
        this.startClimbPos = this.tungSahur.blockPosition();
        this.isActivelyClimbing = true;
        this.tungSahur.setWallClimbing(true);

        // 最大登攀高度を設定（ターゲットの高さ + 余裕）
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        this.maxClimbHeight = (int) Math.min(heightDiff + 3, 10); // 最大10ブロック

        // 壁登り開始音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                0.6F, 1.2F);

        TungSahurMod.LOGGER.debug("TungSahur壁登り開始: 方向={}, 最大高度={}",
                this.climbDirection, this.maxClimbHeight);
    }

    @Override
    public void tick() {
        if (this.target == null || this.climbDirection == null) return;

        this.climbTicks++;

        // 壁登り動作の実行
        performClimbing();

        // パーティクル効果
        if (this.climbTicks % 8 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnClimbingParticles(serverLevel);
        }

        // 壁登り音（定期的に）
        if (this.climbTicks % 15 == 0) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                    0.4F, 1.0F + this.tungSahur.getRandom().nextFloat() * 0.4F);
        }

        // 登攀完了チェック
        double currentHeight = this.tungSahur.getY() - this.startClimbPos.getY();
        if (currentHeight >= this.maxClimbHeight) {
            // 登攀完了、頂上に到達
            completeClimb();
        }
    }

    private void performClimbing() {
        // 蜘蛛のような壁登り物理
        Vec3 currentMovement = this.tungSahur.getDeltaMovement();

        // 上方向の移動
        double climbSpeed = 0.15D + (this.tungSahur.getDayNumber() * 0.02D);

        // 壁に向かう微小な水平移動（壁から離れないように）
        double wallPushForce = 0.02D;
        Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal()).scale(wallPushForce);

        // 新しい移動ベクトルを設定
        Vec3 newMovement = new Vec3(
                currentMovement.x + wallDirection.x,
                climbSpeed,
                currentMovement.z + wallDirection.z
        );

        this.tungSahur.setDeltaMovement(newMovement);

        // ナビゲーションを停止（壁登り中は通常の移動を無効化）
        this.tungSahur.getNavigation().stop();

        // 壁の方向を向く
        double targetYaw = getYawForDirection(this.climbDirection);
        this.tungSahur.setYRot((float) targetYaw);
    }

    private Direction findClimbableDirection() {
        BlockPos tungPos = this.tungSahur.blockPosition();

        // 水平方向の4方向をチェック
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = tungPos.relative(direction);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            // 固体ブロックかチェック
            if (wallState.isSolid() && !wallState.isAir()) {
                // さらに上方向もチェックして登れる高さがあるか確認
                boolean canClimbUp = true;
                for (int i = 1; i <= Math.min(this.maxClimbHeight, 3); i++) {
                    BlockPos checkPos = wallPos.above(i);
                    BlockState checkState = this.tungSahur.level().getBlockState(checkPos);
                    if (!checkState.isSolid() || checkState.isAir()) {
                        canClimbUp = false;
                        break;
                    }
                }

                if (canClimbUp) {
                    return direction;
                }
            }
        }

        return null;
    }

    private double getYawForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0D;
            case SOUTH -> 0.0D;
            case WEST -> 90.0D;
            case EAST -> -90.0D;
            default -> this.tungSahur.getYRot();
        };
    }

    private void spawnClimbingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 壁との接触点にパーティクル
        Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
        Vec3 contactPoint = tungPos.add(wallDirection.scale(0.7D));

        // 爪による引っかき音のパーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                contactPoint.x,
                contactPoint.y + this.tungSahur.getBbHeight() * 0.7,
                contactPoint.z,
                2, 0.1, 0.2, 0.1, 0.02);

        // 壁の破片パーティクル
        BlockPos wallPos = this.tungSahur.blockPosition().relative(this.climbDirection);
        BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

        if (!wallState.isAir()) {

 }

        // 足元の塵
        serverLevel.sendParticles(ParticleTypes.POOF,
                tungPos.x, tungPos.y + 0.1, tungPos.z,
                1, 0.2, 0.1, 0.2, 0.02);
    }

    private void completeClimb() {
        // 登攀完了時の処理
        TungSahurMod.LOGGER.debug("TungSahur壁登り完了: 到達高度={}",
                this.tungSahur.getY() - this.startClimbPos.getY());

        // 頂上でのちょっとした前進（壁から離れる）
        Vec3 forwardDirection = Vec3.atLowerCornerOf(this.climbDirection.getOpposite().getNormal());
        Vec3 currentMovement = this.tungSahur.getDeltaMovement();
        Vec3 finishMovement = currentMovement.add(forwardDirection.scale(0.3D));
        this.tungSahur.setDeltaMovement(finishMovement);

        // 完了音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE,
                0.8F, 1.1F);

        // 完了パーティクル
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnClimbCompleteParticles(serverLevel);
        }

        stop();
    }

    private void spawnClimbCompleteParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 達成感のあるパーティクル
        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                tungPos.x,
                tungPos.y + this.tungSahur.getBbHeight() * 0.8,
                tungPos.z,
                3, 0.3, 0.3, 0.3, 0.1);

        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                tungPos.x,
                tungPos.y + this.tungSahur.getBbHeight() * 0.5,
                tungPos.z,
                5, 0.5, 0.3, 0.5, 0.02);
    }

    @Override
    public void stop() {
        this.target = null;
        this.climbDirection = null;
        this.climbTicks = 0;
        this.maxClimbHeight = 0;
        this.startClimbPos = null;
        this.isActivelyClimbing = false;
        this.tungSahur.setWallClimbing(false);

        TungSahurMod.LOGGER.debug("TungSahur壁登り終了");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // 壁登り状態の外部チェック用
    public boolean isClimbing() {
        return this.isActivelyClimbing;
    }

    // 緊急停止用（見つかった時など）
    public void forceStop() {
        if (this.isActivelyClimbing) {
            // 壁から離れる動作
            if (this.climbDirection != null) {
                Vec3 escapeDirection = Vec3.atLowerCornerOf(this.climbDirection.getOpposite().getNormal());
                Vec3 currentMovement = this.tungSahur.getDeltaMovement();
                Vec3 escapeMovement = currentMovement.add(escapeDirection.scale(0.2D));
                this.tungSahur.setDeltaMovement(escapeMovement);
            }

            // 緊急停止音
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.SPIDER_HURT, SoundSource.HOSTILE,
                    0.5F, 1.5F);
        }

        stop();
    }
}