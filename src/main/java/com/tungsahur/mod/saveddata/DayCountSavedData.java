// DayCountSavedData.java - 改良版（コマンド対応）
package com.tungsahur.mod.saveddata;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DayCountSavedData extends SavedData {
    private static final String DATA_NAME = TungSahurMod.MODID + "_day_count";

    private int dayCount = 0; // 0=1日目, 1=2日目, 2=3日目
    private long lastDayTime = -1;
    private boolean isActive = false;
    private boolean gameStarted = false;

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
        data.gameStarted = tag.getBoolean("gameStarted");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("dayCount", dayCount);
        tag.putLong("lastDayTime", lastDayTime);
        tag.putBoolean("isActive", isActive);
        tag.putBoolean("gameStarted", gameStarted);
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
            String dayMessage = switch (dayCount) {
                case 0 -> "§c§l[Tung Sahur] §r§c1日目の夜が始まった...";
                case 1 -> "§c§l[Tung Sahur] §r§62日目の夜が始まった... 彼らが強くなっている...";
                case 2 -> "§4§l[Tung Sahur] §r§43日目の夜が始まった... 最終形態が解禁された...";
                default -> "§c§l[Tung Sahur] §r§c夜が始まった...";
            };

            level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal(dayMessage), false);
        }
    }

    // ゲッター
    public int getDayCount() {
        return dayCount;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean hasGameStarted() {
        return gameStarted;
    }

    public String getDayStatus() {
        return switch (dayCount) {
            case 0 -> "§e1日目";
            case 1 -> "§62日目";
            case 2 -> "§c3日目";
            default -> "§7不明";
        };
    }

    // コマンド用セッター
    public void setDayCount(int count) {
        this.dayCount = Math.max(0, Math.min(2, count));
        setDirty();

        TungSahurMod.LOGGER.info("Day count manually set to: {} (Display: {})",
                this.dayCount, this.dayCount + 1);
    }

    public void start() {
        this.isActive = true;
        this.gameStarted = true;
        this.dayCount = 0; // 1日目から開始
        this.lastDayTime = -1;
        setDirty();

        TungSahurMod.LOGGER.info("Tung Sahur game started");
    }

    public void stop() {
        this.isActive = false;
        setDirty();

        TungSahurMod.LOGGER.info("Tung Sahur game stopped");
    }

    public void reset() {
        this.dayCount = 0;
        this.lastDayTime = -1;
        this.isActive = false;
        this.gameStarted = false;
        setDirty();

        TungSahurMod.LOGGER.info("Tung Sahur game reset");
    }

    /**
     * ゲームが開始されているかをチェック
     * summon時の判定に使用
     */
    public boolean isGameActive() {
        return isActive && gameStarted;
    }

    /**
     * 現在のステージを取得（エンティティ用）
     */
    public int getCurrentStage() {
        if (dayCount >= 2) {
            return 2; // 3日目以降：最終形態
        } else if (dayCount >= 1) {
            return 1; // 2日目：強化形態
        } else {
            return 0; // 1日目：基本形態
        }
    }
}