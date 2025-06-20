// GameStateManager.java - 新しいゲーム状態管理システム
package com.tungsahur.mod.saveddata;

import com.tungsahur.mod.TungSahurMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class GameStateManager extends SavedData {
    private static final String DATA_NAME = TungSahurMod.MODID + "_game_state";

    private boolean gameStarted = false;
    private boolean gameEnded = false;
    private int currentDay = 0;
    private long gameStartTime = 0;
    private long lastNightCheck = -1;

    // 終了処理フラグ（重要！）
    private boolean endProcessed = false;
    public GameStateManager() {
        // デフォルトコンストラクタ
    }

    public GameStateManager(CompoundTag tag) {
        this.gameStarted = tag.getBoolean("GameStarted");
        this.gameEnded = tag.getBoolean("GameEnded");
        this.endProcessed = tag.getBoolean("EndProcessed"); // 追加
        this.currentDay = tag.getInt("CurrentDay");
        this.gameStartTime = tag.getLong("GameStartTime");
        this.lastNightCheck = tag.getLong("LastNightCheck");
    }

    // === NBT保存/読み込み（修正版） ===
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("GameStarted", this.gameStarted);
        tag.putBoolean("GameEnded", this.gameEnded);
        tag.putBoolean("EndProcessed", this.endProcessed); // 追加
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

    // === ゲーム開始（修正版） ===
    public void startGame(ServerLevel level) {
        this.gameStarted = true;
        this.gameEnded = false;
        this.endProcessed = false; // 終了処理フラグをリセット
        this.currentDay = 0;
        this.gameStartTime = level.getDayTime();
        this.lastNightCheck = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲーム開始: 開始時刻={}", this.gameStartTime);
    }

    // === ゲームリセット（修正版） ===
    public void resetGame() {
        this.gameStarted = false;
        this.gameEnded = false;
        this.endProcessed = false; // 終了処理フラグをリセット
        this.currentDay = 0;
        this.gameStartTime = 0;
        this.lastNightCheck = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲームリセット完了");
    }

    // === 夜のチェック処理（終了ループ修正版） ===
    public void checkNightProgression(ServerLevel level) {
        if (!gameStarted || gameEnded) return;

        long currentTime = level.getDayTime();
        long timeOfDay = currentTime % 24000L;
        long currentGameDay = (currentTime - gameStartTime) / 24000L;

        // 夜の時間帯かチェック（13000tick = 午後7時頃から）
        boolean isNight = timeOfDay >= 13000L;

        // 新しい夜の検出（重複防止強化）
        if (isNight && currentGameDay != lastNightCheck && currentGameDay >= 0) {
            int newDay = (int) currentGameDay + 1;

            // 重複防止：既に同じ日数なら処理しない
            if (newDay <= 3 && newDay > this.currentDay) {
                this.currentDay = newDay;
                this.lastNightCheck = currentGameDay;
                this.setDirty();

                TungSahurMod.LOGGER.info("{}日目の夜が開始 - ゲーム時間: {}", newDay, currentTime);

                // 3日目の夜が終わったらゲーム終了準備
                if (newDay == 3) {
                    scheduleGameEnd(level, currentTime);
                }
            }
        }

        // 4日目の朝になったらゲーム終了（一度だけ実行）
        if (currentGameDay >= 3 && timeOfDay < 13000L && !gameEnded &&
                this.currentDay >= 3 && !endProcessed) {
            endGame();
            TungSahurMod.LOGGER.info("4日目の朝 - ゲーム終了");
        }
    }

    // ゲーム終了の予約
    private void scheduleGameEnd(ServerLevel level, long currentTime) {
        // 翌朝（4日目）まで約11000tick待機
        TungSahurMod.LOGGER.info("ゲーム終了を予約: 4日目の朝に終了");
    }

    // === ゲーム終了（修正版） ===
    public void endGame() {
        if (gameEnded || endProcessed) return; // 重複防止

        this.gameEnded = true;
        this.endProcessed = true; // 終了処理完了フラグ
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

    // === 睡眠が許可されているかチェック（修正版） ===
    public boolean isSleepAllowed() {
        return !gameStarted || gameEnded; // ゲーム開始前または終了後は睡眠可能
    }

    // === ゲーム中かどうか（修正版） ===
    public boolean isGameActive() {
        return gameStarted && !gameEnded;
    }


    // === デバッグ情報（強化版） ===
    public String getGameStatus() {
        if (!gameStarted) {
            return "ゲーム開始前 - 睡眠可能";
        } else if (gameEnded) {
            return "ゲーム終了 - 睡眠可能";
        } else {
            return String.format("ゲーム中 - %d日目 - 睡眠不可", currentDay);
        }
    }
}