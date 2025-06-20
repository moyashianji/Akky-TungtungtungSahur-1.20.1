// DayCounterEvents.java - 完全対応版
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class DayCounterEvents {

    // 各プレイヤーの最後の通知時間を記録
    private static final Map<UUID, Long> lastNotificationTime = new HashMap<>();
    private static final Map<UUID, Integer> lastKnownDay = new HashMap<>();

    // 自動進行設定
    private static final int DAY_PROGRESSION_INTERVAL = 1200; // 1分（1200tick）ごとに確認
    private static final long NOTIFICATION_COOLDOWN = 6000; // 5分のクールダウン

    private static int tickCounter = 0;
    private static int lastGlobalDay = -1;

    /**
     * ワールド読み込み時の初期化
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            dayData.updateDayCount(serverLevel);

            int currentDay = dayData.getDayCount();
            lastGlobalDay = currentDay;

            TungSahurMod.LOGGER.info("DayCounterEvents初期化: 現在{}日目", currentDay);

            // 既存のTungSahurエンティティを更新
            updateAllTungSahurEntities(serverLevel, currentDay);
        }
    }

    /**
     * サーバーティック処理 - 自動日数進行
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;

        // 定期的な日数確認と更新
        if (tickCounter % DAY_PROGRESSION_INTERVAL == 0) {
            checkAndUpdateDayProgression(event.getServer().getAllLevels());
        }

        // TungSahurエンティティの定期更新
        if (tickCounter % 100 == 0) { // 5秒ごと
            updateTungSahurEntitiesRegularly(event.getServer().getAllLevels());
        }
    }

    /**
     * プレイヤーログイン時の通知
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);
        int currentDay = dayData.getDayCount();

        // プレイヤーに現在の日数を通知
        schedulePlayerNotification(player, currentDay, 60); // 3秒後に通知

        // プレイヤーの記録を更新
        lastKnownDay.put(player.getUUID(), currentDay);

        TungSahurMod.LOGGER.debug("プレイヤー {} がログイン: {}日目", player.getName().getString(), currentDay);
    }

    /**
     * プレイヤーログアウト時のクリーンアップ
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        lastNotificationTime.remove(playerUUID);
        lastKnownDay.remove(playerUUID);

        TungSahurMod.LOGGER.debug("プレイヤー {} の記録をクリーンアップ", event.getEntity().getName().getString());
    }

    // === 内部メソッド ===

    /**
     * 日数進行の確認と更新
     */
    private static void checkAndUpdateDayProgression(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            DayCountSavedData dayData = DayCountSavedData.get(level);
            int oldDay = dayData.getDayCount();

            // 日数更新
            dayData.updateDayCount(level);
            int newDay = dayData.getDayCount();

            // 日数が変わった場合の処理
            if (newDay != oldDay || newDay != lastGlobalDay) {
                handleDayProgression(level, oldDay, newDay);
                lastGlobalDay = newDay;
            }
        }
    }

    /**
     * 日数進行時の処理
     */
    private static void handleDayProgression(ServerLevel level, int oldDay, int newDay) {
        TungSahurMod.LOGGER.info("日数進行: {}日目 -> {}日目", oldDay, newDay);

        // 全プレイヤーに通知
        notifyAllPlayersOfDayChange(level, newDay);

        // TungSahurエンティティの更新
        updateAllTungSahurEntities(level, newDay);

        // 日数変更時の特殊効果
        triggerDayChangeEffects(level, newDay);

        // 管理者への詳細ログ
    }

    /**
     * 全プレイヤーへの日数変更通知
     */
    private static void notifyAllPlayersOfDayChange(ServerLevel level, int dayNumber) {
        Component dayMessage = createDayMessage(dayNumber);

        for (ServerPlayer player : level.getPlayers(p -> true)) {
            long currentTime = level.getGameTime();
            UUID playerUUID = player.getUUID();

            // クールダウン確認
            if (!lastNotificationTime.containsKey(playerUUID) ||
                    currentTime - lastNotificationTime.get(playerUUID) >= NOTIFICATION_COOLDOWN) {

                sendDayNotificationToPlayer(player, dayMessage, dayNumber);
                lastNotificationTime.put(playerUUID, currentTime);
                lastKnownDay.put(playerUUID, dayNumber);
            }
        }
    }

    /**
     * プレイヤーへの日数通知メッセージ作成
     */
    private static Component createDayMessage(int dayNumber) {
        ChatFormatting color = switch (dayNumber) {
            case 1 -> ChatFormatting.YELLOW;
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.WHITE;
        };

        String nightMessage = switch (dayNumber) {
            case 1 -> "一日目の夜がやってきた...";
            case 2 -> "二日目の夜がやってきた...";
            case 3 -> "三日目の夜がやってきた...";
            default -> dayNumber + "日目の夜がやってきた...";
        };

        return Component.literal("§l=== " + nightMessage + " ===").withStyle(color);
    }

    /**
     * プレイヤーへの日数通知送信
     */
    private static void sendDayNotificationToPlayer(ServerPlayer player, Component message, int dayNumber) {
        // メインメッセージ
        player.sendSystemMessage(message);

        // 追加の恐怖メッセージ
        Component fearMessage = switch (dayNumber) {
            case 1 -> Component.literal("何かが動き始めている...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case 2 -> Component.literal("Tung Sahurの力が強くなっている...").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
            case 3 -> Component.literal("最終的な恐怖が解き放たれた...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            default -> Component.literal("暗闇が深まっている...").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        };

        player.sendSystemMessage(fearMessage);

        // 警告メッセージ
        Component warningMessage = Component.literal("夜は危険です。十分に注意してください。").withStyle(ChatFormatting.RED);
        player.sendSystemMessage(warningMessage);






        // パーティクル効果
        spawnNotificationParticles(player, dayNumber);

        TungSahurMod.LOGGER.info("プレイヤー {} に{}日目の夜通知を送信", player.getName().getString(), dayNumber);
    }





    /**
     * 通知パーティクルの生成
     */
    private static void spawnNotificationParticles(ServerPlayer player, int dayNumber) {
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.5;
        double z = player.getZ();

        switch (dayNumber) {
            case 1:
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.5, 0.5, 0.5, 0.02);
                break;
            case 2:
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 15, 0.8, 0.5, 0.8, 0.05);
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.3, 0.3, 0.3, 0.02);
                break;
            case 3:
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 20, 1.0, 0.8, 1.0, 0.08);
                level.sendParticles(ParticleTypes.WITCH, x, y, z, 8, 0.5, 0.5, 0.5, 0.03);
                break;
        }
    }

    /**
     * 全TungSahurエンティティの更新
     */
    private static void updateAllTungSahurEntities(ServerLevel level, int dayNumber) {
        // ワールド境界内の全エンティティを取得
        net.minecraft.world.level.border.WorldBorder worldBorder = level.getWorldBorder();
        net.minecraft.world.phys.AABB worldBorderAABB = new net.minecraft.world.phys.AABB(
                worldBorder.getMinX(),
                level.getMinBuildHeight(),
                worldBorder.getMinZ(),
                worldBorder.getMaxX(),
                level.getMaxBuildHeight(),
                worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, worldBorderAABB);

        int updatedCount = 0;
        for (TungSahurEntity entity : entities) {
            if (entity.getDayNumber() != dayNumber) {
                entity.setDayNumber(dayNumber);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを{}日目に更新", updatedCount, dayNumber);
        }
    }

    /**
     * TungSahurエンティティの定期更新
     */
    private static void updateTungSahurEntitiesRegularly(Iterable<ServerLevel> levels) {
        for (ServerLevel level : levels) {
            DayCountSavedData dayData = DayCountSavedData.get(level);
            int currentDay = dayData.getDayCount();

            // ワールド境界内の全エンティティを取得
            net.minecraft.world.level.border.WorldBorder worldBorder = level.getWorldBorder();
            net.minecraft.world.phys.AABB worldBorderAABB = new net.minecraft.world.phys.AABB(
                    worldBorder.getMinX(),
                    level.getMinBuildHeight(),
                    worldBorder.getMinZ(),
                    worldBorder.getMaxX(),
                    level.getMaxBuildHeight(),
                    worldBorder.getMaxZ()
            );

            List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, worldBorderAABB);

            for (TungSahurEntity entity : entities) {
                // 日数の同期確認
                if (entity.getDayNumber() != currentDay) {
                    entity.setDayNumber(currentDay);
                    TungSahurMod.LOGGER.debug("TungSahurエンティティの日数を{}に同期", currentDay);
                }
            }
        }
    }

    /**
     * 日数変更時の特殊効果
     */
    private static void triggerDayChangeEffects(ServerLevel level, int dayNumber) {
        // 全プレイヤー周辺での環境効果
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            spawnEnvironmentalEffects(level, player, dayNumber);
        }

        // ワールド境界内のTungSahurエンティティ周辺での効果
        net.minecraft.world.level.border.WorldBorder worldBorder = level.getWorldBorder();
        net.minecraft.world.phys.AABB worldBorderAABB = new net.minecraft.world.phys.AABB(
                worldBorder.getMinX(),
                level.getMinBuildHeight(),
                worldBorder.getMinZ(),
                worldBorder.getMaxX(),
                level.getMaxBuildHeight(),
                worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, worldBorderAABB);

        for (TungSahurEntity entity : entities) {
            spawnEntityEvolutionEffects(level, entity, dayNumber);
        }
    }

    /**
     * 環境効果の生成
     */
    private static void spawnEnvironmentalEffects(ServerLevel level, ServerPlayer player, int dayNumber) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 日数に応じた広範囲効果
        int radius = 10 + (dayNumber * 5);
        int particleCount = 20 + (dayNumber * 10);

        for (int i = 0; i < particleCount; i++) {
            double offsetX = (player.getRandom().nextDouble() - 0.5) * radius;
            double offsetZ = (player.getRandom().nextDouble() - 0.5) * radius;
            double offsetY = player.getRandom().nextDouble() * 3.0;

            switch (dayNumber) {
                case 1:
                    level.sendParticles(ParticleTypes.SMOKE,
                            x + offsetX, y + offsetY, z + offsetZ,
                            1, 0.1, 0.1, 0.1, 0.01);
                    break;
                case 2:
                    level.sendParticles(ParticleTypes.FLAME,
                            x + offsetX, y + offsetY, z + offsetZ,
                            1, 0.1, 0.1, 0.1, 0.02);
                    break;
                case 3:
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x + offsetX, y + offsetY, z + offsetZ,
                            1, 0.1, 0.1, 0.1, 0.03);
                    break;
            }
        }
    }

    /**
     * エンティティ進化効果の生成
     */
    private static void spawnEntityEvolutionEffects(ServerLevel level, TungSahurEntity entity, int dayNumber) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();

        // 進化パーティクル
        level.sendParticles(ParticleTypes.SOUL,
                x, y, z, 30, 0.8, 0.8, 0.8, 0.1);

        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                x, y, z, 20, 0.5, 0.5, 0.5, 0.08);

        // 進化音
        level.playSound(null, x, y, z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE,
                1.0F, 0.8F + (dayNumber * 0.1F));
    }

    /**
     * プレイヤー通知のスケジュール
     */
    private static void schedulePlayerNotification(ServerPlayer player, int dayNumber, int delayTicks) {
        // 指定したティック後に通知を送信
        player.server.execute(() -> {
            player.server.tell(new net.minecraft.server.TickTask(
                    player.server.getTickCount() + delayTicks,
                    () -> {
                        if (player.isAlive() && player.serverLevel() != null) {
                            Component message = createDayMessage(dayNumber);
                            sendDayNotificationToPlayer(player, message, dayNumber);
                        }
                    }
            ));
        });
    }



    // === ユーティリティメソッド ===

    /**
     * プレイヤーの最後の既知日数を取得
     */
    public static int getPlayerLastKnownDay(Player player) {
        return lastKnownDay.getOrDefault(player.getUUID(), 1);
    }

    /**
     * プレイヤーの通知クールダウン確認
     */
    public static boolean isPlayerNotificationOnCooldown(Player player, long currentTime) {
        UUID uuid = player.getUUID();
        return lastNotificationTime.containsKey(uuid) &&
                currentTime - lastNotificationTime.get(uuid) < NOTIFICATION_COOLDOWN;
    }

    /**
     * 統計情報の取得
     */
    public static void logEventStatistics() {
        TungSahurMod.LOGGER.info("=== DayCounterEvents統計 ===");
        TungSahurMod.LOGGER.info("追跡中プレイヤー数: {}", lastKnownDay.size());
        TungSahurMod.LOGGER.info("最後のグローバル日数: {}", lastGlobalDay);
        TungSahurMod.LOGGER.info("総ティック数: {}", tickCounter);
        TungSahurMod.LOGGER.info("===========================");
    }
}