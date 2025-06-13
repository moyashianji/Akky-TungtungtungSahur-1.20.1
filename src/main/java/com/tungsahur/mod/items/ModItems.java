package com.tungsahur.mod.items;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TungSahurMod.MODID);

    public static final RegistryObject<Item> TUNG_SAHUR_BAT = ITEMS.register("tung_sahur_bat",
            () -> new TungSahurBatItem(new Item.Properties()
                    .durability(250)
                    ));

    public static final RegistryObject<Item> TUNG_SAHUR_SPAWN_EGG = ITEMS.register("tung_sahur_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TUNG_SAHUR, 0x8B4513, 0x2F1B14,
                    new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}