package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.EnumSet;

public class TungSahurDrumAttackGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private int drumCooldown = 0;
    private int drumChargeTime = 0;
    private int drumBeats = 0;

    public TungSahurDrumAttackGoal(TungSahurEntity tungSahur) {
        this.tungSahur = tungSahur;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.drumCooldown > 0) return false;

        long nearbyPlayers = this.tungSahur.level().getEntitiesOfClass(Player.class,
                this.tungSahur.getBoundingBox().inflate(16.0D)).size();

        return (nearbyPlayers >= 2 || this.tungSahur.getEvolutionStage() >= 2) &&
                this.tungSahur.getRandom().nextInt(300) == 0;
    }

    @Override
    public void start() {
        this.drumChargeTime = 60; // 3秒のチャージ
        this.drumBeats = 0;
        spawnDrumStartParticles();
    }

    @Override
    public void tick() {
        if (this.drumChargeTime > 0) {
            this.drumChargeTime--;
            this.tungSahur.getNavigation().stop();

            // 太鼓のビート毎にパーティクル
            if (this.drumChargeTime % 15 == 0) {
                spawnDrumBeatParticles();
                playDrumBeat();
                drumBeats++;
            }

            // 継続的なチャージパーティクル
            if (this.drumChargeTime % 3 == 0) {
                spawnChargingParticles();
            }
        } else {
            performDrumAttack();
        }

        if (this.drumCooldown > 0) {
            this.drumCooldown--;
        }
    }

    private void spawnDrumStartParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 空に向かって上昇する光の柱
            for (int i = 0; i < 30; i++) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        tungSahur.getX(), tungSahur.getY() + i * 0.5, tungSahur.getZ(),
                        1, 0.1, 0.0, 0.1, 0.0);
            }

            // 地面に巨大な魔法陣
            for (int ring = 1; ring <= 8; ring++) {
                for (int i = 0; i < 12 * ring; i++) {
                    double angle = (i * 2 * Math.PI) / (12 * ring);
                    double radius = ring * 2.5;
                    double x = tungSahur.getX() + Math.cos(angle) * radius;
                    double z = tungSahur.getZ() + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }
        }
    }

    private void spawnDrumBeatParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 太鼓からの衝撃波リング
            for (int ring = 1; ring <= 5; ring++) {
                for (int i = 0; i < 20; i++) {
                    double angle = i * Math.PI / 10;
                    double radius = ring * 4.0;
                    double x = tungSahur.getX() + Math.cos(angle) * radius;
                    double z = tungSahur.getZ() + Math.sin(angle) * radius;
                    double y = tungSahur.getY() + 1.0 + Math.sin(angle * 2) * 0.5;

                    serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                            x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                    serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                            x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }

            // 空中に爆発パーティクル
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = 3.0 + drumBeats * 1.5;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 2.0 + drumBeats * 0.5;

                serverLevel.sendParticles(ParticleTypes.FIREWORK,
                        x, y, z, 3, 0.3, 0.3, 0.3, 0.1);
            }

            // 太鼓本体からの稲妻風パーティクル
            for (int i = 0; i < 12; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;
                double offsetY = serverLevel.random.nextDouble() * 2.0;

                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        tungSahur.getX() + offsetX, tungSahur.getY() + 1.0 + offsetY, tungSahur.getZ() + offsetZ,
                        1, 0.0, 0.5, 0.0, 0.2);
            }
        }
    }

    private void spawnChargingParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 螺旋状に上昇するエネルギー
            double time = (60 - drumChargeTime) * 0.2;
            for (int i = 0; i < 4; i++) {
                double angle = time + i * Math.PI / 2;
                double radius = 2.0;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 0.5 + time * 0.1;

                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        x, y, z, 1, 0.0, 0.2, 0.0, 0.0);
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }

            // 地面からの蒸気
            for (int i = 0; i < 15; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 20.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 20.0;

                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        tungSahur.getX() + offsetX, tungSahur.getY() + 0.1, tungSahur.getZ() + offsetZ,
                        1, 0.0, 0.3, 0.0, 0.0);
            }
        }
    }

    private void playDrumBeat() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            float pitch = 0.5F + drumBeats * 0.1F;
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BASEDRUM.get(), SoundSource.HOSTILE,
                    1.5F + drumBeats * 0.2F, pitch);
        }
    }

    private void performDrumAttack() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 最終爆発パーティクル
            spawnFinalDrumExplosion(serverLevel);

            // 範囲内の全プレイヤーに効果
            this.tungSahur.level().getEntitiesOfClass(Player.class,
                    this.tungSahur.getBoundingBox().inflate(20.0D)).forEach(player -> {

                // ダメージ
                player.hurt(this.tungSahur.damageSources().mobAttack(this.tungSahur),
                        4.0F + this.tungSahur.getEvolutionStage() * 2.0F);

                // 状態異常
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));

                // ノックバック
                double dx = player.getX() - this.tungSahur.getX();
                double dz = player.getZ() - this.tungSahur.getZ();
                player.knockback(2.0F, dx, dz);

                // プレイヤー周りの衝撃パーティクル
                spawnPlayerImpactParticles(serverLevel, player);
            });

            // 強力な太鼓音
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BASEDRUM.get(), SoundSource.HOSTILE, 3.0F, 0.3F);
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 0.5F);
        }

        this.drumCooldown = 600; // 30秒のクールダウン
        this.stop();
    }

    private void spawnFinalDrumExplosion(ServerLevel serverLevel) {
        // 巨大な爆発の中心
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                tungSahur.getX(), tungSahur.getY() + 1.0, tungSahur.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);

        // 超巨大衝撃波
        for (int ring = 1; ring <= 10; ring++) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                double radius = ring * 3.0;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 0.5 + Math.sin(angle * 3 + ring) * 0.8;

                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 1, 0.0, 0.3, 0.0, 0.0);
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, y, z, 1, 0.0, 0.2, 0.0, 0.0);
            }
        }

        // 天空への光の柱
        for (int i = 0; i < 50; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    tungSahur.getX() + offsetX, tungSahur.getY() + i * 0.8, tungSahur.getZ() + offsetZ,
                    1, 0.0, 0.5, 0.0, 0.0);
        }

        // 地面のクラック表現
        for (int i = 0; i < 100; i++) {
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
            double radius = serverLevel.random.nextDouble() * 25.0;
            double x = tungSahur.getX() + Math.cos(angle) * radius;
            double z = tungSahur.getZ() + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.LAVA,
                    x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnPlayerImpactParticles(ServerLevel serverLevel, Player player) {
        // プレイヤー周りの衝撃リング
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = ring * 0.8;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.CRIT,
                        x, player.getY() + 1.0, z, 1, 0.0, 0.2, 0.0, 0.1);
            }
        }

        // プレイヤーの足元から炎
        for (int i = 0; i < 10; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX() + offsetX, player.getY() + 0.1, player.getZ() + offsetZ,
                    1, 0.0, 0.3, 0.0, 0.1);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.drumChargeTime > 0;
    }

    @Override
    public void stop() {
        this.drumChargeTime = 0;
        this.drumBeats = 0;
    }
}