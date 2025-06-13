package com.tungsahur.mod.items;

import com.tungsahur.mod.client.renderer.BatItemRenderer;
import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class TungSahurBatItem extends Item {

    public TungSahurBatItem(Properties properties) {
        super(properties.rarity(Rarity.UNCOMMON));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target instanceof TungSahurEntity tungSahur) {
            // Tung Sahurに対する特効ダメージ
            target.hurt(attacker.damageSources().playerAttack((Player)attacker), 12.0F);

            // ノックバック効果
            double knockbackStrength = 2.0D;
            double dx = attacker.getX() - target.getX();
            double dz = attacker.getZ() - target.getZ();
            target.knockback(knockbackStrength, dx, dz);

            // 特効時の特殊パーティクル
            if (attacker.level() instanceof ServerLevel serverLevel) {
                spawnSpecialEffectParticles(serverLevel, target);
            }

            // キル数カウント（血痕レベル用）
            incrementKillCount(stack);

            // 耐久度減少
            stack.hurtAndBreak(1, attacker, (entity) -> {
                entity.broadcastBreakEvent(entity.getUsedItemHand());
            });

            return true;
        }

        // 通常のエンティティへのダメージ
        stack.hurtAndBreak(1, attacker, (entity) -> {
            entity.broadcastBreakEvent(entity.getUsedItemHand());
        });

        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemstack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide && livingEntity instanceof Player player) {
            // 使用中のクライアント側パーティクル
            if (remainingUseDuration % 10 == 0) {
                spawnUsageParticles(level, player);
            }
        } else if (!level.isClientSide && livingEntity instanceof Player player) {
            // サーバー側での使用中効果
            if (remainingUseDuration % 20 == 0) {
                spawnServerUsageEffects((ServerLevel) level, player);
            }
        }
    }

    @Override
    public float getDestroySpeed(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        return 1.0F;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.OAK_PLANKS) ||
                repair.is(net.minecraft.world.item.Items.STICK);
    }

    /**
     * キル数をカウントする（血痕レベル計算用）
     */
    private void incrementKillCount(ItemStack stack) {
        if (!stack.hasTag()) {
            stack.getOrCreateTag();
        }
        int currentKills = stack.getTag().getInt("KillCount");
        stack.getTag().putInt("KillCount", currentKills + 1);
    }

    /**
     * Tung Sahur特効時の特殊パーティクル
     */
    private void spawnSpecialEffectParticles(ServerLevel level, LivingEntity target) {
        // 特効ダメージ時の爆発的パーティクル
        level.sendParticles(ParticleTypes.EXPLOSION,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                5, 0.5, 0.5, 0.5, 0.0);

        // 聖なる光のパーティクル
        for (int i = 0; i < 15; i++) {
            double angle = i * Math.PI / 7.5;
            double radius = 2.0;
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            double y = target.getY() + 1.0;

            level.sendParticles(ParticleTypes.END_ROD,
                    x, y, z, 1, 0.0, 0.3, 0.0, 0.0);
        }

        // 特効音
        level.playSound(null, target.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /**
     * 使用中のクライアント側パーティクル
     */
    private void spawnUsageParticles(Level level, Player player) {
        // プレイヤー周りの威圧オーラ
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2.0 / 3.0 + level.getGameTime() * 0.1;
            double radius = 1.5;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + 1.0;

            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 0.0, 0.05, 0.0);
        }

        // バット周りのエネルギー
        double batX = player.getX() + Math.sin(Math.toRadians(-player.getYRot())) * 0.8;
        double batZ = player.getZ() + Math.cos(Math.toRadians(-player.getYRot())) * 0.8;
        double batY = player.getY() + 1.2;

        level.addParticle(ParticleTypes.ENCHANT,
                batX, batY, batZ, 0.0, 0.0, 0.0);
    }

    /**
     * 使用中のサーバー側効果
     */
    private void spawnServerUsageEffects(ServerLevel level, Player player) {
        // 使用中の威圧効果
        level.sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                5, 1.0, 0.5, 1.0, 0.02);

        // 使用音
        level.playSound(null, player.blockPosition(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.3F, 1.5F);
    }

    /**
     * クライアント側でのカスタムレンダラー登録
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new BatItemRenderer();
            }
        });
    }
    /**
     * Tung Sahurが持っている時の特殊プロパティ処理
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        // Tung Sahurが持っている時の特殊処理
        if (entity instanceof TungSahurEntity tungSahur) {
            updateTungSahurBatProperties(stack, tungSahur, level);
        }
    }

    /**
     * Tung Sahur用バットプロパティ更新
     */
    private void updateTungSahurBatProperties(ItemStack stack, TungSahurEntity tungSahur, Level level) {
        if (!stack.hasTag()) {
            stack.getOrCreateTag();
        }

        // 進化段階に応じた自動強化
        int evolutionStage = tungSahur.getEvolutionStage();
        stack.getTag().putInt("TungSahurStage", evolutionStage);

        // 時間経過による自動キル数増加（恐怖演出）
        if (level.getGameTime() % 1200 == 0) { // 1分ごと
            int currentKills = stack.getTag().getInt("KillCount");
            stack.getTag().putInt("KillCount", currentKills + 1);
        }

        // 特殊状態の設定
        switch (evolutionStage) {
            case 0 -> {
                // 基本状態
                stack.getTag().putBoolean("BasicTungSahur", true);
            }
            case 1 -> {
                // 血染め状態
                stack.getTag().putBoolean("Bloodstained", true);
                stack.getTag().putInt("BloodLevel", 1);
            }
            case 2 -> {
                // 呪い状態
                stack.getTag().putBoolean("Cursed", true);
                stack.getTag().putBoolean("DarkEnergy", true);
                stack.getTag().putInt("BloodLevel", 3);
                stack.getTag().putBoolean("SoulBound", true);
            }
        }

        // Tung Sahurの近くにプレイヤーがいる時の反応
        if (!level.isClientSide) {
            Player nearestPlayer = level.getNearestPlayer(tungSahur, 16.0D);
            if (nearestPlayer != null) {
                stack.getTag().putBoolean("PlayerNearby", true);
                stack.getTag().putLong("LastPlayerSeen", level.getGameTime());

                // 恐怖レベル上昇
                float distance = tungSahur.distanceTo(nearestPlayer);
                int fearLevel = (int) Math.max(1, 5 - (distance / 3.0));
                stack.getTag().putInt("FearLevel", fearLevel);
            } else {
                stack.getTag().putBoolean("PlayerNearby", false);
            }
        }
    }

// ClientSetup.java への追加アイテムプロパティ



// BatItemRenderer.java への Tung Sahur専用レンダリング追加


}