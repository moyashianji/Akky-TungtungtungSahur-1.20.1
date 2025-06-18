// TungSahurMeleeAttackGoal.java - 完全版近接攻撃ゴール
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

public class TungSahurMeleeAttackGoal extends MeleeAttackGoal {
    private final TungSahurEntity tungSahur;
    private int attackDelay = 0;
    private int comboCounter = 0;
    private int lastAttackTime = 0;

    public TungSahurMeleeAttackGoal(TungSahurEntity tungSahur, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(tungSahur, speedModifier, followingTargetEvenIfNotSeen);
        this.tungSahur = tungSahur;
    }

    @Override
    public boolean canUse() {
        // 基本条件チェック
        if (!super.canUse()) return false;

        // 攻撃可能状態かチェック
        if (!this.tungSahur.canAttack()) return false;

        // 他のアクション中は近接攻撃不可
        if (this.tungSahur.isThrowing() || this.tungSahur.isJumping()) return false;

        LivingEntity target = this.tungSahur.getTarget();
        if (target == null) return false;

        double distance = this.tungSahur.distanceTo(target);

        // 近接攻撃範囲内かチェック
        return distance <= getAttackReachSqr(target);
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) return false;

        // 攻撃中は継続
        if (this.tungSahur.isAttacking()) return true;

        LivingEntity target = this.tungSahur.getTarget();
        if (target == null) return false;

        double distance = this.tungSahur.distanceTo(target);
        return distance <= getAttackReachSqr(target) * 1.5; // 少し余裕を持たせる
    }

    @Override
    public void start() {
        super.start();
        this.attackDelay = 0;
        this.tungSahur.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.tungSahur.setAggressive(false);
        this.comboCounter = 0;
        this.lastAttackTime = 0;
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity target = this.tungSahur.getTarget();
        if (target == null) return;

        // 攻撃ディレイの更新
        if (this.attackDelay > 0) {
            this.attackDelay--;
        }

        // ターゲットを見る
        this.tungSahur.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distance = this.tungSahur.distanceTo(target);

        // 攻撃可能距離内で攻撃可能状態の場合
        if (distance <= getAttackReachSqr(target) && this.attackDelay <= 0 && this.tungSahur.canAttack()) {
            // 見られている場合は攻撃を遅らせる
            if (this.tungSahur.isBeingWatched()) {
                this.attackDelay = 10 + this.tungSahur.getRandom().nextInt(20);
            } else {
                performAttack(target);
            }
        }

        // コンボタイマーの更新
        if (this.tungSahur.tickCount - this.lastAttackTime > 60) {
            this.comboCounter = 0; // 3秒経過でコンボリセット
        }
    }

    private void performAttack(LivingEntity target) {
        // 攻撃実行
        this.tungSahur.performMeleeAttack(target);

        // 攻撃後のディレイ設定（進化段階に応じて短縮）
        int baseDelay = 30;
        int stageReduction = this.tungSahur.getEvolutionStage() * 5;
        this.attackDelay = Math.max(10, baseDelay - stageReduction);

        // コンボカウンター更新
        this.comboCounter++;
        this.lastAttackTime = this.tungSahur.tickCount;

        // コンボボーナス（3回連続攻撃後）
        if (this.comboCounter >= 3) {
            applyComboBonus(target);
            this.comboCounter = 0;
        }

        // 進化段階に応じた追加効果
        applyStageBasedEffects(target);
    }

    private void applyComboBonus(LivingEntity target) {
        // コンボボーナス：追加ダメージとノックバック
        float bonusDamage = 3.0F + (this.tungSahur.getEvolutionStage() * 2.0F);
        target.hurt(this.tungSahur.damageSources().mobAttack(this.tungSahur), bonusDamage);

        // ノックバック効果
        double knockbackStrength = 0.5D + (this.tungSahur.getEvolutionStage() * 0.2D);
        double dx = target.getX() - this.tungSahur.getX();
        double dz = target.getZ() - this.tungSahur.getZ();
        target.knockback(knockbackStrength, dx, dz);

        // コンボエフェクト
        if (this.tungSahur.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    5, 0.5, 0.3, 0.5, 0.1);
        }

        // コンボ音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.2F);
    }

    private void applyStageBasedEffects(LivingEntity target) {
        switch (this.tungSahur.getEvolutionStage()) {
            case 1:
                // Stage1: 基本攻撃＋軽微な出血効果
                if (this.tungSahur.getRandom().nextFloat() < 0.3F) {
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WITHER, 40, 0));
                }
                break;

            case 2:
                // Stage2: より強力な効果
                if (this.tungSahur.getRandom().nextFloat() < 0.5F) {
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WITHER, 60, 1));
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                }

                // 恐怖効果（プレイヤーの場合）
                if (target instanceof net.minecraft.world.entity.player.Player) {
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.DARKNESS, 100, 0));
                }
                break;
        }
    }

    public double getAttackReachSqr(LivingEntity target) {
        // 基本攻撃範囲 + 進化段階ボーナス + エンティティサイズ
        double baseReach = 2.0D;
        double stageBonus = this.tungSahur.getEvolutionStage() * 0.5D;
        double entitySize = this.tungSahur.getBbWidth() + target.getBbWidth();

        double totalReach = baseReach + stageBonus + entitySize;
        return totalReach * totalReach; // 2乗で返す
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distToTargetSqr) {
        double attackReachSqr = this.getAttackReachSqr(target);

        if (distToTargetSqr <= attackReachSqr && this.getTicksUntilNextAttack() <= 0) {
            this.resetAttackCooldown();
            performAttack(target);
        }
    }

    @Override
    protected int getTicksUntilNextAttack() {
        return this.attackDelay;
    }

    @Override
    protected void resetAttackCooldown() {
        // 攻撃クールダウンをリセット（進化段階に応じて調整）
        int baseCooldown = 20;
        int stageReduction = this.tungSahur.getEvolutionStage() * 3;
        this.attackDelay = Math.max(8, baseCooldown - stageReduction);
    }

    /**
     * 緊急回避：他のゴールから呼び出して近接攻撃を中断
     */
    public void interruptAttack() {
        this.attackDelay = 20; // 1秒間攻撃不可
        this.comboCounter = 0; // コンボリセット
    }

    /**
     * コンボ状況の取得
     */
    public int getComboCounter() {
        return this.comboCounter;
    }

    /**
     * 攻撃可能状態の確認
     */
    public boolean isReadyToAttack() {
        return this.attackDelay <= 0 && this.tungSahur.canAttack();
    }
}