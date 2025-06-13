package com.tungsahur.mod.entity.projectiles;

import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

public class TungBatProjectile extends ThrowableItemProjectile {

    public TungBatProjectile(EntityType<? extends TungBatProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public TungBatProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.TUNG_BAT_PROJECTILE.get(), shooter, level);
    }

    public TungBatProjectile(Level level, double x, double y, double z) {
        super(ModEntities.TUNG_BAT_PROJECTILE.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.TUNG_SAHUR_BAT.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            // パーティクル効果
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem())),
                    this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);

            // 着弾音
            this.playSound(SoundEvents.WOOD_HIT, 1.0F, 1.0F);

            if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) result;
                Entity entity = entityHit.getEntity();

                if (entity instanceof LivingEntity livingEntity && this.getOwner() instanceof TungSahurEntity) {
                    // ダメージを与える
                    livingEntity.hurt(this.damageSources().thrown(this, this.getOwner()), 8.0F);

                    // 恐怖効果を追加
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 1));
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                    livingEntity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 1));

                    // ノックバック
                    if (this.getOwner() != null) {
                        double dx = this.getOwner().getX() - livingEntity.getX();
                        double dz = this.getOwner().getZ() - livingEntity.getZ();
                        livingEntity.knockback(1.5F, dx, dz);
                    }
                }
            }

            this.discard();
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 3) {
            // 破壊時のパーティクル
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem())),
                        this.getX(), this.getY(), this.getZ(),
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D,
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D,
                        ((double) this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected float getGravity() {
        return 0.03F;
    }

    @Override
    public void tick() {
        super.tick();

        // 回転効果
        this.setYRot(this.getYRot() + 20.0F);
        this.setXRot(this.getXRot() + 20.0F);

        // 一定時間後に消滅
        if (this.tickCount > 200) {
            this.discard();
        }
    }
}