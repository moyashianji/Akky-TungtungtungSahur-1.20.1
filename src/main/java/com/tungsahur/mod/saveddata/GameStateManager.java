// GameStateManager.java - 3日目の夜明けでゲーム終了版
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
    private boolean endProcessed = false;

    // 3日目終了チェック用の追加フィールド
    private boolean day3NightStarted = false;
    private long day3NightStartTime = -1;

    public GameStateManager() {
        // デフォルトコンストラクタ
    }

    public GameStateManager(CompoundTag tag) {
        this.gameStarted = tag.getBoolean("GameStarted");
        this.gameEnded = tag.getBoolean("GameEnded");
        this.endProcessed = tag.getBoolean("EndProcessed");
        this.currentDay = tag.getInt("CurrentDay");
        this.gameStartTime = tag.getLong("GameStartTime");
        this.lastNightCheck = tag.getLong("LastNightCheck");
        this.day3NightStarted = tag.getBoolean("Day3NightStarted");
        this.day3NightStartTime = tag.getLong("Day3NightStartTime");
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("GameStarted", this.gameStarted);
        tag.putBoolean("GameEnded", this.gameEnded);
        tag.putBoolean("EndProcessed", this.endProcessed);
        tag.putInt("CurrentDay", this.currentDay);
        tag.putLong("GameStartTime", this.gameStartTime);
        tag.putLong("LastNightCheck", this.lastNightCheck);
        tag.putBoolean("Day3NightStarted", this.day3NightStarted);
        tag.putLong("Day3NightStartTime", this.day3NightStartTime);
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
        this.endProcessed = false;
        this.currentDay = 0;
        this.gameStartTime = level.getDayTime();
        this.lastNightCheck = -1;
        this.day3NightStarted = false;
        this.day3NightStartTime = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲーム開始: 開始時刻={}", this.gameStartTime);
    }

    // === ゲームリセット（修正版） ===
    public void resetGame() {
        this.gameStarted = false;
        this.gameEnded = false;
        this.endProcessed = false;
        this.currentDay = 0;
        this.gameStartTime = 0;
        this.lastNightCheck = -1;
        this.day3NightStarted = false;
        this.day3NightStartTime = -1;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲームリセット完了");
    }

    // === 夜のチェック処理（3日目夜明け終了版） ===
    public void checkNightProgression(ServerLevel level) {
        if (!gameStarted || gameEnded) return;

        long currentTime = level.getDayTime();
        long timeOfDay = currentTime % 24000L;
        long currentGameDay = (currentTime - gameStartTime) / 24000L;

        // 夜の時間帯かチェック（13000tick = 午後7時頃から）
        boolean isNight = timeOfDay >= 13000L;
        // 朝の時間帯かチェック（6000tick = 午前6時頃から）
        boolean isMorning = timeOfDay >= 6000L && timeOfDay < 13000L;

        // === 新しい夜の検出と日数進行 ===
        if (isNight && currentGameDay != lastNightCheck && currentGameDay >= 0) {
            int newDay = (int) currentGameDay + 1;

            // 重複防止：既に同じ日数なら処理しない
            if (newDay <= 3 && newDay > this.currentDay) {
                this.currentDay = newDay;
                this.lastNightCheck = currentGameDay;
                this.setDirty();

                TungSahurMod.LOGGER.info("{}日目の夜が開始 - ゲーム時間: {}", newDay, currentTime);

                // 3日目の夜が開始された時の記録
                if (newDay == 3) {
                    this.day3NightStarted = true;
                    this.day3NightStartTime = currentTime;
                    this.setDirty();
                    TungSahurMod.LOGGER.info("3日目の夜が開始 - 夜明けでゲーム終了予定");
                }
            }
        }

        // === 3日目の夜明けでゲーム終了 ===
        if (this.day3NightStarted && !gameEnded && !endProcessed) {
            // 3日目の夜が開始されてから、次の朝（約11000tick後）になったら終了
            long timeSinceDay3Night = currentTime - this.day3NightStartTime;

            // 条件1: 一定時間経過（最低1つの夜を経験）
            // 条件2: 現在が朝の時間帯
            // 条件3: 3日目の夜開始から十分な時間が経過
            if (timeSinceDay3Night > 10000L && isMorning) {
                endGame();
                TungSahurMod.LOGGER.info("3日目の夜明け - ゲーム終了！（経過時間: {}tick）", timeSinceDay3Night);
            }
        }

        // === 代替終了条件（安全装置） ===
        // もし何らかの理由で上記条件が満たされない場合の保険
        if (this.currentDay >= 3 && currentGameDay >= 3 && !gameEnded && !endProcessed) {
            // 4日目に入ってしまった場合の緊急終了
            if (currentGameDay > 3 || (currentGameDay == 3 && timeOfDay < 6000L)) {
                endGame();
                TungSahurMod.LOGGER.info("代替終了条件により3日間完了 - ゲーム終了");
            }
        }
    }

    // === ゲーム終了（修正版） ===
    public void endGame() {
        if (gameEnded || endProcessed) return; // 重複防止

        this.gameEnded = true;
        this.endProcessed = true;
        this.setDirty();

        TungSahurMod.LOGGER.info("ゲーム終了: 3日間の恐怖が終了");
    }

    // === ゲッター（修正版） ===
    public boolean isGameStarted() {
        return gameStarted;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public boolean isDay3NightStarted() {
        return day3NightStarted;
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
            String dayStatus = String.format("ゲーム中 - %d日目", currentDay);
            if (day3NightStarted) {
                dayStatus += " (3日目夜開始済み - 夜明けで終了)";
            }
            return dayStatus + " - 睡眠不可";
        }
    }

    // === 詳細デバッグ情報 ===
    public String getDetailedDebugInfo() {
        return String.format(
                "GameState[started=%s, ended=%s, day=%d, day3Night=%s, day3Time=%d]",
                gameStarted, gameEnded, currentDay, day3NightStarted, day3NightStartTime
        );
    }
}