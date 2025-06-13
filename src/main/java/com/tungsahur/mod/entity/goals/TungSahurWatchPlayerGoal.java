package com.tungsahur.mod.entity.goals;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.player.Player;

// プレイヤー監視ゴール
public // プレイヤー監視ゴール
class TungSahurWatchPlayerGoal extends MoveTowardsTargetGoal {
    private final TungSahurEntity tungSahur;

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
            // プレイヤーが見ている場合の特殊行動
            if (this.tungSahur.isBeingWatched()) {
                // 見られている間は極端に遅く移動
                this.tungSahur.getNavigation().setSpeedModifier(0.1D);

                // 時々テレポート攻撃
                if (this.tungSahur.getRandom().nextInt(200) == 0 && this.tungSahur.getEvolutionStage() >= 2) {
                    performTeleportAttack(player);
                }
            } else {
                // 見られていない時は高速移動
                this.tungSahur.getNavigation().setSpeedModifier(1.5D + this.tungSahur.getEvolutionStage() * 0.5D);
            }
        }
    }

    private void performTeleportAttack(Player player) {
        // プレイヤーの背後にテレポート
        double angle = Math.toRadians(player.getYRot() + 180 + (tungSahur.getRandom().nextFloat() - 0.5F) * 60);
        double distance = 3.0D + tungSahur.getRandom().nextDouble() * 2.0D;

        double newX = player.getX() + Math.sin(angle) * distance;
        double newZ = player.getZ() + Math.cos(angle) * distance;
        double newY = player.getY();

        // テレポート実行
        this.tungSahur.teleportTo(newX, newY, newZ);

        // テレポート効果
        this.tungSahur.level().playSound(null, this.tungSahur.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.8F);
    }
}
