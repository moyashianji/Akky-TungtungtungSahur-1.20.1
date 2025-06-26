// TungSahurBatItem.java - プレイヤー投擲＆近接攻撃対応版
package com.tungsahur.mod.items;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TungSahurBatItem extends Item {

    // 投擲クールダウン（ティック）
    private static final int THROW_COOLDOWN = 60; // 3秒
    // 近接攻撃のダメージ
    private static final float BASE_ATTACK_DAMAGE = 6.0F;

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

            // プレイヤーの場合はクールダウン管理
            if (livingEntity instanceof Player player) {
                updatePlayerCooldowns(stack, level, player);
            }
        }
    }

    /**
     * 右クリック使用（バット投げ）
     */
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

        // クールダウンチェック
        if (isOnThrowCooldown(itemStack, level)) {
            if (!level.isClientSide) {
                int remainingCooldown = getRemainingThrowCooldown(itemStack, level);
                player.sendSystemMessage(Component.literal("投擲クールダウン中... " + (remainingCooldown / 20 + 1) + "秒")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResultHolder.fail(itemStack);
        }

        // プレイヤーがバットを投げる
        if (!level.isClientSide) {
            throwPlayerBat(level, player, itemStack);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    /**
     * 左クリック攻撃（近接攻撃）
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (isEntityBat(stack)) {
            return false; // エンティティバットは攻撃に使用不可
        }

        if (attacker instanceof Player player) {
            // プレイヤーの近接攻撃処理
            handlePlayerMeleeAttack(stack, target, player);
        }

        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * プレイヤーによるバット投擲
     */
    private void throwPlayerBat(Level level, Player player, ItemStack stack) {
        TungBatProjectile projectile = new TungBatProjectile(level, player);

        // ★弱体化された設定★
        projectile.setThrowerDayNumber(Math.min(getPlayerBatPower(stack), 2)); // 最大Day2相当まで
        projectile.setDamageMultiplier(0.4F); // 1.0F → 0.4F (60%削減)
        projectile.setHoming(false);
        projectile.setExplosive(false);

        Vec3 lookDirection = player.getLookAngle();
        double throwSpeed = 1.0D + (getPlayerBatPower(stack) * 0.1D); // 1.5D + 0.3D → 1.0D + 0.1D

        projectile.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        projectile.shoot(lookDirection.x, lookDirection.y + 0.1D, lookDirection.z, (float) throwSpeed, 1.0F);

        level.addFreshEntity(projectile);

        // 音量削減
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS,
                0.5F, 1.0F + (getPlayerBatPower(stack) * 0.05F)); // 0.8F → 0.5F

        if (level instanceof ServerLevel serverLevel) {
            // パーティクル削減
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getEyeY(), player.getZ(),
                    4, 0.2, 0.2, 0.2, 0.05); // 8 → 4
        }

        setThrowCooldown(stack, level);
        handlePlayerThrow(level, player, stack);

        TungSahurMod.LOGGER.debug("プレイヤー {} が弱体化バットを投擲", player.getName().getString());
    }

    /**
     * プレイヤーの近接攻撃処理
     */
    private void handlePlayerMeleeAttack(ItemStack stack, LivingEntity target, Player attacker) {
        CompoundTag tag = stack.getOrCreateTag();

        int attackCount = tag.getInt("PlayerAttackCount") + 1;
        tag.putInt("PlayerAttackCount", attackCount);

        // ★弱体化されたダメージ★
        float extraDamage = calculatePlayerMeleeDamage(stack);

        if (extraDamage > 0) {
            target.hurt(attacker.damageSources().playerAttack(attacker), extraDamage);
        }

        // 特殊効果も弱体化
        applyMeleeEffects(stack, target, attacker);

        // キル記録（条件をより厳しく）
        if (target.isDeadOrDying()) {
            int killCount = tag.getInt("KillCount") + 1;
            tag.putInt("KillCount", killCount);

            // 血痕になる条件を厳しく（3キル以上必要）
            if (killCount >= 3) {
                tag.putBoolean("Bloodstained", true);
            }

            // 呪いになる条件をより厳しく（20キル必要）
            if (killCount >= 20) {
                tag.putBoolean("Cursed", true);
                attacker.sendSystemMessage(Component.literal("バットが呪われた...").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        // 音も小さく
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, // CRIT → WEAK
                0.4F, 1.0F + (getPlayerBatPower(stack) * 0.05F)); // 0.7F → 0.4F

        TungSahurMod.LOGGER.debug("プレイヤー {} が {} に弱体化近接攻撃 (ダメージ: {})",
                attacker.getName().getString(), target.getClass().getSimpleName(), extraDamage);
    }

    /**
     * プレイヤーの近接ダメージ計算（Level不要版）
     */
    private float calculatePlayerMeleeDamageFromTag(CompoundTag tag) {
        if (tag == null) return 0.0F;

        float baseDamage = BASE_ATTACK_DAMAGE; // 2.0F

        // 日数による強化削減
        int dayNumber = tag.getInt("DayNumber");
        float dayBonus = dayNumber * 0.5F; // 1.5F → 0.5F

        // 使用経験による強化削減
        int attackCount = tag.getInt("PlayerAttackCount");
        float experienceBonus = Math.min(attackCount * 0.02F, 1.0F); // 0.1F → 0.02F, 3.0F → 1.0F

        // 呪いボーナス削減
        float curseBonus = tag.getBoolean("Cursed") ? 0.5F : 0.0F; // 2.0F → 0.5F

        // 血痕ボーナス削減
        float bloodBonus = tag.getBoolean("Bloodstained") ? 0.3F : 0.0F; // 1.0F → 0.3F

        float totalDamage = baseDamage + dayBonus + experienceBonus + curseBonus + bloodBonus;
        return Math.min(totalDamage, 5.0F); // 最大5ダメージ制限
    }


    /**
     * プレイヤーの近接ダメージ計算
     */
    private float calculatePlayerMeleeDamage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0.0F;

        float baseDamage = BASE_ATTACK_DAMAGE; // 2.0F

        // 日数ボーナス大幅削減
        int dayNumber = tag.getInt("DayNumber");
        float dayBonus = dayNumber * 0.5F; // 1.5F → 0.5F

        // 経験ボーナス削減
        int attackCount = tag.getInt("PlayerAttackCount");
        float experienceBonus = Math.min(attackCount * 0.02F, 1.0F); // 0.1F → 0.02F, 3.0F → 1.0F

        // 特殊効果ボーナス削減
        float curseBonus = tag.getBoolean("Cursed") ? 0.5F : 0.0F; // 2.0F → 0.5F
        float bloodBonus = tag.getBoolean("Bloodstained") ? 0.3F : 0.0F; // 1.0F → 0.3F

        float totalDamage = baseDamage + dayBonus + experienceBonus + curseBonus + bloodBonus;
        return Math.min(totalDamage, 5.0F); // 最大5ダメージ制限
    }


    /**
     * 近接攻撃の特殊効果
     */
    private void applyMeleeEffects(ItemStack stack, LivingEntity target, Player attacker) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        RandomSource random = attacker.level().getRandom();

        // 呪いの特殊効果弱体化
        if (tag.getBoolean("Cursed") && random.nextFloat() < 0.1F) { // 0.3F → 0.1F
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 40, 0)); // 100 → 40
        }

        // 血痕の特殊効果弱体化
        if (tag.getBoolean("Bloodstained") && random.nextFloat() < 0.05F) { // 0.2F → 0.05F
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WITHER, 20, 0)); // 60 → 20
        }

        // パーティクル効果削減
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    2, 0.1, 0.1, 0.1, 0.05); // 5 → 2

            if (tag.getBoolean("Cursed")) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        1, 0.1, 0.1, 0.1, 0.02); // 3 → 1
            }
        }
    }

    /**
     * プレイヤーのクールダウン管理
     */
    private void updatePlayerCooldowns(ItemStack stack, Level level, Player player) {
        CompoundTag tag = stack.getOrCreateTag();

        // クールダウン時間の減少は自動で行われる（ゲーム時間ベース）
        // ここでは表示のためのUIアップデートなどを行う

        if (isOnThrowCooldown(stack, level)) {
            // クールダウン中の視覚効果
            if (level.getGameTime() % 10 == 0 && player.getMainHandItem() == stack) {
                spawnCooldownParticles(level, player);
            }
        }
    }

    /**
     * クールダウン中のパーティクル
     */
    private void spawnCooldownParticles(Level level, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * 投擲処理
     */
    private void handlePlayerThrow(Level level, Player player, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        int throwCount = tag.getInt("PlayerThrowCount") + 1;
        tag.putInt("PlayerThrowCount", throwCount);
        tag.putLong("LastPlayerThrow", level.getGameTime());

        // メッセージの閾値を上げる
        if (throwCount == 5) { // 1 → 5
            player.sendSystemMessage(Component.literal("バットが少し手に馴染んできた...").withStyle(ChatFormatting.GRAY));
        } else if (throwCount >= 50) { // 20 → 50
            player.sendSystemMessage(Component.literal("バットが微かに意志を持っているようだ...").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * プレイヤーバットの威力取得
     */
    private int getPlayerBatPower(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 1;

        int power = Math.min(tag.getInt("DayNumber"), 2); // 最大Day2まで制限
        if (tag.getBoolean("Cursed")) power += 1; // 2 → 1
        if (tag.getBoolean("Bloodstained")) power += 1; // 変更なし

        return Math.min(power, 3); // 最大3（5 → 3）
    }
    // === クールダウン管理 ===

    private boolean isOnThrowCooldown(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;

        long lastThrow = tag.getLong("LastThrowTime");
        long currentTime = level.getGameTime();

        return (currentTime - lastThrow) < THROW_COOLDOWN;
    }

    private int getRemainingThrowCooldown(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;

        long lastThrow = tag.getLong("LastThrowTime");
        long currentTime = level.getGameTime();

        return Math.max(0, (int)(THROW_COOLDOWN - (currentTime - lastThrow)));
    }

    private void setThrowCooldown(ItemStack stack, Level level) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong("LastThrowTime", level.getGameTime());
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            // エンティティバットの場合
            if (isEntityBat(stack)) {
                addEntityBatTooltip(tooltipComponents, tag);
            } else {
                // プレイヤー用バットの場合
                addPlayerBatTooltip(tooltipComponents, tag, level);
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

    private void addPlayerBatTooltip(List<Component> tooltip, CompoundTag tag, Level level) {
        tooltip.add(Component.literal("§7恐怖のバット").withStyle(ChatFormatting.GRAY));

        // 基本機能の説明
        tooltip.add(Component.literal("§e右クリック: §fバットを投げる").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("§e左クリック: §f近接攻撃").withStyle(ChatFormatting.YELLOW));

        // クールダウン表示（levelがnullでない場合のみ）
        if (level != null) {
            ItemStack tempStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
            tempStack.setTag(tag.copy());
            if (isOnThrowCooldown(tempStack, level)) {
                int remaining = getRemainingThrowCooldown(tempStack, level);
                tooltip.add(Component.literal("§c投擲クールダウン: " + (remaining / 20 + 1) + "秒").withStyle(ChatFormatting.RED));
            }
        }

        // 統計情報
        int attackCount = tag.getInt("PlayerAttackCount");
        int throwCount = tag.getInt("PlayerThrowCount");
        if (attackCount > 0 || throwCount > 0) {
            tooltip.add(Component.literal("§7攻撃: §e" + attackCount + " §7投擲: §e" + throwCount).withStyle(ChatFormatting.GRAY));
        }

        // ダメージ情報（levelに依存しないメソッドを使用）
        float damage = calculatePlayerMeleeDamageFromTag(tag);
        //tooltip.add(Component.literal("§7近接ダメージ: §e" + String.format("%.1f", damage)).withStyle(ChatFormatting.GRAY));

        // 特殊状態
        if (tag.getBoolean("Bloodstained")) {
            //tooltip.add(Component.literal("§4§l血に染まっている").withStyle(ChatFormatting.DARK_RED));
        }

        if (tag.getBoolean("Cursed")) {
            //tooltip.add(Component.literal("§5§l呪われている").withStyle(ChatFormatting.DARK_PURPLE));
        }

        // 恐怖度
        int fearLevel = tag.getInt("FearLevel");
        if (fearLevel > 0) {
          //  tooltip.add(Component.literal("§8恐怖度: " + "■".repeat(Math.min(fearLevel, 10))).withStyle(ChatFormatting.DARK_GRAY));
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