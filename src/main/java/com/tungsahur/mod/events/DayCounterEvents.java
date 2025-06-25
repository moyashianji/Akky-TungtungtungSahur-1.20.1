// DayCounterEvents.java - サフール1体厳格制限版（朝消去機能追加）
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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

    // 対象プレイヤー設定
    private static final String TARGET_PLAYER = "Dev";

    // サフール管理（1体厳格制限対応）
    private static final Map<Integer, Boolean> dayEntityKilled = new HashMap<>();
    private static TungSahurEntity currentTungSahurEntity = null; // 現在のサフール（1体のみ）
    private static int currentTungSahurDay = -1; // 現在のサフールが対応している日数

    // スポーン制御用フラグ（複数スポーン完全防止）
    private static volatile boolean isSpawning = false;
    private static long lastSpawnTime = 0;
    private static final long MIN_SPAWN_INTERVAL = 2000; // 2秒間隔でのスポーン制限

    // ★新機能★ 朝のサフール削除機能
    private static boolean lastWasNight = false; // 前のティックが夜だったかどうか
    private static long lastMorningCleanup = 0; // 最後に朝の清掃を行った時間
    private static final long MORNING_CLEANUP_COOLDOWN = 30000; // 30秒のクールダウン

    /**
     * ★新機能★ サフールのdiscard監視（LivingDeathEventの補完）
     */
    private static void checkTungSahurDiscard(ServerLevel level) {
        // 現在のサフールが無効化された場合の処理
        if (currentTungSahurEntity != null && currentTungSahurDay > 0) {
            // エンティティが死んでいるか削除されている場合
            if (!currentTungSahurEntity.isAlive() || currentTungSahurEntity.isRemoved()) {
                int killedDay = currentTungSahurDay;

                TungSahurMod.LOGGER.warn("★★★ TungSahur discard detected - setting kill flag for Day {} ★★★", killedDay);

                // 撃破フラグを設定
                dayEntityKilled.put(killedDay, true);

                // プレイヤーに勝利メッセージを送信
                ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(TARGET_PLAYER);
                if (targetPlayer != null) {
                    Component victoryMessage = Component.literal("§a§l★ Tung Sahur撃破！ ★")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                            .append("\n")
                            .append(Component.literal("§7今夜はもうスポーンしません。")
                                    .withStyle(ChatFormatting.GRAY))
                            .append("\n")
                            .append(Component.literal("§7朝まで安全に過ごせます。")
                                    .withStyle(ChatFormatting.GRAY))
                            .append("\n")
                            .append(Component.literal("§8[デバッグ] Day " + killedDay + " 撃破フラグ設定完了（discard検出）")
                                    .withStyle(ChatFormatting.DARK_GRAY));
               //     targetPlayer.sendSystemMessage(victoryMessage);
                }

                // 現在のサフール参照をクリア
                currentTungSahurEntity = null;
                currentTungSahurDay = -1;

                TungSahurMod.LOGGER.warn("Kill Flag Map After Discard: {}", dayEntityKilled);
                TungSahurMod.LOGGER.warn("NO MORE SPAWNS TODAY - Next spawn only on Day {}", killedDay + 1);
            }
        }
    }

    /**
     * サーバーティック処理 - discard監視機能追加版
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
                // ★新機能★ サフールのdiscard監視
                checkTungSahurDiscard(level);

                // ★新機能★ 朝になったらサフールを削除
                checkMorningCleanup(level);

                checkGameProgression(level);
                cleanupExtraEntities(level); // 余分なエンティティを定期的に削除
            } catch (Exception e) {
                TungSahurMod.LOGGER.error("ティック処理でエラー発生", e);
            }
            break; // 重要：1つのワールドのみ処理
        }
    }

    /**
     * ★新機能★ 朝になったらサフールを削除する処理
     */
    private static void checkMorningCleanup(ServerLevel level) {
        GameStateManager gameState = GameStateManager.get(level);

        // ゲームが開始されていない、または終了している場合はスキップ
        if (!gameState.isGameStarted() || gameState.isGameEnded()) {
            return;
        }

        boolean isCurrentlyNight = level.isNight();
        long currentTime = System.currentTimeMillis();

        // 夜から朝に変わった瞬間を検出
        if (lastWasNight && !isCurrentlyNight) {
            // クールダウンチェック（連続実行防止）
            if (currentTime - lastMorningCleanup > MORNING_CLEANUP_COOLDOWN) {
                handleMorningCleanup(level);
                lastMorningCleanup = currentTime;
            }
        }

        // 前の状態を記録
        lastWasNight = isCurrentlyNight;
    }

    /**
     * ★新機能★ 朝のサフール削除処理
     */
    private static void handleMorningCleanup(ServerLevel level) {
        TungSahurMod.LOGGER.info("朝になりました - サフールを削除します");

        // 現在のサフールを削除
        if (currentTungSahurEntity != null && currentTungSahurEntity.isAlive()) {
            currentTungSahurEntity.discard();
            TungSahurMod.LOGGER.info("Morning cleanup: Current TungSahur removed (Day {})", currentTungSahurDay);

            // プレイヤーにメッセージを送信
            ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(TARGET_PLAYER);
            if (targetPlayer != null) {
                Component morningMessage = Component.literal("§e☀ 朝がやってきました ☀")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append("\n")
                        .append(Component.literal("§7Tung Sahurは朝の光とともに消え去りました...")
                                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                targetPlayer.sendSystemMessage(morningMessage);

                // 朝の効果音とパーティクル
                spawnMorningEffects(level, targetPlayer);
            }
        }

        // 念のため全てのサフールを削除
        forceRemoveAllTungSahurEntitiesWithMessage(level, "朝の清掃");

        // 現在のサフール参照をクリア
        currentTungSahurEntity = null;
        currentTungSahurDay = -1;

        TungSahurMod.LOGGER.info("朝の清掃完了 - 次の夜まで安全です");
    }

    /**
     * ★新機能★ 朝の効果（パーティクルと音）
     */
    private static void spawnMorningEffects(ServerLevel level, ServerPlayer player) {
        // 温かい朝のパーティクル効果
        for (int i = 0; i < 15; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 6.0;
            double y = player.getY() + level.random.nextDouble() * 4.0 + 1.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 6.0;

            // 暖かい朝の光のようなパーティクル
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.1, 0.0, 0.05);
        }

        // 朝の鳥の鳴き声のような音
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(),
                SoundSource.AMBIENT, 0.8f, 1.2f);
    }

    /**
     * 余分なサフールエンティティを削除（ゲーム開始時のみ1体制限）
     */
    private static void cleanupExtraEntities(ServerLevel level) {
        GameStateManager gameState = GameStateManager.get(level);

        // ゲームが開始されていない場合は何体でもOK
        if (!gameState.isGameStarted()) {
            return;
        }

        // ゲーム開始後は1体制限を適用
        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> allEntities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        // 現在のサフール以外は全て削除（ゲーム開始後のみ）
        for (TungSahurEntity entity : allEntities) {
            if (entity != currentTungSahurEntity) {
                entity.discard();
                TungSahurMod.LOGGER.warn("Extra TungSahur entity removed during game - only one allowed!");
            }
        }

        // 現在のサフールが死んでいる場合はnullに設定
        if (currentTungSahurEntity != null && !currentTungSahurEntity.isAlive()) {
            TungSahurMod.LOGGER.info("Current TungSahur is dead - clearing reference");
            currentTungSahurEntity = null;
            currentTungSahurDay = -1;
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

        // 現在のサフールを削除
        removeCurrentTungSahur();
        forceRemoveAllTungSahurEntitiesUnconditionally(level); // ゲーム終了時は無条件で全削除

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
     * 全TungSahurエンティティを無条件で強制削除（ゲーム終了時用）
     */
    private static void forceRemoveAllTungSahurEntitiesUnconditionally(ServerLevel level) {
        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        if (entities.size() > 0) {
            TungSahurMod.LOGGER.info("Game end: Removed {} TungSahur entities", entities.size());
        }
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

        // サフール管理のリセット
        dayEntityKilled.clear();
        currentTungSahurEntity = null;
        currentTungSahurDay = -1;

        // スポーン制御のリセット
        isSpawning = false;
        lastSpawnTime = 0;

        // ★新機能★ 朝の清掃機能のリセット
        lastWasNight = false;
        lastMorningCleanup = 0;

        TungSahurMod.LOGGER.info("DayCounterEvents状態完全リセット実行");
    }

    /**
     * 日数進行時の処理（1体制限版・撃破状態リセット）
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

        // ★重要★ 新しい日が始まったら、その日の撃破フラグをリセット
        // （前の日に倒されていても、新しい日なら再びスポーン可能にする）
        if (dayEntityKilled.containsKey(newDay)) {
            dayEntityKilled.remove(newDay);
            TungSahurMod.LOGGER.info("Day {} kill flag RESET - new spawns now possible", newDay);
        }

        // 現在のサフールを削除して新しい日数用にスポーン
        removeCurrentTungSahur();
        forceRemoveAllTungSahurEntities(level); // 念のため全削除

        // 少し遅延してスポーン（削除処理完了を待つ）
        level.getServer().tell(new TickTask(10, () -> spawnSingleTungSahur(level, newDay)));

        // 日数変更時の特殊効果
        triggerDayChangeEffects(level, newDay);

        TungSahurMod.LOGGER.info("日数更新完了: {}日目", newDay);
    }

    /**
     * 単一サフールのスポーン（1体制限版・複数スポーン完全防止・夜のみスポーン・撃破チェック超強化）
     */
    private static void spawnSingleTungSahur(ServerLevel level, int day) {
        TungSahurMod.LOGGER.info("=== Spawn Single TungSahur Debug ===");
        TungSahurMod.LOGGER.info("Requested Day: {}", day);

        // ★超重要★ 再度撃破フラグをチェック（スポーン直前の最終確認）
        boolean isKilledBeforeSpawn = dayEntityKilled.getOrDefault(day, false);
        TungSahurMod.LOGGER.info("Kill Flag Check Before Spawn: Day {} = {}", day, isKilledBeforeSpawn);

        if (isKilledBeforeSpawn) {
            TungSahurMod.LOGGER.warn("★★★ SPAWN COMPLETELY BLOCKED: Day {} TungSahur was killed - ZERO TOLERANCE ★★★", day);
            return;
        }

        // ★新機能★ 夜でない場合はスポーンしない
        boolean isNight = level.isNight();
        TungSahurMod.LOGGER.info("Is Night Check: {}", isNight);
        if (!isNight) {
            TungSahurMod.LOGGER.info("Not nighttime - skipping TungSahur spawn for day {}", day);
            return;
        }

        // スポーン制御（複数スポーン完全防止）
        synchronized (DayCounterEvents.class) {
            long currentTime = System.currentTimeMillis();

            // スポーン中フラグチェック
            if (isSpawning) {
                TungSahurMod.LOGGER.warn("Already spawning - preventing duplicate spawn");
                return;
            }

            // 最小スポーン間隔チェック
            if (currentTime - lastSpawnTime < MIN_SPAWN_INTERVAL) {
                TungSahurMod.LOGGER.warn("Spawn too frequent - preventing duplicate spawn");
                return;
            }

            // 現在のサフールがまだ生きている場合はスポーンしない
            if (currentTungSahurEntity != null && currentTungSahurEntity.isAlive()) {
                TungSahurMod.LOGGER.warn("Current TungSahur still alive - preventing duplicate spawn");
                return;
            }

            isSpawning = true; // スポーン開始フラグ
            TungSahurMod.LOGGER.info("Spawn flag set to TRUE - proceeding with spawn");
        }

        try {
            // ★最終確認★ スポーン処理内でも再度撃破フラグをチェック
            if (dayEntityKilled.getOrDefault(day, false)) {
                TungSahurMod.LOGGER.warn("★★★ FINAL CHECK FAILED: Day {} kill flag is TRUE - ABORTING SPAWN ★★★", day);
                return;
            }

            // 対象プレイヤーを取得
            ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(TARGET_PLAYER);
            if (targetPlayer == null) {
                TungSahurMod.LOGGER.warn("Player {} not found - skipping spawn", TARGET_PLAYER);
                return;
            }

            // 念のため全サフールを削除
            forceRemoveAllTungSahurEntities(level);

            // スポーン位置決定（プレイヤーの周囲20-40ブロック）
            Vec3 playerPos = targetPlayer.position();
            RandomSource random = level.getRandom();
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 20 + random.nextDouble() * 20;

            BlockPos spawnPos = new BlockPos(
                    (int)(playerPos.x + Math.cos(angle) * distance),
                    (int)(playerPos.y + random.nextInt(10) - 5),
                    (int)(playerPos.z + Math.sin(angle) * distance)
            );

            // 地面調整
            spawnPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos);

            // 新しいサフールをスポーン
            TungSahurEntity entity = ModEntities.TUNG_SAHUR.get().create(level);
            if (entity != null) {
                entity.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                entity.setDayNumber(day);
                entity.setPersistenceRequired(); // デスポーン防止

                level.addFreshEntity(entity);

                // 現在のサフールとして記録
                currentTungSahurEntity = entity;
                currentTungSahurDay = day;
                lastSpawnTime = System.currentTimeMillis();

                TungSahurMod.LOGGER.info("★ SINGLE TungSahur spawned for Day {} near {} at {} ★", day, TARGET_PLAYER, spawnPos);

                // プレイヤーにスポーン通知
                Component spawnNotification = Component.literal("§c[デバッグ] 新しいサフールがスポーンしました (Day " + day + ")")
                        .withStyle(ChatFormatting.RED);
                //targetPlayer.sendSystemMessage(spawnNotification);
                System.out.println(spawnNotification
                );
            }
        } finally {
            // 必ずスポーンフラグを解除
            synchronized (DayCounterEvents.class) {
                isSpawning = false;
                TungSahurMod.LOGGER.info("Spawn flag reset to FALSE");
            }
        }
        TungSahurMod.LOGGER.info("=== Spawn Single TungSahur Debug End ===");
    }

    /**
     * 現在のサフールを削除
     */
    private static void removeCurrentTungSahur() {
        if (currentTungSahurEntity != null && currentTungSahurEntity.isAlive()) {
            currentTungSahurEntity.discard();
            TungSahurMod.LOGGER.info("Current TungSahur removed (Day {})", currentTungSahurDay);
        }
        currentTungSahurEntity = null;
        currentTungSahurDay = -1;
    }

    /**
     * 全TungSahurエンティティを強制削除（ゲーム開始後のみ）
     */
    private static void forceRemoveAllTungSahurEntities(ServerLevel level) {
        GameStateManager gameState = GameStateManager.get(level);

        // ゲームが開始されていない場合は削除しない
        if (!gameState.isGameStarted()) {
            TungSahurMod.LOGGER.info("Game not started - skipping entity cleanup");
            return;
        }

        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        if (entities.size() > 0) {
            TungSahurMod.LOGGER.info("Forced removal of {} TungSahur entities during game", entities.size());
        }
    }

    /**
     * ★新機能★ メッセージ付きで全TungSahurエンティティを強制削除
     */
    private static void forceRemoveAllTungSahurEntitiesWithMessage(ServerLevel level, String reason) {
        GameStateManager gameState = GameStateManager.get(level);

        // ゲームが開始されていない場合は削除しない
        if (!gameState.isGameStarted()) {
            return;
        }

        var worldBorder = level.getWorldBorder();
        var searchArea = new AABB(
                worldBorder.getMinX(), level.getMinBuildHeight(), worldBorder.getMinZ(),
                worldBorder.getMaxX(), level.getMaxBuildHeight(), worldBorder.getMaxZ()
        );

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        if (entities.size() > 0) {
            TungSahurMod.LOGGER.info("{}: Removed {} TungSahur entities", reason, entities.size());
        }
    }

    /**
     * プレイヤー死亡時の処理
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TARGET_PLAYER.equals(player.getName().getString())) return;

        ServerLevel level = player.serverLevel();
        GameStateManager gameState = GameStateManager.get(level);

        if (gameState.isGameActive()) {
            TungSahurMod.LOGGER.info("Target player {} died - removing current TungSahur", TARGET_PLAYER);
            removeCurrentTungSahur();
        }
    }

    /**
     * プレイヤーリスポーン時の処理（撃破状態チェック超強化版）
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!TARGET_PLAYER.equals(event.getEntity().getName().getString())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        GameStateManager gameState = GameStateManager.get(level);

        TungSahurMod.LOGGER.info("=== Player Respawn Debug ===");
        TungSahurMod.LOGGER.info("Player: {}", TARGET_PLAYER);
        TungSahurMod.LOGGER.info("Game Active: {}", gameState.isGameActive());

        if (gameState.isGameActive()) {
            int currentDay = gameState.getCurrentDay();
            TungSahurMod.LOGGER.info("Current Day: {}", currentDay);

            if (currentDay > 0) {
                // 撃破フラグの詳細ログ
                boolean isKilled = dayEntityKilled.getOrDefault(currentDay, false);
                TungSahurMod.LOGGER.info("Day {} Kill Flag: {}", currentDay, isKilled);
                TungSahurMod.LOGGER.info("Kill Flag Map: {}", dayEntityKilled);

                // ★最優先★ その日のサフールが既に倒されている場合は絶対にスポーンしない
                if (isKilled) {
                    TungSahurMod.LOGGER.warn("★★★ RESPAWN BLOCKED: Day {} TungSahur already killed - ABSOLUTELY NO RESPAWN ★★★", currentDay);

                    // プレイヤーに明確なメッセージを送信
                    Component noRespawnMessage = Component.literal("§7[デバッグ] その日のサフールは既に撃破済み - リスポーンしません")
                            .withStyle(ChatFormatting.GRAY);
                    player.sendSystemMessage(noRespawnMessage);
                    return;
                }

                // ★新機能★ 夜でない場合はスポーンしない
                boolean isNight = level.isNight();
                TungSahurMod.LOGGER.info("Is Night: {}", isNight);
                if (!isNight) {
                    TungSahurMod.LOGGER.info("Target player {} respawned, but it's not nighttime - NO SPAWN during day", TARGET_PLAYER);

                    Component dayMessage = Component.literal("§7[デバッグ] 昼間のため新しいサフールはスポーンしません")
                            .withStyle(ChatFormatting.GRAY);
                    player.sendSystemMessage(dayMessage);
                    return;
                }

                // 現在のサフールの状態チェック
                boolean hasCurrentEntity = (currentTungSahurEntity != null);
                boolean isCurrentAlive = hasCurrentEntity && currentTungSahurEntity.isAlive();
                boolean isSameDay = (currentTungSahurDay == currentDay);

                TungSahurMod.LOGGER.info("Current Entity Exists: {}", hasCurrentEntity);
                TungSahurMod.LOGGER.info("Current Entity Alive: {}", isCurrentAlive);
                TungSahurMod.LOGGER.info("Current Entity Day: {} (Game Day: {})", currentTungSahurDay, currentDay);

                // 現在のサフールがまだ生きている場合はスポーンしない
                if (hasCurrentEntity && isCurrentAlive && isSameDay) {
                    TungSahurMod.LOGGER.info("Target player {} respawned, but Day {} TungSahur still alive - NO DUPLICATE SPAWN", TARGET_PLAYER, currentDay);

                    Component aliveMessage = Component.literal("§7[デバッグ] 現在のサフールがまだ生きているため新しくスポーンしません")
                            .withStyle(ChatFormatting.GRAY);
                   // player.sendSystemMessage(aliveMessage);
                    return;
                }

                TungSahurMod.LOGGER.info("★ Conditions met - spawning new TungSahur for day {} ★", currentDay);

                Component spawnMessage = Component.literal("§c[デバッグ] 条件を満たしたため新しいサフールをスポーンします...")
                        .withStyle(ChatFormatting.RED);
               // player.sendSystemMessage(spawnMessage);

                // 2秒後にスポーン（リスポーン処理完了を待つ）
                level.getServer().tell(new TickTask(40, () -> spawnSingleTungSahur(level, currentDay)));
            }
        }
        TungSahurMod.LOGGER.info("=== Player Respawn Debug End ===");
    }

    /**
     * サフール死亡時の処理（撃破フラグ設定超強化版）
     */
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TungSahurEntity tungSahur)) return;
        if (!(tungSahur.level() instanceof ServerLevel level)) return;

        GameStateManager gameState = GameStateManager.get(level);
        if (gameState.isGameActive()) {
            int currentDay = gameState.getCurrentDay();

            TungSahurMod.LOGGER.info("=== TungSahur Death Debug ===");
            TungSahurMod.LOGGER.info("Current Day: {}", currentDay);
            TungSahurMod.LOGGER.info("Died Entity: {}", tungSahur);
            TungSahurMod.LOGGER.info("Current Entity: {}", currentTungSahurEntity);
            TungSahurMod.LOGGER.info("Is Same Entity: {}", (tungSahur == currentTungSahurEntity));

            // 死亡したサフールが現在のサフールかチェック
            if (tungSahur == currentTungSahurEntity) {
                // ★最重要★ その日の撃破フラグを強制設定（絶対にリスポーンしない）
                dayEntityKilled.put(currentDay, true);
                currentTungSahurEntity = null;
                currentTungSahurDay = -1;

                TungSahurMod.LOGGER.warn("★★★ Day {} TungSahur KILLED - KILL FLAG SET TO TRUE ★★★", currentDay);
                TungSahurMod.LOGGER.warn("Kill Flag Map After Death: {}", dayEntityKilled);
                TungSahurMod.LOGGER.warn("NO MORE SPAWNS TODAY - Next spawn only on Day {}", currentDay + 1);

                // プレイヤーに詳細な勝利メッセージを送信
                ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(TARGET_PLAYER);
                if (targetPlayer != null) {
                    Component victoryMessage = Component.literal("§a§l★ Tung Sahur撃破！ ★")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                            .append("\n")
                            .append(Component.literal("§7今夜はもうスポーンしません。")
                                    .withStyle(ChatFormatting.GRAY))
                            .append("\n")
                            .append(Component.literal("§7朝まで安全に過ごせます。")
                                    .withStyle(ChatFormatting.GRAY))
                            .append("\n")
                            .append(Component.literal("§8[デバッグ] Day " + currentDay + " 撃破フラグ設定完了")
                                    .withStyle(ChatFormatting.DARK_GRAY));
                    //targetPlayer.sendSystemMessage(victoryMessage);
                }
            } else {
                TungSahurMod.LOGGER.warn("Dead entity is NOT the current TungSahur - ignoring death");
            }
            TungSahurMod.LOGGER.info("=== TungSahur Death Debug End ===");
        }
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
            boolean isNight = level.isNight();
            if (isNight) {
                statusMessage = Component.literal("§cゲーム進行中: " + currentDay + "日目の夜")
                        .withStyle(ChatFormatting.RED)
                        .append("\n")
                        .append(Component.literal("§7眠ることはできません。")
                                .withStyle(ChatFormatting.GRAY));
            } else {
                statusMessage = Component.literal("§eゲーム進行中: " + currentDay + "日目の昼")
                        .withStyle(ChatFormatting.YELLOW)
                        .append("\n")
                        .append(Component.literal("§7夜になると危険が始まります。")
                                .withStyle(ChatFormatting.GRAY));
            }
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
     * デバッグ情報の取得（強化版・撃破状態表示追加）
     */
    public static String getDebugInfo() {
        StringBuilder killStatus = new StringBuilder();
        for (int day = 1; day <= 3; day++) {
            if (dayEntityKilled.getOrDefault(day, false)) {
                killStatus.append("Day").append(day).append(":KILLED ");
            }
        }

        return String.format("DayCounterEvents[lastGlobalDay=%d, gameEndSent=%s, endComplete=%s, initialized=%s, processing=%s, currentEntity=%s, entityDay=%d, killFlags=%s, spawning=%s, lastWasNight=%s]",
                lastGlobalDay, gameEndNotificationSent, gameEndProcessingComplete, isGameInitialized,
                isProcessingGameProgression, (currentTungSahurEntity != null ? "alive" : "null"), currentTungSahurDay,
                killStatus.toString().trim(), isSpawning, lastWasNight);
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