// BedSleepEvent.java - ベッド睡眠阻害に超カッコいいパーティクル追加
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

            // 強化されたホラー効果を発動
            spawnEnhancedHorrorEffects(player, serverLevel, !nearbyTungSahur.isEmpty());
        }
    }

    private void spawnEnhancedHorrorEffects(Player player, ServerLevel level, boolean tungSahurNearby) {
        // 恐怖音
        if (tungSahurNearby) {
            // Tung Sahurが近くにいる場合はより恐ろしい効果
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                    SoundSource.HOSTILE, 1.0F, 0.5F);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT,
                    SoundSource.HOSTILE, 0.8F, 0.7F);

            // より強い恐怖効果
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 2));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));

            // 追加メッセージ
            player.sendSystemMessage(Component.literal("§4§l彼がすぐそこにいる..."));

            // 超恐怖パーティクル
            spawnIntenseHorrorParticles(player, level);
        } else {
            // 通常の恐怖効果
            level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.get(),
                    SoundSource.AMBIENT, 0.8F, 0.8F);
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE,
                    SoundSource.HOSTILE, 1.0F, 0.6F);

            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));

            // 標準恐怖パーティクル
            spawnStandardHorrorParticles(player, level);
        }

        // 共通パーティクル効果
        spawnCommonHorrorParticles(player, level);

        // 体力減少（軽微）
        if (player.getHealth() > 1.0F) {
            player.setHealth(player.getHealth() - 1.0F);
        }

        // 食料減少
        if (player.getFoodData().getFoodLevel() > 2) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - 1);
        }
    }

    private void spawnIntenseHorrorParticles(Player player, ServerLevel level) {
        // 暗黒の渦巻き
        for (int ring = 1; ring <= 5; ring++) {
            for (int i = 0; i < 12 * ring; i++) {
                double angle = (i * 2 * Math.PI) / (12 * ring) + level.getGameTime() * 0.2;
                double radius = ring * 1.0;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                double y = player.getY() + 0.1 + Math.sin(angle * 3) * 0.5;

                level.sendParticles(ParticleTypes.SOUL,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.SCULK_SOUL,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }

        // プレイヤーを囲む炎の壁
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            double radius = 3.0;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            // 炎の柱
            for (int j = 0; j < 8; j++) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, player.getY() + j * 0.5, z, 1, 0.0, 0.2, 0.0, 0.0);
            }
        }

        // 空から降る暗黒の雨
        for (int i = 0; i < 30; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 10.0;
            double y = player.getY() + 5.0 + level.random.nextDouble() * 3.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 10.0;

            level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                    x, y, z, 1, 0.0, -0.5, 0.0, 0.0);
            level.sendParticles(ParticleTypes.ASH,
                    x, y, z, 1, 0.0, -0.3, 0.0, 0.0);
        }

        // 頭上に恐怖の象徴
        double headY = player.getY() + 3.0;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            double radius = 2.0;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    x, headY, z, 1, 0.0, -0.1, 0.0, 0.0);
        }

        // 地面からの亀裂表現
        for (int i = 0; i < 20; i++) {
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double radius = level.random.nextDouble() * 5.0;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.LAVA,
                    x, player.getY(), z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnStandardHorrorParticles(Player player, ServerLevel level) {
        // 周囲に立ち上る煙
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = ring * 1.5;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;

                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x, player.getY() + 0.1, z, 1, 0.0, 0.4, 0.0, 0.0);
            }
        }

        // 空中に浮遊する暗黒パーティクル
        for (int i = 0; i < 15; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 6.0;
            double y = player.getY() + level.random.nextDouble() * 3.0 + 1.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 6.0;

            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            level.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.02);
        }

        // プレイヤー周りの恐怖オーラ
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            double radius = 2.0 + Math.sin(level.getGameTime() * 0.1 + i) * 0.5;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + 1.0 + Math.sin(angle * 2) * 0.3;

            level.sendParticles(ParticleTypes.ENCHANT,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void spawnCommonHorrorParticles(Player player, ServerLevel level) {
        // プレイヤーの足元から立ち上る不吉な煙
        for (int i = 0; i < 25; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 4.0;
            double y = player.getY() + level.random.nextDouble() * 2.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 4.0;

            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    x, y, z, 1, 0.0, 0.1, 0.0, 0.02);
        }

        // ダークネスパーティクルの強化版
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 3.0;
            double y = player.getY() + level.random.nextDouble() * 2.5;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 3.0;

            level.sendParticles(ParticleTypes.ASH,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.01);
            level.sendParticles(ParticleTypes.SQUID_INK,
                    x, y, z, 1, 0.0, -0.1, 0.0, 0.0);
        }

        // 周囲の明かりを暗くする演出
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5;
            double x = player.getX() + Math.cos(angle) * 4.0;
            double z = player.getZ() + Math.sin(angle) * 4.0;

            level.sendParticles(ParticleTypes.MYCELIUM,
                    x, player.getY() + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // ベッド周辺の呪われた演出
        for (int i = 0; i < 8; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2.0;

            level.sendParticles(ParticleTypes.WITCH,
                    player.getX() + offsetX, player.getY() + 1.0, player.getZ() + offsetZ,
                    1, 0.0, 0.2, 0.0, 0.0);
        }

        // 時折現れる不吉な光
        if (level.random.nextFloat() < 0.3F) {
            for (int i = 0; i < 5; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 8.0;
                double y = player.getY() + 2.0 + level.random.nextDouble() * 2.0;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 8.0;

                level.sendParticles(ParticleTypes.END_ROD,
                        x, y, z, 1, 0.0, -0.2, 0.0, 0.0);
            }
        }

        // プレイヤーの視界を遮る濃い霧
        for (int i = 0; i < 30; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 6.0;
            double y = player.getY() + level.random.nextDouble() * 3.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 6.0;

            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    x, y, z, 1, 0.0, 0.3, 0.0, 0.0);
        }
    }
}