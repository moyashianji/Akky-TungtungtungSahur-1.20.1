// TungSahurCommands.java - 完全版（時間変更コマンド + 既存機能統合）
package com.tungsahur.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.events.BedSleepEvent;
import com.tungsahur.mod.events.DayCounterEvents;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.items.TungSahurBatItem;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.core.appender.SyslogAppender;

import java.util.Collection;
import java.util.List;

public class TungSahurCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tungsahur")
                        .requires(source -> source.hasPermission(2)) // OP権限が必要

                        // /tungsahur start - ゲーム開始
                        .then(Commands.literal("start")
                                .executes(TungSahurCommands::startGame))

                        // /tungsahur reset - ゲームリセット
                        .then(Commands.literal("reset")
                                .executes(TungSahurCommands::resetGame))

                        // === 新機能：時間変更コマンド ===
                        // /tungsahur time dawn - 朝にする
                        .then(Commands.literal("time")
                                .then(Commands.literal("dawn")
                                        .executes(TungSahurCommands::setTimeToDawn))
                                .then(Commands.literal("day")
                                        .executes(TungSahurCommands::setTimeToDay))
                                .then(Commands.literal("night")
                                        .executes(TungSahurCommands::setTimeToNight))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0, 23999))
                                                .executes(context -> setTime(context, IntegerArgumentType.getInteger(context, "time"))))))

                        // === 新機能：夜をスキップ ===
                        // /tungsahur skip night - 現在の夜をスキップして朝にする
                        .then(Commands.literal("skip")
                                .then(Commands.literal("night")
                                        .executes(TungSahurCommands::skipNight)))

                        // === 新機能：安全な睡眠モード ===
                        // /tungsahur sleep force - 強制的に朝にして安全に休める状態にする
                        .then(Commands.literal("sleep")
                                .then(Commands.literal("force")
                                        .executes(TungSahurCommands::forceSafeSleep)))

                        // 既存のコマンドも維持
                        .then(Commands.literal("status")
                                .executes(TungSahurCommands::getStatus))

                        // 日数管理コマンド（デバッグ用）
                        .then(Commands.literal("day")
                                .then(Commands.literal("get")
                                        .executes(TungSahurCommands::getCurrentDay))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                .executes(context -> setDay(context, IntegerArgumentType.getInteger(context, "day")))))
                                .then(Commands.literal("reset")
                                        .executes(TungSahurCommands::resetDays))
                                .then(Commands.literal("advance")
                                        .executes(TungSahurCommands::advanceDay)))

                        // エンティティ管理コマンド
                        .then(Commands.literal("entity")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(context -> spawnEntity(context, Vec3Argument.getVec3(context, "pos"))))
                                        .executes(TungSahurCommands::spawnEntityAtPlayer))
                                .then(Commands.literal("clear")
                                        .executes(TungSahurCommands::clearAllEntities))
                                .then(Commands.literal("count")
                                        .executes(TungSahurCommands::countEntities))
                                .then(Commands.literal("update")
                                        .executes(TungSahurCommands::updateAllEntities)))

                        // アイテム関連コマンド
                        .then(Commands.literal("give")
                                .then(Commands.literal("bat")
                                        .executes(TungSahurCommands::giveBat)
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                .executes(context -> giveEntityBat(context, IntegerArgumentType.getInteger(context, "day")))))
                                .then(Commands.literal("spawnegg")
                                        .executes(TungSahurCommands::giveSpawnEgg)))

                        // 統計・分析コマンド
                        .then(Commands.literal("stats")
                                .executes(TungSahurCommands::getStatistics))

                        // デバッグ機能
                        .then(Commands.literal("debug")
                                .then(Commands.literal("particles")
                                        .executes(TungSahurCommands::testParticles))
                                .then(Commands.literal("sounds")
                                        .executes(TungSahurCommands::testSounds))
                                .then(Commands.literal("sleep")
                                        .executes(TungSahurCommands::debugSleepSystem))
                                .then(Commands.literal("events")
                                        .executes(TungSahurCommands::debugEvents)))
        );
    }

    // === 新機能：時間変更コマンド群 ===

    /**
     * 朝（夜明け）に時間を設定
     */
    private static int setTimeToDawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long newTime = currentDay * 24000L + 0L; // 夜明け（0tick）

        level.setDayTime(newTime);

        // サフールを削除（朝なので）
        removeAllTungSahurEntitiesWithMessage(level, "朝の時間設定により削除");

        // プレイヤーに通知
        Component message = Component.literal("§e☀ 時間を夜明けに設定しました ☀")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append("\n")
                .append(Component.literal("§7すべてのTung Sahurが朝の光とともに消え去りました")
                        .withStyle(ChatFormatting.GRAY));

        context.getSource().sendSuccess(() -> message, true);

        // 朝の効果音とパーティクル
        try {
            spawnDawnEffects(level, context.getSource().getPlayerOrException());
        } catch (CommandSyntaxException e) {
            // プレイヤーが見つからない場合はエフェクトをスキップ
        }

        TungSahurMod.LOGGER.info("時間を夜明けに設定: {} -> {}", currentTime, newTime);
        return 1;
    }

    /**
     * 昼（正午）に時間を設定
     */
    private static int setTimeToDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long newTime = currentDay * 24000L + 6000L; // 正午（6000tick）

        level.setDayTime(newTime);

        // サフールを削除（昼間なので）
        removeAllTungSahurEntitiesWithMessage(level, "昼間の時間設定により削除");

        //Component message = Component.literal("§e☀ 時間を昼間に設定しました ☀")
        //        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
//
        //context.getSource().sendSuccess(() -> message, true);

        TungSahurMod.LOGGER.info("時間を昼間に設定: {} -> {}", currentTime, newTime);
        return 1;
    }

    /**
     * 夜に時間を設定
     */
    private static int setTimeToNight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long newTime = currentDay * 24000L + 13000L; // 夜（13000tick）

        level.setDayTime(newTime);

       // Component message = Component.literal("§c🌙 時間を夜に設定しました 🌙")
       //         .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
       //         .append("\n")
       //         .append(Component.literal("§7危険な時間帯です。注意してください...")
       //                 .withStyle(ChatFormatting.GRAY));
//
       // context.getSource().sendSuccess(() -> message, true);

        TungSahurMod.LOGGER.info("時間を夜に設定: {} -> {}", currentTime, newTime);
        return 1;
    }

    /**
     * 指定した時間に設定
     */
    private static int setTime(CommandContext<CommandSourceStack> context, int timeOfDay) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long newTime = currentDay * 24000L + timeOfDay;

        level.setDayTime(newTime);

        // 時間帯に応じたメッセージ
        String timeDescription;
        if (timeOfDay < 6000) {
            timeDescription = "夜明け前";
            // 夜明け前なのでサフールを削除
            removeAllTungSahurEntitiesWithMessage(level, "夜明け前の時間設定により削除");
        } else if (timeOfDay < 13000) {
            timeDescription = "昼間";
            // 昼間なのでサフールを削除
            removeAllTungSahurEntitiesWithMessage(level, "昼間の時間設定により削除");
        } else if (timeOfDay < 18000) {
            timeDescription = "夕方";
        } else {
            timeDescription = "夜";
        }

        Component message = Component.literal("§a時間を " + timeOfDay + "tick（" + timeDescription + "）に設定しました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);

        TungSahurMod.LOGGER.info("時間を{}tickに設定: {} -> {}", timeOfDay, currentTime, newTime);
        return 1;
    }

    // === 新機能：夜スキップ機能 ===

    /**
     * 現在の夜をスキップして朝にする
     */
    private static int skipNight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        // 現在が夜でない場合
        if (!level.isNight()) {
            context.getSource().sendFailure(Component.literal("現在は夜ではありません")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // 次の朝に時間を設定
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long nextMorning = (currentDay + 1) * 24000L + 0L; // 次の日の夜明け

        level.setDayTime(nextMorning);

        // サフールを削除
        removeAllTungSahurEntitiesWithMessage(level, "夜スキップにより削除");

        // ゲーム中の場合の特別処理
        if (gameState.isGameActive()) {
            Component gameMessage = Component.literal("§6⚡ 夜をスキップしました ⚡")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append("\n")
                    .append(Component.literal("§7今夜のTung Sahurは諦めて帰って行きました...")
                            .withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.literal("§7しかし、次の夜には再び現れるでしょう...")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            System.out.println(gameMessage);
           // context.getSource().sendSuccess(() -> gameMessage, true);
        } else {
            Component normalMessage = Component.literal("§6⚡ 夜をスキップして朝になりました ⚡")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

            context.getSource().sendSuccess(() -> normalMessage, true);
        }

        // 朝の効果
        try {
            spawnDawnEffects(level, context.getSource().getPlayerOrException());
        } catch (CommandSyntaxException e) {
            // プレイヤーが見つからない場合はエフェクトをスキップ
        }

        TungSahurMod.LOGGER.info("夜スキップ: {} -> {}", currentTime, nextMorning);
        return 1;
    }

    // === 新機能：強制安全睡眠 ===

    /**
     * 強制的に安全な睡眠状態にする（朝にして全サフール削除）
     */
    private static int forceSafeSleep(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        // 朝に時間を設定
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L;
        long morningTime = currentDay * 24000L + 1000L; // 朝の1000tick

        level.setDayTime(morningTime);

        // 全サフールを強制削除
        removeAllTungSahurEntitiesWithMessage(level, "強制安全睡眠により削除");

        // プレイヤーに詳細なメッセージ
        if (gameState.isGameActive()) {
            Component message = Component.literal("§a💤 強制安全睡眠モード発動 💤")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append("\n")
                    .append(Component.literal("§7すべてのTung Sahurを排除し、安全な朝の時間にしました")
                            .withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.literal("§7これで安心して休むことができます")
                            .withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.literal("§8※ゲーム進行は継続中です")
                            .withStyle(ChatFormatting.DARK_GRAY));

            context.getSource().sendSuccess(() -> message, true);
        } else {
            Component message = Component.literal("§a💤 安全な睡眠環境を設定しました 💤")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);

            context.getSource().sendSuccess(() -> message, true);
        }

        // 平和な効果音とパーティクル
        try {
            spawnPeacefulSleepEffects(level, context.getSource().getPlayerOrException());
        } catch (CommandSyntaxException e) {
            // プレイヤーが見つからない場合はエフェクトをスキップ
        }

        TungSahurMod.LOGGER.info("強制安全睡眠: {} -> {}", currentTime, morningTime);
        return 1;
    }

    // === 新しいゲームフローコマンド ===

    /**
     * ゲーム開始コマンド（修正版）
     */
    private static int startGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        if (gameState.isGameStarted() && !gameState.isGameEnded()) {
            context.getSource().sendFailure(Component.literal("ゲームは既に開始されています")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // 重要：ゲーム開始前にDayCounterEventsをリセット
        DayCounterEvents.resetGameState();

        // ゲーム開始
        gameState.startGame(level);

        // 開始演出
        performGameStartEffects(level);

        // 全プレイヤーに通知
        Component startMessage = Component.literal("§l=== Tung Sahur ゲーム開始 ===")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append("\n")
                .append(Component.literal("3日間の恐怖が始まります...")
                        .withStyle(ChatFormatting.RED))
                .append("\n")
                .append(Component.literal("夜になると1日目が開始されます。3日目の夜が終わるまで眠ることはできません。")
                        .withStyle(ChatFormatting.GRAY))
                .append("\n")
                ;

        for (ServerPlayer player : level.getPlayers(p -> true)) {
            player.sendSystemMessage(startMessage);
        }

      //  context.getSource().sendSuccess(() -> Component.literal("ゲームを開始しました！恐怖の始まり...")
      //          .withStyle(ChatFormatting.GREEN), true);

        TungSahurMod.LOGGER.info("新しいゲーム開始 - DayCounterEvents初期化完了");
        return 1;
    }

    /**
     * ゲームリセットコマンド（修正版）
     */
    private static int resetGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);
        DayCountSavedData dayData = DayCountSavedData.get(level);

        // 重要：DayCounterEventsの状態もリセット
        DayCounterEvents.resetGameState();

        // ゲーム状態をリセット
        gameState.resetGame();
        dayData.resetDayCount();

        // 全エンティティを削除
        clearAllTungSahurEntities(level);

        // リセット演出
        performGameResetEffects(level);

        // 全プレイヤーに通知
        Component resetMessage = Component.literal("§l=== ゲームリセット完了 ===")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append("\n")
                .append(Component.literal("ゲーム状態がリセットされました。再び眠ることができます。")
                        .withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : level.getPlayers(p -> true)) {
           // player.sendSystemMessage(resetMessage);
            System.out.println(resetMessage);
        }

      //  context.getSource().sendSuccess(() -> Component.literal("ゲームをリセットしました")
      //          .withStyle(ChatFormatting.GREEN), true);

        TungSahurMod.LOGGER.info("ゲーム完全リセット実行 - 次回開始時に正常動作予定");
        return 1;
    }

    /**
     * ゲーム状態確認コマンド
     */
    private static int getStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);
        DayCountSavedData dayData = DayCountSavedData.get(level);

        Component statusMessage = Component.literal("§6=== Tung Sahur ゲーム状態 ===\n")
                .append(String.format("§fゲーム状態: §e%s\n", gameState.getGameStatus()))
                .append(String.format("§f現在の日数: §b%d日目\n", gameState.getCurrentDay()))
                .append(String.format("§f睡眠可能: §%s%s\n",
                        gameState.isSleepAllowed() ? "a" : "c",
                        gameState.isSleepAllowed() ? "はい" : "いいえ"))
                .append(String.format("§fTungSahurエンティティ数: §d%d体\n",
                        countTungSahurEntities(level)))
                .append(String.format("§f現在時刻: §e%d tick (%s)",
                        level.getDayTime() % 24000L,
                        level.isNight() ? "夜" : "昼"));

        context.getSource().sendSuccess(() -> statusMessage, false);
        return 1;
    }

    // === エフェクト関数 ===

    /**
     * 夜明けの効果（パーティクルと音）
     */
    private static void spawnDawnEffects(ServerLevel level, ServerPlayer player) {
        // 夜明けのパーティクル効果
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 8.0;
            double y = player.getY() + level.random.nextDouble() * 5.0 + 1.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 8.0;

            // 朝の光のパーティクル
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.1, 0.0, 0.05);
            level.sendParticles(ParticleTypes.GLOW, x, y, z, 1, 0.0, 0.1, 0.0, 0.1);
        }

        // 夜明けの音
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(),
                SoundSource.AMBIENT, 1.0f, 1.2f);
    }

    /**
     * 平和な睡眠の効果（パーティクルと音）
     */
    private static void spawnPeacefulSleepEffects(ServerLevel level, ServerPlayer player) {
        // 平和なパーティクル効果
        for (int i = 0; i < 15; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 6.0;
            double y = player.getY() + level.random.nextDouble() * 4.0 + 1.0;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 6.0;

            // 安らぎのパーティクル
            level.sendParticles(ParticleTypes.HEART, x, y, z, 1, 0.0, 0.1, 0.0, 0.05);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.1, 0.0, 0.1);
        }

        // 平和な音
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * ゲーム開始時の演出
     */
    private static void performGameStartEffects(ServerLevel level) {
        // 全プレイヤーの周囲に恐怖の演出
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            BlockPos pos = player.blockPosition();

            // 不気味なパーティクル
            for (int i = 0; i < 30; i++) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + (level.random.nextDouble() - 0.5) * 10.0,
                        pos.getY() + level.random.nextDouble() * 3.0,
                        pos.getZ() + (level.random.nextDouble() - 0.5) * 10.0,
                        1, 0.1, 0.1, 0.1, 0.02);
            }

            // 恐怖の音
            level.playSound(null, pos, SoundEvents.WITHER_SPAWN,
                    SoundSource.HOSTILE, 0.5f, 0.8f);
        }

        TungSahurMod.LOGGER.info("ゲーム開始演出を実行");
    }

    /**
     * ゲームリセット時の演出
     */
    private static void performGameResetEffects(ServerLevel level) {
        // 全プレイヤーの周囲に平和な演出
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            BlockPos pos = player.blockPosition();

            // 平和なパーティクル
            for (int i = 0; i < 20; i++) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + (level.random.nextDouble() - 0.5) * 5.0,
                        pos.getY() + level.random.nextDouble() * 2.0,
                        pos.getZ() + (level.random.nextDouble() - 0.5) * 5.0,
                        1, 0.0, 0.1, 0.0, 0.1);
            }

            // 平和な音
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        TungSahurMod.LOGGER.info("ゲームリセット演出を実行");
    }

    /**
     * メッセージ付きで全TungSahurエンティティを削除
     */
    private static void removeAllTungSahurEntitiesWithMessage(ServerLevel level, String reason) {
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()));

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        if (entities.size() > 0) {
            TungSahurMod.LOGGER.info("{}: {}体のTungSahurエンティティを削除", reason, entities.size());
        }
    }

    // === ユーティリティメソッド ===

    /**
     * TungSahurエンティティの数をカウント
     */
    private static int countTungSahurEntities(ServerLevel level) {
        return level.getEntitiesOfClass(TungSahurEntity.class,
                        new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                                level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()))
                .size();
    }

    /**
     * 全TungSahurエンティティを削除
     */
    private static void clearAllTungSahurEntities(ServerLevel level) {
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()));

        for (TungSahurEntity entity : entities) {
            entity.discard();
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを削除", entities.size());
    }

    // === 既存のコマンドメソッド ===

    private static int getCurrentDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        Component message = Component.literal("現在の日数: " + gameState.getCurrentDay() + "日目")
                .withStyle(ChatFormatting.YELLOW);

        context.getSource().sendSuccess(() -> message, false);
        return gameState.getCurrentDay();
    }

    private static int setDay(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        int oldDay = dayData.getDayCount();
        dayData.forceDayCount(day);

        // 全エンティティの更新
        updateAllTungSahurEntities(level, day);

        Component message = Component.literal(String.format("日数を %d日目 から %d日目 に変更しました", oldDay, day))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return day;
    }

    private static int resetDays(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        dayData.resetDayCount();
        updateAllTungSahurEntities(level, 1);

        Component message = Component.literal("日数を1日目にリセットしました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int advanceDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        int oldDay = dayData.getDayCount();
        int newDay = Math.min(3, oldDay + 1);
        dayData.forceDayCount(newDay);

        updateAllTungSahurEntities(level, newDay);

        Component message = Component.literal(String.format("日数を進行: %d日目 -> %d日目", oldDay, newDay))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return newDay;
    }

    private static int spawnEntity(CommandContext<CommandSourceStack> context, Vec3 pos) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        TungSahurEntity entity = ModEntities.TUNG_SAHUR.get().create(level);
        if (entity != null) {
            entity.setPos(pos.x, pos.y, pos.z);
            entity.setDayNumber(gameState.getCurrentDay());
            entity.finalizeSpawn((ServerLevelAccessor) level, level.getCurrentDifficultyAt(BlockPos.containing(pos)),
                    MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(entity);

            Component message = Component.literal(String.format("TungSahurエンティティを (%.1f, %.1f, %.1f) にスポーンしました",
                    pos.x, pos.y, pos.z)).withStyle(ChatFormatting.GREEN);

            context.getSource().sendSuccess(() -> message, true);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("エンティティのスポーンに失敗しました"));
        return 0;
    }

    private static int spawnEntityAtPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return spawnEntity(context, player.position());
    }

    private static int clearAllEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        clearAllTungSahurEntities(level);

        Component message = Component.literal("全てのTungSahurエンティティを削除しました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int countEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        int count = countTungSahurEntities(level);

        Component message = Component.literal("TungSahurエンティティ数: " + count + "体")
                .withStyle(ChatFormatting.YELLOW);

        context.getSource().sendSuccess(() -> message, false);
        return count;
    }

    private static int updateAllEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        int count = updateAllTungSahurEntities(level, gameState.getCurrentDay());

        Component message = Component.literal(String.format("%d体のTungSahurエンティティを更新しました", count))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return count;
    }

    private static int giveBat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack bat = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        player.getInventory().add(bat);

        Component message = Component.literal("TungSahurバットを与えました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int giveEntityBat(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack entityBat = TungSahurBatItem.createEntityBat(day);

        player.getInventory().add(entityBat);

        Component message = Component.literal(String.format("エンティティバット (Day %d) を与えました", day))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int giveSpawnEgg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack spawnEgg = new ItemStack(ModItems.TUNG_SAHUR_SPAWN_EGG.get());

        player.getInventory().add(spawnEgg);

        Component message = Component.literal("TungSahurスポーンエッグを与えました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int getStatistics(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()));

        Component message = Component.literal("§6=== TungSahur統計情報 ===\n")
                .append(String.format("§fゲーム状態: §e%s\n", gameState.getGameStatus()))
                .append(String.format("§f現在の日数: §e%d日目\n", gameState.getCurrentDay()))
                .append(String.format("§f総エンティティ数: §b%d体\n", entities.size()))
                .append(String.format("§f睡眠可能: §%s%s\n",
                        gameState.isSleepAllowed() ? "a" : "c",
                        gameState.isSleepAllowed() ? "はい" : "いいえ"))
                .append(String.format("§f現在時刻: §e%d tick (%s)",
                        level.getDayTime() % 24000L,
                        level.isNight() ? "夜" : "昼"));

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    // === デバッグコマンド ===

    private static int testParticles(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // テストパーティクル
        for (int i = 0; i < 50; i++) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + (level.random.nextDouble() - 0.5) * 5.0,
                    pos.getY() + level.random.nextDouble() * 3.0,
                    pos.getZ() + (level.random.nextDouble() - 0.5) * 5.0,
                    1, 0.1, 0.1, 0.1, 0.02);
        }

        context.getSource().sendSuccess(() -> Component.literal("パーティクルテスト実行")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int testSounds(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        level.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.5f, 1.0f);

        context.getSource().sendSuccess(() -> Component.literal("サウンドテスト実行")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int debugSleepSystem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BedSleepEvent.logSleepPreventionStatistics();

        context.getSource().sendSuccess(() -> Component.literal("睡眠システムデバッグ情報をログに出力")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int debugEvents(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String debugInfo = DayCounterEvents.getDebugInfo();

        Component message = Component.literal("§6=== DayCounterEvents Debug Info ===\n")
                .append(Component.literal(debugInfo).withStyle(ChatFormatting.GRAY));

        context.getSource().sendSuccess(() -> message, false);

        TungSahurMod.LOGGER.info("DayCounterEvents Debug: {}", debugInfo);
        return 1;
    }

    // === ヘルパーメソッド ===

    private static int updateAllTungSahurEntities(ServerLevel level, int dayNumber) {
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                        level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()));

        for (TungSahurEntity entity : entities) {
            entity.setDayNumber(dayNumber);
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを{}日目に更新", entities.size(), dayNumber);
        return entities.size();
    }
}