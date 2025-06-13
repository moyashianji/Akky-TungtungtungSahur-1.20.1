package com.tungsahur.mod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLE_DRUM_SOUNDS = BUILDER
            .comment("太鼓音を有効にするかどうか")
            .define("enableDrumSounds", true);

    private static final ForgeConfigSpec.DoubleValue SPAWN_RATE_MULTIPLIER = BUILDER
            .comment("Tung Sahurのスポーン率倍率 (0.0-2.0)")
            .defineInRange("spawnRateMultiplier", 1.0, 0.0, 2.0);

    private static final ForgeConfigSpec.IntValue MAX_EVOLUTION_STAGE = BUILDER
            .comment("最大進化段階 (0-2)")
            .defineInRange("maxEvolutionStage", 2, 0, 2);

    private static final ForgeConfigSpec.BooleanValue DISABLE_BED_SLEEP = BUILDER
            .comment("ベッドでの睡眠を無効にするかどうか")
            .define("disableBedSleep", true);

    private static final ForgeConfigSpec.DoubleValue WATCH_SLOWDOWN_FACTOR = BUILDER
            .comment("プレイヤーに見られている時の速度低下率 (0.0-1.0)")
            .defineInRange("watchSlowdownFactor", 0.1, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue THROW_ATTACK_COOLDOWN = BUILDER
            .comment("投擲攻撃のクールダウン時間（tick）")
            .defineInRange("throwAttackCooldown", 120, 20, 600);

    private static final ForgeConfigSpec.DoubleValue JUMP_ATTACK_POWER = BUILDER
            .comment("ジャンプ攻撃の威力倍率")
            .defineInRange("jumpAttackPower", 1.0, 0.5, 3.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enableDrumSounds;
    public static double spawnRateMultiplier;
    public static int maxEvolutionStage;
    public static boolean disableBedSleep;
    public static double watchSlowdownFactor;
    public static int throwAttackCooldown;
    public static double jumpAttackPower;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enableDrumSounds = ENABLE_DRUM_SOUNDS.get();
        spawnRateMultiplier = SPAWN_RATE_MULTIPLIER.get();
        maxEvolutionStage = MAX_EVOLUTION_STAGE.get();
        disableBedSleep = DISABLE_BED_SLEEP.get();
        watchSlowdownFactor = WATCH_SLOWDOWN_FACTOR.get();
        throwAttackCooldown = THROW_ATTACK_COOLDOWN.get();
        jumpAttackPower = JUMP_ATTACK_POWER.get();
    }
}