// TungSahurEvolutionManager.java - 進化管理システム
package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TungSahurMod.MODID)
public class TungSahurEvolutionManager {

    private static int lastKnownDayCount = -1;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        // 5秒毎にチェック（100tick）
        tickCounter++;
        if (tickCounter >= 100) {
            tickCounter = 0;
            checkForDayChanges(serverLevel);
        }
    }

    private static void checkForDayChanges(ServerLevel level) {
        DayCountSavedData dayData = DayCountSavedData.get(level);
        int currentDayCount = dayData.getDayCount();

        // 日数が変更された場合（setdayコマンド等）
        if (lastKnownDayCount != -1 && lastKnownDayCount != currentDayCount) {
            updateAllTungSahurEntities(level, currentDayCount);
        }

        lastKnownDayCount = currentDayCount;
    }

    /**
     * ワールド内の全TungSahurエンティティを現在の日数に応じて更新
     */
    private static void updateAllTungSahurEntities(ServerLevel level, int newDayCount) {
        // ワールド全体からTungSahurエンティティを取得
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(
                        level.getWorldBorder().getMinX(), level.getMinBuildHeight(),
                        level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                        level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                ));

        if (!entities.isEmpty()) {
            TungSahurMod.LOGGER.info("Updating {} TungSahur entities due to day change to day {}",
                    entities.size(), newDayCount + 1);

            for (TungSahurEntity entity : entities) {
                entity.forceUpdateToCurrentDay();
            }
        }
    }

    /**
     * 特定エンティティの強制更新（コマンド用）
     */
    public static void forceUpdateEntity(TungSahurEntity entity) {
        if (entity != null && entity.level() instanceof ServerLevel) {
            entity.forceUpdateToCurrentDay();
        }
    }

    /**
     * 範囲内のTungSahurエンティティを更新
     */
    public static void updateEntitiesInRange(ServerLevel level, double x, double y, double z, double radius) {
        List<TungSahurEntity> entities = level.getEntitiesOfClass(TungSahurEntity.class,
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius,
                        x + radius, y + radius, z + radius));

        for (TungSahurEntity entity : entities) {
            entity.forceUpdateToCurrentDay();
        }
    }
}