// DayCounterEvents.java - 完全修正版（ゲーム終了後の処理停止）
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
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

    // グローバル状態管理（完全修正版）
    private static int lastGlobalDay = -1; // -1で初期化
    private static boolean gameEndNotificationSent = false;
    private static boolean isGameInitialized = false;
    private static boolean gameEndProcessingComplete = false; // 終了処理完了フラグ

    // 無限ループ防止用フラグ（強化版）
    private static volatile boolean isProcessingGameProgression = false;
    private static long lastProcessTime = 0;
    private static final long MIN_PROCESS_INTERVAL = 100; // 5秒間隔

    // ゲーム終了処理制御
    private static long lastGameEndTime = 0;
    private static final long GAME_END_COOLDOWN = 10000; // 10秒のクールダウン

    /**
     * サーバーティック処理 - 完全修正版
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // ゲーム終了処理が完了している場合、処理を停止
        if (gameEndProcessingComplete) {
            return; // 一切の処理を停止
        }

        // 厳重な無限ループ防止
        if (isProcessingGameProgression) {
            return; // デバッグログも出力しない
        }

        MinecraftServer server = event.getServer();
        if (server == null) return;

        // 最小間隔チェック（強化版）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastProcessTime < MIN_PROCESS_INTERVAL) return;
        lastProcessTime = currentTime;

        // 最初のワールドのみ処理
        for (ServerLevel level : server.getAllLevels()) {
            try {
                checkGameProgression(level);
            } catch (Exception e) {
                TungSahurMod.LOGGER.error("ティック処理でエラー発生", e);
            }
            break; // 重要：1つのワールドのみ処理
        }
    }

    /**
     * ゲーム進行の確認と更新（完全修正版）
     */
    private static void checkGameProgression(ServerLevel level) {
        // ゲーム終了処理が完了している場合、処理を停止
        if (gameEndProcessingComplete) {
            return;
        }

        // 二重実行防止
        synchronized (DayCounterEvents.class) {
            if (isProcessingGameProgression) {
                return;
            }
            isProcessingGameProgression = true;
        }

        try {
            GameStateManager gameState = GameStateManager.get(level);

            // ゲーム終了状態の場合、処理を停止
            if (gameState.isGameEnded()) {
                if (!gameEndProcessingComplete) {
                    handleGameEndOnce(level, gameState);
                }
                return; // 終了後は一切の処理を停止
            }

            // ゲームが開始されていない場合
            if (!gameState.isGameStarted()) {
                handleGameNotStarted();
                return;
            }

            // ゲーム開始直後の初期化
            if (!isGameInitialized) {
                initializeGameState();
            }

            // ゲーム進行をチェック
            gameState.checkNightProgression(level);

            // 日数変更の検出（修正版）
            int currentDay = gameState.getCurrentDay();

            // 厳密な条件チェック
            if (currentDay != lastGlobalDay && currentDay > 0 && currentDay <= 3) {
                if (lastGlobalDay >= 0) { // 初回は-1なのでスキップ
                    handleDayProgression(level, lastGlobalDay, currentDay);
                }
                lastGlobalDay = currentDay;
                TungSahurMod.LOGGER.info("日数状態更新: lastGlobalDay={}", lastGlobalDay);
            }

        } catch (Exception e) {
            TungSahurMod.LOGGER.error("checkGameProgression でエラー発生", e);
        } finally {
            // 必ず解除
            synchronized (DayCounterEvents.class) {
                isProcessingGameProgression = false;
            }
        }
    }

    /**
     * ゲーム終了時の処理（一度だけ実行版）
     */
    private static void handleGameEndOnce(ServerLevel level, GameStateManager gameState) {
        long currentTime = System.currentTimeMillis();

        // クールダウンチェック
        if (currentTime - lastGameEndTime < GAME_END_COOLDOWN) {
            TungSahurMod.LOGGER.debug("ゲーム終了処理を間隔制限によりスキップ");
            return;
        }

        // 終了処理が既に完了している場合はスキップ
        if (gameEndNotificationSent || gameEndProcessingComplete) {
            return;
        }

        TungSahurMod.LOGGER.info("ゲーム終了処理を開始");

        // 全TungSahurエンティティを削除
        removeAllTungSahurEntities(level);

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
            spawnPeacefulEffects(level, player);
        }

        // 終了処理完了マーク
        gameEndNotificationSent = true;
        gameEndProcessingComplete = true;
        lastGameEndTime = currentTime;

        // 次回ゲーム開始の準備（状態はリセットしない）
        TungSahurMod.LOGGER.info("ゲーム終了処理完了 - 以降の処理を停止");
    }

    /**
     * ゲーム未開始状態の処理（修正版）
     */
    private static void handleGameNotStarted() {
        if (gameEndNotificationSent || isGameInitialized || lastGlobalDay != -1) {
            resetGameStateInternal();
            TungSahurMod.LOGGER.debug("ゲーム未開始 - 状態リセット完了");
        }
    }

    /**
     * ゲーム初期化処理（修正版）
     */
    private static void initializeGameState() {
        lastGlobalDay = 0; // 0に設定（初期化済みマーク）
        gameEndNotificationSent = false;
        gameEndProcessingComplete = false; // リセット
        isGameInitialized = true;
        TungSahurMod.LOGGER.info("ゲーム初期化完了 - 1日目から開始可能");
    }

    /**
     * 内部リセット処理（強化版）
     */
    private static void resetGameStateInternal() {
        lastGlobalDay = -1; // -1にリセット
        gameEndNotificationSent = false;
        gameEndProcessingComplete = false; // リセット
        isGameInitialized = false;
        lastGameEndTime = 0;
        lastNotificationTime.clear();
        lastKnownDay.clear();
        TungSahurMod.LOGGER.info("DayCounterEvents状態完全リセット実行");
    }

    /**
     * 日数進行時の処理（修正版）
     */
    private static void handleDayProgression(ServerLevel level, int oldDay, int newDay) {
        // 不正な日数進行を防止
        if (newDay <= oldDay || newDay > 3 || oldDay < 0) {
            TungSahurMod.LOGGER.warn("不正な日数進行を検出: {} -> {} - 処理をスキップ", oldDay, newDay);
            return;
        }

        TungSahurMod.LOGGER.info("日数進行: {}日目 -> {}日目", oldDay, newDay);

        // 全プレイヤーに通知
        notifyAllPlayersOfDayChange(level, newDay);

        // TungSahurエンティティの更新
        updateAllTungSahurEntities(level, newDay);

        // 日数変更時の特殊効果
        triggerDayChangeEffects(level, newDay);

        TungSahurMod.LOGGER.info("日数更新完了: {}日目", newDay);
    }

    /**
     * 全TungSahurエンティティを削除（強化版）
     */
    private static void removeAllTungSahurEntities(ServerLevel level) {
        // ワールドボーダーに基づくAABBを作成
        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティが平和に消失", entities.size());
    }

    /**
     * 平和な効果のスポーン
     */
    private static void spawnPeacefulEffects(ServerLevel level, ServerPlayer player) {
        // 平和なパーティクル効果
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 4.0;
            double y = player.getY() + level.random.nextDouble() * 3.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 4.0;

            level.sendParticles(ParticleTypes.HEART, x, y, z, 1, 0.0, 0.1, 0.0, 0.1);
        }

        // 平和な音
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // === プレイヤーイベント処理 ===
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (gameEndProcessingComplete) return; // ゲーム終了後は処理しない

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
        schedulePlayerNotification(player, statusMessage, 60);

        // プレイヤーの記録を更新
        lastKnownDay.put(player.getUUID(), currentDay);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        lastNotificationTime.remove(playerUUID);
        lastKnownDay.remove(playerUUID);
    }

    /**
     * ゲーム状態のリセット（外部からの呼び出し用）
     */
    public static void resetGameState() {
        synchronized (DayCounterEvents.class) {
            if (isProcessingGameProgression) {
                TungSahurMod.LOGGER.warn("処理中のためリセットを待機");
                return;
            }
            resetGameStateInternal();
            TungSahurMod.LOGGER.info("DayCounterEventsの状態を完全リセット");
        }
    }

    /**
     * デバッグ情報の取得（強化版）
     */
    public static String getDebugInfo() {
        return String.format("DayCounterEvents[lastGlobalDay=%d, gameEndSent=%s, endComplete=%s, initialized=%s, processing=%s]",
                lastGlobalDay, gameEndNotificationSent, gameEndProcessingComplete, isGameInitialized, isProcessingGameProgression);
    }

    // === 未実装メソッドのスタブ ===
    private static void notifyAllPlayersOfDayChange(ServerLevel level, int dayNumber) {
        Component dayMessage = createDayMessage(dayNumber);
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            player.sendSystemMessage(dayMessage);
        }
    }

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

    private static void updateAllTungSahurEntities(ServerLevel level, int newDay) {
        // ワールドボーダーに基づくAABBを作成
        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.setDayNumber(newDay);
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを{}日目に更新", entities.size(), newDay);
    }

    private static void triggerDayChangeEffects(ServerLevel level, int dayNumber) {
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            Component fearMessage = switch (dayNumber) {
                case 1 -> Component.literal("何かが動き始めている...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                case 2 -> Component.literal("Tung Sahurの力が強くなっている...").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
                case 3 -> Component.literal("最終的な恐怖が解き放たれた...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                default -> Component.literal("暗闇が深まっている...").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            };

            schedulePlayerNotification(player, fearMessage, 40);
        }
    }

    private static void schedulePlayerNotification(ServerPlayer player, Component message, int delayTicks) {
        // 実装省略（適切な遅延通知システムが必要）
        player.sendSystemMessage(message);
    }
}