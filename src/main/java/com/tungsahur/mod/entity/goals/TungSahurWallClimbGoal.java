// TungSahurWallClimbGoal.java - より自然で恐怖感のある壁登りシステム
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

    // 改善された壁登り設定
    private static final double MIN_HEIGHT_DIFF = 2.0D;
    private static final double MAX_CLIMB_DISTANCE = 24.0D;
    private static final double MAX_CLIMB_HEIGHT = 20.0D; // より高く登れるように
    private static final double CLIMB_SPEED_BASE = 0.25D; // より速く
    private static final int CLIMB_DETECTION_RADIUS = 4; // より広範囲で検出

    // 改善された登攀経路計算用
    private final List<BlockPos> climbPath = new ArrayList<>();
    private int currentPathIndex = 0;
    private Vec3 lastPosition = Vec3.ZERO;
    private int stuckCounter = 0;
    private final Random random = new Random();

    public TungSahurWallClimbGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られている時は壁登りしない（恐怖演出強化）
        if (this.tungSahur.isBeingWatched()) return false;

        // ターゲットが高い位置にいる場合のみ
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        if (heightDiff < MIN_HEIGHT_DIFF) return false;

        // ターゲットまでの水平距離をチェック（より遠くからでも開始）
        double horizontalDistance = getHorizontalDistance(this.tungSahur.position(), this.target.position());
        if (horizontalDistance > MAX_CLIMB_DISTANCE) return false;

        // 壁登り可能なルートを検索（改善版）
        ClimbRoute route = findOptimalClimbRoute();
        if (route == null) return false;

        // ルート情報を設定
        this.climbDirection = route.direction;
        this.climbPath.clear();
        this.climbPath.addAll(route.path);
        this.currentPathIndex = 0;
        this.targetReachPos = route.targetPos;

        TungSahurMod.LOGGER.debug("壁登りルート発見: 日数={}, 方向={}, 経路長={}, 目標高度差={}",
                this.tungSahur.getDayNumber(), this.climbDirection, this.climbPath.size(), heightDiff);

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;

        // 見られている時は中止（恐怖演出）
        if (this.tungSahur.isBeingWatched()) return false;

        // 登攀完了チェック
        if (this.hasReachedTarget) return false;

        // スタック検出による強制終了
        if (this.stuckCounter > 40) return false;

        // 最大登攀時間チェック（40秒に延長）
        if (this.climbTicks > 800) return false;

        // ターゲットが近くに来た場合は終了
        double distance = this.tungSahur.distanceTo(this.target);
        if (distance <= 2.5D) return false;

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
        this.lastPosition = this.tungSahur.position();
        this.stuckCounter = 0;

        // 最大登攀高度を設定（より高く）
        double heightDiff = this.target.getY() - this.tungSahur.getY();
        this.maxClimbHeight = (int) Math.min(heightDiff + 5, MAX_CLIMB_HEIGHT);

        // 壁登り開始音（より恐ろしく）
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                1.0F, 0.6F); // より低い音程

        // 恐怖音も追加
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE,
                0.3F, 1.8F);

        // 開始時の恐怖パーティクル（強化）
        spawnStartClimbingEffects();

        TungSahurMod.LOGGER.debug("TungSahur壁登り開始: 経路={}, 最大高度={}",
                this.climbPath.size(), this.maxClimbHeight);
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.climbTicks++;

        // スタック検出
        Vec3 currentPos = this.tungSahur.position();
        if (currentPos.distanceTo(this.lastPosition) < 0.1D) {
            this.stuckCounter++;
        } else {
            this.stuckCounter = 0;
            this.lastPosition = currentPos;
        }

        // 改善された登攀実行
        performSmoothClimbing();

        // より頻繁な視覚・音響効果
        if (this.climbTicks % 4 == 0 && this.tungSahur.level() instanceof ServerLevel serverLevel) {
            spawnClimbingParticles(serverLevel);
        }

        if (this.climbTicks % 15 == 0) {
            playClimbingSound();
        }

        // ターゲットを見続ける（より自然に）
        this.tungSahur.getLookControl().setLookAt(this.target, 20.0F, 20.0F);

        // 登攀完了チェック
        checkClimbCompletion();
    }

    /**
     * より滑らかで自然な壁登り動作
     */
    private void performSmoothClimbing() {
        if (this.climbPath.isEmpty()) return;

        // 現在の目標位置を取得
        BlockPos currentTarget = getCurrentTargetPosition();
        if (currentTarget == null) {
            completeClimb();
            return;
        }

        Vec3 currentPos = this.tungSahur.position();
        Vec3 targetPos = Vec3.atCenterOf(currentTarget);
        Vec3 direction = targetPos.subtract(currentPos).normalize();

        // 日数に応じた速度調整（より強力に）
        double climbSpeed = CLIMB_SPEED_BASE + (this.tungSahur.getDayNumber() * 0.08D);

        // 日数による壁登り速度の向上（制限なし）
        switch (this.tungSahur.getDayNumber()) {
            case 1:
                climbSpeed *= 0.8D; // 1日目は少し遅め
                break;
            case 2:
                climbSpeed *= 1.0D; // 基本速度
                break;
            case 3:
                climbSpeed *= 1.5D; // 3日目は1.5倍の速度
                break;
            default:
                climbSpeed *= 1.0D; // デフォルトは基本速度
                break;
        }

        // スタック時の追加速度
        if (this.stuckCounter > 5) {
            climbSpeed *= 1.5D;
        }

        // 壁との接触を維持するための力（強化）
        Vec3 wallDirection = getWallDirection();
        Vec3 wallAdhesion = wallDirection.scale(0.05D);

        // より自然な移動ベクトル計算
        Vec3 baseMovement = direction.scale(climbSpeed);

        // Y軸の重力対抗を強化
        double verticalBoost = 0.2D + (this.tungSahur.getDayNumber() * 0.05D);
        if (baseMovement.y < verticalBoost) {
            baseMovement = new Vec3(baseMovement.x, Math.max(verticalBoost, baseMovement.y + 0.25D), baseMovement.z);
        }

        // 最終移動ベクトル
        Vec3 finalMovement = baseMovement.add(wallAdhesion);

        // ランダムな微調整で自然さを演出
        if (this.climbTicks % 8 == 0) {
            double randomX = (this.random.nextDouble() - 0.5) * 0.02D;
            double randomZ = (this.random.nextDouble() - 0.5) * 0.02D;
            finalMovement = finalMovement.add(randomX, 0, randomZ);
        }

        this.tungSahur.setDeltaMovement(finalMovement);
        this.tungSahur.getNavigation().stop();

        // 壁の方向を向く（より滑らかに）
        float targetYaw = getYawForDirection(this.climbDirection);
        float currentYaw = this.tungSahur.getYRot();
        float yawDiff = targetYaw - currentYaw;

        // 角度の正規化
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        // 滑らかな回転
        float smoothYaw = currentYaw + yawDiff * 0.2F;
        this.tungSahur.setYRot(smoothYaw);

        // 次のポイントに到達したかチェック（より寛容に）
        double distanceToTarget = currentPos.distanceTo(targetPos);
        if (distanceToTarget < 1.5D) {
            this.currentPathIndex++;
            if (this.currentPathIndex >= this.climbPath.size()) {
                completeClimb();
            }
        }
    }

    /**
     * 改善された最適登攀ルート検索
     */
    private ClimbRoute findOptimalClimbRoute() {
        BlockPos tungPos = this.tungSahur.blockPosition();
        BlockPos targetPos = this.target.blockPosition();

        ClimbRoute bestRoute = null;
        double bestScore = Double.MAX_VALUE;

        // 各方向から最適なルートを探索（改善版）
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            ClimbRoute route = calculateSmoothClimbRoute(tungPos, targetPos, direction);
            if (route != null && route.isValid()) {
                double score = evaluateRouteQuality(route, targetPos);
                if (score < bestScore) {
                    bestScore = score;
                    bestRoute = route;
                }
            }
        }

        return bestRoute;
    }

    /**
     * より滑らかな登攀ルート計算
     */
    private ClimbRoute calculateSmoothClimbRoute(BlockPos start, BlockPos target, Direction direction) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = start;

        // 初期壁チェック（1マスでもOK、ただし最低高度をチェック）
        if (!hasClimbableWallStructure(current, direction)) {
            return null;
        }

        // より密な経路点を生成
        for (int height = 0; height < MAX_CLIMB_HEIGHT; height++) {
            // 現在高度での壁の継続チェック
            if (!isClimbableWall(current.relative(direction))) {
                break;
            }

            // 登攀位置の計算
            BlockPos climbPos = current.above();
            if (isValidClimbPosition(climbPos)) {
                path.add(climbPos);
                current = climbPos;

                // ターゲット高度付近に到達したかチェック
                if (current.getY() >= target.getY() - 1) {
                    // 目標位置への最終アプローチ
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

        // 短いルートでも有効とする
        return path.size() >= 1 ? new ClimbRoute(direction, path, current) : null;
    }

    /**
     * 登攀可能な壁構造かチェック（1マスの棒状ブロック対応）
     */
    private boolean hasClimbableWallStructure(BlockPos start, Direction direction) {
        BlockPos wallPos = start.relative(direction);

        // 基本的な壁の存在チェック
        if (!isClimbableWall(wallPos)) {
            return false;
        }

        // 1マスの棒状ブロックでも登攀可能とするため、
        // 最低高度（3ブロック以上）の壁があるかチェック
        int wallHeight = 0;
        for (int y = 0; y < 20; y++) { // 最大20ブロックまでチェック
            BlockPos checkPos = wallPos.above(y);
            if (isClimbableWall(checkPos)) {
                wallHeight++;
            } else {
                break;
            }
        }

        // 最低3ブロック以上の高さがあれば登攀可能
        return wallHeight >= 3;
    }

    /**
     * ルート品質評価
     */
    private double evaluateRouteQuality(ClimbRoute route, BlockPos target) {
        if (route.path.isEmpty()) return Double.MAX_VALUE;

        BlockPos endPos = route.path.get(route.path.size() - 1);
        // BlockPos用の距離計算（正しい方法）
        double distanceToTarget = Math.sqrt(endPos.distSqr(target));
        double pathLength = route.path.size();

        // 短い距離で目標に近づけるルートを優先
        return distanceToTarget + (pathLength * 0.1);
    }

    /**
     * 登攀可能な壁かチェック（改善版）
     */
    private boolean isClimbableWall(BlockPos pos) {
        BlockState state = this.tungSahur.level().getBlockState(pos);

        if (!state.isSolid() || state.isAir()) return false;

        // 登攀不可能なブロック（滑りやすい・特殊なブロック）
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) ||
                state.is(Blocks.BLUE_ICE) || state.is(Blocks.SLIME_BLOCK) ||
                state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.MAGMA_BLOCK)) {
            return false;
        }

        // 絶対に登れないブロック
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
            return false;
        }

        // その他全てのブロックは登攀可能（日数に関係なく）
        return true;
    }

    /**
     * 有効な登攀位置かチェック（改善版）
     */
    private boolean isValidClimbPosition(BlockPos pos) {
        BlockState state = this.tungSahur.level().getBlockState(pos);
        BlockState above = this.tungSahur.level().getBlockState(pos.above());

        // 空間があることを確認（エンティティサイズ考慮）
        boolean hasSpace = state.isAir() && above.isAir();

        // 足場の安定性チェック
        BlockState below = this.tungSahur.level().getBlockState(pos.below());
        boolean hasSupport = below.isSolid() || this.isActivelyClimbing;

        return hasSpace && (hasSupport || this.isActivelyClimbing);
    }

    /**
     * 現在の目標位置を取得（改善版）
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
     * 恐怖感を演出する登攀開始エフェクト
     */
    private void spawnStartClimbingEffects() {
        if (!(this.tungSahur.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.tungSahur.position();

        // より強烈な爪痕パーティクル
        for (int i = 0; i < 25; i++) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    pos.x + (this.random.nextDouble() - 0.5) * 2.0,
                    pos.y + this.random.nextDouble() * 2.5,
                    pos.z + (this.random.nextDouble() - 0.5) * 2.0,
                    1, 0.3, 0.3, 0.3, 0.15);
        }

        // 恐怖の煙（日数に応じて強化）
        int smokeCount = 10 + (this.tungSahur.getDayNumber() * 5);
        for (int i = 0; i < smokeCount; i++) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x + (this.random.nextDouble() - 0.5) * 1.5,
                    pos.y + this.random.nextDouble() * 2.0,
                    pos.z + (this.random.nextDouble() - 0.5) * 1.5,
                    1, 0.15, 0.15, 0.15, 0.05);
        }
    }

    /**
     * より迫力のある登攀中パーティクル
     */
    private void spawnClimbingParticles(ServerLevel serverLevel) {
        Vec3 tungPos = this.tungSahur.position();

        // 壁との接触点により多くのパーティクル
        if (this.climbDirection != null) {
            Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
            Vec3 contactPoint = tungPos.add(wallDirection.scale(0.9D));

            // 激しい爪による引っかき効果
            for (int i = 0; i < 5; i++) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        contactPoint.x + (this.random.nextDouble() - 0.5) * 0.3,
                        contactPoint.y + this.tungSahur.getBbHeight() * 0.6 + (this.random.nextDouble() - 0.5) * 0.5,
                        contactPoint.z + (this.random.nextDouble() - 0.5) * 0.3,
                        1, 0.2, 0.3, 0.2, 0.08);
            }

            // 壁の破片効果（より現実的）
            BlockPos wallPos = this.tungSahur.blockPosition().relative(this.climbDirection);
            BlockState wallState = this.tungSahur.level().getBlockState(wallPos);

            if (!wallState.isAir()) {

         }
        }

        // 恐怖の煙エフェクト（日数に応じて濃厚に）
        int smokeIntensity = 2 + (this.tungSahur.getDayNumber() * 2);
        for (int i = 0; i < smokeIntensity; i++) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    tungPos.x + (this.random.nextDouble() - 0.5) * 1.2,
                    tungPos.y + this.random.nextDouble() * 1.8,
                    tungPos.z + (this.random.nextDouble() - 0.5) * 1.2,
                    1, 0.1, 0.1, 0.1, 0.03);
        }

        // 3日目は特別な赤いパーティクル
        if (this.tungSahur.getDayNumber() >= 3 && this.climbTicks % 8 == 0) {
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    tungPos.x, tungPos.y + 1.0, tungPos.z,
                    2, 0.5, 0.5, 0.5, 0.02);
        }
    }

    /**
     * より恐ろしい登攀音
     */
    private void playClimbingSound() {
        // 基本的な爪音（音程をランダムに）
        float pitch = 0.6F + this.random.nextFloat() * 0.6F;
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE,
                0.7F, pitch);

        // 日数に応じた恐怖音
        if (this.tungSahur.getDayNumber() >= 2 && this.random.nextFloat() < 0.4F) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE,
                    0.3F, 1.3F + this.random.nextFloat() * 0.4F);
        }

        // 3日目は金属音も追加
        if (this.tungSahur.getDayNumber() >= 3 && this.random.nextFloat() < 0.3F) {
            this.tungSahur.level().playSound(null,
                    this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                    SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE,
                    0.2F, 2.0F);
        }
    }

    /**
     * 登攀完了チェック（改善版）
     */
    private void checkClimbCompletion() {
        if (this.targetReachPos != null) {
            double distanceToTarget = this.tungSahur.position().distanceTo(Vec3.atCenterOf(this.targetReachPos));
            if (distanceToTarget < 2.5D) {
                this.hasReachedTarget = true;
                completeClimb();
                return;
            }
        }

        // 目標高度到達チェック
        double currentHeight = this.tungSahur.getY() - this.startClimbPos.getY();
        if (currentHeight >= this.maxClimbHeight) {
            completeClimb();
            return;
        }

        // ターゲットと同じ高度に到達したかチェック
        if (this.tungSahur.getY() >= this.target.getY() - 1.0D) {
            completeClimb();
        }
    }

    /**
     * より自然な登攀完了処理
     */
    private void completeClimb() {
        this.isActivelyClimbing = false;
        this.hasReachedTarget = true;

        TungSahurMod.LOGGER.debug("TungSahur壁登り完了: 到達高度={}, 経路進行度={}/{}",
                this.tungSahur.getY() - this.startClimbPos.getY(),
                this.currentPathIndex, this.climbPath.size());

        // 壁から離れる自然な動作
        if (this.climbDirection != null) {
            Vec3 forwardDirection = Vec3.atLowerCornerOf(this.climbDirection.getOpposite().getNormal());
            Vec3 currentMovement = this.tungSahur.getDeltaMovement();
            Vec3 finishMovement = currentMovement.add(forwardDirection.scale(0.5D));
            // 少し上向きの力も加える
            finishMovement = new Vec3(finishMovement.x, Math.max(finishMovement.y, 0.2D), finishMovement.z);
            this.tungSahur.setDeltaMovement(finishMovement);
        }

        // 恐怖感のある完了音
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE,
                1.2F, 0.8F);

        // 邪悪な笑い声
        this.tungSahur.level().playSound(null,
                this.tungSahur.getX(), this.tungSahur.getY(), this.tungSahur.getZ(),
                SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE,
                0.5F, 0.6F);

        // 完了パーティクル
        spawnCompletionEffects();
    }

    /**
     * 恐怖感のある完了エフェクト
     */
    private void spawnCompletionEffects() {
        if (!(this.tungSahur.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.tungSahur.position();

        // 勝利の煙（より邪悪に）
        for (int i = 0; i < 30; i++) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x + (this.random.nextDouble() - 0.5) * 2.5,
                    pos.y + this.random.nextDouble() * 2.5,
                    pos.z + (this.random.nextDouble() - 0.5) * 2.5,
                    1, 0.4, 0.4, 0.4, 0.15);
        }

        // 邪悪な輝き
        for (int i = 0; i < 15; i++) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    pos.x + (this.random.nextDouble() - 0.5) * 3.0,
                    pos.y + this.random.nextDouble() * 3.0,
                    pos.z + (this.random.nextDouble() - 0.5) * 3.0,
                    1, 0.5, 0.5, 0.5, 0.1);
        }

        // 3日目は特別なエフェクト
        if (this.tungSahur.getDayNumber() >= 3) {
            for (int i = 0; i < 10; i++) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + (this.random.nextDouble() - 0.5) * 2.0,
                        pos.y + this.random.nextDouble() * 2.0,
                        pos.z + (this.random.nextDouble() - 0.5) * 2.0,
                        1, 0.3, 0.3, 0.3, 0.05);
            }
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
        this.stuckCounter = 0;

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