// TungSahurThrowGoal.java - 完全版投擲攻撃ゴール（TungSahurEntity互換）
package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class TungSahurThrowGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private LivingEntity target;
    private int chargeTime = 0;
    private int aimTime = 0;
    private boolean isCharging = false;
    private int cooldownAfterThrow = 0;

    public TungSahurThrowGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.tungSahur.getTarget();
        if (this.target == null) return false;

        // Stage1以降のみ使用可能（修正: 0->1に変更）
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        // クールダウン中は使用不可
        if (!this.tungSahur.canThrow()) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // 中距離から遠距離での使用
        return distance >= 6.0D && distance <= 20.0D &&
                this.tungSahur.hasLineOfSight(this.target) &&
                cooldownAfterThrow <= 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.tungSahur.getEvolutionStage() < 1) return false;

        double distance = this.tungSahur.distanceTo(this.target);

        // チャージ中は継続、それ以外は距離と視線をチェック
        return (this.isCharging) ||
                (distance >= 4.0D && distance <= 25.0D &&
                        this.tungSahur.hasLineOfSight(this.target));
    }

    @Override
    public void start() {
        // 進化段階に応じたチャージ時間調整
        int baseChargeTime = 20;
        int stageBonus = this.tungSahur.getEvolutionStage() * 5;
        this.chargeTime = baseChargeTime + this.tungSahur.getRandom().nextInt(20) - stageBonus;

        this.aimTime = 0;
        this.isCharging = true;
        this.cooldownAfterThrow = 0;

        // チャージ開始音（進化段階に応じてピッチ変更）
        float pitch = 0.8F + (this.tungSahur.getEvolutionStage() * 0.1F);
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                SoundEvents.CROSSBOW_LOADING_START, SoundSource.HOSTILE, 0.8F, pitch);

        // チャージ開始パーティクル
        spawnChargeParticles();

        // 投擲状態をエンティティに通知
        this.tungSahur.setThrowing(true);
    }

    @Override
    public void stop() {
        this.chargeTime = 0;
        this.aimTime = 0;
        this.isCharging = false;
        this.tungSahur.getNavigation().stop();
        this.cooldownAfterThrow = 60; // 3秒間のクールダウン

        // 投擲状態を解除
        this.tungSahur.setThrowing(false);
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        // クールダウン更新
        if (this.cooldownAfterThrow > 0) {
            this.cooldownAfterThrow--;
        }

        // ターゲットを見る（進化段階に応じて精度向上）
        float lookAccuracy = 30.0F + (this.tungSahur.getEvolutionStage() * 5.0F);
        this.tungSahur.getLookControl().setLookAt(this.target, lookAccuracy, lookAccuracy);

        if (this.isCharging) {
            // チャージ中は移動停止
            this.tungSahur.getNavigation().stop();

            this.chargeTime--;
            this.aimTime++;

            // チャージ中のパーティクル効果（頻度は進化段階に応じて）
            int particleInterval = 5 - this.tungSahur.getEvolutionStage();
            if (this.aimTime % Math.max(1, particleInterval) == 0) {
                spawnChargeParticles();
            }

            // 最大チャージ時間に達したか、最小チャージ時間経過後にランダムで発動
            boolean shouldThrow = this.chargeTime <= 0;
            if (!shouldThrow && this.aimTime > 15) {
                // 最小チャージ後、進化段階に応じた確率で早期発動
                float earlyThrowChance = 0.05F + (this.tungSahur.getEvolutionStage() * 0.02F);
                shouldThrow = this.tungSahur.getRandom().nextFloat() < earlyThrowChance;
            }

            // チャージ完了時の処理
            if (shouldThrow) {
                executeThrow();
                this.isCharging = false;
            }
        }
    }

    private void executeThrow() {
        if (this.target != null && this.tungSahur.canThrow()) {
            // 投擲実行（TungSahurEntityのメソッドを使用）
            this.tungSahur.performThrowAttack(this.target);

            // 投擲音（進化段階に応じて変化）
            float volume = 1.0F + (this.tungSahur.getEvolutionStage() * 0.2F);
            float pitch = 1.2F + (this.tungSahur.getEvolutionStage() * 0.1F);
            this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, volume, pitch);

            // 投擲時のパーティクル
            spawnThrowParticles();

            // 投擲後の追加効果（Stage2以降）
            if (this.tungSahur.getEvolutionStage() >= 2) {
                spawnAdvancedThrowEffects();
            }
        }
    }

    private void spawnChargeParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 進化段階に応じたパーティクル数
            int particleCount = 3 + this.tungSahur.getEvolutionStage();

            // チャージ中の警告パーティクル
            for (int i = 0; i < particleCount; i++) {
                double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;
                double y = this.tungSahur.getY() + this.tungSahur.getBbHeight() * 0.7 +
                        (this.tungSahur.getRandom().nextDouble() - 0.5) * 0.5;
                double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 1.5;

                // 進化段階に応じてパーティクルタイプを変更
                if (this.tungSahur.getEvolutionStage() >= 2) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
                } else {
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }

            // 手元のエネルギーパーティクル
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.2, this.tungSahur.getZ(),
                    2 + this.tungSahur.getEvolutionStage(), 0.3, 0.1, 0.3, 0.1);
        }
    }

    private void spawnThrowParticles() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // 投擲時の煙パーティクル
            int smokeCount = 8 + (this.tungSahur.getEvolutionStage() * 3);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.0, this.tungSahur.getZ(),
                    smokeCount, 0.5, 0.3, 0.5, 0.1);

            // 威力を示すパーティクル
            int critCount = 6 + (this.tungSahur.getEvolutionStage() * 2);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.2, this.tungSahur.getZ(),
                    critCount, 0.4, 0.2, 0.4, 0.2);
        }
    }

    private void spawnAdvancedThrowEffects() {
        if (this.tungSahur.level() instanceof ServerLevel serverLevel) {
            // Stage2以降の特殊効果

            // 暗い炎のパーティクル
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.tungSahur.getX(), this.tungSahur.getY() + 1.0, this.tungSahur.getZ(),
                    12, 0.6, 0.4, 0.6, 0.15);

            // 不吉な雲
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.tungSahur.getX(), this.tungSahur.getY() + 0.5, this.tungSahur.getZ(),
                    8, 0.8, 0.2, 0.8, 0.05);

            // 追加音響効果
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.5F, 0.8F);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * 他のゴールがこの投擲ゴールの状態を確認できるメソッド
     */
    public boolean isCharging() {
        return this.isCharging;
    }

    /**
     * 緊急時にチャージを中断するメソッド
     */
    public void cancelCharge() {
        if (this.isCharging) {
            this.isCharging = false;
            this.tungSahur.setThrowing(false);
            this.cooldownAfterThrow = 30; // 短めのクールダウン
        }
    }
}