// TungSahurWallClimbGoal.java - 強化された壁登りシステム
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class TungSahurWallClimbGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private Direction climbDirection;
    private int climbTicks = 0;
    private int maxClimbHeight = 0;
    private BlockPos startClimbPos;
    private BlockPos targetReachPos;
    private boolean isActivelyClimbing = false;
    private boolean hasReachedTarget = false;

    // 強化された壁登り設定
    private static final double MIN_HEIGHT_DIFF = 2.0D; // 最小高度差（2ブロック以上）
    private static final double MAX_CLIMB_DISTANCE = 24.0D; // 最大登攀開始距離
    private static final double MAX_CLIMB_HEIGHT = 16.0D; // 最大登攀高度
    private static final double CLIMB_SPEED_BASE = 0.2D; // 基本登攀速度
    private static final int CLIMB_DETECTION_RADIUS = 3; // 壁検出半径

    // 登攀経路計算用
    private final List<BlockPos> climbPath = new ArrayList<>();
    private int currentPathIndex = 0;

    public TungSahurWallClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られている時は壁登りしない（恐怖演出）
        if (this.tungSahur.isBeingWatched()) return false;

        // ターゲットが高い位置にいる場合のみ
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        if (heightDiff < MIN_HEIGHT_DIFF) return false;

        // ターゲットまでの水平距離をチェック
        double horizontalDistance = getHorizontalDistance(this.tungSahur.position(), this.target.position());
        if (horizontalDistance > MAX_CLIMB_DISTANCE) return false;

        // 壁登り可能なルートを検索
        ClimbRoute route = findOptimalClimbRoute();
        if (route == null) return false;

        // ルート情報を設定
        this.climbDirection = route.direction;
        this.climbPath.clear();
        this.climbPath.addAll(route.path);
        this.currentPathIndex = 0;
        this.targetReachPos = route.targetPos;

        TungSahurMod.LOGGER.debug("壁登りルート発見: 方向={}, 経路長={}, 目標高度差={}",
                this.climbDirection, this.climbPath.size(), heightDiff);

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られている時は中止
        if (this.tungSahur.isBeingWatched()) return false;

        // 登攀完了チェック
        if (this.hasReachedTarget) return false;

        // 最大登攀時間チェック（30秒）
        if (this.climbTicks > 600) return false;

        // ターゲットが近くに来た場合は終了
        double distance = this.tungSahur.distanceTo(this.target);
        if (distance <= 3.0D) return false;

        // 経路が有効かチェック
        return this.currentPathIndex < this.climbPath.size() || this.isActivelyClimbing;
    }

    @Override
    public void start() {
        this.climbTicks = 0;
        this.currentPathIndex = 0;
        this.startClimbPos = this.tungSahur.blockPosition();
        this.isActivelyClimbing = true;
        this.hasReachedTarget = false;
        this.tungSahur.setWallClimbing(true);

        // 最大登攀高度を設定
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        this.maxClimbHeight = (int) Math.min(heightDiff + 3, MAX_CLIMB_HEIGHT);

        // 壁登り開始音（より迫力のあるサウンド）
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                0.8F, 0.8F);

        // 開始時の恐怖パーティクル
        spawnStartClimbingEffects();

        TungSahurMod.LOGGER.debug("TungSahur壁登り開始: 経路={}, 最大高度={}",
                this.climbPath.size(), this.maxClimbHeight);
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.climbTicks++;

        // 経路に沿った登攀実行
        performAdvancedClimbing();

        // 視覚・音響効果
        if (this.climbTicks % 6 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnClimbingParticles(serverLevel);
        }

        if (this.climbTicks % 20 == 0) {
            playClimbingSound();
        }

        // ターゲットを見続ける
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 登攀完了チェック
        checkClimbCompletion();
    }

    /**
     * 強化された壁登り動作
     */
    private void performAdvancedClimbing() {
        if (this.climbPath.isEmpty()) return;

        // 現在の目標位置を取得
        BlockPos currentTarget = getCurrentTargetPosition();
        if (currentTarget == null) {
            completeClimb();
            return;
        }

        // 目標位置への移動ベクトル計算
        Vec3 currentPos = this.tungSahur.position();
        Vec3 targetPos = Vec3.atCenterOf(currentTarget);
        Vec3 direction = targetPos.subtract(currentPos).normalize();

        // 日数に応じた速度調整
        double climbSpeed = CLIMB_SPEED_BASE + (this.tungSahur.getDayNumber() * 0.05D);

        // 壁との接触を維持するための横方向の力
        Vec3 wallDirection = getWallDirection();

        // 最終的な移動ベクトル
        Vec3 movement = direction.scale(climbSpeed).add(wallDirection.scale(0.03D));

        // Y軸の移動を強制的に上方向に（重力に対抗）
        if (movement.y < 0.1D) {
            movement = new Vec3(movement.x, Math.max(0.1D, movement.y + 0.15D), movement.z);
        }

        this.tungSahur.setDeltaMovement(movement);
        this.tungSahur.getNavigation().stop(); // 通常のナビゲーションを無効化

        // 壁の方向を向く
        this.tungSahur.setYRot(getYawForDirection(this.climbDirection));

        // 次のポイントに到達したかチェック
        double distanceToTarget = currentPos.distanceTo(targetPos);
        if (distanceToTarget < 1.2D) {
            this.currentPathIndex++;
            if (this.currentPathIndex >= this.climbPath.size()) {
                completeClimb();
            }
        }
    }

    /**
     * 最適な登攀ルートを検索
     */
    private ClimbRoute findOptimalClimbRoute() {
        BlockPos tungPos = this.tungSahur.blockPosition();
        BlockPos targetPos = this.target.blockPosition();

        // 各方向から最適なルートを探索
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            ClimbRoute route = calculateClimbRoute(tungPos, targetPos, direction);
            if (route != null && route.isValid()) {
                return route;
            }
        }

        return null;
    }

    /**
     * 指定方向での登攀ルートを計算
     */
    private ClimbRoute calculateClimbRoute(BlockPos start, BlockPos target, Direction direction) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = start;

        // 登攀経路を段階的に計算
        for (int height = 0; height < MAX_CLIMB_HEIGHT; height++) {
            // 壁の存在確認
            BlockPos wallPos = current.relative(direction);
            if (!isClimbableWall(wallPos)) {
                break;
            }

            // 登攀可能な位置を追加
            BlockPos climbPos = current.above();
            if (isValidClimbPosition(climbPos)) {
                path.add(climbPos);
                current = climbPos;

                // ターゲット高度に到達したかチェック
                if (current.getY() >= target.getY()) {
                    // 目標位置への最終移動
                    BlockPos finalPos = current.relative(direction.getOpposite());
                    if (isValidClimbPosition(finalPos)) {
                        path.add(finalPos);
                        return new ClimbRoute(direction, path, finalPos);
                    }
                }
            } else {
                break;
            }
        }

        // 有効な経路が見つからない場合
        return path.size() >= 2 ? new ClimbRoute(direction, path, current) : null;
    }

    /**
     * 登攀可能な壁かチェック
     */
    private boolean isClimbableWall(BlockPos pos) {
        BlockState state = this.tungSahur.level().getBlockState(pos);

        // 固体ブロックで、登攀可能な素材
        if (!state.isSolid() || state.isAir()) return false;

        // 特定のブロックは登攀不可
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) ||
                state.is(Blocks.BLUE_ICE) || state.is(Blocks.SLIME_BLOCK)) {
            return false;
        }

        return true;
    }

    /**
     * 有効な登攀位置かチェック
     */
    private boolean isValidClimbPosition(BlockPos pos) {
        BlockState state = this.tungSahur.level().getBlockState(pos);
        BlockState above = this.tungSahur.level().getBlockState(pos.above());

        // 空間があることを確認
        return state.isAir() && above.isAir();
    }

    /**
     * 現在の目標位置を取得
     */
    private BlockPos getCurrentTargetPosition() {
        if (this.currentPathIndex >= this.climbPath.size()) {
            return null;
        }
        return this.climbPath.get(this.currentPathIndex);
    }

    /**
     * 壁方向のベクトルを取得
     */
    private Vec3 getWallDirection() {
        if (this.climbDirection == null) return Vec3.ZERO;
        return Vec3.atLowerCornerOf(this.climbDirection.getNormal());
    }

    /**
     * 方向に応じたYaw角度を取得
     */
    private float getYawForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> this.tungSahur.getYRot();
        };
    }

    /**
     * 水平距離を計算
     */
    private double getHorizontalDistance(Vec3 pos1, Vec3 pos2) {
        double dx = pos1.x - pos2.x;
        double dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * 登攀開始時のエフェクト
     */
    private void spawnStartClimbingEffects() {
        if (!(this.tungSahur.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.tungSahur.position();

        // 強力な爪痕パーティクル
        for (int i = 0; i < 15; i++) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    pos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5,
                    pos.y + this.tungSahur.getRandom().nextDouble() * 2.0,
                    pos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5,
                    1, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /**
     * 登攀中のパーティクル効果
     */
    private void spawnClimbingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 壁との接触点にパーティクル
        if (this.climbDirection != null) {
            Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
            Vec3 contactPoint = tungPos.add(wallDirection.scale(0.8D));

            // 爪による引っかき効果
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    contactPoint.x,
                    contactPoint.y + this.tungSahur.getBbHeight() * 0.6,
                    contactPoint.z,
                    3, 0.15, 0.25, 0.15, 0.05);

            // 壁の破片（強化版）
            BlockPos wallPos = this.tungSahur.blockPosition().relative(this.climbDirection);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            if (!wallState.isAir()) {


            }
        }

        // 恐怖の煙（日数に応じて濃く）
        int smokeIntensity = this.tungSahur.getDayNumber() * 2;
        for (int i = 0; i < smokeIntensity; i++) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    tungPos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.0,
                    tungPos.y + this.tungSahur.getRandom().nextDouble() * 1.5,
                    tungPos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.0,
                    1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * 登攀音の再生
     */
    private void playClimbingSound() {
        // 基本的な爪音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                0.5F, 0.8F + this.tungSahur.getRandom().nextFloat() * 0.4F);

        // 日数に応じた追加音
        if (this.tungSahur.getDayNumber() >= 2 && this.tungSahur.getRandom().nextFloat() < 0.3F) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE,
                    0.2F, 1.5F);
        }
    }

    /**
     * 登攀完了チェック
     */
    private void checkClimbCompletion() {
        if (this.targetReachPos != null) {
            double distanceToTarget = this.tungSahur.position().distanceTo(Vec3.atCenterOf(this.targetReachPos));
            if (distanceToTarget < 2.0D) {
                this.hasReachedTarget = true;
                completeClimb();
            }
        }

        // 目標高度に到達した場合
        double currentHeight = this.tungSahur.getY() - this.startClimbPos.getY();
        if (currentHeight >= this.maxClimbHeight) {
            completeClimb();
        }
    }

    /**
     * 登攀完了処理
     */
    private void completeClimb() {
        this.isActivelyClimbing = false;
        this.hasReachedTarget = true;

        TungSahurMod.LOGGER.debug("TungSahur壁登り完了: 到達高度={}, 経路進行度={}/{}",
                this.tungSahur.getY() - this.startClimbPos.getY(),
                this.currentPathIndex, this.climbPath.size());

        // 完了時の前進（壁から離れる）
        if (this.climbDirection != null) {
            Vec3 forwardDirection = Vec3.atLowerCornerOf(this.climbDirection.getOpposite().getNormal());
            Vec3 currentMovement = this.tungSahur.getDeltaMovement();
            Vec3 finishMovement = currentMovement.add(forwardDirection.scale(0.4D));
            this.tungSahur.setDeltaMovement(finishMovement);
        }

        // 完了音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE,
                0.9F, 1.0F);

        // 完了パーティクル
        spawnCompletionEffects();
    }

    /**
     * 完了時のエフェクト
     */
    private void spawnCompletionEffects() {
        if (!(this.tungSahur.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.tungSahur.position();

        // 勝利の煙
        for (int i = 0; i < 20; i++) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    pos.x + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0,
                    pos.y + this.tungSahur.getRandom().nextDouble() * 2.0,
                    pos.z + (this.tungSahur.getRandom().nextDouble() - 0.5) * 2.0,
                    1, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @Override
    public void stop() {
        this.tungSahur.setWallClimbing(false);
        this.isActivelyClimbing = false;
        this.hasReachedTarget = false;
        this.climbDirection = null;
        this.climbPath.clear();
        this.currentPathIndex = 0;
        this.targetReachPos = null;

        TungSahurMod.LOGGER.debug("TungSahur壁登り終了");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * 登攀ルート情報クラス
     */
    private static class ClimbRoute {
        final Direction direction;
        final List<BlockPos> path;
        final BlockPos targetPos;

        ClimbRoute(Direction direction, List<BlockPos> path, BlockPos targetPos) {
            this.direction = direction;
            this.path = new ArrayList<>(path);
            this.targetPos = targetPos;
        }

        boolean isValid() {
            return direction != null && !path.isEmpty() && targetPos != null;
        }
    }

    // 外部アクセス用メソッド
    public boolean isActivelyClimbing() {
        return this.isActivelyClimbing;
    }

    public Direction getClimbDirection() {
        return this.climbDirection;
    }

    public int getClimbProgress() {
        return this.climbPath.isEmpty() ? 0 : (this.currentPathIndex * 100 / this.climbPath.size());
    }
}