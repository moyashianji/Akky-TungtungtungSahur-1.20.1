// TungSahurCommands.java - 改良版（エンティティ自動更新対応）
package com.tungsahur.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.entity.TungSahurEvolutionManager;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class TungSahurCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tungsahur")
                        .requires(source -> source.hasPermission(2)) // OP権限が必要
                        .then(Commands.literal("status")
                                .executes(TungSahurCommands::getStatus))
                        .then(Commands.literal("reset")
                                .executes(TungSahurCommands::resetDays))
                        .then(Commands.literal("setday")
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> setDay(context, IntegerArgumentType.getInteger(context, "day")))))
                        .then(Commands.literal("start")
                                .executes(TungSahurCommands::startGame))
                        .then(Commands.literal("stop")
                                .executes(TungSahurCommands::stopGame))
                        .then(Commands.literal("updateall")
                                .executes(TungSahurCommands::updateAllEntities))
                        .then(Commands.literal("count")
                                .executes(TungSahurCommands::countEntities))
                        .then(Commands.literal("debug")
                                .executes(TungSahurCommands::debugInfo))
        );
    }

    private static int getStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        // 現在の日数とステージ情報
        int dayCount = data.getDayCount();
        int displayDay = dayCount + 1; // 表示用（1日目、2日目、3日目）

        // ワールド内のTungSahurエンティティ数を取得
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        // ステージ別の集計
        long stage0Count = entities.stream().filter(e -> e.getEvolutionStage() == 0).count();
        long stage1Count = entities.stream().filter(e -> e.getEvolutionStage() == 1).count();
        long stage2Count = entities.stream().filter(e -> e.getEvolutionStage() == 2).count();

        Component message = Component.literal("§6=== Tung Sahur ゲーム状態 ===\n")
                .append(String.format("§f現在の日数: §e%d日目\n", displayDay))
                .append(String.format("§fゲーム状態: %s\n", data.isActive() ? "§aアクティブ" : "§c非アクティブ"))
                .append(String.format("§fエンティティ総数: §b%d体\n", entities.size()))
                .append(String.format("§f├ Stage 0 (基本): §7%d体\n", stage0Count))
                .append(String.format("§f├ Stage 1 (強化): §e%d体\n", stage1Count))
                .append(String.format("§f└ Stage 2 (最終): §c%d体\n", stage2Count))
                .append("§6========================");

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int debugInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        Component message = Component.literal("§c=== TungSahur デバッグ情報 ===\n")
                .append(String.format("§fDayCountSavedData:\n"))
                .append(String.format("§f├ Day Count: §e%d\n", data.getDayCount()))
                .append(String.format("§f├ Display Day: §e%d\n", data.getDayCount() + 1))
                .append(String.format("§f├ Is Active: §e%s\n", data.isActive()))
                .append(String.format("§f└ Game Started: §e%s\n", data.hasGameStarted()))
                .append(String.format("§fエンティティ詳細 (§b%d体§f):\n", entities.size()));

        for (int i = 0; i < Math.min(entities.size(), 5); i++) {
            TungSahurEntity entity = entities.get(i);
       }

        if (entities.size() > 5) {
        }

        // 期待値を計算
        int expectedStage = data.getDayCount() >= 2 ? 2 : (data.getDayCount() >= 1 ? 1 : 0);

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int setDay(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        int dayIndex = day - 1; // 内部的には0から始まる
        int oldDay = data.getDayCount() + 1;

        data.setDayCount(dayIndex);

        // 既存のTungSahurエンティティを全て更新
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        int updatedCount = 0;
        for (TungSahurEntity entity : entities) {
            entity.forceUpdateToCurrentDay();
            updatedCount++;
        }

        Component message = Component.literal("§a日数を" + oldDay + "日目から" + day + "日目に変更しました")
                .append(String.format("\n§b%d体のTungSahurエンティティが更新されました", updatedCount));

        source.sendSuccess(() -> message, true);

        // 全プレイヤーに通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(String.format("§c§l[Tung Sahur] §r§e%d日目に変更されました", day)), false);

        return 1;
    }

    private static int updateAllEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        int updatedCount = 0;
        for (TungSahurEntity entity : entities) {
            entity.forceUpdateToCurrentDay();
            updatedCount++;
        }

        Component message = Component.literal(String.format("§a%d体のTungSahurエンティティを現在の日数に更新しました", updatedCount));
        source.sendSuccess(() -> message, false);

        return updatedCount;
    }

    private static int countEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        // ステージ別集計
        long stage0Count = entities.stream().filter(e -> e.getEvolutionStage() == 0).count();
        long stage1Count = entities.stream().filter(e -> e.getEvolutionStage() == 1).count();
        long stage2Count = entities.stream().filter(e -> e.getEvolutionStage() == 2).count();

        Component message = Component.literal("§6=== TungSahur エンティティ統計 ===\n")
                .append(String.format("§f総数: §b%d体\n", entities.size()))
                .append(String.format("§f├ Stage 0 (基本形態): §7%d体\n", stage0Count))
                .append(String.format("§f├ Stage 1 (強化形態): §e%d体\n", stage1Count))
                .append(String.format("§f└ Stage 2 (最終形態): §c%d体", stage2Count));

        source.sendSuccess(() -> message, false);
        return entities.size();
    }

    private static int resetDays(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        data.reset();

        // 既存エンティティも初期状態に戻す
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        for (TungSahurEntity entity : entities) {
            entity.forceUpdateToCurrentDay();
        }

        Component message = Component.literal("§aゲームがリセットされました")
                .append(String.format("\n§b%d体のエンティティが初期状態に戻されました", entities.size()));

        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int startGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        data.start();

        Component message = Component.literal("§c§l恐怖の始まり...")
                .append("\n§7Tung Sahurゲームが開始されました");

        source.sendSuccess(() -> message, true);

        // 全プレイヤーに通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§c§l[Tung Sahur] §r§c恐怖の夜が始まった..."), false);

        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        data.stop();

        Component message = Component.literal("§aゲームが停止されました");
        source.sendSuccess(() -> message, true);

        return 1;
    }
}