// TungSahurAdvancedMoveToTargetGoal.java - 高度移動ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TungSahurAdvancedMoveToTargetGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private final double speedModifier;
    private LivingEntity target;
    private Path path;
    private int delayCounter = 0;
    private int consecutiveFailures = 0;
    private int lastTargetCheckTime = 0;
    private BlockPos lastTargetPos;
    private boolean wasTargetSprinting = false;

    // 追跡の持続性設定
    private static final int MAX_TRACKING_TIME = 1200; // 60秒間追跡可能
    private static final int TARGET_LOST_TOLERANCE = 200; // 10秒間見失ってもOK
    private int trackingTime = 0;
    private int targetLostTime = 0;

    public TungSahurAdvancedMoveToTargetGoal(TungSahurEntity tungSahur, double speedModifier) {
        this.tungSahur = tungSahur;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null || !this.target.isAlive()) {
            resetTracking();
            return false;
        }

        // 壁登り中は通常移動を無効化
        if (this.tungSahur.isWallClimbing()) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // 極近距離の判定を緩和（攻撃距離と合わせる）
        if (distance <= 1.5D) return false; // 2.0Dから1.5Dに変更

        // 最大追跡距離内かチェック
        return distance <= 64.0D;
    }


    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) {
            return handleTargetLoss();
        }

        // 壁登り中は中断
        if (this.tungSahur.isWallClimbing()) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // ターゲットが見える場合は追跡継続
        if (this.tungSahur.hasLineOfSight(this.target)) {
            this.targetLostTime = 0;
            return distance <= 96.0D; // 継続可能距離は開始距離より広め
        }

        // ターゲットが見えない場合の処理
        return handleTargetOutOfSight(distance);
    }

    private boolean handleTargetLoss() {
        this.targetLostTime++;

        // 一定時間内なら最後の位置に向かって移動継続
        if (this.targetLostTime <= TARGET_LOST_TOLERANCE && this.lastTargetPos != null) {
            moveToLastKnownPosition();
            return true;
        }

        resetTracking();
        return false;
    }

    private boolean handleTargetOutOfSight(double distance) {
        this.targetLostTime++;

        // 距離が近い場合は視界外でも継続追跡
        if (distance <= 16.0D) {
            return this.targetLostTime <= TARGET_LOST_TOLERANCE * 2;
        }

        // 遠距離の場合は短時間で追跡終了
        return this.targetLostTime <= TARGET_LOST_TOLERANCE;
    }

    @Override
    public void start() {
        this.delayCounter = 0;
        this.consecutiveFailures = 0;
        this.trackingTime = 0;
        this.targetLostTime = 0;
        this.lastTargetPos = this.target.blockPosition();

        updateMovementToTarget();

        TungSahurMod.LOGGER.debug("TungSahur追跡開始: ターゲット={}, 距離={}",
                this.target.getClass().getSimpleName(),
                this.tungSahur.distanceTo(this.target));
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.trackingTime++;

        // ターゲットを注視
        this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 定期的な移動更新
        if (--this.delayCounter <= 0) {
            this.delayCounter = calculateUpdateDelay();
            updateMovementToTarget();
        }

        // プレイヤーのスプリント状態変化への対応
        handlePlayerSprintChange();

        // 最後の既知位置を更新
        if (this.tungSahur.hasLineOfSight(this.target)) {
            this.lastTargetPos = this.target.blockPosition();
            this.targetLostTime = 0;
        }

        // 移動速度の動的調整
        adjustMovementSpeed();
    }

    private int calculateUpdateDelay() {
        double distance = this.tungSahur.distanceTo(this.target);

        // 距離に応じた更新頻度調整
        if (distance <= 8.0D) {
            return 2 + this.tungSahur.getRandom().nextInt(3); // 近距離：高頻度更新
        } else if (distance <= 16.0D) {
            return 4 + this.tungSahur.getRandom().nextInt(4); // 中距離：中頻度更新
        } else {
            return 6 + this.tungSahur.getRandom().nextInt(6); // 遠距離：低頻度更新
        }
    }

    private void updateMovementToTarget() {
        if (this.target == null) return;

        PathNavigation navigation = this.tungSahur.getNavigation();
        double distance = this.tungSahur.distanceTo(this.target);

        // 距離に応じた移動戦略を調整
        if (distance > 2.0D) { // 3.0Dから2.0Dに変更（より近くまで接近）
            // 直接移動
            this.path = navigation.createPath(this.target, 1);

            if (this.path != null && this.path.canReach()) {
                double currentSpeed = calculateOptimalSpeed(distance);
                navigation.moveTo(this.path, currentSpeed);
                this.consecutiveFailures = 0;
            } else {
                // パス生成失敗時の代替移動
                handleNavigationFailure();
            }
        } else {
            // 極近距離では移動停止
            navigation.stop();
        }
    }

    private void moveToLastKnownPosition() {
        if (this.lastTargetPos == null) return;

        PathNavigation navigation = this.tungSahur.getNavigation();
        Path lastPosPath = navigation.createPath(this.lastTargetPos, 1);

        if (lastPosPath != null && lastPosPath.canReach()) {
            navigation.moveTo(lastPosPath, this.speedModifier * 0.8D); // やや遅めで移動
        }
    }

    private void handleNavigationFailure() {
        this.consecutiveFailures++;

        // 連続失敗時の代替行動
        if (this.consecutiveFailures <= 2) {
            // 少し上や下の位置を試す
            tryAlternativePosition();
        } else if (this.consecutiveFailures <= 4) {
            // ターゲット周辺のランダム位置を試す
            tryRandomNearbyPosition();
        } else {
            // 直線移動を試行
            attemptDirectMovement();
        }
    }

    private void tryAlternativePosition() {
        if (this.target == null) return;

        BlockPos targetPos = this.target.blockPosition();
        PathNavigation navigation = this.tungSahur.getNavigation();

        // 上下の位置を試す
        for (int yOffset : new int[]{1, -1, 2, -2}) {
            BlockPos altPos = targetPos.offset(0, yOffset, 0);
            Path altPath = navigation.createPath(altPos, 1);

            if (altPath != null && altPath.canReach()) {
                navigation.moveTo(altPath, this.speedModifier);
                return;
            }
        }
    }

    private void tryRandomNearbyPosition() {
        if (this.target == null) return;

        BlockPos targetPos = this.target.blockPosition();
        PathNavigation navigation = this.tungSahur.getNavigation();

        // ターゲット周辺のランダム位置
        for (int attempt = 0; attempt < 5; attempt++) {
            int offsetX = this.tungSahur.getRandom().nextInt(6) - 3;
            int offsetZ = this.tungSahur.getRandom().nextInt(6) - 3;
            BlockPos nearbyPos = targetPos.offset(offsetX, 0, offsetZ);

            Path nearbyPath = navigation.createPath(nearbyPos, 1);
            if (nearbyPath != null && nearbyPath.canReach()) {
                navigation.moveTo(nearbyPath, this.speedModifier * 0.9D);
                return;
            }
        }
    }

    private void attemptDirectMovement() {
        if (this.target == null) return;

        // ナビゲーションによる移動が失敗した場合の直接移動
        Vec3 direction = this.target.position().subtract(this.tungSahur.position()).normalize();
        Vec3 targetVelocity = direction.scale(this.speedModifier * 0.5D);

        this.tungSahur.setDeltaMovement(
                this.tungSahur.getDeltaMovement().add(targetVelocity.x * 0.1D, 0, targetVelocity.z * 0.1D)
        );
    }

    private void handlePlayerSprintChange() {
        if (!(this.target instanceof Player player)) return;

        boolean isCurrentlySprinting = player.isSprinting();

        if (isCurrentlySprinting != this.wasTargetSprinting) {
            this.wasTargetSprinting = isCurrentlySprinting;

            // プレイヤーのスプリント状態に合わせてTungSahurも調整
            if (!this.tungSahur.isBeingWatched()) { // 見られている時は無効
                this.tungSahur.setSprinting(isCurrentlySprinting);

                // スプリント状態に応じた速度調整
                adjustSpeedForSprint(isCurrentlySprinting);
            }
        }
    }

    private void adjustSpeedForSprint(boolean sprinting) {
        double baseSpeed = this.tungSahur.getBaseSpeedForDay();
        double sprintMultiplier = sprinting ? 1.6D : 1.0D;
        double newSpeed = baseSpeed * sprintMultiplier;

        // 見られている時は速度制限が適用されるので、ここでは基本速度のみ設定
        if (!this.tungSahur.isBeingWatched()) {
            this.tungSahur.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .setBaseValue(newSpeed);
        }
    }

    private double calculateOptimalSpeed(double distance) {
        double baseSpeed = this.speedModifier;

        // 距離に応じた速度調整
        if (distance > 20.0D) {
            baseSpeed *= 1.3D; // 遠距離：高速移動
        } else if (distance > 10.0D) {
            baseSpeed *= 1.1D; // 中距離：やや高速
        } else if (distance < 5.0D) {
            baseSpeed *= 0.8D; // 近距離：慎重に
        }

        // 日数による能力向上
        baseSpeed *= (1.0D + this.tungSahur.getDayNumber() * 0.08D);

        // スプリント時の追加速度
        if (this.tungSahur.isSprinting() && !this.tungSahur.isBeingWatched()) {
            baseSpeed *= 1.4D;
        }

        return baseSpeed;
    }

    private void adjustMovementSpeed() {
        double distance = this.tungSahur.distanceTo(this.target);

        // 見られている時は別の速度制御が適用されるのでスキップ
        if (this.tungSahur.isBeingWatched()) return;

        // 現在のナビゲーション速度を調整
        if (this.path != null) {
            double optimalSpeed = calculateOptimalSpeed(distance);
            this.tungSahur.getNavigation().setSpeedModifier(optimalSpeed);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.path = null;
        this.tungSahur.getNavigation().stop();
        this.tungSahur.setSprinting(false);
        resetTracking();

        TungSahurMod.LOGGER.debug("TungSahur追跡終了");
    }

    private void resetTracking() {
        this.trackingTime = 0;
        this.targetLostTime = 0;
        this.lastTargetPos = null;
        this.consecutiveFailures = 0;
        this.wasTargetSprinting = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // 外部から追跡状態を確認するためのメソッド
    public boolean isActivelyTracking() {
        return this.target != null && this.trackingTime > 0;
    }

    public LivingEntity getCurrentTarget() {
        return this.target;
    }

    public int getTrackingDuration() {
        return this.trackingTime;
    }
}