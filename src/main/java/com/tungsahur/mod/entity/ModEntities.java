// ModEntities.java - 完全対応版
package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TungSahurMod.MODID);

    // TungSahurエンティティの登録
    public static final RegistryObject<EntityType<TungSahurEntity>> TUNG_SAHUR =
            ENTITY_TYPES.register("tung_sahur", () -> EntityType.Builder.of(TungSahurEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .clientTrackingRange(64) // クライアント追跡範囲
                    .updateInterval(1) // 更新間隔（1tick）
                    .setShouldReceiveVelocityUpdates(true)
                    .build("tung_sahur"));

    // TungBatプロジェクタイルの登録
    public static final RegistryObject<EntityType<TungBatProjectile>> TUNG_BAT_PROJECTILE =
            ENTITY_TYPES.register("tung_bat_projectile", () -> EntityType.Builder.<TungBatProjectile>of(TungBatProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("tung_bat_projectile"));

    /**
     * エンティティタイプを登録
     */
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        TungSahurMod.LOGGER.info("ModEntities登録完了");
    }




    /**
     * エンティティがワールドに参加した時のイベント処理
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof TungSahurEntity tungSahur && !event.getLevel().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();

            try {
                // 日数データの取得と設定
                DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
                dayData.updateDayCount(serverLevel);
                int currentDay = dayData.getDayCount();

                // TungSahurに現在の日数を設定
                tungSahur.setDayNumber(currentDay);

                // バットを装備
                equipDayAppropriateItems(tungSahur, currentDay);

                // スポーン時の初期化
                initializeTungSahurOnSpawn(tungSahur, serverLevel);

                TungSahurMod.LOGGER.info("TungSahur (Day {}) がワールドに参加しました。位置: {}, {}, {}",
                        currentDay, tungSahur.getX(), tungSahur.getY(), tungSahur.getZ());

            } catch (Exception e) {
                TungSahurMod.LOGGER.error("TungSahurのワールド参加処理中にエラー発生: ", e);

                // エラー時のフォールバック処理
                tungSahur.setDayNumber(1); // デフォルトで1日目
                equipDayAppropriateItems(tungSahur, 1);
            }
        }
    }

    /**
     * ワールド読み込み時の処理
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 日数データの初期化
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            dayData.updateDayCount(serverLevel);

            TungSahurMod.LOGGER.info("ワールド読み込み完了: 現在{}日目", dayData.getDayCount());
        }
    }

    /**
     * 日数に応じたアイテム装備
     */
    private static void equipDayAppropriateItems(TungSahurEntity tungSahur, int dayNumber) {
        // バットアイテムの作成と装備
        ItemStack batStack = createDayBat(dayNumber);
        tungSahur.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, batStack);

        // オフハンドは空にする
        tungSahur.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);

        // ドロップ確率の設定（エンティティ用バットはドロップしない）
        tungSahur.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        tungSahur.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0.0F);
    }

    /**
     * 日数に応じたバットアイテムの作成
     */
    private static ItemStack createDayBat(int dayNumber) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        // 基本情報の設定
        tag.putInt("DayNumber", dayNumber);
        tag.putBoolean("EntityBat", true);
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 63); // すべてのツールチップを非表示

        // 日数に応じた特性
        switch (dayNumber) {
            case 1:
                tag.putString("DisplayName", "{\"text\":\"TungSahur's Bat\",\"color\":\"gray\"}");
                tag.putInt("Damage", 0); // 耐久度無限
                break;

            case 2:
                tag.putString("DisplayName", "{\"text\":\"TungSahur's Enhanced Bat\",\"color\":\"yellow\"}");
                tag.putBoolean("Enchanted", true); // グリント効果
                tag.putInt("CustomModelData", 2);
                break;

            case 3:
                tag.putString("DisplayName", "{\"text\":\"TungSahur's Ultimate Bat\",\"color\":\"red\",\"bold\":true}");
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 3);

                // 3日目のバットには特別なLoreを追加
                CompoundTag loreTag = new CompoundTag();
                loreTag.putString("Lore", "[{\"text\":\"The ultimate weapon\",\"color\":\"dark_red\",\"italic\":true}]");
                tag.put("display", loreTag);
                break;
        }

        return batStack;
    }

    /**
     * スポーン時の初期化処理
     */
    private static void initializeTungSahurOnSpawn(TungSahurEntity tungSahur, ServerLevel serverLevel) {
        // 体力を最大値に設定
        tungSahur.setHealth(tungSahur.getMaxHealth());

        // 初期AIステートの設定
        tungSahur.setCurrentlyAttacking(false);
        tungSahur.setCurrentlyThrowing(false);
        tungSahur.setCurrentlyJumping(false);
        tungSahur.setWallClimbing(false);
        tungSahur.setSprinting(false);
        tungSahur.setBeingWatched(false);

        // 初期ターゲット検索の実行
        scheduleInitialTargetSearch(tungSahur);

        // スポーン時のサウンド効果
        playSpawnEffects(tungSahur, serverLevel);
    }

    /**
     * 初期ターゲット検索のスケジュール
     */
    private static void scheduleInitialTargetSearch(TungSahurEntity tungSahur) {
        // 20tick（1秒）後にターゲット検索を開始
        tungSahur.level().scheduleTick(tungSahur.blockPosition(),
                tungSahur.level().getBlockState(tungSahur.blockPosition()).getBlock(),
                20);
    }

    /**
     * スポーン時の効果
     */
    private static void playSpawnEffects(TungSahurEntity tungSahur, ServerLevel serverLevel) {
        // スポーン音
        serverLevel.playSound(null, tungSahur.getX(), tungSahur.getY(), tungSahur.getZ(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.HOSTILE,
                0.8F, 0.8F);

        // スポーンパーティクル
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                tungSahur.getX(), tungSahur.getY() + 1.0, tungSahur.getZ(),
                10, 0.5, 0.5, 0.5, 0.1);

        // 日数に応じた特別な効果
        switch (tungSahur.getDayNumber()) {
            case 2:
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        tungSahur.getX(), tungSahur.getY() + 1.0, tungSahur.getZ(),
                        5, 0.3, 0.3, 0.3, 0.05);
                break;

            case 3:
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        tungSahur.getX(), tungSahur.getY() + 1.0, tungSahur.getZ(),
                        8, 0.4, 0.4, 0.4, 0.08);

                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                        tungSahur.getX(), tungSahur.getY() + 1.5, tungSahur.getZ(),
                        3, 0.2, 0.2, 0.2, 0.02);
                break;
        }
    }

    /**
     * エンティティの統計情報を取得
     */
    public static void logEntityStats() {
        TungSahurMod.LOGGER.info("=== TungSahur エンティティ統計 ===");
        TungSahurMod.LOGGER.info("登録済みエンティティタイプ数: {}", ENTITY_TYPES.getEntries().size());
        TungSahurMod.LOGGER.info("TungSahur ID: {}", TUNG_SAHUR.getId());
        TungSahurMod.LOGGER.info("TungBatProjectile ID: {}", TUNG_BAT_PROJECTILE.getId());
        TungSahurMod.LOGGER.info("================================");
    }
}