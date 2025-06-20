// GameStateManager.java - 新しいゲーム状態管理システム
package com.tungsahur.mod.saveddata;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class GameStateManager extends SavedData {
    private static final String DATA_NAME = TungSahurMod.MODID + "_game_state";

    // ゲーム状態
    private boolean gameStarted = false;
    private boolean gameEnded = false;
    private int currentDay = 0;
    private long gameStartTime = 0;
    private long lastNightCheck = -1;

    public GameStateManager() {
        // デフォルトコンストラクタ
    }

    public GameStateManager(CompoundTag tag) {
        this.gameStarted = tag.getBoolean("GameStarted");
        this.gameEnded = tag.getBoolean("GameEnded");
        this.currentDay = tag.getInt("CurrentDay");
        this.gameStartTime = tag.getLong("GameStartTime");
        this.lastNightCheck = tag.getLong("LastNightCheck");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("GameStarted", this.gameStarted);
        tag.putBoolean("GameEnded", this.gameEnded);
        tag.putInt("CurrentDay", this.currentDay);
        tag.putLong("GameStartTime", this.gameStartTime);
        tag.putLong("LastNightCheck", this.lastNightCheck);
        return tag;
    }

    public static GameStateManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                GameStateManager::new,
                GameStateManager::new,
                DATA_NAME
        );
    }

    // ゲーム開始
    public void startGame(ServerLevel level) {
        this.gameStarted = true;
        this.gameEnded = false;
        this.currentDay = 0;
        this.gameStartTime = level.getDayTime();
        this.lastNightCheck = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲーム開始: 開始時刻={}", this.gameStartTime);
    }

    // ゲームリセット
    public void resetGame() {
        this.gameStarted = false;
        this.gameEnded = false;
        this.currentDay = 0;
        this.gameStartTime = 0;
        this.lastNightCheck = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲームリセット完了");
    }

    // 夜のチェックと日数進行
    public void checkNightProgression(ServerLevel level) {
        if (!gameStarted || gameEnded) return;

        long currentTime = level.getDayTime();
        long timeOfDay = currentTime % 24000L;
        long currentGameDay = (currentTime - gameStartTime) / 24000L;

        // 夜の時間帯かチェック（13000tick = 午後7時頃から）
        boolean isNight = timeOfDay >= 13000L;

        if (isNight && currentGameDay != lastNightCheck && currentGameDay >= 0) {
            // 新しい夜になった
            int newDay = (int) currentGameDay + 1;

            if (newDay <= 3) {
                this.currentDay = newDay;
                this.lastNightCheck = currentGameDay;
                this.setDirty();

                TungSahurMod.LOGGER.info("{}日目の夜が開始", newDay);

                // 3日目の夜が終わったらゲーム終了
                if (newDay == 3) {
                    // 3日目の夜の翌朝（4日目）にゲーム終了
                    scheduleGameEnd(level, currentTime);
                }
            }
        }

        // 4日目の朝になったらゲーム終了
        if (currentGameDay >= 3 && timeOfDay < 13000L && !gameEnded) {
            endGame();
        }
    }

    // ゲーム終了の予約
    private void scheduleGameEnd(ServerLevel level, long currentTime) {
        // 翌朝（4日目）まで約11000tick待機
        TungSahurMod.LOGGER.info("ゲーム終了を予約: 4日目の朝に終了");
    }

    // ゲーム終了
    public void endGame() {
        this.gameEnded = true;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲーム終了: 3日間の恐怖が終了");
    }

    // ゲッター
    public boolean isGameStarted() {
        return gameStarted;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    // 睡眠が許可されているかチェック
    public boolean isSleepAllowed() {
        return gameEnded;
    }

    // ゲーム中かどうか
    public boolean isGameActive() {
        return gameStarted && !gameEnded;
    }

    // デバッグ情報
    public String getGameStatus() {
        if (!gameStarted) {
            return "ゲーム開始前";
        } else if (gameEnded) {
            return "ゲーム終了 - 睡眠可能";
        } else {
            return String.format("ゲーム中 - %d日目", currentDay);
        }
    }
}