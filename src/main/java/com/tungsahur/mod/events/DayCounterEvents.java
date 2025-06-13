package com.tungsahur.mod.events;

import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DayCounterEvents {

    private int tickCounter = 0;

    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        // 20tick（1秒）ごとにチェック
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;

            DayCountSavedData data = DayCountSavedData.get(serverLevel);
            data.tick(serverLevel);
        }
    }
}