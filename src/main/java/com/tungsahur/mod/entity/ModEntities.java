// ModEntities.java - バット装備確実実行版
package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TungSahurMod.MODID);

    public static final RegistryObject<EntityType<TungSahurEntity>> TUNG_SAHUR = ENTITY_TYPES.register("tung_sahur",
            () -> EntityType.Builder.of(TungSahurEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.2F)
                    .build("tung_sahur"));

    public static final RegistryObject<EntityType<TungBatProjectile>> TUNG_BAT_PROJECTILE = ENTITY_TYPES.register("tung_bat_projectile",
            () -> EntityType.Builder.<TungBatProjectile>of(TungBatProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .build("tung_bat_projectile"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    /**
     * エンティティがワールドに参加した時のイベント処理
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof TungSahurEntity tungSahur) {
            if (!event.getLevel().isClientSide) {
                // サーバー側で遅延実行してバット装備を確実にする
                ServerLevel serverLevel = (ServerLevel) event.getLevel();

                // 即座に実行
                setupTungSahurEquipment(tungSahur);

                // 1tick後にも実行（確実にするため）
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(1, () -> {
                    setupTungSahurEquipment(tungSahur);
                }));

                // 5tick後にも実行（念のため）
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(5, () -> {
                    setupTungSahurEquipment(tungSahur);
                }));
            }
        }
    }

    /**
     * TungSahur専用装備設定（改良版）
     */
    public static void setupTungSahurEquipment(TungSahurEntity entity) {
        if (entity == null || entity.level().isClientSide) {
            return;
        }

        try {
            // 現在の装備をチェック
            ItemStack currentMainHand = entity.getMainHandItem();

            // バットが装備されていない場合
            if (currentMainHand.isEmpty() || !currentMainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {

                // 新しいバットを作成
                ItemStack batStack = createTungSahurBat(entity.getEvolutionStage());

                // 装備設定
                entity.setItemSlot(EquipmentSlot.MAINHAND, batStack);
                entity.setDropChance(EquipmentSlot.MAINHAND, 0.15F);

                // アイテムピックアップを無効化（バットを失わないように）
                entity.setCanPickUpLoot(false);

                // 装備保護（バットを他のアイテムと交換されないように）
                entity.setGuaranteedDrop(EquipmentSlot.MAINHAND);

                System.out.println("TungSahur equipped with bat: Stage " + entity.getEvolutionStage());
            }
            // バットはあるが進化段階が違う場合
            else if (currentMainHand.hasTag()) {
                int currentStage = currentMainHand.getTag().getInt("TungSahurStage");
                if (currentStage != entity.getEvolutionStage()) {
                    ItemStack newBat = createTungSahurBat(entity.getEvolutionStage());
                    entity.setItemSlot(EquipmentSlot.MAINHAND, newBat);
                    System.out.println("TungSahur bat upgraded to stage: " + entity.getEvolutionStage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error setting up TungSahur equipment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 進化段階に応じたTungSahurバットを作成
     */
    private static ItemStack createTungSahurBat(int evolutionStage) {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        // NBTタグを確実に作成
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        // 基本データ設定
        batStack.getTag().putInt("TungSahurStage", evolutionStage);
        batStack.getTag().putBoolean("TungSahurOwned", true);
        batStack.getTag().putLong("EquippedTime", System.currentTimeMillis());

        // 進化段階に応じた特殊効果
        switch (evolutionStage) {
            case 0 -> {
                // 基本形態
                batStack.getTag().putString("FormType", "Basic");
                batStack.getTag().putInt("BasePower", 100);
            }
            case 1 -> {
                // 血染め形態
                batStack.getTag().putString("FormType", "Bloodstained");
                batStack.getTag().putBoolean("Bloodstained", true);
                batStack.getTag().putInt("KillCount", 10);
                batStack.getTag().putInt("BloodLevel", 1);
                batStack.getTag().putInt("BasePower", 150);
                batStack.getTag().putBoolean("Enhanced", true);
            }
            case 2 -> {
                // 最終形態（魂縛り）
                batStack.getTag().putString("FormType", "SoulBound");
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putInt("KillCount", 25);
                batStack.getTag().putBoolean("DarkEnergy", true);
                batStack.getTag().putInt("BloodLevel", 3);
                batStack.getTag().putBoolean("SoulBound", true);
                batStack.getTag().putInt("BasePower", 200);
                batStack.getTag().putBoolean("FinalForm", true);
                batStack.getTag().putBoolean("Unbreakable", true);
            }
        }

        // 表示プロパティ強制設定
        batStack.getTag().putBoolean("ForceRender", true);
        batStack.getTag().putBoolean("AlwaysVisible", true);

        return batStack;
    }

    /**
     * TungSahurの装備を定期的にチェックするメソッド
     */
    public static void validateTungSahurEquipment(TungSahurEntity entity) {
        if (entity == null || entity.level().isClientSide) {
            return;
        }

        ItemStack mainHand = entity.getMainHandItem();

        // バットが消失している場合は再装備
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            setupTungSahurEquipment(entity);
            return;
        }

        // バットはあるが、NBTが破損している場合
        if (!mainHand.hasTag() || !mainHand.getTag().getBoolean("TungSahurOwned")) {
            setupTungSahurEquipment(entity);
            return;
        }

        // 進化段階の不一致チェック
        int batStage = mainHand.getTag().getInt("TungSahurStage");
        if (batStage != entity.getEvolutionStage()) {
            setupTungSahurEquipment(entity);
        }
    }

    /**
     * デバッグ用：TungSahurの装備状態を出力
     */
    public static void debugTungSahurEquipment(TungSahurEntity entity) {
        if (entity == null) {
            System.out.println("DEBUG: Entity is null");
            return;
        }

        ItemStack mainHand = entity.getMainHandItem();
        System.out.println("=== TungSahur Equipment Debug ===");
        System.out.println("Entity Stage: " + entity.getEvolutionStage());
        System.out.println("Main Hand: " + (mainHand.isEmpty() ? "EMPTY" : mainHand.getItem().toString()));

        if (!mainHand.isEmpty() && mainHand.hasTag()) {
            System.out.println("Bat Stage: " + mainHand.getTag().getInt("TungSahurStage"));
            System.out.println("Is TungSahur Owned: " + mainHand.getTag().getBoolean("TungSahurOwned"));
            System.out.println("Form Type: " + mainHand.getTag().getString("FormType"));
        }
        System.out.println("================================");
    }
}