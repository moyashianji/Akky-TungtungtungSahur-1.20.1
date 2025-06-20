// TungSahurMeleeAttackGoal.java - 完璧な近接攻撃
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

        // 日数に応じた攻撃距離（バットの長いリーチ）
        double maxAttackDistance = switch (tungSahur.getDayNumber()) {
            case 1 -> 3.5D; // 1日目: 3.5ブロック
            case 2 -> 4.0D; // 2日目: 4ブロック
            case 3 -> 4.5D; // 3日目: 4.5ブロック
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
        return distance <= 5.0D; // 継続可能距離
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
        double attackReachSqr = this.getAttackReachSqr(target);

        if (distToTargetSqr <= attackReachSqr && this.isTimeToAttack()) {
            performEnhancedAttack(target);
        }
    }

    /**
     * 強化された攻撃実行
     */
    private void performEnhancedAttack(LivingEntity target) {
        if (!tungSahur.canAttack()) return;

        // 攻撃準備開始
        isPreparingAttack = true;
        attackPreparationTime = switch (tungSahur.getDayNumber()) {
            case 1 -> 10; // 0.5秒
            case 2 -> 8;  // 0.4秒
            case 3 -> 5;  // 0.25秒
            default -> 10;
        };

        tungSahur.setCurrentlyAttacking(true);

        // 実際の攻撃実行
        executeAttack(target);
    }

    private void executeAttack(LivingEntity target) {
        // 基本攻撃実行
        boolean hitSuccessful = tungSahur.doHurtTarget(target);

        if (hitSuccessful) {
            // 日数に応じた追加ダメージ
            float additionalDamage = tungSahur.getDayNumber() * 1.5F;
            target.hurt(tungSahur.damageSources().mobAttack(tungSahur), additionalDamage);

            // 日数に応じた特殊効果
            applyDaySpecificEffects(target);

            // ノックバック効果
            applyKnockback(target);

            // パーティクルとサウンド
            spawnAttackEffects();




            TungSahurMod.LOGGER.debug("TungSahur近接攻撃成功: Day={}, ダメージ追加={}",
                    tungSahur.getDayNumber(), additionalDamage);
        }

        // 攻撃完了
        isPreparingAttack = false;
        tungSahur.setCurrentlyAttacking(false);
    }

    /**
     * 日数に応じた特殊効果
     */
    private void applyDaySpecificEffects(LivingEntity target) {
        switch (tungSahur.getDayNumber()) {
            case 1:
                // 1日目: 基本的なノックバック
                break;
            case 2:
                // 2日目: 少し強いノックバック
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
     * 攻撃距離の計算（バットの長さを考慮）
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

    @Override
    protected void resetAttackCooldown() {
        // オーバーライドして独自のクールダウン管理を使用
        // 実際のクールダウンはperformEnhancedAttack内で設定
    }

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

            if (attackPreparationTime <= 0) {
                isPreparingAttack = false;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        isPreparingAttack = false;
        tungSahur.setCurrentlyAttacking(false);
        attackPreparationTime = 0;
    }
}