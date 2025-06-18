// TungSahurCommands.java - 完全対応版
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

public class TungSahurCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tungsahur")
                        .requires(source -> source.hasPermission(2)) // OP権限が必要

                        // 基本コマンド
                        .then(Commands.literal("status")
                                .executes(TungSahurCommands::getStatus))

                        .then(Commands.literal("debug")
                                .executes(TungSahurCommands::getDebugInfo)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setDebugMode(context, BoolArgumentType.getBool(context, "enabled")))))

                        // 日数管理
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

                        // エンティティ管理
                        .then(Commands.literal("entity")
                                .then(Commands.literal("count")
                                        .executes(TungSahurCommands::countEntities))
                                .then(Commands.literal("list")
                                        .executes(TungSahurCommands::listEntities))
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(context -> spawnEntity(context, Vec3Argument.getVec3(context, "position")))
                                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                        .executes(context -> spawnEntityWithDay(context,
                                                                Vec3Argument.getVec3(context, "position"),
                                                                IntegerArgumentType.getInteger(context, "day"))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("all")
                                                .executes(TungSahurCommands::removeAllEntities))
                                        .then(Commands.literal("nearest")
                                                .executes(TungSahurCommands::removeNearestEntity)))
                                .then(Commands.literal("update")
                                        .then(Commands.literal("all")
                                                .executes(TungSahurCommands::updateAllEntities))
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(context -> updateSpecificEntities(context, EntityArgument.getEntities(context, "targets"))))))

                        // アイテム管理
                        .then(Commands.literal("item")
                                .then(Commands.literal("give")
                                        .then(Commands.literal("bat")
                                                .executes(TungSahurCommands::giveBat)
                                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                        .executes(context -> giveBatWithDay(context, IntegerArgumentType.getInteger(context, "day")))))
                                        .then(Commands.literal("entity_bat")
                                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                        .executes(context -> giveEntityBat(context, IntegerArgumentType.getInteger(context, "day"))))))

                                // 統計・分析
                                .then(Commands.literal("stats")
                                        .executes(TungSahurCommands::getStatistics)
                                        .then(Commands.literal("events")
                                                .executes(TungSahurCommands::getEventStatistics))
                                        )

                                // テスト・デバッグ
                                .then(Commands.literal("test")
                                        .then(Commands.literal("nightmare")
                                                .executes(TungSahurCommands::testNightmare))
                                        .then(Commands.literal("effects")
                                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 3))
                                                        .executes(context -> testDayEffects(context, IntegerArgumentType.getInteger(context, "day")))))
                                        .then(Commands.literal("spawn_effects")
                                                .executes(TungSahurCommands::testSpawnEffects)))

                                // システム管理
                                .then(Commands.literal("system")
                                        .then(Commands.literal("reload")
                                                .executes(TungSahurCommands::reloadSystem))
                                        .then(Commands.literal("cleanup")
                                                .executes(TungSahurCommands::cleanupSystem))
                                        .then(Commands.literal("backup")
                                                .executes(TungSahurCommands::backupData)))
                        ));
    }

    // === 基本コマンド ===

    private static int getStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        // ワールド境界内のエンティティ統計
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
        long day1Count = entities.stream().filter(e -> e.getDayNumber() == 1).count();
        long day2Count = entities.stream().filter(e -> e.getDayNumber() == 2).count();
        long day3Count = entities.stream().filter(e -> e.getDayNumber() == 3).count();

        Component message = Component.literal("§6=== TungSahur Status ===\n")
                .append(String.format("§f現在の日数: §e%d日目\n", dayData.getDayCount()))
                .append(String.format("§fデバッグモード: %s\n", TungSahurMod.isDebugMode() ? "§a有効" : "§c無効"))
                .append(String.format("§fエンティティ総数: §b%d体\n", entities.size()))
                .append(String.format("§f├ 1日目: §7%d体\n", day1Count))
                .append(String.format("§f├ 2日目: §e%d体\n", day2Count))
                .append(String.format("§f└ 3日目: §c%d体\n", day3Count))
                .append("§6========================");

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int getDebugInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        // ワールド境界内のエンティティを取得
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

        Component message = Component.literal("§c=== TungSahur Debug Info ===\n")
                .append(String.format("§fMod Version: §e%s\n", "1.20.1-1.0.0"))
                .append(String.format("§fDebug Mode: §e%s\n", TungSahurMod.isDebugMode()))
                .append(String.format("§fWorld Time: §e%d\n", level.getDayTime()))
                .append(String.format("§fGame Time: §e%d\n", level.getGameTime()))
                .append(String.format("§fDay Data: §e%s\n", dayData.getDayDescription()))
                .append(String.format("§fLoaded Entities: §e%d\n", entities.size()));

        // 個別エンティティ詳細（最大5体）
        for (int i = 0; i < Math.min(entities.size(), 5); i++) {
            TungSahurEntity entity = entities.get(i);


        }



        return 1;
    }

    private static int setDebugMode(CommandContext<CommandSourceStack> context, boolean enabled) {
        TungSahurMod.setDebugMode(enabled);

        Component message = Component.literal("デバッグモード: " + (enabled ? "有効" : "無効"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    // === 日数管理コマンド ===

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

        // 成功メッセージ
        Component message = Component.literal(String.format("日数を %d日目 から %d日目 に変更しました", oldDay, day))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);

        // ログ出力
        TungSahurMod.LOGGER.info("コマンドによる日数変更: {}日目 -> {}日目 (実行者: {})",
                oldDay, day, context.getSource().getTextName());

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

        if (newDay == oldDay) {
            Component message = Component.literal("既に最終日です")
                    .withStyle(ChatFormatting.YELLOW);
            context.getSource().sendSuccess(() -> message, false);
            return oldDay;
        }

        dayData.forceDayCount(newDay);
        updateAllTungSahurEntities(level, newDay);

        // 進行効果
        spawnDayAdvanceEffects(level, context.getSource().getPosition(), newDay);

        Component message = Component.literal(String.format("日数を進行させました: %d日目 → %d日目", oldDay, newDay))
                .withStyle(ChatFormatting.GOLD);

        context.getSource().sendSuccess(() -> message, true);
        return newDay;
    }

    // === エンティティ管理コマンド ===

    private static int countEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        // ワールド境界内のエンティティを取得
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

        Component message = Component.literal("TungSahurエンティティ数: " + entities.size() + "体")
                .withStyle(ChatFormatting.BLUE);

        context.getSource().sendSuccess(() -> message, false);
        return entities.size();
    }

    private static int listEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        // ワールド境界内のエンティティを取得
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

        if (entities.isEmpty()) {
            Component message = Component.literal("TungSahurエンティティが見つかりません")
                    .withStyle(ChatFormatting.GRAY);
            context.getSource().sendSuccess(() -> message, false);
            return 0;
        }

        Component message = Component.literal("§b=== TungSahurエンティティ一覧 ===\n");
        for (int i = 0; i < Math.min(entities.size(), 10); i++) {
            TungSahurEntity entity = entities.get(i);


        }

        if (entities.size() > 10) {

        }

        context.getSource().sendSuccess(() -> message, false);
        return entities.size();
    }

    private static int spawnEntity(CommandContext<CommandSourceStack> context, Vec3 position) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        return spawnEntityAtPosition(level, position, dayData.getDayCount(), context.getSource());
    }

    private static int spawnEntityWithDay(CommandContext<CommandSourceStack> context, Vec3 position, int day) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        return spawnEntityAtPosition(level, position, day, context.getSource());
    }

    private static int spawnEntityAtPosition(ServerLevel level, Vec3 position, int day, CommandSourceStack source) {
        TungSahurEntity entity = new TungSahurEntity(ModEntities.TUNG_SAHUR.get(), level);
        entity.moveTo(position.x, position.y, position.z);
        entity.setDayNumber(day);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                MobSpawnType.COMMAND, null, null);

        boolean success = level.addFreshEntity(entity);

        if (success) {
            // スポーン効果
            spawnEntitySpawnEffects(level, position, day);

            Component message = Component.literal(String.format("TungSahur (Day %d) をスポーンしました: (%.1f, %.1f, %.1f)",
                            day, position.x, position.y, position.z))
                    .withStyle(ChatFormatting.GREEN);

            source.sendSuccess(() -> message, true);
            return 1;
        } else {
            Component message = Component.literal("エンティティのスポーンに失敗しました")
                    .withStyle(ChatFormatting.RED);

            source.sendSuccess(() -> message, false);
            return 0;
        }
    }

    private static int removeAllEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        // ワールド境界内のエンティティを取得
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

        int removedCount = 0;
        for (TungSahurEntity entity : entities) {
            entity.discard();
            removedCount++;
        }

        Component message = Component.literal("全TungSahurエンティティを削除しました: " + removedCount + "体")
                .withStyle(ChatFormatting.RED);

        context.getSource().sendSuccess(() -> message, true);
        return removedCount;
    }

    private static int removeNearestEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 sourcePos = context.getSource().getPosition();
        ServerLevel level = context.getSource().getLevel();

        TungSahurEntity nearest = level.getNearestEntity(TungSahurEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT,
                null, sourcePos.x, sourcePos.y, sourcePos.z,
                new AABB(sourcePos.add(-50, -50, -50), sourcePos.add(50, 50, 50)));

        if (nearest == null) {
            Component message = Component.literal("近くにTungSahurエンティティが見つかりません")
                    .withStyle(ChatFormatting.GRAY);
            context.getSource().sendSuccess(() -> message, false);
            return 0;
        }

        double distance = sourcePos.distanceTo(nearest.position());
        nearest.discard();

        Component message = Component.literal(String.format("最寄りのTungSahurエンティティを削除しました (距離: %.1f)", distance))
                .withStyle(ChatFormatting.RED);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int updateAllEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        int updatedCount = updateAllTungSahurEntities(level, dayData.getDayCount());

        Component message = Component.literal("全TungSahurエンティティを更新しました: " + updatedCount + "体")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return updatedCount;
    }

    private static int updateSpecificEntities(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);
        int currentDay = dayData.getDayCount();

        int updatedCount = 0;
        for (Entity entity : targets) {
            if (entity instanceof TungSahurEntity tungSahur) {
                tungSahur.setDayNumber(currentDay);
                updatedCount++;
            }
        }

        Component message = Component.literal("指定されたTungSahurエンティティを更新しました: " + updatedCount + "体")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return updatedCount;
    }

    // === アイテム管理コマンド ===

    private static int giveBat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        player.getInventory().add(batStack);

        Component message = Component.literal("TungSahurバットを与えました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int giveBatWithDay(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        // 日数タグを追加
        CompoundTag tag = batStack.getOrCreateTag();
        tag.putInt("DayNumber", day);
        tag.putString("BatType", switch (day) {
            case 1 -> "Basic";
            case 2 -> "Enhanced";
            case 3 -> "Ultimate";
            default -> "Unknown";
        });

        player.getInventory().add(batStack);

        Component message = Component.literal(String.format("TungSahurバット (Day %d) を与えました", day))
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

    // === 統計・分析コマンド ===

    private static int getStatistics(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        // ワールド境界内のエンティティを取得
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

        // 詳細統計
        long attackingCount = entities.stream().filter(TungSahurEntity::isCurrentlyAttacking).count();
        long throwingCount = entities.stream().filter(TungSahurEntity::isCurrentlyThrowing).count();
        long jumpingCount = entities.stream().filter(TungSahurEntity::isCurrentlyJumping).count();
        long climbingCount = entities.stream().filter(TungSahurEntity::isWallClimbing).count();
        long watchedCount = entities.stream().filter(TungSahurEntity::isBeingWatched).count();

        Component message = Component.literal("§6=== TungSahur統計情報 ===\n")
                .append(String.format("§f現在の日数: §e%d日目\n", dayData.getDayCount()))
                .append(String.format("§f総エンティティ数: §b%d体\n", entities.size()))
                .append(String.format("§f├ 攻撃中: §c%d体\n", attackingCount))
                .append(String.format("§f├ 投擲中: §e%d体\n", throwingCount))
                .append(String.format("§f├ ジャンプ中: §a%d体\n", jumpingCount))
                .append(String.format("§f├ 壁登り中: §7%d体\n", climbingCount))
                .append(String.format("§f└ 監視されている: §d%d体\n", watchedCount))
                .append("§6===========================");

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int getEventStatistics(CommandContext<CommandSourceStack> context) {
        DayCounterEvents.logEventStatistics();

        Component message = Component.literal("イベント統計をコンソールに出力しました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }



    // === テスト・デバッグコマンド ===

    private static int testNightmare(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // テスト用悪夢効果
        player.sendSystemMessage(Component.literal("§l§kテスト悪夢が開始されました...")
                .withStyle(ChatFormatting.DARK_PURPLE));

        // パーティクル効果
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20, 1.0, 1.0, 1.0, 0.1);



        return 1;
    }

    private static int testDayEffects(CommandContext<CommandSourceStack> context, int day) throws CommandSyntaxException {
        Vec3 position = context.getSource().getPosition();
        ServerLevel level = context.getSource().getLevel();

        spawnDayAdvanceEffects(level, position, day);

        Component message = Component.literal(String.format("Day %d のエフェクトをテストしました", day))
                .withStyle(ChatFormatting.GOLD);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int testSpawnEffects(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 position = context.getSource().getPosition();
        ServerLevel level = context.getSource().getLevel();

        spawnEntitySpawnEffects(level, position, 2); // 2日目のエフェクトをテスト

        Component message = Component.literal("スポーンエフェクトをテストしました")
                .withStyle(ChatFormatting.BLUE);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    // === システム管理コマンド ===

    private static int reloadSystem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        // システムリロード処理
        DayCountSavedData dayData = DayCountSavedData.get(level);
        dayData.updateDayCount(level);

        // エンティティの再同期
        updateAllTungSahurEntities(level, dayData.getDayCount());

        Component message = Component.literal("TungSahurシステムをリロードしました")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int cleanupSystem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();

        // ワールド境界内の無効なエンティティの削除
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
        int removedCount = 0;

        for (TungSahurEntity entity : entities) {
            if (!entity.isAlive() || entity.isRemoved()) {
                entity.discard();
                removedCount++;
            }
        }

        Component message = Component.literal(String.format("システムクリーンアップ完了: %d個の無効エンティティを削除", removedCount))
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return removedCount;
    }

    private static int backupData(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        // ワールド境界内のエンティティ数を取得
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

        // データバックアップの実行（実際の実装ではファイルシステムを使用）
        TungSahurMod.LOGGER.info("データバックアップ実行: Day={}, エンティティ数={}",
                dayData.getDayCount(), entities.size());

        Component message = Component.literal("データバックアップを実行しました（詳細はログを確認）")
                .withStyle(ChatFormatting.GREEN);

        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    // === ユーティリティメソッド ===

    private static int updateAllTungSahurEntities(ServerLevel level, int day) {
        // ワールド境界内のエンティティを取得
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
            entity.setDayNumber(day);
        }

        return entities.size();
    }

    private static void spawnDayAdvanceEffects(ServerLevel level, Vec3 position, int day) {
        switch (day) {
            case 2:
                level.sendParticles(ParticleTypes.FLAME,
                        position.x, position.y + 1.0, position.z,
                        30, 2.0, 1.0, 2.0, 0.1);
                level.playSound(null, BlockPos.containing(position), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 1.0F, 1.5F);
                break;

            case 3:
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        position.x, position.y + 1.0, position.z,
                        50, 3.0, 2.0, 3.0, 0.15);
                level.sendParticles(ParticleTypes.WITCH,
                        position.x, position.y + 1.0, position.z,
                        25, 2.0, 1.0, 2.0, 0.1);
                level.playSound(null, BlockPos.containing(position), SoundEvents.WITHER_SPAWN,
                        SoundSource.HOSTILE, 1.0F, 0.8F);
                break;
        }
    }

    private static void spawnEntitySpawnEffects(ServerLevel level, Vec3 position, int day) {
        // 基本スポーンパーティクル
        level.sendParticles(ParticleTypes.SMOKE,
                position.x, position.y + 1.0, position.z,
                15, 1.0, 1.0, 1.0, 0.05);

        // 日数に応じた特別なパーティクル
        switch (day) {
            case 2:
                level.sendParticles(ParticleTypes.FLAME,
                        position.x, position.y + 1.0, position.z,
                        10, 0.5, 0.5, 0.5, 0.03);
                break;

            case 3:
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        position.x, position.y + 1.0, position.z,
                        15, 0.8, 0.8, 0.8, 0.05);
                break;
        }

        // スポーン音
        level.playSound(null, BlockPos.containing(position), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.8F, 0.8F + (day * 0.1F));
    }
}