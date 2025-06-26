// TungSahurSmartTargetGoal.java - マルチプレイヤー対応スマートターゲティング
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.Comparator;

/**
 * TungSahur専用の改良されたターゲットシステム
 * マルチプレイヤー環境での安定したターゲット選択を提供
 */
public class TungSahurSmartTargetGoal extends Goal {

    private final TungSahurEntity tungSahur;

    // ターゲット管理
    private static final int SCAN_INTERVAL = 20;        // 1秒間隔
    private static final int EMERGENCY_SCAN_INTERVAL = 5; // 緊急時は0.25秒間隔
    private static final double MAX_TARGET_RANGE = 64.0D;
    private static final double PRIORITY_RANGE = 32.0D;  // この範囲内のプレイヤーを優先
    private static final double TARGET_SWITCH_THRESHOLD = 15.0D; // この距離差があれば切り替え

    private int scanCooldown = 0;
    private Player lastValidTarget = null;
    private int targetLostTicks = 0;
    private static final int MAX_TARGET_LOST_TIME = 100; // 5秒でターゲット喪失判定

    public TungSahurSmartTargetGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // 死亡中や特殊状態では無効
        if (tungSahur.isDeathAnimationPlaying() || !tungSahur.isAlive()) {
            return false;
        }

        // スキャンクールダウン管理
        if (scanCooldown > 0) {
            scanCooldown--;

            // 現在のターゲットの緊急チェック
            if (needsEmergencyTargetCheck()) {
                scanCooldown = 0; // 緊急スキャン実行
            } else {
                return tungSahur.getTarget() != null; // 現在のターゲットがあれば継続
            }
        }

        // ターゲットスキャン実行
        return performTargetScan();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity currentTarget = tungSahur.getTarget();

        if (currentTarget == null) {
            targetLostTicks++;
            return targetLostTicks < MAX_TARGET_LOST_TIME;
        }

        // ターゲットが有効かチェック
        if (isValidTarget(currentTarget)) {
            targetLostTicks = 0;
            return true;
        }

        // 無効なターゲットの場合、即座に新しいターゲットを探す
        TungSahurMod.LOGGER.debug("現在のターゲット無効 - 即座に再スキャン");
        scanCooldown = 0;
        return false;
    }

    @Override
    public void start() {
        TungSahurMod.LOGGER.debug("SmartTargetGoal開始");
        targetLostTicks = 0;
        scanCooldown = 0;
    }

    @Override
    public void stop() {
        TungSahurMod.LOGGER.debug("SmartTargetGoal停止");
        lastValidTarget = tungSahur.getTarget() instanceof Player ? (Player) tungSahur.getTarget() : null;
    }

    @Override
    public void tick() {
        // 定期スキャンのクールダウン管理
        if (scanCooldown > 0) {
            scanCooldown--;
        }

        // 現在のターゲットの継続的な妥当性チェック
        LivingEntity currentTarget = tungSahur.getTarget();
        if (currentTarget != null) {
            if (!isValidTarget(currentTarget)) {
                TungSahurMod.LOGGER.debug("ターゲット無効化検出 - 強制再スキャン");
                scanCooldown = 0;
                performTargetScan();
            }
        }
    }

    /**
     * 緊急ターゲットチェックが必要かどうか
     */
    private boolean needsEmergencyTargetCheck() {
        LivingEntity currentTarget = tungSahur.getTarget();

        if (currentTarget == null) return true;

        // プレイヤーの場合の特別チェック
        if (currentTarget instanceof Player player) {
            // 死亡、スペクテイター、範囲外の場合は緊急
            if (!player.isAlive() || player.isSpectator() || tungSahur.distanceTo(player) > MAX_TARGET_RANGE) {
                return true;
            }
        }

        return false;
    }

    /**
     * ターゲットスキャンの実行
     */
    private boolean performTargetScan() {
        List<Player> availablePlayers = findAvailablePlayers();

        if (availablePlayers.isEmpty()) {
            handleNoTargetsAvailable();
            return false;
        }

        Player bestTarget = selectBestTarget(availablePlayers);

        if (bestTarget != null) {
            setNewTarget(bestTarget);
            resetScanCooldown(false);
            return true;
        }

        resetScanCooldown(true);
        return false;
    }

    /**
     * 利用可能なプレイヤーを見つける
     */
    private List<Player> findAvailablePlayers() {
        AABB searchArea = new AABB(tungSahur.blockPosition()).inflate(MAX_TARGET_RANGE);

        return tungSahur.level().getEntitiesOfClass(Player.class, searchArea)
                .stream()
                .filter(this::isValidTarget)
                .toList();
    }

    /**
     * ターゲットの妥当性をチェック
     */
    private boolean isValidTarget(LivingEntity target) {
        if (!(target instanceof Player player)) return false;

        // 基本的な生存・状態チェック
        if (!player.isAlive() || player.isSpectator()) return false;

        // クリエイティブモードは除外
        if (player.isCreative()) return false;

        // 距離チェック
        double distance = tungSahur.distanceTo(player);
        if (distance > MAX_TARGET_RANGE) return false;

        // TungSahurが攻撃可能かチェック
        return tungSahur.canAttack(player);
    }

    /**
     * 最適なターゲットを選択
     */
    private Player selectBestTarget(List<Player> availablePlayers) {
        Player currentTarget = tungSahur.getTarget() instanceof Player ? (Player) tungSahur.getTarget() : null;

        // 現在のターゲットが有効で良好な位置にいる場合は維持
        if (currentTarget != null && isValidTarget(currentTarget)) {
            double currentDistance = tungSahur.distanceTo(currentTarget);

            // 優先範囲内にいる場合は維持
            if (currentDistance <= PRIORITY_RANGE) {
                return currentTarget;
            }

            // より近いプレイヤーがいるかチェック
            Player closestPlayer = availablePlayers.stream()
                    .min(Comparator.comparingDouble(tungSahur::distanceTo))
                    .orElse(null);

            if (closestPlayer != null) {
                double closestDistance = tungSahur.distanceTo(closestPlayer);

                // 大幅に近い場合のみ切り替え
                if (closestDistance + TARGET_SWITCH_THRESHOLD < currentDistance) {
                    TungSahurMod.LOGGER.debug("より近いプレイヤーに切り替え: {}→{} (距離差: {})",
                            currentTarget.getName().getString(),
                            closestPlayer.getName().getString(),
                            String.format("%.1f", currentDistance - closestDistance));
                    return closestPlayer;
                }
            }

            return currentTarget; // 現在のターゲットを維持
        }

        // 新しいターゲットを選択
        return selectNewTarget(availablePlayers);
    }

    /**
     * 新しいターゲットを選択
     */
    private Player selectNewTarget(List<Player> availablePlayers) {
        // 優先度付きソート
        return availablePlayers.stream()
                .sorted((p1, p2) -> {
                    double dist1 = tungSahur.distanceTo(p1);
                    double dist2 = tungSahur.distanceTo(p2);

                    // 優先範囲内のプレイヤーを優先
                    boolean p1InPriority = dist1 <= PRIORITY_RANGE;
                    boolean p2InPriority = dist2 <= PRIORITY_RANGE;

                    if (p1InPriority && !p2InPriority) return -1;
                    if (!p1InPriority && p2InPriority) return 1;

                    // 同等の優先度なら距離で比較
                    return Double.compare(dist1, dist2);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 新しいターゲットを設定
     */
    private void setNewTarget(Player newTarget) {
        Player oldTarget = tungSahur.getTarget() instanceof Player ? (Player) tungSahur.getTarget() : null;

        if (oldTarget != newTarget) {
            tungSahur.setTarget(newTarget);

            TungSahurMod.LOGGER.debug("ターゲット変更: {} → {} (距離: {}ブロック)",
                    oldTarget != null ? oldTarget.getName().getString() : "なし",
                    newTarget.getName().getString(),
                    String.format("%.1f", tungSahur.distanceTo(newTarget)));

            // ナビゲーションリセット
            tungSahur.getNavigation().stop();

            // 特殊攻撃を中断（ターゲット変更時）
            if (tungSahur.isCurrentlyThrowing() || tungSahur.isCurrentlyJumping()) {
                TungSahurMod.LOGGER.debug("ターゲット変更により特殊攻撃中断");
                // 特殊攻撃の中断処理は TungSahurEntity で実装される想定
            }
        }
    }

    /**
     * 利用可能なターゲットがない場合の処理
     */
    private void handleNoTargetsAvailable() {
        if (tungSahur.getTarget() != null) {
            TungSahurMod.LOGGER.debug("利用可能なプレイヤーなし - ターゲット解除");
            tungSahur.setTarget(null);
            tungSahur.getNavigation().stop();
        }

        resetScanCooldown(true); // より頻繁にスキャン
    }

    /**
     * スキャンクールダウンをリセット
     */
    private void resetScanCooldown(boolean emergency) {
        if (emergency) {
            scanCooldown = EMERGENCY_SCAN_INTERVAL;
        } else {
            scanCooldown = SCAN_INTERVAL;
        }
    }

    /**
     * デバッグ情報を取得
     */
    public String getDebugInfo() {
        LivingEntity currentTarget = tungSahur.getTarget();
        String targetInfo = currentTarget instanceof Player player ?
                player.getName().getString() :
                (currentTarget != null ? currentTarget.getClass().getSimpleName() : "なし");

        return String.format("SmartTarget[Target=%s, ScanCD=%d, LostTicks=%d]",
                targetInfo, scanCooldown, targetLostTicks);
    }
}