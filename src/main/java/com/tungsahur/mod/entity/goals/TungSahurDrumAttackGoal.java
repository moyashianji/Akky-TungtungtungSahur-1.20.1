package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

// ドラム攻撃ゴール（範囲攻撃）
class TungSahurDrumAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private int drumCooldown = 0;
    private int drumChargeTime = 0;

    public TungSahurDrumAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.drumCooldown > 0) return false;

        // 近くに複数のプレイヤーがいる場合、またはステージ2以上で発動
        long nearbyPlayers = this.tungSahur.level().getEntitiesOfClass(Player.class,
                this.tungSahur.getBoundingBox().inflate(16.0D)).size();

        return (nearbyPlayers >= 2 || this.tungSahur.getEvolutionStage() >= 2) &&
                this.tungSahur.getRandom().nextInt(300) == 0;
    }

    @Override
    public void start() {
        this.drumChargeTime = 40; // 2秒のチャージ
    }

    @Override
    public void tick() {
        if (this.drumChargeTime > 0) {
            this.drumChargeTime--;
            this.tungSahur.getNavigation().stop();

            // チャージ音
            if (this.drumChargeTime % 10 == 0) {
                this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 0.5F);
            }
        } else {
            performDrumAttack();
        }

        if (this.drumCooldown > 0) {
            this.drumCooldown--;
        }
    }

    private void performDrumAttack() {
        // 範囲内の全プレイヤーに効果
        this.tungSahur.level().getEntitiesOfClass(Player.class,
                this.tungSahur.getBoundingBox().inflate(20.0D)).forEach(player -> {

            // ダメージ
            player.hurt(this.tungSahur.damageSources().mobAttack(this.tungSahur),
                    4.0F + this.tungSahur.getEvolutionStage() * 2.0F);

            // 状態異常
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION, 100, 1));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

            // ノックバック
            double dx = player.getX() - this.tungSahur.getX();
            double dz = player.getZ() - this.tungSahur.getZ();
            player.knockback(2.0F, dx, dz);
        });

        // 強力な太鼓音
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.3F);

        // パーティクル効果
        for (int i = 0; i < 50; i++) {
            double x = this.tungSahur.getX() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 40.0;
            double y = this.tungSahur.getY() + this.tungSahur.getRandom().nextDouble() * 4.0;
            double z = this.tungSahur.getZ() + (this.tungSahur.getRandom().nextDouble() - 0.5) * 40.0;

            this.tungSahur.level().addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    x, y, z, 0.0, 0.0, 0.0);
        }

        this.drumCooldown = 600; // 30秒のクールダウン
        this.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return this.drumChargeTime > 0;
    }

    @Override
    public void stop() {
        this.drumChargeTime = 0;
    }
}
