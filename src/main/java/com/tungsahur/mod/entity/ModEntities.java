package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
}