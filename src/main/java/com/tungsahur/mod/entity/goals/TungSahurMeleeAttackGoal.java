// TungSahurMeleeAttackGoal.java - 強制アニメーション再生版
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

public class TungSahurMeleeAttackGoal extends MeleeAttackGoal {
    private final TungSahurEntity tungSahur;
    private int attackPreparationTime = 0;
    private boolean isPreparingAttack = false;
    private LivingEntity pendingTarget = null;

    // 攻撃クールダウン管理
    private static final int ATTACK_COOLDOWN_TICKS = 20; // 1秒 = 20tick

    public TungSahurMeleeAttackGoal(TungSahurEntity tungSahur, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(tungSahur, speedModifier, followingTargetEvenIfNotSeen);
        this.tungSahur = tungSahur;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) return false;
        if (!tungSahur.canAttack()) return false;

        LivingEntity target = tungSahur.getTarget();
        if (target == null) return false;

        double distance = tungSahur.distanceTo(target);

        // 日数に応じた攻撃距離
        double maxAttackDistance = switch (tungSahur.getDayNumber()) {
            case 1 -> 3.5D;
            case 2 -> 4.0D;
            case 3 -> 4.5D;
            default -> 3.5D;
        };

        boolean inRange = distance <= maxAttackDistance;
        boolean hasLineOfSight = tungSahur.hasLineOfSight(target);

        return inRange && hasLineOfSight;
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) return false;
        if (isPreparingAttack) return true; // 攻撃準備中は継続

        LivingEntity target = tungSahur.getTarget();
        if (target == null) return false;

        double distance = tungSahur.distanceTo(target);
        double maxDistance = switch (tungSahur.getDayNumber()) {
            case 1 -> 4.0D;
            case 2 -> 4.5D;
            case 3 -> 5.0D;
            default -> 4.0D;
        };

        return distance <= maxDistance;
    }

    @Override
    public void start() {
        super.start();
        TungSahurMod.LOGGER.debug("近接攻撃Goal開始");
    }

    /**
     * ★修正: 攻撃実行処理（強制アニメーション版）★
     */
    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
        double attackReach = this.getAttackReachSqr(target);

        if (distanceSqr <= attackReach && this.isTimeToAttack()) {
            TungSahurMod.LOGGER.debug("★攻撃実行開始★: 距離²={}, 射程²={}", distanceSqr, attackReach);

            // ★重要: 既存のアニメーション状態を完全にリセット★
            tungSahur.forceResetAttackAnimation();

            // ★攻撃アニメーション開始★
            tungSahur.setCurrentlyAttacking(true);

            // 攻撃準備開始
            isPreparingAttack = true;
            pendingTarget = target;
            attackPreparationTime = switch (tungSahur.getDayNumber()) {
                case 1 -> 10; // 0.5秒
                case 2 -> 8;  // 0.4秒
                case 3 -> 5;  // 0.25秒
                default -> 10;
            };

            // 攻撃クールダウンを即座に設定
            tungSahur.setAttackCooldown(ATTACK_COOLDOWN_TICKS);

            TungSahurMod.LOGGER.debug("★攻撃開始★: 準備時間={}tick, クールダウン={}tick",
                    attackPreparationTime, ATTACK_COOLDOWN_TICKS);
        }
    }

    /**
     * 実際のダメージ処理
     */
    private void executeDamage(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            TungSahurMod.LOGGER.debug("攻撃対象が無効");
            return;
        }

        TungSahurMod.LOGGER.debug("ダメージ実行: ターゲット={}", target.getClass().getSimpleName());

        // 基本攻撃実行
        boolean hitSuccessful = tungSahur.doHurtTarget(target);

        if (hitSuccessful) {
            // 追加ダメージ
            float additionalDamage = tungSahur.getDayNumber() * 1.5F;
            boolean additionalHit = target.hurt(tungSahur.damageSources().mobAttack(tungSahur), additionalDamage);

            TungSahurMod.LOGGER.debug("攻撃成功: 基本攻撃={}, 追加ダメージ={}({})",
                    hitSuccessful, additionalDamage, additionalHit);

            // 特殊効果適用
            applyDaySpecificEffects(target);
            applyKnockback(target);
            spawnAttackEffects();
        } else {
            TungSahurMod.LOGGER.debug("攻撃失敗");
        }
    }

    /**
     * 日数に応じた特殊効果
     */
    private void applyDaySpecificEffects(LivingEntity target) {
        switch (tungSahur.getDayNumber()) {
            case 1:
                // 1日目: 基本効果のみ
                break;
            case 2:
                // 2日目: 軽いノックバック
                Vec3 knockback2 = target.position().subtract(tungSahur.position()).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(
                        knockback2.x * 0.3D, 0.1D, knockback2.z * 0.3D));
                break;
            case 3:
                // 3日目: 強いノックバック + パーティクル
                Vec3 knockback3 = target.position().subtract(tungSahur.position()).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(
                        knockback3.x * 0.5D, 0.2D, knockback3.z * 0.5D));

                if (tungSahur.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            5, 0.3, 0.3, 0.3, 0.1);
                }
                break;
        }
    }

    /**
     * ノックバック効果
     */
    private void applyKnockback(LivingEntity target) {
        Vec3 direction = target.position().subtract(tungSahur.position()).normalize();
        double knockbackStrength = 0.2D + (tungSahur.getDayNumber() * 0.1D);

        target.setDeltaMovement(target.getDeltaMovement().add(
                direction.x * knockbackStrength,
                0.1D,
                direction.z * knockbackStrength));
    }

    /**
     * 攻撃エフェクト
     */
    private void spawnAttackEffects() {
        // バットスイング音
        SoundEvent swingSound = switch (tungSahur.getDayNumber()) {
            case 1 -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case 2 -> SoundEvents.PLAYER_ATTACK_STRONG;
            case 3 -> SoundEvents.PLAYER_ATTACK_CRIT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };

        tungSahur.level().playSound(null,
                tungSahur.getX(), tungSahur.getY(), tungSahur.getZ(),
                swingSound, SoundSource.HOSTILE,
                0.8F, 0.8F + (tungSahur.getDayNumber() * 0.1F));

        // パーティクル効果
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            Vec3 tungPos = tungSahur.position();

            // スイング軌跡パーティクル
            for (int i = 0; i < 3 + tungSahur.getDayNumber(); i++) {
                double x = tungPos.x + (tungSahur.getRandom().nextDouble() - 0.5) * 2.0;
                double y = tungPos.y + tungSahur.getBbHeight() * 0.7;
                double z = tungPos.z + (tungSahur.getRandom().nextDouble() - 0.5) * 2.0;

                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * 攻撃距離の計算
     */
    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        double baseReach = switch (tungSahur.getDayNumber()) {
            case 1 -> 3.5D;
            case 2 -> 4.0D;
            case 3 -> 4.5D;
            default -> 3.5D;
        };

        // スケールファクターによる調整
        double scaleBonus = (tungSahur.getScaleFactor() - 1.0F) * 1.5D;
        double totalReach = baseReach + scaleBonus;

        return totalReach * totalReach;
    }

    /**
     * 攻撃クールダウン管理を無効化
     */
    @Override
    protected void resetAttackCooldown() {
        // ここでは何もしない - checkAndPerformAttackで直接設定
    }

    /**
     * 攻撃可能判定
     */
    @Override
    protected boolean isTimeToAttack() {
        return tungSahur.canAttack() && !isPreparingAttack;
    }

    @Override
    public void tick() {
        super.tick();

        if (isPreparingAttack) {
            attackPreparationTime--;

            // 準備中のパーティクル
            if (attackPreparationTime % 3 == 0 && tungSahur.level() instanceof ServerLevel serverLevel) {
                Vec3 tungPos = tungSahur.position().add(0, tungSahur.getBbHeight() * 0.8, 0);
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        tungPos.x, tungPos.y, tungPos.z,
                        1, 0.2D, 0.2D, 0.2D, 0.0D);
            }

            // 準備完了時にダメージ実行
            if (attackPreparationTime <= 0) {
                isPreparingAttack = false;

                if (pendingTarget != null) {
                    executeDamage(pendingTarget);
                    pendingTarget = null;
                }

                TungSahurMod.LOGGER.debug("攻撃準備完了 - ダメージ実行");
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        isPreparingAttack = false;
        pendingTarget = null;
        attackPreparationTime = 0;

        // Goal停止時はアニメーション状態をリセット
        if (tungSahur.isCurrentlyAttacking()) {
            tungSahur.setCurrentlyAttacking(false);
            TungSahurMod.LOGGER.debug("攻撃Goal停止 - アニメーション状態リセット");
        }
    }
}