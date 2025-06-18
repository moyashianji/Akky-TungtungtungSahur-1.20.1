// BedSleepEvent.java - 完全対応版
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class BedSleepEvent {

    // 睡眠セッション管理
    private static final Map<UUID, SleepSession> activeSleepSessions = new HashMap<>();
    private static final Map<UUID, Long> lastSleepAttempt = new HashMap<>();

    // 設定
    private static final long SLEEP_COOLDOWN = 12000; // 10分のクールダウン
    private static final int MIN_SLEEP_TIME = 100; // 最小睡眠時間（5秒）
    private static final double NIGHTMARE_CHANCE_BASE = 0.3; // 30%の基本悪夢確率

    /**
     * プレイヤーがベッドで眠り始めた時
     */
    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        UUID playerUUID = player.getUUID();
        long currentTime = level.getGameTime();

        // クールダウン確認
        if (lastSleepAttempt.containsKey(playerUUID)) {
            long timeSinceLastSleep = currentTime - lastSleepAttempt.get(playerUUID);
            if (timeSinceLastSleep < SLEEP_COOLDOWN) {
                long remainingTime = (SLEEP_COOLDOWN - timeSinceLastSleep) / 20; // 秒に変換
                player.sendSystemMessage(Component.literal("まだ眠れません... (" + remainingTime + "秒後)")
                        .withStyle(ChatFormatting.YELLOW));
                event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
                return;
            }
        }

        // 睡眠セッション開始
        startSleepSession(player, level, event.getPos());

        TungSahurMod.LOGGER.debug("プレイヤー {} が睡眠開始: {}", player.getName().getString(), event.getPos());
    }

    /**
     * プレイヤーが目覚めた時
     */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        UUID playerUUID = player.getUUID();

        SleepSession session = activeSleepSessions.remove(playerUUID);
        if (session == null) return;

        long sleepDuration = level.getGameTime() - session.startTime;

        // 最小睡眠時間チェック
        if (sleepDuration < MIN_SLEEP_TIME) {
            handleShortSleep(player, session, sleepDuration);
            return;
        }

        // 正常な睡眠処理
        handleNormalWakeUp(player, level, session, sleepDuration);

        TungSahurMod.LOGGER.debug("プレイヤー {} が目覚め: 睡眠時間={}tick",
                player.getName().getString(), sleepDuration);
    }

    // === 内部メソッド ===

    /**
     * 睡眠セッション開始
     */
    private static void startSleepSession(ServerPlayer player, ServerLevel level, BlockPos bedPos) {
        UUID playerUUID = player.getUUID();
        DayCountSavedData dayData = DayCountSavedData.get(level);

        SleepSession session = new SleepSession(
                level.getGameTime(),
                bedPos,
                dayData.getDayCount(),
                calculateNightmareChance(level, player)
        );

        activeSleepSessions.put(playerUUID, session);
        lastSleepAttempt.put(playerUUID, level.getGameTime());

        // 睡眠開始メッセージ
        player.sendSystemMessage(Component.literal("安らかに眠れるでしょうか...")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        // 周囲のTungSahurエンティティに睡眠を通知
        notifyNearbyTungSahurOfSleep(level, player, bedPos, true);

        // 睡眠開始音

    }

    /**
     * 正常な目覚め処理
     */
    private static void handleNormalWakeUp(ServerPlayer player, ServerLevel level, SleepSession session, long sleepDuration) {
        DayCountSavedData dayData = DayCountSavedData.get(level);
        int oldDay = session.dayAtSleepStart;

        // 睡眠による日数進行の判定
        boolean shouldAdvanceDay = determineDayAdvancement(sleepDuration, oldDay);

        if (shouldAdvanceDay) {
            // 日数を進行
            int newDay = Math.min(3, oldDay + 1);
            dayData.forceDayCount(newDay);

            handleDayAdvancement(player, level, oldDay, newDay, session);
        } else {
            // 悪夢判定
            if (player.getRandom().nextDouble() < session.nightmareChance) {
                handleNightmare(player, level, session);
            } else {
                handlePeacefulWakeUp(player, level, session);
            }
        }

        // 周囲のTungSahurエンティティに目覚めを通知
        notifyNearbyTungSahurOfSleep(level, player, session.bedPos, false);
    }

    /**
     * 短時間睡眠の処理
     */
    private static void handleShortSleep(ServerPlayer player, SleepSession session, long sleepDuration) {
        player.sendSystemMessage(Component.literal("十分に眠れませんでした...")
                .withStyle(ChatFormatting.YELLOW));

        // 短時間睡眠のペナルティ
        if (player.getRandom().nextDouble() < 0.5) {
            player.sendSystemMessage(Component.literal("何かが近づいてくる気配がします...")
                    .withStyle(ChatFormatting.DARK_RED));

            // パーティクル効果
            spawnShortSleepEffects(player);
        }

        TungSahurMod.LOGGER.debug("プレイヤー {} の短時間睡眠: {}tick",
                player.getName().getString(), sleepDuration);
    }

    /**
     * 日数進行の判定
     */
    private static boolean determineDayAdvancement(long sleepDuration, int currentDay) {
        // 睡眠時間に基づく進行確率
        double baseChance = Math.min(0.8, sleepDuration / 2400.0); // 最大80%、2分で最大

        // 現在の日数による調整
        double dayMultiplier = switch (currentDay) {
            case 1 -> 1.0; // 1日目から2日目は通常確率
            case 2 -> 0.8; // 2日目から3日目は少し低い確率
            default -> 0.0; // 3日目以降は進行しない
        };

        return baseChance * dayMultiplier > 0.6; // 60%以上で進行
    }

    /**
     * 日数進行時の処理
     */
    private static void handleDayAdvancement(ServerPlayer player, ServerLevel level, int oldDay, int newDay, SleepSession session) {
        // 進行メッセージ
        Component advanceMessage = Component.literal("§l時が進みました... " + newDay + "日目")
                .withStyle(getColorForDay(newDay));
        player.sendSystemMessage(advanceMessage);

        // 警告メッセージ
        Component warningMessage = createDayAdvanceWarning(newDay);
        player.sendSystemMessage(warningMessage);

        // 進行効果
        spawnDayAdvanceEffects(player, level, newDay);

        // 全TungSahurエンティティの更新
        updateAllTungSahurForNewDay(level, newDay);

        // 他のプレイヤーへの通知
        notifyOtherPlayersOfDayAdvance(level, player, newDay);

        TungSahurMod.LOGGER.info("プレイヤー {} の睡眠により日数進行: {}日目 -> {}日目",
                player.getName().getString(), oldDay, newDay);
    }

    /**
     * 悪夢の処理
     */
    private static void handleNightmare(ServerPlayer player, ServerLevel level, SleepSession session) {
        player.sendSystemMessage(Component.literal("§l悪夢にうなされました...")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));

        // 悪夢の内容を選択
        NightmareType nightmare = selectNightmareType(session.dayAtSleepStart, (Random) player.getRandom());
        executeNightmare(player, level, nightmare);

        TungSahurMod.LOGGER.debug("プレイヤー {} が悪夢を体験: {}",
                player.getName().getString(), nightmare);
    }

    /**
     * 平和な目覚めの処理
     */
    private static void handlePeacefulWakeUp(ServerPlayer player, ServerLevel level, SleepSession session) {
        player.sendSystemMessage(Component.literal("よく眠れました...")
                .withStyle(ChatFormatting.GREEN));

        // 小さな回復効果
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(2.0F);
        }

        // 平和なパーティクル
        spawnPeacefulWakeUpEffects(player);
    }

    /**
     * 悪夢確率の計算
     */
    private static double calculateNightmareChance(ServerLevel level, Player player) {
        double chance = NIGHTMARE_CHANCE_BASE;

        // 日数による確率増加
        DayCountSavedData dayData = DayCountSavedData.get(level);
        int dayNumber = dayData.getDayCount();
        chance += dayNumber * 0.2; // 各日数で20%増加

        // 周囲のTungSahurエンティティによる確率増加
        List<TungSahurEntity> nearbyEntities = level.getEntitiesOfClass(TungSahurEntity.class,
                player.getBoundingBox().inflate(32.0));
        chance += nearbyEntities.size() * 0.15; // 1体につき15%増加

        // 夜間ボーナス
        if (level.isNight()) {
            chance += 0.2;
        }

        return Math.min(0.9, chance); // 最大90%
    }

    /**
     * 周囲のTungSahurエンティティへの通知
     */
    private static void notifyNearbyTungSahurOfSleep(ServerLevel level, Player player, BlockPos bedPos, boolean isStarting) {
        List<TungSahurEntity> nearbyEntities = level.getEntitiesOfClass(TungSahurEntity.class,
                player.getBoundingBox().inflate(64.0));

        for (TungSahurEntity entity : nearbyEntities) {
            if (isStarting) {
                // プレイヤーが眠り始めた
                entity.setTarget(null); // 一時的にターゲット解除

                // ベッド周辺に集まる行動
                if (entity.getNavigation().isDone()) {
                    double angle = entity.getRandom().nextDouble() * 2 * Math.PI;
                    double distance = 8.0 + entity.getRandom().nextDouble() * 8.0;
                    BlockPos targetPos = bedPos.offset(
                            (int) (Math.cos(angle) * distance),
                            0,
                            (int) (Math.sin(angle) * distance)
                    );
                    entity.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 0.5);
                }
            } else {
                // プレイヤーが目覚めた - 通常の行動を再開
                if (entity.getTarget() == null) {
                    entity.setTarget(player);
                }
            }
        }

        TungSahurMod.LOGGER.debug("{}体のTungSahurエンティティに睡眠状態通知: {}",
                nearbyEntities.size(), isStarting ? "開始" : "終了");
    }

    /**
     * 悪夢タイプの選択
     */
    private static NightmareType selectNightmareType(int dayNumber, Random random) {
        return switch (dayNumber) {
            case 1 -> random.nextBoolean() ? NightmareType.WHISPERS : NightmareType.SHADOWS;
            case 2 -> random.nextBoolean() ? NightmareType.CHASE : NightmareType.MULTIPLYING;
            case 3 -> NightmareType.ULTIMATE_TERROR;
            default -> NightmareType.WHISPERS;
        };
    }

    /**
     * 悪夢の実行
     */
    private static void executeNightmare(ServerPlayer player, ServerLevel level, NightmareType nightmare) {
        switch (nightmare) {
            case WHISPERS:
                player.sendSystemMessage(Component.literal("「お前を見つけた...」")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

    break;

            case SHADOWS:
                player.sendSystemMessage(Component.literal("影が蠢いている...")
                        .withStyle(ChatFormatting.BLACK, ChatFormatting.ITALIC));
                spawnShadowParticles(player);
                break;

            case CHASE:
                player.sendSystemMessage(Component.literal("何かが追いかけてくる！")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT,
                        SoundSource.HOSTILE, 0.6F, 1.2F);
                break;

            case MULTIPLYING:
                player.sendSystemMessage(Component.literal("数が増えている... どんどん増えている...")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
                spawnMultiplyingEffects(player);
                break;

            case ULTIMATE_TERROR:
                player.sendSystemMessage(Component.literal("§k§l████ 絶対的恐怖 ████")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                spawnUltimateTerrrorEffects(player);
                break;
        }
    }

    // === パーティクル効果メソッド ===

    private static void spawnShortSleepEffects(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                5, 0.5, 0.5, 0.5, 0.02);
    }

    private static void spawnDayAdvanceEffects(ServerPlayer player, ServerLevel level, int newDay) {
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        switch (newDay) {
            case 2:
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 20, 1.0, 1.0, 1.0, 0.05);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 0.5F, 1.5F);
                break;

            case 3:
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 30, 1.5, 1.5, 1.5, 0.08);
                level.sendParticles(ParticleTypes.WITCH, x, y, z, 15, 1.0, 1.0, 1.0, 0.05);
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN,
                        SoundSource.HOSTILE, 0.8F, 0.8F);
                break;
        }
    }

    private static void spawnPeacefulWakeUpEffects(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                3, 0.3, 0.3, 0.3, 0.02);
    }

    private static void spawnShadowParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 2.0, 1.0, 2.0, 0.1);
    }

    private static void spawnMultiplyingEffects(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 10; i++) {
            double angle = (i / 10.0) * 2 * Math.PI;
            double radius = 2.0 + i * 0.3;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    x, player.getY() + 1.0, z,
                    1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private static void spawnUltimateTerrrorEffects(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, 3.0, 2.0, 3.0, 0.2);
        level.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                25, 2.0, 1.5, 2.0, 0.15);
    }

    // === ユーティリティメソッド ===

    private static ChatFormatting getColorForDay(int dayNumber) {
        return switch (dayNumber) {
            case 1 -> ChatFormatting.YELLOW;
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
    }

    private static Component createDayAdvanceWarning(int dayNumber) {
        String warning = switch (dayNumber) {
            case 2 -> "TungSahurが強くなりました...";
            case 3 -> "最終的な脅威が解き放たれました...";
            default -> "何かが変わりました...";
        };

        return Component.literal(warning).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
    }

    private static void updateAllTungSahurForNewDay(ServerLevel level, int newDay) {
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
            entity.setDayNumber(newDay);
        }

        TungSahurMod.LOGGER.info("{}体のTungSahurエンティティを{}日目に更新", entities.size(), newDay);
    }

    private static void notifyOtherPlayersOfDayAdvance(ServerLevel level, ServerPlayer sleeper, int newDay) {
        Component message = Component.literal(sleeper.getName().getString() + " の睡眠により " + newDay + "日目になりました")
                .withStyle(getColorForDay(newDay));

        for (ServerPlayer player : level.getPlayers(p -> !p.equals(sleeper))) {
            player.sendSystemMessage(message);
        }
    }

    // === 内部クラス ===

    private static class SleepSession {
        final long startTime;
        final BlockPos bedPos;
        final int dayAtSleepStart;
        final double nightmareChance;

        SleepSession(long startTime, BlockPos bedPos, int dayAtSleepStart, double nightmareChance) {
            this.startTime = startTime;
            this.bedPos = bedPos;
            this.dayAtSleepStart = dayAtSleepStart;
            this.nightmareChance = nightmareChance;
        }
    }

    private enum NightmareType {
        WHISPERS,
        SHADOWS,
        CHASE,
        MULTIPLYING,
        ULTIMATE_TERROR
    }

    // === 統計・デバッグメソッド ===

    public static void logSleepStatistics() {
        TungSahurMod.LOGGER.info("=== BedSleepEvent統計 ===");
        TungSahurMod.LOGGER.info("アクティブ睡眠セッション数: {}", activeSleepSessions.size());
        TungSahurMod.LOGGER.info("記録済みプレイヤー数: {}", lastSleepAttempt.size());
        TungSahurMod.LOGGER.info("========================");
    }
}