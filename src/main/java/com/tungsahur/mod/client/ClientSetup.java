package com.tungsahur.mod.client;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.client.renderer.TungBatProjectileRenderer;
import com.tungsahur.mod.client.renderer.TungSahurRenderer;
import com.tungsahur.mod.entity.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // エンティティレンダラー登録
            EntityRenderers.register(ModEntities.TUNG_SAHUR.get(), TungSahurRenderer::new);
            // 投擲物レンダラー登録（カスタムレンダラー使用）
            EntityRenderers.register(ModEntities.TUNG_BAT_PROJECTILE.get(), TungBatProjectileRenderer::new);

        });
    }
}