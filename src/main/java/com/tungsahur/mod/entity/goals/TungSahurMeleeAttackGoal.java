// TungSahurMeleeAttackGoal.java - 近接攻撃ゴール
package com.tungsahur.mod.entity.goals;

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
    private int attackAnimationTick = 0;
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

        // 日数に応じた攻撃距離の調整
        double maxAttackDistance = switch (tungSahur.getDayNumber()) {
            case 1 -> 2.5D;
            case 2 -> 3.0D;
            case 3 -> 3.5D;
            default -> 2.5D;
        };

        return distance <= maxAttackDistance;
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) return false;
        if (isPreparingAttack) return true; // 攻撃準備中は継続

        LivingEntity target = tungSahur.getTarget();
        if (target == null) return false;

        double distance = tungSahur.distanceTo(target);
        return distance <= 4.0D; // 継続可能距離は少し広めに
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
        double d0 = this.getAttackReachSqr(target);

        if (distToTargetSqr <= d0 && this.isTimeToAttack()) {
            this.resetAttackCooldown();
            performEnhancedAttack(target);
        }
    }

    private void performEnhancedAttack(LivingEntity target) {
        if (!tungSahur.canAttack()) return;

        // 攻撃準備開始
        isPreparingAttack = true;
        attackAnimationTick = 0;
        tungSahur.setCurrentlyAttacking(true);

        // 準備時間（日数が高いほど短い）
        int preparationTime = switch (tungSahur.getDayNumber()) {
            case 1 -> 15; // 0.75秒
            case 2 -> 12; // 0.6秒
            case 3 -> 10; // 0.5秒
            default -> 15;
        };

        // 攻撃実行をスケジュール
        tungSahur.level().scheduleTick(tungSahur.blockPosition(),
                tungSahur.level().getBlockState(tungSahur.blockPosition()).getBlock(),
                preparationTime);

        executeAttack(target);
    }

    private void executeAttack(LivingEntity target) {
        // 基本攻撃力に日数ボーナスを追加
        float baseDamage = (float) tungSahur.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float dayBonus = tungSahur.getDayNumber() * 1.5F;
        float totalDamage = baseDamage + dayBonus;

        // スケールによるダメージボーナス
        float scaleBonus = (tungSahur.getScaleFactor() - 1.0F) * 2.0F;
        totalDamage += scaleBonus;

        // 実際の攻撃実行
        boolean hitSuccessful = tungSahur.doHurtTarget(target);

        if (hitSuccessful) {
            // 追加ダメージ適用
            target.hurt(tungSahur.damageSources().mobAttack(tungSahur), totalDamage - baseDamage);

            // 日数に応じた特殊効果
            applyDaySpecificEffects(target);

            // ノックバック効果
            applyKnockback(target);

            // パーティクルとサウンド
            spawnAttackEffects(target);
        }

        // クールダウン設定
        int cooldown = switch (tungSahur.getDayNumber()) {
            case 1 -> 40; // 2秒
            case 2 -> 35; // 1.75秒
            case 3 -> 30; // 1.5秒
            default -> 40;
        };

        tungSahur.setAttackCooldown(cooldown);

        // 攻撃状態リセット
        isPreparingAttack = false;
        tungSahur.setCurrentlyAttacking(false);
    }

    private void applyDaySpecificEffects(LivingEntity target) {
        switch (tungSahur.getDayNumber()) {
            case 1:
                // 1日目：基本攻撃のみ、追加効果なし
                break;

            case 2:
                // 2日目：軽い出血効果（継続ダメージ）
                if (tungSahur.level() instanceof ServerLevel) {
                    target.hurt(tungSahur.damageSources().mobAttack(tungSahur), 1.0F);
                }
                break;

            case 3:
                // 3日目：強力な出血効果
                if (tungSahur.level() instanceof ServerLevel) {
                    target.hurt(tungSahur.damageSources().mobAttack(tungSahur), 2.0F);
                    // 追加の混乱効果（視界を少し揺らす）
                    if (target instanceof net.minecraft.world.entity.player.Player) {
                        // プレイヤーには効果を付与しない（要求仕様）
                    }
                }
                break;
        }
    }

    private void applyKnockback(LivingEntity target) {
        // 日数とスケールに応じたノックバック
        float knockbackStrength = tungSahur.getDayNumber() * 0.3F + (tungSahur.getScaleFactor() - 1.0F) * 0.4F;

        Vec3 direction = target.position().subtract(tungSahur.position()).normalize();
        Vec3 knockbackVec = direction.scale(knockbackStrength);

        target.setDeltaMovement(target.getDeltaMovement().add(knockbackVec.x, Math.max(0.1D, knockbackVec.y * 0.5D), knockbackVec.z));
        target.hurtMarked = true;
    }

    private void spawnAttackEffects(LivingEntity target) {
        if (!(tungSahur.level() instanceof ServerLevel serverLevel)) return;

        // 攻撃時のパーティクル
        Vec3 attackPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // 基本の攻撃パーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                attackPos.x, attackPos.y, attackPos.z,
                5 + tungSahur.getDayNumber() * 2,
                0.2D, 0.2D, 0.2D, 0.1D);

        // 日数に応じた追加パーティクル
        switch (tungSahur.getDayNumber()) {
            case 2:
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        attackPos.x, attackPos.y, attackPos.z,
                        3, 0.3D, 0.3D, 0.3D, 0.1D);
                break;

            case 3:
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        attackPos.x, attackPos.y, attackPos.z,
                        5, 0.5D, 0.5D, 0.5D, 0.1D);

                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        attackPos.x, attackPos.y, attackPos.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                break;
        }

        // サウンド効果
        SoundEvent attackSound = switch (tungSahur.getDayNumber()) {
            case 1 -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case 2 -> SoundEvents.PLAYER_ATTACK_STRONG;
            case 3 -> SoundEvents.PLAYER_ATTACK_CRIT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };

        tungSahur.level().playSound(null, tungSahur.getX(), tungSahur.getY(), tungSahur.getZ(),
                attackSound, SoundSource.HOSTILE,
                0.8F + tungSahur.getDayNumber() * 0.1F,
                0.8F + tungSahur.getDayNumber() * 0.1F);

        // バットの振り音
        tungSahur.level().playSound(null, tungSahur.getX(), tungSahur.getY(), tungSahur.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE,
                0.6F, 1.2F);
    }

    @Override
    protected double getAttackReachSqr(LivingEntity attackTarget) {
        // 日数とスケールに応じた攻撃範囲
        double baseReach = tungSahur.getBbWidth() * 2.0D + attackTarget.getBbWidth();
        double dayBonus = tungSahur.getDayNumber() * 0.3D;
        double scaleBonus = (tungSahur.getScaleFactor() - 1.0D) * 0.5D;

        return (baseReach + dayBonus + scaleBonus) * (baseReach + dayBonus + scaleBonus);
    }

    @Override
    protected void resetAttackCooldown() {
        // オーバーライドして独自のクールダウン管理を使用
        // 実際のクールダウンはperformEnhancedAttack内で設定
    }

    @Override
    protected boolean isTimeToAttack() {
        // 独自のクールダウン管理を使用
        return tungSahur.canAttack() && !isPreparingAttack;
    }

    @Override
    public void tick() {
        super.tick();

        if (isPreparingAttack) {
            attackAnimationTick++;

            // 準備中のパーティクル
            if (attackAnimationTick % 3 == 0 && tungSahur.level() instanceof ServerLevel serverLevel) {
                Vec3 tungPos = tungSahur.position().add(0, tungSahur.getBbHeight() * 0.8, 0);
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        tungPos.x, tungPos.y, tungPos.z,
                        1, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        isPreparingAttack = false;
        tungSahur.setCurrentlyAttacking(false);
        attackAnimationTick = 0;
    }
}