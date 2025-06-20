// TungSahurBatItem.java - 完全対応版
package com.tungsahur.mod.items;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TungSahurBatItem extends Item {

    public TungSahurBatItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof LivingEntity livingEntity) {
            updateBatProperties(stack, level, livingEntity);

            // エンティティバットの場合の特別処理
            if (isEntityBat(stack) && livingEntity instanceof TungSahurEntity tungSahur) {
                updateEntityBatTick(stack, level, tungSahur);
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        // エンティティバットは使用不可
        if (isEntityBat(itemStack)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("このバットは使用できません...").withStyle(ChatFormatting.DARK_RED));
            }
            return InteractionResultHolder.fail(itemStack);
        }

        // プレイヤーが使用する場合
        if (!level.isClientSide) {
            handlePlayerUse(level, player, itemStack);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // 弓のような使用アニメーション
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // 長時間使用可能
    }



    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (isEntityBat(stack)) {
            return false; // エンティティバットは攻撃に使用不可
        }

        if (attacker instanceof Player player) {
            handlePlayerAttack(stack, target, player);
        }

        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            // エンティティバットの場合
            if (isEntityBat(stack)) {
                addEntityBatTooltip(tooltipComponents, tag);
            } else {
                // 通常バットの場合
                addNormalBatTooltip(tooltipComponents, tag, level);
            }


        } else {
            tooltipComponents.add(Component.literal("不気味なバット...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            // エンチャント効果またはエンティティバット
            return tag.getBoolean("Enchanted") || isEntityBat(stack) || tag.getInt("DayNumber") >= 2;
        }
        return super.isFoil(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return !isEntityBat(stack); // エンティティバットはエンチャント不可
    }

    @Override
    public int getEnchantmentValue() {
        return 1; // 低いエンチャント価値
    }

    // === 内部メソッド ===

    /**
     * バットプロパティの更新
     */
    private void updateBatProperties(ItemStack stack, Level level, LivingEntity holder) {
        CompoundTag tag = stack.getOrCreateTag();

        // 基本情報の更新
        tag.putLong("LastUpdate", level.getGameTime());
        tag.putString("HolderType", holder.getClass().getSimpleName());

        // 持ち主に応じた特殊効果
        if (holder instanceof Player player) {
            updatePlayerBatProperties(tag, player, level);
        } else if (holder instanceof TungSahurEntity tungSahur) {
            updateTungSahurBatProperties(tag, tungSahur, level);
        }

        // 恐怖レベルの計算
        calculateFearLevel(tag, level, holder);
    }

    /**
     * エンティティバットの定期更新
     */
    private void updateEntityBatTick(ItemStack stack, Level level, TungSahurEntity tungSahur) {
        CompoundTag tag = stack.getOrCreateTag();

        // 日数の同期
        int currentDay = tungSahur.getDayNumber();
        if (tag.getInt("DayNumber") != currentDay) {
            tag.putInt("DayNumber", currentDay);
            updateEntityBatForDay(tag, currentDay);
        }

        // 戦闘状態の反映
        updateCombatState(tag, tungSahur);

        // 特殊状態の更新
        updateSpecialStates(tag, tungSahur, level);
    }

    /**
     * プレイヤー使用時の処理
     */
    private void handlePlayerUse(Level level, Player player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        // 使用回数の記録
        int useCount = tag.getInt("PlayerUseCount") + 1;
        tag.putInt("PlayerUseCount", useCount);
        tag.putLong("LastPlayerUse", level.getGameTime());



        // パーティクル効果
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    5, 0.2, 0.2, 0.2, 0.02);
        }

        // 使用回数に応じた警告
        if (useCount >= 5) {
            player.sendSystemMessage(Component.literal("このバットから不吉な力を感じる...").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * プレイヤー攻撃時の処理
     */
    private void handlePlayerAttack(ItemStack stack, LivingEntity target, Player attacker) {
        CompoundTag tag = stack.getOrCreateTag();

        // 攻撃回数の記録
        int attackCount = tag.getInt("PlayerAttackCount") + 1;
        tag.putInt("PlayerAttackCount", attackCount);

        // ダメージ倍率の計算
        float damageMultiplier = 1.0F + (attackCount * 0.1F);
        tag.putFloat("DamageMultiplier", Math.min(damageMultiplier, 2.0F)); // 最大200%

        // 血の記録
        if (target.isDeadOrDying()) {
            int killCount = tag.getInt("KillCount") + 1;
            tag.putInt("KillCount", killCount);
            tag.putBoolean("Bloodstained", true);

            if (killCount >= 10) {
                tag.putBoolean("Cursed", true);
                attacker.sendSystemMessage(Component.literal("バットが呪われた...").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    /**
     * プレイヤーバットプロパティの更新
     */
    private void updatePlayerBatProperties(CompoundTag tag, Player player, Level level) {
        // プレイヤー情報
        tag.putString("PlayerName", player.getName().getString());
        tag.putBoolean("PlayerCreative", player.isCreative());

        // 時間経過による変化
        long gameTime = level.getGameTime();
        long heldTime = gameTime - tag.getLong("FirstHeld");
        if (!tag.contains("FirstHeld")) {
            tag.putLong("FirstHeld", gameTime);
        }

        // 長時間保持による変化
        if (heldTime > 24000) { // 1日以上
            tag.putBoolean("PlayerOwned", true);
            tag.putInt("OwnershipLevel", (int) (heldTime / 24000));
        }
    }

    /**
     * TungSahurバットプロパティの更新
     */
    private void updateTungSahurBatProperties(CompoundTag tag, TungSahurEntity tungSahur, Level level) {
        // エンティティ情報
        tag.putBoolean("EntityBat", true);
        tag.putString("OwnerUUID", tungSahur.getUUID().toString());
        tag.putFloat("OwnerScale", tungSahur.getScaleFactor());

        // 戦闘経験
        if (tungSahur.getLastHurtMob() != null) {
            int combatExperience = tag.getInt("CombatExperience") + 1;
            tag.putInt("CombatExperience", combatExperience);
        }
    }

    /**
     * 日数に応じたエンティティバット更新
     */
    private void updateEntityBatForDay(CompoundTag tag, int dayNumber) {
        tag.putInt("DayNumber", dayNumber);

        switch (dayNumber) {
            case 1:
                tag.putString("BatType", "Basic");
                tag.putInt("BaseDamage", 6);
                tag.putFloat("AttackSpeed", 1.0F);
                break;

            case 2:
                tag.putString("BatType", "Enhanced");
                tag.putInt("BaseDamage", 9);
                tag.putFloat("AttackSpeed", 1.2F);
                tag.putBoolean("Enchanted", true);
                break;

            case 3:
                tag.putString("BatType", "Ultimate");
                tag.putInt("BaseDamage", 12);
                tag.putFloat("AttackSpeed", 1.5F);
                tag.putBoolean("Enchanted", true);
                tag.putBoolean("Legendary", true);
                break;
        }
    }

    /**
     * 戦闘状態の更新
     */
    private void updateCombatState(CompoundTag tag, TungSahurEntity tungSahur) {
        tag.putBoolean("InCombat", tungSahur.getTarget() != null);
        tag.putBoolean("Attacking", tungSahur.isCurrentlyAttacking());
        tag.putBoolean("Throwing", tungSahur.isCurrentlyThrowing());
        tag.putBoolean("Jumping", tungSahur.isCurrentlyJumping());
        tag.putBoolean("WallClimbing", tungSahur.isWallClimbing());
        tag.putBoolean("BeingWatched", tungSahur.isBeingWatched());
    }

    /**
     * 特殊状態の更新
     */
    private void updateSpecialStates(CompoundTag tag, TungSahurEntity tungSahur, Level level) {
        // 周囲のプレイヤー検出
        Player nearestPlayer = level.getNearestPlayer(tungSahur, 16.0D);
        if (nearestPlayer != null) {
            tag.putBoolean("PlayerNearby", true);
            tag.putFloat("PlayerDistance", tungSahur.distanceTo(nearestPlayer));
            tag.putLong("LastPlayerSeen", level.getGameTime());
        } else {
            tag.putBoolean("PlayerNearby", false);
        }

        // 環境による変化
        if (level.isNight()) {
            tag.putBoolean("NightTime", true);
            tag.putInt("NightPower", tag.getInt("NightPower") + 1);
        } else {
            tag.putBoolean("NightTime", false);
        }
    }

    /**
     * 恐怖レベルの計算
     */
    private void calculateFearLevel(CompoundTag tag, Level level, LivingEntity holder) {
        int fearLevel = 0;

        // 基本恐怖レベル
        if (isEntityBat(tag)) {
            fearLevel += tag.getInt("DayNumber") * 2;
        }

        // 血による恐怖レベル
        if (tag.getBoolean("Bloodstained")) {
            fearLevel += tag.getInt("KillCount");
        }

        // 呪いによる恐怖レベル
        if (tag.getBoolean("Cursed")) {
            fearLevel += 5;
        }

        // 夜間ボーナス
        if (level.isNight()) {
            fearLevel += 2;
        }

        // プレイヤーが近くにいる場合
        if (tag.getBoolean("PlayerNearby")) {
            fearLevel += 3;
        }

        tag.putInt("FearLevel", Math.min(fearLevel, 20)); // 最大20
    }

    // === ツールチップメソッド ===

    private void addEntityBatTooltip(List<Component> tooltip, CompoundTag tag) {
        int dayNumber = tag.getInt("DayNumber");
        String batType = tag.getString("BatType");

        tooltip.add(Component.literal("§c§lTungSahurのバット").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        tooltip.add(Component.literal("§7日数: §e" + dayNumber + "日目").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7タイプ: §f" + batType).withStyle(ChatFormatting.GRAY));

        if (tag.getBoolean("InCombat")) {
            tooltip.add(Component.literal("§c§l⚔ 戦闘中").withStyle(ChatFormatting.RED));
        }

        if (tag.getBoolean("BeingWatched")) {
            tooltip.add(Component.literal("§5§l👁 監視されている").withStyle(ChatFormatting.DARK_PURPLE));
        }

        tooltip.add(Component.literal("§8このアイテムは使用できません").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private void addNormalBatTooltip(List<Component> tooltip, CompoundTag tag, Level level) {
        tooltip.add(Component.literal("§7不気味なバット").withStyle(ChatFormatting.GRAY));

        int useCount = tag.getInt("PlayerUseCount");
        if (useCount > 0) {
            tooltip.add(Component.literal("§7使用回数: §e" + useCount).withStyle(ChatFormatting.GRAY));
        }

        if (tag.getBoolean("Bloodstained")) {
            tooltip.add(Component.literal("§4§l血に染まっている").withStyle(ChatFormatting.DARK_RED));
        }

        if (tag.getBoolean("Cursed")) {
            tooltip.add(Component.literal("§5§l呪われている").withStyle(ChatFormatting.DARK_PURPLE));
        }

        int fearLevel = tag.getInt("FearLevel");
        if (fearLevel > 0) {
            tooltip.add(Component.literal("§8恐怖度: " + "■".repeat(Math.min(fearLevel, 10))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void addDebugTooltip(List<Component> tooltip, CompoundTag tag) {
        tooltip.add(Component.literal("§6§l=== DEBUG INFO ===").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("§7Entity Bat: §e" + tag.getBoolean("EntityBat")));
        tooltip.add(Component.literal("§7Day Number: §e" + tag.getInt("DayNumber")));
        tooltip.add(Component.literal("§7Fear Level: §e" + tag.getInt("FearLevel")));
        tooltip.add(Component.literal("§7Last Update: §e" + tag.getLong("LastUpdate")));

        if (tag.contains("OwnerUUID")) {
            tooltip.add(Component.literal("§7Owner UUID: §e" + tag.getString("OwnerUUID")));
        }
    }

    // === ユーティリティメソッド ===

    /**
     * エンティティバットかどうかの判定
     */
    public static boolean isEntityBat(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("EntityBat");
    }

    /**
     * エンティティバットかどうかの判定
     */
    public static boolean isEntityBat(CompoundTag tag) {
        return tag != null && tag.getBoolean("EntityBat");
    }

    /**
     * 日数に応じたエンティティバットアイテムの作成
     */
    public static ItemStack createEntityBat(int dayNumber) {
        ItemStack stack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putBoolean("EntityBat", true);
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 63);
        tag.putInt("DayNumber", dayNumber);

        // 日数に応じた設定を適用
        TungSahurBatItem item = (TungSahurBatItem) stack.getItem();
        item.updateEntityBatForDay(tag, dayNumber);

        return stack;
    }
}