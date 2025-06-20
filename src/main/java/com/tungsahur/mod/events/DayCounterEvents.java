// DayCounterEvents.java - 新しいゲームフロー対応版
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class DayCounterEvents {

    // プレイヤー管理用
    private static final Map<UUID, Long> lastNotificationTime = new HashMap<>();
    private static final Map<UUID, Integer> lastKnownDay = new HashMap<>();
    private static final long NOTIFICATION_COOLDOWN = 100; // 5秒のクールダウン

    // グローバル状態管理
    private static int lastGlobalDay = 0;
    private static boolean gameEndNotificationSent = false;

    /**
     * サーバーティック処理 - ゲーム状態とゲーム進行の監視
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        // 全ワールドのゲーム状態をチェック
        for (ServerLevel level : server.getAllLevels()) {
            checkGameProgression(level);
        }
    }

    /**
     * ゲーム進行の確認と更新
     */
    private static void checkGameProgression(ServerLevel level) {
        GameStateManager gameState = GameStateManager.get(level);

        if (!gameState.isGameStarted()) return;

        // ゲーム進行をチェック
        gameState.checkNightProgression(level);

        // 日数変更の検出
        int currentDay = gameState.getCurrentDay();
        if (currentDay != lastGlobalDay && currentDay > 0) {
            handleDayProgression(level, lastGlobalDay, currentDay);
            lastGlobalDay = currentDay;
        }

        // ゲーム終了の検出
        if (gameState.isGameEnded() && !gameEndNotificationSent) {
            handleGameEnd(level);
            gameEndNotificationSent = true;
        }

        // ゲームがリセットされた場合
        if (!gameState.isGameStarted() && gameEndNotificationSent) {
            gameEndNotificationSent = false;
            lastGlobalDay = 0;
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
    }

    /**
     * ゲーム終了時の処理
     */
    private static void handleGameEnd(ServerLevel level) {
        TungSahurMod.LOGGER.info("ゲーム終了: 3日間の恐怖が終了");

        // 全プレイヤーにゲーム終了を通知
        Component endMessage = Component.literal("§l=== ゲーム終了 ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append("\n")
                .append(Component.literal("3日間の恐怖がついに終わりました...")
                        .withStyle(ChatFormatting.GREEN))
                .append("\n")
                .append(Component.literal("これで安らかに眠ることができます。")
                        .withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : level.getPlayers(p -> true)) {
            player.sendSystemMessage(endMessage);

            // 平和な演出
            spawnPeacefulEffects(level, player);
        }

        // 全TungSahurエンティティを削除（平和になったため）
        removeAllTungSahurEntities(level);
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

        // 少し遅延して恐怖メッセージを送信
        schedulePlayerNotification(player, fearMessage, 40); // 2秒後
    }

    /**
     * 日数変更時の特殊効果
     */
    private static void triggerDayChangeEffects(ServerLevel level, int dayNumber) {
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            // 恐怖度に応じたパーティクル効果
            spawnDayChangeParticles(level, player, dayNumber);

            // 恐怖度に応じた音響効果
            playDayChangeSounds(level, player, dayNumber);
        }
    }

    /**
     * 日数変更時のパーティクル効果
     */
    private static void spawnDayChangeParticles(ServerLevel level, ServerPlayer player, int dayNumber) {
        int particleCount = dayNumber * 10; // 日数が増えるほど多くのパーティクル

        for (int i = 0; i < particleCount; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * (dayNumber * 2.0);
            double y = player.getY() + level.random.nextDouble() * 2.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * (dayNumber * 2.0);

            var particleType = switch (dayNumber) {
                case 1 -> ParticleTypes.SMOKE;
                case 2 -> ParticleTypes.LARGE_SMOKE;
                case 3 -> ParticleTypes.SOUL_FIRE_FLAME;
                default -> ParticleTypes.SMOKE;
            };

            level.sendParticles(particleType, x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * 日数変更時の音響効果
     */
    private static void playDayChangeSounds(ServerLevel level, ServerPlayer player, int dayNumber) {
        var sound = switch (dayNumber) {
            case 1 -> SoundEvents.AMBIENT_CAVE;
            case 2 -> SoundEvents.WITHER_AMBIENT;
            case 3 -> SoundEvents.WITHER_SPAWN;
            default -> SoundEvents.AMBIENT_CAVE;
        };

        float volume = 0.3f + (dayNumber * 0.2f); // 日数が増えるほど音量アップ
        float pitch = 1.0f - (dayNumber * 0.1f); // 日数が増えるほど低音に


    }

    /**
     * TungSahurエンティティの更新
     */
    private static void updateAllTungSahurEntities(ServerLevel level, int dayNumber) {
        var worldBorder = level.getWorldBorder();
        var searchArea = new net.minecraft.world.phys.AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.setDayNumber(dayNumber);
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを{}日目に更新", entities.size(), dayNumber);
    }

    /**
     * 全TungSahurエンティティを削除
     */
    private static void removeAllTungSahurEntities(ServerLevel level) {
        var worldBorder = level.getWorldBorder();
        var searchArea = new net.minecraft.world.phys.AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            // 平和な消失演出
            for (int i = 0; i < 10; i++) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        entity.getX() + (level.random.nextDouble() - 0.5) * 2.0,
                        entity.getY() + level.random.nextDouble() * 1.5,
                        entity.getZ() + (level.random.nextDouble() - 0.5) * 2.0,
                        1, 0.0, 0.1, 0.0, 0.1);
            }
            entity.discard();
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティが平和に消失", entities.size());
    }

    /**
     * 平和な演出
     */
    private static void spawnPeacefulEffects(ServerLevel level, ServerPlayer player) {
        // 美しいパーティクル効果
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + (level.random.nextDouble() - 0.5) * 5.0,
                    player.getY() + level.random.nextDouble() * 3.0,
                    player.getZ() + (level.random.nextDouble() - 0.5) * 5.0,
                    1, 0.0, 0.1, 0.0, 0.1);
        }



    }

    /**
     * プレイヤー通知のスケジューリング
     */
    private static void schedulePlayerNotification(ServerPlayer player, Component message, int delayTicks) {
        var server = player.getServer();
        if (server != null) {
            server.execute(() -> {
                // 指定されたtick数後に実行
                new Thread(() -> {
                    try {
                        Thread.sleep(delayTicks * 50); // 1tick = 50ms
                        if (player.isAlive() && !player.isRemoved()) {
                            player.sendSystemMessage(message);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
        }
    }

    /**
     * プレイヤー通知のスケジューリング（遅延版）
     */
    private static void schedulePlayerNotification(ServerPlayer player, int dayNumber, int delayTicks) {
        Component message = Component.literal("現在: " + dayNumber + "日目")
                .withStyle(dayNumber == 1 ? ChatFormatting.YELLOW :
                        dayNumber == 2 ? ChatFormatting.GOLD : ChatFormatting.DARK_RED);

        schedulePlayerNotification(player, message, delayTicks);
    }

    /**
     * プレイヤーログイン時の処理
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        GameStateManager gameState = GameStateManager.get(level);

        if (!gameState.isGameStarted()) return;

        int currentDay = gameState.getCurrentDay();

        // ゲーム状態の通知
        Component statusMessage;
        if (gameState.isGameEnded()) {
            statusMessage = Component.literal("§aゲームは終了しています。安らかに眠ることができます。")
                    .withStyle(ChatFormatting.GREEN);
        } else if (currentDay > 0) {
            statusMessage = Component.literal("§cゲーム進行中: " + currentDay + "日目の夜")
                    .withStyle(ChatFormatting.RED)
                    .append("\n")
                    .append(Component.literal("§7眠ることはできません。")
                            .withStyle(ChatFormatting.GRAY));
        } else {
            statusMessage = Component.literal("§eゲーム開始済み: 夜になると1日目が始まります")
                    .withStyle(ChatFormatting.YELLOW);
        }

        // 遅延して通知
        schedulePlayerNotification(player, statusMessage, 60); // 3秒後

        // プレイヤーの記録を更新
        lastKnownDay.put(player.getUUID(), currentDay);

        TungSahurMod.LOGGER.debug("プレイヤー {} がログイン: ゲーム状態={}",
                player.getName().getString(), gameState.getGameStatus());
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

    // === ユーティリティメソッド ===

    /**
     * ゲーム状態のリセット（外部からの呼び出し用）
     */
    public static void resetGameState() {
        lastGlobalDay = 0;
        gameEndNotificationSent = false;
        lastNotificationTime.clear();
        lastKnownDay.clear();

        TungSahurMod.LOGGER.info("DayCounterEventsの状態をリセット");
    }

    /**
     * デバッグ情報の取得
     */
    public static String getDebugInfo() {
        return String.format("DayCounterEvents状態: lastGlobalDay=%d, gameEndNotificationSent=%s, trackedPlayers=%d",
                lastGlobalDay, gameEndNotificationSent, lastKnownDay.size());
    }
}