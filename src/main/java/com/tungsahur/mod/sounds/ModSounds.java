package com.tungsahur.mod.sounds;


import com.tungsahur.mod.TungSahurMod;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;



public class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TungSahurMod.MODID);
    public static final RegistryObject<SoundEvent> TUNGTUNGSAHUR = REGISTRY.register("tungtungsahur", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("tungsahurmod", "tungtungsahur")));
}
