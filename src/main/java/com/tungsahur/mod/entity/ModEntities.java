
// ModEntities.java への追加 - エンティティ属性でバット強制装備

package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
     * エンティティスポーン後の装備設定を強制実行
     */
    public static void setupTungSahurEquipment(TungSahurEntity entity) {
        // メインハンドにバットを装備
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

        // 進化段階に応じて強化
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        int stage = entity.getEvolutionStage();
        batStack.getTag().putInt("TungSahurStage", stage);

        switch (stage) {
            case 1 -> {
                batStack.getTag().putBoolean("Bloodstained", true);
                batStack.getTag().putInt("BloodLevel", 1);
            }
            case 2 -> {
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putBoolean("DarkEnergy", true);
                batStack.getTag().putInt("BloodLevel", 3);
                batStack.getTag().putBoolean("SoulBound", true);
            }
        }

        // 装備設定
        entity.setItemSlot(EquipmentSlot.MAINHAND, batStack);
        entity.setDropChance(EquipmentSlot.MAINHAND, 0.15F);

        // 装備スロットを保護（アイテムが消えないように）
        entity.setCanPickUpLoot(false);
    }
}
