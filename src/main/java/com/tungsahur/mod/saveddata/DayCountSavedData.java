package com.tungsahur.mod.saveddata;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DayCountSavedData extends SavedData {
    private static final String DATA_NAME = TungSahurMod.MODID + "_day_count";

    private int dayCount = 0;
    private long lastDayTime = -1;
    private boolean isActive = false;

    public DayCountSavedData() {
        super();
    }

    public static DayCountSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                DayCountSavedData::load,
                DayCountSavedData::new,
                DATA_NAME
        );
    }

    public static DayCountSavedData load(CompoundTag tag) {
        DayCountSavedData data = new DayCountSavedData();
        data.dayCount = tag.getInt("dayCount");
        data.lastDayTime = tag.getLong("lastDayTime");
        data.isActive = tag.getBoolean("isActive");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("dayCount", dayCount);
        tag.putLong("lastDayTime", lastDayTime);
        tag.putBoolean("isActive", isActive);
        return tag;
    }

    public void tick(ServerLevel level) {
        if (!isActive) {
            return;
        }

        long currentDayTime = level.getDayTime();
        long currentDay = currentDayTime / 24000L;

        if (lastDayTime == -1) {
            lastDayTime = currentDay;
        }

        if (currentDay > lastDayTime) {
            dayCount = Math.min(dayCount + 1, 2); // 最大3日目（0,1,2）
            lastDayTime = currentDay;
            setDirty();

            // 日数変更の通知
            level.getServer().getPlayerList().getPlayers().forEach(player -> {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c§l[Tung Sahur] §r§c" + (dayCount + 1) + "日目の夜が始まった..."
                ));
            });
        }
    }

    public int getDayCount() {
        return dayCount;
    }

    public void setDayCount(int dayCount) {
        this.dayCount = Math.max(0, Math.min(dayCount, 2));
        setDirty();
    }

    public void resetDayCount() {
        this.dayCount = 0;
        this.lastDayTime = -1;
        setDirty();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if (active && lastDayTime == -1) {
            // 初回起動時は現在の日を基準にする
            lastDayTime = 0;
        }
        setDirty();
    }

    public String getDayStatus() {
        if (!isActive) {
            return "§7非アクティブ";
        }

        String[] dayNames = {"§a1日目", "§e2日目", "§c3日目"};
        if (dayCount < dayNames.length) {
            return dayNames[dayCount];
        }
        return "§4終了";
    }

    public boolean isGameOver() {
        return dayCount >= 2 && isActive;
    }
}