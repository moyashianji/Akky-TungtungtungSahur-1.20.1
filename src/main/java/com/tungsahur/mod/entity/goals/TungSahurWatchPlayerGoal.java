package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.player.Player;

public class TungSahurWatchPlayerGoal extends MoveTowardsTargetGoal {
    private final TungSahurEntity tungSahur;
    private int teleportChargeTime = 0;

    public TungSahurWatchPlayerGoal(TungSahurEntity tungSahur, double speedModifier, float within32Blocks) {
        super(tungSahur, speedModifier, within32Blocks);
        this.tungSahur = tungSahur;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.tungSahur.getTarget() instanceof Player;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tungSahur.getTarget() instanceof Player player) {
            if (this.tungSahur.isBeingWatched()) {
                // 見られている間は極端に遅く移動
                this.tungSahur.getNavigation().setSpeedModifier(0.1D);

                // テレポート攻撃の準備
                if (this.tungSahur.getRandom().nextInt(200) == 0 && this.tungSahur.getEvolutionStage() >= 2) {
                    if (teleportChargeTime <= 0) {
                        teleportChargeTime = 30; // 1.5秒チャージ
                        spawnTeleportChargeParticles();
                    }
                }
            } else {
                // 見られていない時は高速移動
                this.tungSahur.getNavigation().setSpeedModifier(1.5D + this.tungSahur.getEvolutionStage() * 0.5D);
            }

            // テレポートチャージ処理
            if (teleportChargeTime > 0) {
                teleportChargeTime--;
                if (teleportChargeTime % 5 == 0) {
                    spawnTeleportChargingParticles();
                }
                if (teleportChargeTime <= 0) {
                    performTeleportAttack(player);
                }
            }
        }
    }

    private void spawnTeleportChargeParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 体の周りに次元の裂け目風パーティクル
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI / 10;
                double radius = 1.5;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 1.0 + Math.sin(angle * 2) * 0.5;

                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
                serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
            }

            // 足元に暗黒の渦
            for (int ring = 1; ring <= 4; ring++) {
                for (int i = 0; i < 8 * ring; i++) {
                    double angle = (i * 2 * Math.PI) / (8 * ring) + tungSahur.tickCount * 0.1;
                    double radius = ring * 0.5;
                    double x = tungSahur.getX() + Math.cos(angle) * radius;
                    double z = tungSahur.getZ() + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                            x, tungSahur.getY() + 0.1, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }

            // チャージ音
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }

    private void spawnTeleportChargingParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 体が消失していく演出
            for (int i = 0; i < 15; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.5;
                double offsetY = serverLevel.random.nextDouble() * 2.5;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.5;

                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        tungSahur.getX() + offsetX, tungSahur.getY() + offsetY, tungSahur.getZ() + offsetZ,
                        1, 0.0, 0.2, 0.0, 0.1);
                serverLevel.sendParticles(ParticleTypes.ASH,
                        tungSahur.getX() + offsetX, tungSahur.getY() + offsetY, tungSahur.getZ() + offsetZ,
                        1, 0.0, -0.1, 0.0, 0.0);
            }

            // エンドポータル風のパーティクル
            for (int i = 0; i < 8; i++) {
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                        tungSahur.getX(), tungSahur.getY() + 1.0, tungSahur.getZ(),
                        1, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    private void performTeleportAttack(Player player) {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // テレポート前の消失パーティクル
            spawnTeleportDisappearParticles(serverLevel, tungSahur.getX(), tungSahur.getY(), tungSahur.getZ());
        }

        // プレイヤーの背後にテレポート
        double angle = Math.toRadians(player.getYRot() + 180 + (tungSahur.getRandom().nextFloat() - 0.5F) * 60);
        double distance = 3.0D + tungSahur.getRandom().nextDouble() * 2.0D;

        double newX = player.getX() + Math.sin(angle) * distance;
        double newZ = player.getZ() + Math.cos(angle) * distance;
        double newY = player.getY();

        // テレポート実行
        this.tungSahur.teleportTo(newX, newY, newZ);

        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // テレポート後の出現パーティクル
            spawnTeleportAppearParticles(serverLevel, newX, newY, newZ);

            // テレポート効果音
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
            serverLevel.playSound(null, this.tungSahur.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.8F, 1.5F);
        }

        teleportChargeTime = 0;
    }

    private void spawnTeleportDisappearParticles(ServerLevel serverLevel, double x, double y, double z) {
        // 消失時の暗黒爆発
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                x, y + 1.0, z, 3, 0.5, 0.5, 0.5, 0.0);

        // 上昇する暗黒パーティクル
        for (int i = 0; i < 30; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 2.0;
            double velocityY = serverLevel.random.nextDouble() * 1.5 + 0.5;

            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    x + offsetX, y + 0.5, z + offsetZ,
                    1, 0.0, velocityY, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    x + offsetX, y + 0.5, z + offsetZ,
                    1, 0.0, velocityY * 0.5, 0.0, 0.0);
        }

        // 次元の裂け目
        for (int i = 0; i < 50; i++) {
            double angle = i * Math.PI / 25;
            double radius = 2.0;
            double particleX = x + Math.cos(angle) * radius;
            double particleZ = z + Math.sin(angle) * radius;
            double particleY = y + 1.0 + Math.sin(angle * 3) * 0.8;

            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    particleX, particleY, particleZ, 1, 0.0, 0.0, 0.0, 0.1);
        }
    }

    private void spawnTeleportAppearParticles(ServerLevel serverLevel, double x, double y, double z) {
        // 出現時の暗黒爆発
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);

        // 放射状の衝撃波
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            double velocityX = Math.cos(angle) * 1.5;
            double velocityZ = Math.sin(angle) * 1.5;

            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                    x, y + 1.0, z, 1, velocityX, 0.2, velocityZ, 0.2);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x, y + 1.0, z, 1, velocityX * 0.5, 0.5, velocityZ * 0.5, 0.0);
        }

        // 地面からの炎の柱
        for (int i = 0; i < 20; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 3.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 3.0;

            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x + offsetX, y + 0.1, z + offsetZ,
                    1, 0.0, 1.0, 0.0, 0.0);
        }

        // 次元の歪み
        for (int ring = 1; ring <= 5; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = ring * 0.8;
                double particleX = x + Math.cos(angle) * radius;
                double particleZ = z + Math.sin(angle) * radius;
                double particleY = y + 0.5 + ring * 0.2;

                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        particleX, particleY, particleZ, 1, 0.0, 0.0, 0.0, 0.1);
            }
        }

        // 雷撃風パーティクル
        for (int i = 0; i < 15; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
            double offsetY = serverLevel.random.nextDouble() * 3.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0.0, 0.0, 0.0, 0.3);
        }
    }
}