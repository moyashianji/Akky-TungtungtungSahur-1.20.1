// BedSleepEvent.java - ゲーム状態に対応した睡眠制御
package com.tungsahur.mod.events;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import com.tungsahur.mod.saveddata.GameStateManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class BedSleepEvent {

    // 恐怖メッセージのプール
    private static final List<String> SCARY_MESSAGES = Arrays.asList(
            "なんか嫌な夢を見そうな気がする...",
            "今日は寝るのやめておこう",
            "ベッドに触れた瞬間、背筋に悪寒が走る",
            "何かに見られているような気がする...",
            "闇の奥から何かがこちらを見ている",
            "寝てはいけない... 絶対に寝てはいけない",
            "夢の中で「あいつ」に捕まってしまう",
            "眠りは死への入り口だ...",
            "今夜、悪夢があなたを待っている",
            "ベッドの下から何かが這い出してきそうだ",
            "眠った途端に襲われる予感がする",
            "この静寂... 何かが間違っている",
            "目を閉じたら最後、もう目覚めないかもしれない",
            "今夜だけは眠らない方がいい",
            "夢の中で追いかけられる姿が見える..."
    );

    // 日数に応じた特別メッセージ
    private static final Map<Integer, List<String>> DAY_SPECIFIC_MESSAGES = Map.of(
            1, Arrays.asList(
                    "1日目... まだ始まったばかりだというのに",
                    "今夜から悪夢の日々が始まる",
                    "眠れば眠るほど「奴ら」は強くなる"
            ),
            2, Arrays.asList(
                    "2日目... もう戻れない",
                    "昨夜の悪夢がまた蘇る",
                    "眠るたびに恐怖は増していく"
            ),
            3, Arrays.asList(
                    "3日目... 終わりの始まり",
                    "今夜眠れば、すべてが終わる",
                    "最後の夜になるかもしれない"
            )
    );

    // TungSahurからのささやき
    private static final List<String> TUNG_SAHUR_WHISPERS = Arrays.asList(
            "§k§o「見つけた...」",
            "§k§o「逃がさない...」",
            "§k§o「今夜が最後だ...」",
            "§k§o「眠るな... 眠るな... 眠るな...」",
            "§k§o「夢の中で待っている...」",
            "§k§o「お前の恐怖が美味しい...」",
            "§k§o「もうすぐそこまで来ている...」"
    );

    private static final Random RANDOM = new Random();

    /**
     * プレイヤーがベッドに触れた時 - ゲーム状態に応じて睡眠制御
     */
    @SubscribeEvent
    public static void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        GameStateManager gameState = GameStateManager.get(level);

        // ゲーム終了後は睡眠を許可
        if (gameState.isSleepAllowed()) {
            // 睡眠成功メッセージ
            player.sendSystemMessage(Component.literal("§a3日間の恐怖が終わり、ついに安らかに眠ることができる...")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
            return; // 通常の睡眠処理を続行
        }

        // ゲーム中は睡眠を阻止
        if (gameState.isGameActive()) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);

            // 恐怖演出を実行
            executeTerrifyingSleepPrevention(player, level, gameState.getCurrentDay(), event.getPos());

            TungSahurMod.LOGGER.debug("プレイヤー {} の睡眠を阻害: {}日目",
                    player.getName().getString(), gameState.getCurrentDay());
        }
    }

    /**
     * 恐怖の睡眠阻害演出
     */
    private static void executeTerrifyingSleepPrevention(ServerPlayer player, ServerLevel level,
                                                         int dayNumber, BlockPos bedPos) {
        // メッセージ表示
        displayScaryMessage(player, dayNumber);

        // 恐怖の音響効果
        playTerrifyingSounds(player, level, bedPos);

        // 不気味なパーティクル効果
        spawnOminousParticles(level, bedPos, player);

        // TungSahurのささやき（確率的）
        if (RANDOM.nextFloat() < 0.4f) {
            scheduleWhisper(player, level);
        }

        // 周囲の環境を不気味に変化
        createScaryEnvironment(level, bedPos, dayNumber);

        // TungSahurエンティティの反応
        alertNearbyTungSahur(level, player, bedPos);
    }

    /**
     * 恐怖メッセージの表示
     */
    private static void displayScaryMessage(ServerPlayer player, int dayNumber) {
        // 日数特有のメッセージがあるかチェック
        if (DAY_SPECIFIC_MESSAGES.containsKey(dayNumber) && RANDOM.nextFloat() < 0.6f) {
            List<String> dayMessages = DAY_SPECIFIC_MESSAGES.get(dayNumber);
            String message = dayMessages.get(RANDOM.nextInt(dayMessages.size()));
            player.sendSystemMessage(Component.literal(message)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            // 通常の恐怖メッセージ
            String message = SCARY_MESSAGES.get(RANDOM.nextInt(SCARY_MESSAGES.size()));
            player.sendSystemMessage(Component.literal(message)
                    .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
    }

    /**
     * 恐怖の音響効果
     */
    private static void playTerrifyingSounds(ServerPlayer player, ServerLevel level, BlockPos bedPos) {
        // ランダムな恐怖音を再生
        var sounds = Arrays.asList(
                SoundEvents.AMBIENT_CAVE,
                SoundEvents.WITHER_AMBIENT,
                SoundEvents.ENDERMAN_SCREAM,
                SoundEvents.GHAST_AMBIENT,
                SoundEvents.ZOMBIE_AMBIENT
        );

        var selectedSound = sounds.get(RANDOM.nextInt(sounds.size()));

 }

    /**
     * 不気味なパーティクル効果
     */
    private static void spawnOminousParticles(ServerLevel level, BlockPos bedPos, ServerPlayer player) {
        // ベッド周囲にダークパーティクル
        for (int i = 0; i < 15; i++) {
            double x = bedPos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0;
            double y = bedPos.getY() + 0.5 + RANDOM.nextDouble();
            double z = bedPos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0;

            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1,
                    0.1, 0.1, 0.1, 0.02);
        }

        // プレイヤー周囲に煙パーティクル
        for (int i = 0; i < 8; i++) {
            double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 1.5;
            double y = player.getY() + RANDOM.nextDouble() * 1.8;
            double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 1.5;

            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1,
                    0.0, 0.0, 0.0, 0.01);
        }
    }

    /**
     * TungSahurのささやき演出をスケジュール
     */
    private static void scheduleWhisper(ServerPlayer player, ServerLevel level) {
        // 2-5秒後にささやきを実行
        int delay = 40 + RANDOM.nextInt(80); // 2-6秒

        level.getServer().execute(() -> {
            if (player.isAlive() && !player.isRemoved()) {
                String whisper = TUNG_SAHUR_WHISPERS.get(RANDOM.nextInt(TUNG_SAHUR_WHISPERS.size()));
                player.sendSystemMessage(Component.literal(whisper));

                // ささやきと共に恐怖音
                level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE,
                        SoundSource.HOSTILE, 0.3f, 0.5f);
            }
        });
    }

    /**
     * 周囲の環境を不気味に変化
     */
    private static void createScaryEnvironment(ServerLevel level, BlockPos bedPos, int dayNumber) {
        // 周囲の光源を検索
        List<BlockPos> lightSources = findNearbyLightSources(level, bedPos, 8);

        // 確率で光源を一時的に暗くする演出
        if (!lightSources.isEmpty() && RANDOM.nextFloat() < 0.4f) {
            // 明かりの一部を一時的に消す（演出用）
            BlockPos targetLight = lightSources.get(RANDOM.nextInt(lightSources.size()));

            // 3日目以降は実際にブロックを破壊
            if (dayNumber >= 3) {
                level.destroyBlock(targetLight, false);
                level.playSound(null, targetLight, SoundEvents.GLASS_BREAK,
                        SoundSource.BLOCKS, 0.8f, 0.8f);
            }
        }
    }

    /**
     * 周囲の光源を検索
     */
    private static List<BlockPos> findNearbyLightSources(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> lightSources = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockState(pos).getLightEmission() > 0) {
                        lightSources.add(pos);
                    }
                }
            }
        }

        return lightSources;
    }

    /**
     * 近くのTungSahurエンティティに警告
     */
    private static void alertNearbyTungSahur(ServerLevel level, ServerPlayer player, BlockPos bedPos) {
        AABB searchArea = new AABB(bedPos).inflate(32.0);
        List<TungSahurEntity> nearbyTungSahur = level.getEntitiesOfClass(TungSahurEntity.class, searchArea);

        for (TungSahurEntity tungSahur : nearbyTungSahur) {
            // TungSahurに睡眠試行を通知（より積極的な行動を促す）
            tungSahur.setTarget(player);

            // TungSahurの能力を一時的にブースト
            if (RANDOM.nextFloat() < 0.6f) {
                tungSahur.setSprinting(true);
            }
        }

        TungSahurMod.LOGGER.debug("{}体のTungSahurに睡眠試行を通知", nearbyTungSahur.size());
    }

    /**
     * プレイヤーが目覚めた時（ゲーム終了後のみ発生）
     */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        GameStateManager gameState = GameStateManager.get(level);

        if (gameState.isGameEnded()) {
            // ゲーム終了後の平和な目覚め
            player.sendSystemMessage(Component.literal("§a久しぶりに安らかな朝を迎えることができた...")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));

            // 平和なパーティクル効果
            spawnPeacefulWakeUpEffects(level, player);
        }

        TungSahurMod.LOGGER.debug("プレイヤー {} が目覚め", player.getName().getString());
    }

    /**
     * 平和な目覚め時の演出
     */
    private static void spawnPeacefulWakeUpEffects(ServerLevel level, ServerPlayer player) {
        // 美しいパーティクル効果
        for (int i = 0; i < 20; i++) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX() + (RANDOM.nextDouble() - 0.5) * 2.0,
                    player.getY() + RANDOM.nextDouble() * 2.0,
                    player.getZ() + (RANDOM.nextDouble() - 0.5) * 2.0,
                    1, 0.0, 0.1, 0.0, 0.1);
        }



    }

    // === デバッグ・ユーティリティメソッド ===

    public static void logSleepPreventionStatistics() {
        TungSahurMod.LOGGER.info("=== 睡眠阻害システム統計 ===");
        TungSahurMod.LOGGER.info("恐怖メッセージプール: {}", SCARY_MESSAGES.size());
        TungSahurMod.LOGGER.info("日数別メッセージ: {}", DAY_SPECIFIC_MESSAGES.size());
        TungSahurMod.LOGGER.info("TungSahurささやき: {}", TUNG_SAHUR_WHISPERS.size());
        TungSahurMod.LOGGER.info("========================");
    }
}