package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ImprovedWatchPlayerGoal extends Goal {
    private final TungSahurEntity tungSahur;
    private final double speedModifier;
    private final float maxDistance;
    private Player targetPlayer;
    private int teleportChargeTime = 0;
    private int lastTeleportTime = 0;
    private Vec3 lastPlayerPosition = Vec3.ZERO;
    private int stalkTimer = 0;

    public ImprovedWatchPlayerGoal(TungSahurEntity tungSahur, double speedModifier, float maxDistance) {
        this.tungSahur = tungSahur;
        this.speedModifier = speedModifier;
        this.maxDistance = maxDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.targetPlayer = this.tungSahur.level().getNearestPlayer(this.tungSahur, this.maxDistance);
        return this.targetPlayer != null && this.targetPlayer.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetPlayer != null && this.targetPlayer.isAlive() &&
                this.tungSahur.distanceTo(this.targetPlayer) <= this.maxDistance;
    }

    @Override
    public void start() {
        this.stalkTimer = 0;
    }

    @Override
    public void stop() {
        this.targetPlayer = null;
        this.teleportChargeTime = 0;
        this.stalkTimer = 0;
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null) return;

        double distanceToPlayer = this.tungSahur.distanceTo(this.targetPlayer);

        // 視線制御を滑らかに
        this.tungSahur.getLookControl().setLookAt(this.targetPlayer, 30.0F, 30.0F);

        if (this.tungSahur.isBeingWatched()) {
            handleBeingWatched(distanceToPlayer);
        } else {
            handleNotBeingWatched(distanceToPlayer);
        }

        // テレポート処理（頻度を大幅に減らす）
        handleTeleportLogic(distanceToPlayer);

        stalkTimer++;
    }

    private void handleBeingWatched(double distance) {
        // 見られている時は極端に遅く移動（でも完全停止はしない）
        this.tungSahur.getNavigation().setSpeedModifier(0.05D);

        // 近すぎる場合は少し後退
        if (distance < 3.0D) {
            Vec3 awayFromPlayer = this.tungSahur.position().subtract(this.targetPlayer.position()).normalize();
            Vec3 retreatPos = this.tungSahur.position().add(awayFromPlayer.scale(0.5));
            this.tungSahur.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 0.3D);
        }

        // 見られている時のパーティクル（頻度を下げる）
        if (stalkTimer % 30 == 0) {
            spawnWatchedParticles();
        }
    }

    private void handleNotBeingWatched(double distance) {
        // 見られていない時は積極的に接近（でも速すぎない）
        double approachSpeed = this.speedModifier * (1.2D + this.tungSahur.getEvolutionStage() * 0.3D);
        this.tungSahur.getNavigation().setSpeedModifier(approachSpeed);

        // 理想的な距離を保ちながら接近
        if (distance > 8.0D) {
            // 遠い時は直接接近
            this.tungSahur.getNavigation().moveTo(this.targetPlayer, approachSpeed);
        } else if (distance > 4.0D) {
            // 中距離では側面から接近
            Vec3 playerPos = this.targetPlayer.position();
            Vec3 offset = new Vec3(
                    (this.tungSahur.getRandom().nextDouble() - 0.5) * 6.0,
                    0,
                    (this.tungSahur.getRandom().nextDouble() - 0.5) * 6.0
            );
            Vec3 targetPos = playerPos.add(offset);
            this.tungSahur.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, approachSpeed * 0.8);
        }

        // プレイヤーの位置を記録（テレポート判定用）
        lastPlayerPosition = this.targetPlayer.position();
    }

    private void handleTeleportLogic(double distance) {
        // テレポート条件を厳しくして頻度を大幅に減らす
        if (this.tungSahur.getEvolutionStage() >= 2 &&
                lastTeleportTime <= 0 &&
                this.tungSahur.getRandom().nextInt(800) == 0) { // 0.125%の確率（元の1/6.4）

            // プレイヤーが移動している場合のみテレポートを検討
            double playerMovement = lastPlayerPosition.distanceTo(this.targetPlayer.position());
            if (playerMovement > 2.0 || distance > 20.0D) {
                if (teleportChargeTime <= 0) {
                    teleportChargeTime = 40; // 2秒チャージ
                    spawnTeleportChargeParticles();
                }
            }
        }

        // テレポートチャージ処理
        if (teleportChargeTime > 0) {
            teleportChargeTime--;
            this.tungSahur.getNavigation().stop();

            if (teleportChargeTime % 8 == 0) {
                spawnTeleportChargingParticles();
            }

            if (teleportChargeTime <= 0) {
                performTeleportAttack();
                lastTeleportTime = 600; // 30秒クールダウン
            }
        }

        if (lastTeleportTime > 0) {
            lastTeleportTime--;
        }
    }

    private void spawnWatchedParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 控えめな恐怖パーティクル
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2.0 / 3.0;
                double radius = 1.0;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 0.5;

                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        x, y, z, 1, 0.0, 0.05, 0.0, 0.0);
            }
        }
    }

    private void spawnTeleportChargeParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 控えめなチャージパーティクル
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = 1.0;
                double x = tungSahur.getX() + Math.cos(angle) * radius;
                double z = tungSahur.getZ() + Math.sin(angle) * radius;
                double y = tungSahur.getY() + 1.0;

                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.1);
            }

            // チャージ音
            serverLevel.playSound(null, tungSahur.blockPosition(),
                    SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.5F, 0.5F);
        }
    }

    private void spawnTeleportChargingParticles() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // 段階的なチャージ演出
            int stage = (40 - teleportChargeTime) / 10;
            for (int i = 0; i < 5 + stage * 2; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
                double offsetY = serverLevel.random.nextDouble() * 2.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;

                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        tungSahur.getX() + offsetX, tungSahur.getY() + offsetY, tungSahur.getZ() + offsetZ,
                        1, 0.0, 0.1, 0.0, 0.05);
            }
        }
    }

    private void performTeleportAttack() {
        if (tungSahur.level() instanceof ServerLevel serverLevel) {
            // テレポート前のパーティクル
            spawnTeleportDisappearParticles(serverLevel);
        }

        // プレイヤーの近くの安全な場所を探す
        Vec3 teleportPos = findSafeTeleportPosition();
        if (teleportPos != null) {
            this.tungSahur.teleportTo(teleportPos.x, teleportPos.y, teleportPos.z);

            if (tungSahur.level() instanceof ServerLevel serverLevel) {
                spawnTeleportAppearParticles(serverLevel, teleportPos);

                // テレポート音
                serverLevel.playSound(null, this.tungSahur.blockPosition(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.8F);
            }
        }

        teleportChargeTime = 0;
    }

    private Vec3 findSafeTeleportPosition() {
        Vec3 playerPos = this.targetPlayer.position();

        // プレイヤーの周囲8ブロック以内の安全な場所を探す
        for (int attempts = 0; attempts < 20; attempts++) {
            double angle = this.tungSahur.getRandom().nextDouble() * 2 * Math.PI;
            double distance = 4.0 + this.tungSahur.getRandom().nextDouble() * 4.0;

            double x = playerPos.x + Math.cos(angle) * distance;
            double z = playerPos.z + Math.sin(angle) * distance;
            double y = playerPos.y;

            // 安全性チェック
            if (isSafeTeleportLocation(x, y, z)) {
                return new Vec3(x, y, z);
            }
        }

        return null; // 安全な場所が見つからない
    }

    private boolean isSafeTeleportLocation(double x, double y, double z) {
        // 地面があり、頭上に十分な空間があるかチェック
        return this.tungSahur.level().isEmptyBlock(new net.minecraft.core.BlockPos((int)x, (int)y, (int)z)) &&
                this.tungSahur.level().isEmptyBlock(new net.minecraft.core.BlockPos((int)x, (int)y + 1, (int)z)) &&
                !this.tungSahur.level().isEmptyBlock(new net.minecraft.core.BlockPos((int)x, (int)y - 1, (int)z));
    }

    private void spawnTeleportDisappearParticles(ServerLevel serverLevel) {
        // 控えめな消失パーティクル
        for (int i = 0; i < 15; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
            double offsetY = serverLevel.random.nextDouble() * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;

            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    tungSahur.getX() + offsetX, tungSahur.getY() + offsetY, tungSahur.getZ() + offsetZ,
                    1, 0.0, 0.2, 0.0, 0.0);
        }
    }

    private void spawnTeleportAppearParticles(ServerLevel serverLevel, Vec3 pos) {
        // 控えめな出現パーティクル
        for (int i = 0; i < 20; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.5;
            double offsetY = serverLevel.random.nextDouble() * 2.5;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.5;

            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0.0, 0.0, 0.0, 0.1);
        }
    }
}