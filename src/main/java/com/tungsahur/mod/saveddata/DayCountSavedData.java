// DayCountSavedData.java - 日数管理
package com.tungsahur.mod.saveddata;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DayCountSavedData extends SavedData {
    private static final String DATA_NAME = TungSahurMod.MODID + "_day_count";

    private int dayCount = 1;
    private long lastDayTime = 0;
    private boolean isInitialized = false;

    public DayCountSavedData() {
        // デフォルトコンストラクタ
    }

    public DayCountSavedData(CompoundTag tag) {
        this.dayCount = tag.getInt("DayCount");
        this.lastDayTime = tag.getLong("LastDayTime");
        this.isInitialized = tag.getBoolean("IsInitialized");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DayCount", this.dayCount);
        tag.putLong("LastDayTime", this.lastDayTime);
        tag.putBoolean("IsInitialized", this.isInitialized);
        return tag;
    }

    public static DayCountSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                DayCountSavedData::new,
                DayCountSavedData::new,
                DATA_NAME
        );
    }

    public int getDayCount() {
        return Math.max(1, Math.min(3, this.dayCount)); // 1-3日目に制限
    }

    public void updateDayCount(ServerLevel level) {
        long currentTime = level.getDayTime();
        long currentDay = currentTime / 24000L; // Minecraftの1日は24000tick

        if (!this.isInitialized) {
            // 初回初期化
            this.lastDayTime = currentDay;
            this.dayCount = 1;
            this.isInitialized = true;
            this.setDirty();

            TungSahurMod.LOGGER.info("DayCountSavedData初期化完了: 開始日={}", this.dayCount);
            return;
        }

        if (currentDay > this.lastDayTime) {
            // 日が変わった
            int daysPassed = (int) (currentDay - this.lastDayTime);
            this.dayCount = Math.min(3, this.dayCount + daysPassed); // 最大3日目まで
            this.lastDayTime = currentDay;
            this.setDirty();

            TungSahurMod.LOGGER.info("日数更新: 現在{}日目 (経過日数: {})", this.dayCount, daysPassed);
        }
    }

    public void forceDayCount(int day) {
        this.dayCount = Math.max(1, Math.min(3, day));
        this.setDirty();

        TungSahurMod.LOGGER.info("日数強制設定: {}日目", this.dayCount);
    }

    public void resetDayCount() {
        this.dayCount = 1;
        this.lastDayTime = 0;
        this.isInitialized = false;
        this.setDirty();

        TungSahurMod.LOGGER.info("日数リセット完了");
    }

    public boolean isDay(int targetDay) {
        return this.getDayCount() == targetDay;
    }

    public String getDayDescription() {
        return switch (this.getDayCount()) {
            case 1 -> "1日目 - 基本形態";
            case 2 -> "2日目 - 強化形態";
            case 3 -> "3日目 - 最終形態";
            default -> "不明";
        };
    }
}