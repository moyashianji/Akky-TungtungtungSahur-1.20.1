package com.tungsahur.mod.events;

import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class BedSleepEvent {

    @SubscribeEvent
    public void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        Level world = player.level();

        if (world.isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) world;
        DayCountSavedData data = DayCountSavedData.get(serverLevel);

        // ゲームがアクティブでない場合は通常通り
        if (!data.isActive()) {
            return;
        }

        // Tung Sahurが近くにいるかチェック
        List<TungSahurEntity> nearbyTungSahur = world.getEntitiesOfClass(
                TungSahurEntity.class,
                player.getBoundingBox().inflate(64.0)
        );

        // ゲームがアクティブな場合は常にベッドを使用不可
        if (data.isActive()) {
            event.setResult(Player.BedSleepingProblem.NOT_SAFE);

            // 恐怖メッセージをランダムで選択
            String[] fearMessages = {
                    "§c何かが見ている...",
                    "§4なんか嫌な夢を見そうな気がする",
                    "§c太鼓の音が聞こえる...",
                    "§4眠ってはいけない...",
                    "§c彼がやってくる...",
                    "§4Tung... Tung... Tung...",
                    "§c安全ではない...",
                    "§4夜はまだ終わらない"
            };

            String message = fearMessages[world.random.nextInt(fearMessages.length)];
            player.sendSystemMessage(Component.literal(message));

            // ホラー効果を発動
            spawnHorrorEffects(player, serverLevel, !nearbyTungSahur.isEmpty());
        }
    }

    private void spawnHorrorEffects(Player player, ServerLevel level, boolean tungSahurNearby) {
        // 恐怖音
        if (tungSahurNearby) {
            // Tung Sahurが近くにいる場合はより恐ろしい効果
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                    SoundSource.HOSTILE, 1.0F, 0.5F);

            // より強い恐怖効果
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));

            // 追加メッセージ
            player.sendSystemMessage(Component.literal("§4§l彼がすぐそこにいる..."));
        } else {
            // 通常の恐怖効果
            level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.get(),
                    SoundSource.AMBIENT, 0.8F, 0.8F);

            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
        }

        // パーティクル効果
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 4.0;
            double y = player.getY() + level.random.nextDouble() * 2.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 4.0;

            level.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 1,
                    0.0, 0.1, 0.0, 0.02);
        }

        // ダークネスパーティクル
        for (int i = 0; i < 10; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 2.0;
            double y = player.getY() + level.random.nextDouble() * 2.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 2.0;

            level.sendParticles(ParticleTypes.ASH,
                    x, y, z, 1,
                    0.0, 0.0, 0.0, 0.01);
        }

        // 体力減少（軽微）
        if (player.getHealth() > 1.0F) {
            player.setHealth(player.getHealth() - 1.0F);
        }

        // 食料減少
        if (player.getFoodData().getFoodLevel() > 2) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - 1);
        }
    }
}