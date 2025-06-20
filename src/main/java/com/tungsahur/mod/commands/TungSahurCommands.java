// TungSahurCommands.java - 新しいゲームフロー対応版
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
                                        .executes(TungSahurCommands::debugSleepSystem)))
        );
    }

    // === 新しいゲームフローコマンド ===

    /**
     * ゲーム開始コマンド
     */
    private static int startGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);

        if (gameState.isGameStarted() && !gameState.isGameEnded()) {
            context.getSource().sendFailure(Component.literal("ゲームは既に開始されています")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

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
                        .withStyle(ChatFormatting.GRAY));

        for (ServerPlayer player : level.getPlayers(p -> true)) {
            player.sendSystemMessage(startMessage);
        }

        context.getSource().sendSuccess(() -> Component.literal("ゲームを開始しました！恐怖の始まり...")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    /**
     * ゲームリセットコマンド
     */
    private static int resetGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        GameStateManager gameState = GameStateManager.get(level);
        DayCountSavedData dayData = DayCountSavedData.get(level);

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
            player.sendSystemMessage(resetMessage);
        }

        context.getSource().sendSuccess(() -> Component.literal("ゲームをリセットしました")
                .withStyle(ChatFormatting.GREEN), true);

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
                .append(String.format("§fTungSahurエンティティ数: §d%d体",
                        countTungSahurEntities(level)));

        context.getSource().sendSuccess(() -> statusMessage, false);
        return 1;
    }

    // === 演出メソッド ===

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



        }

        TungSahurMod.LOGGER.info("ゲームリセット演出を実行");
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

    // === 既存のコマンドメソッド（そのまま維持） ===

    private static int getCurrentDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        Component message = Component.literal("現在の日数: " + dayData.getDayCount() + "日目")
                .withStyle(ChatFormatting.YELLOW);

        context.getSource().sendSuccess(() -> message, false);
        return dayData.getDayCount();
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

    // === その他の既存メソッドもそのまま維持 ===

    private static int spawnEntity(CommandContext<CommandSourceStack> context, Vec3 pos) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        TungSahurEntity entity = ModEntities.TUNG_SAHUR.get().create(level);
        if (entity != null) {
            entity.setPos(pos.x, pos.y, pos.z);
            entity.setDayNumber(dayData.getDayCount());
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
        DayCountSavedData dayData = DayCountSavedData.get(level);

        int count = updateAllTungSahurEntities(level, dayData.getDayCount());

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
                .append(String.format("§f睡眠可能: §%s%s",
                        gameState.isSleepAllowed() ? "a" : "c",
                        gameState.isSleepAllowed() ? "はい" : "いいえ"));

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