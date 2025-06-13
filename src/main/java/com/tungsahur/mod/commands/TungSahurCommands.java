package com.tungsahur.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

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
                                .then(Commands.argument("day", IntegerArgumentType.integer(0, 2))
                                        .executes(context -> setDay(context, IntegerArgumentType.getInteger(context, "day")))))
                        .then(Commands.literal("start")
                                .executes(TungSahurCommands::startGame))
                        .then(Commands.literal("stop")
                                .executes(TungSahurCommands::stopGame))
        );
    }

    private static int getStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        Component message = Component.literal("§6=== Tung Sahur ゲーム状態 ===\n")
                .append("§f現在の日数: ").append(data.getDayStatus()).append("\n")
                .append("§fゲーム状態: ").append(data.isActive() ? "§aアクティブ" : "§c非アクティブ").append("\n")
                .append("§fゲーム進行: ").append(data.isGameOver() ? "§c終了" : "§a進行中");

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int resetDays(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        data.resetDayCount();
        data.setActive(false);

        // 全プレイヤーに通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Tung Sahur] §fゲームがリセットされました"), false);

        source.sendSuccess(() -> Component.literal("§aTung Sahurゲームをリセットしました"), true);
        return 1;
    }

    private static int setDay(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        data.setDayCount(day);

        String[] dayNames = {"1日目", "2日目", "3日目"};
        String dayName = day < dayNames.length ? dayNames[day] : "不明";

        // 全プレイヤーに通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Tung Sahur] §f日数が" + dayName + "に設定されました"), false);

        source.sendSuccess(() -> Component.literal("§a日数を" + dayName + "に設定しました"), true);
        return 1;
    }

    private static int startGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        if (data.isActive()) {
            source.sendFailure(Component.literal("§cTung Sahurゲームは既にアクティブです"));
            return 0;
        }

        data.setActive(true);
        data.resetDayCount();

        // 全プレイヤーに恐怖の開始を通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§4§l=== Tung Tung Tung Sahur ===\n")
                        .append("§c恐怖の3日間が始まった...\n")
                        .append("§7夜になると彼がやってくる\n")
                        .append("§8ベッドで眠ることはできない"), false);

        source.sendSuccess(() -> Component.literal("§cTung Sahurゲームを開始しました"), true);
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData data = DayCountSavedData.get(level);

        if (!data.isActive()) {
            source.sendFailure(Component.literal("§cTung Sahurゲームは既に停止しています"));
            return 0;
        }

        data.setActive(false);

        // 全プレイヤーに通知
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Tung Sahur] §fゲームが停止されました"), false);

        source.sendSuccess(() -> Component.literal("§aTung Sahurゲームを停止しました"), true);
        return 1;
    }
}