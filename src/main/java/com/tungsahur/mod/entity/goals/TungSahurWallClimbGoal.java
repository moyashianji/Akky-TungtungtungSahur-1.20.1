// TungSahurWallClimbGoal.java - クールダウン修正版
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

    // ローカルクールダウン - エンティティから更新される
    private int cooldownAfterClimb = 0;

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

        // ローカルクールダウンチェック
        if (this.cooldownAfterClimb > 0) return false;

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
        this.targetReachPos = route.targetPos;
        this.maxClimbHeight = route.height;

        // 確率チェック（積極的に壁登り）
        return this.tungSahur.getRandom().nextFloat() < 0.5F; // 50%の確率
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られ始めたら即座に停止
        if (this.tungSahur.isBeingWatched()) return false;

        // 登攀中は継続
        if (this.isActivelyClimbing) {
            // 最大登攀時間を超えた場合は停止
            return this.climbTicks < 400; // 20秒
        }

        // ターゲットが近すぎる、または遠すぎる場合は停止
        double distance = this.tungSahur.distanceTo(this.target);
        return distance >= 3.0D && distance <= MAX_CLIMB_DISTANCE;
    }

    @Override
    public void start() {
        this.startClimbPos = this.tungSahur.blockPosition();
        this.climbTicks = 0;
        this.currentPathIndex = 0;
        this.isActivelyClimbing = true;
        this.hasReachedTarget = false;

        this.tungSahur.setWallClimbing(true);

        // 壁登り開始音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                0.6F, 1.2F);

        TungSahurMod.LOGGER.debug("TungSahur壁登り開始: 方向={}, 高度={}",
                this.climbDirection, this.maxClimbHeight);
    }

    @Override
    public void tick() {
        if (!this.isActivelyClimbing || this.target == null) return;

        this.climbTicks++;

        // ターゲットを注視
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 経路に沿って移動
        if (this.currentPathIndex < this.climbPath.size()) {
            moveAlongPath();
        } else {
            // 経路完了
            completeClimb();
        }

        // 登攀中のパーティクル効果
        if (this.climbTicks % 5 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnClimbingParticles(serverLevel);
        }

        // 登攀中の音（時々）
        if (this.climbTicks % 30 == 0) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                    0.4F, 1.0F);
        }
    }

    private void moveAlongPath() {
        BlockPos currentTarget = this.climbPath.get(this.currentPathIndex);
        Vec3 targetVec = Vec3.atCenterOf(currentTarget);
        Vec3 currentPos = this.tungSahur.position();

        // 目標位置への移動ベクトル計算
        Vec3 moveVec = targetVec.subtract(currentPos).normalize();

        // 登攀速度（日数が高いほど速く）
        double climbSpeed = CLIMB_SPEED_BASE + (this.tungSahur.getDayNumber() * 0.05D);
        moveVec = moveVec.scale(climbSpeed);

        // 重力を無効化（壁登り中）
        if (moveVec.y < 0) {
            moveVec = new Vec3(moveVec.x, Math.max(moveVec.y, 0.1D), moveVec.z);
        }

        this.tungSahur.setDeltaMovement(moveVec);

        // 目標位置に近づいたら次のポイントへ
        double distanceToTarget = currentPos.distanceTo(targetVec);
        if (distanceToTarget < 1.2D) {
            this.currentPathIndex++;

            TungSahurMod.LOGGER.debug("壁登り経路進行: {}/{}",
                    this.currentPathIndex, this.climbPath.size());
        }
    }

    private void completeClimb() {
        this.isActivelyClimbing = false;
        this.hasReachedTarget = true;
        this.tungSahur.setWallClimbing(false);

        // 登攀完了音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE,
                0.5F, 1.1F);

        TungSahurMod.LOGGER.debug("TungSahur壁登り完了: 所要時間={}tick", this.climbTicks);
    }

    private void spawnClimbingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 壁との接触面にパーティクル
        Vec3 wallContact = tungPos.add(this.climbDirection.getStepX() * 0.6, 0, this.climbDirection.getStepZ() * 0.6);



        // 手足の位置にスパーク
        for (int i = 0; i < 2; i++) {
            double offsetY = this.tungSahur.getBbHeight() * (0.2 + i * 0.6);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    wallContact.x, tungPos.y + offsetY, wallContact.z,
                    1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.climbDirection = null;
        this.climbTicks = 0;
        this.currentPathIndex = 0;
        this.isActivelyClimbing = false;
        this.hasReachedTarget = false;
        this.tungSahur.setWallClimbing(false);

        this.climbPath.clear();

        // 壁登り後のクールダウン設定
        this.cooldownAfterClimb = 200 + this.tungSahur.getRandom().nextInt(100); // 10-15秒

        TungSahurMod.LOGGER.debug("TungSahur壁登り終了: クールダウン={}tick", this.cooldownAfterClimb);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // クールダウン更新（エンティティのtickで呼び出される）
    public void updateCooldown() {
        if (this.cooldownAfterClimb > 0) {
            this.cooldownAfterClimb--;

            // デバッグ用（5秒に1回出力）
            if (this.cooldownAfterClimb % 100 == 0 && this.cooldownAfterClimb > 0) {
                TungSahurMod.LOGGER.debug("壁登りクールダウン残り: {}tick", this.cooldownAfterClimb);
            }
        }
    }

    // === ヘルパーメソッド ===

    private double getHorizontalDistance(Vec3 pos1, Vec3 pos2) {
        double dx = pos1.x - pos2.x;
        double dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private ClimbRoute findOptimalClimbRoute() {
        BlockPos startPos = this.tungSahur.blockPosition();
        BlockPos targetPos = this.target.blockPosition();

        // 4方向をチェック
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : directions) {
            ClimbRoute route = analyzeClimbRoute(startPos, targetPos, dir);
            if (route != null) {
                return route;
            }
        }

        return null;
    }

    private ClimbRoute analyzeClimbRoute(BlockPos start, BlockPos target, Direction direction) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = start;

        // 壁を検索
        BlockPos wallPos = current.relative(direction);
        if (!isClimbableWall(wallPos)) {
            return null;
        }

        // 登攀経路を計算
        int height = 0;
        while (height < MAX_CLIMB_HEIGHT && current.getY() < target.getY() + 2) {
            current = current.above();

            // 壁が続いているかチェック
            BlockPos wallCheck = current.relative(direction);
            if (!isClimbableWall(wallCheck)) {
                break;
            }

            // 登攀可能な空間があるかチェック
            if (!this.tungSahur.level().isEmptyBlock(current)) {
                break;
            }

            path.add(current);
            height++;

            // ターゲットの高さに到達したら終了
            if (current.getY() >= target.getY()) {
                break;
            }
        }

        // 有効な経路かチェック
        if (path.size() < 2) {
            return null;
        }

        return new ClimbRoute(direction, path, target, height);
    }

    private boolean isClimbableWall(BlockPos pos) {
        BlockState state = this.tungSahur.level().getBlockState(pos);

        // 空気ブロックは登れない
        if (state.isAir()) {
            return false;
        }

        // 特定のブロックは登れない
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) ||
                state.is(Blocks.BLUE_ICE) || state.is(Blocks.SLIME_BLOCK)) {
            return false;
        }

        // 固体ブロックなら登攀可能
        return state.isSolidRender(this.tungSahur.level(), pos);
    }

    // === 内部クラス ===

    private static class ClimbRoute {
        public final Direction direction;
        public final List<BlockPos> path;
        public final BlockPos targetPos;
        public final int height;

        public ClimbRoute(Direction direction, List<BlockPos> path, BlockPos targetPos, int height) {
            this.direction = direction;
            this.path = new ArrayList<>(path);
            this.targetPos = targetPos;
            this.height = height;
        }
    }

    // === デバッグ情報 ===

    public int getCooldownRemaining() {
        return this.cooldownAfterClimb;
    }

    public boolean isCurrentlyClimbing() {
        return this.isActivelyClimbing;
    }

    public int getClimbProgress() {
        return this.climbPath.isEmpty() ? 0 : (this.currentPathIndex * 100 / this.climbPath.size());
    }

    public Direction getCurrentClimbDirection() {
        return this.climbDirection;
    }
}