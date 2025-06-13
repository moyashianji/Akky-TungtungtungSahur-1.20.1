package com.tungsahur.mod.items;

import com.tungsahur.mod.entity.TungSahurEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

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
    public float getDestroySpeed(ItemStack stack, net.minecraft.world.level.block.state.BlockState state) {
        return 1.0F;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.OAK_PLANKS) ||
                repair.is(net.minecraft.world.item.Items.STICK);
    }
}