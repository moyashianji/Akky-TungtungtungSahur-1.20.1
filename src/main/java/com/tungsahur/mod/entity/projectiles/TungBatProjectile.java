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
            // 着弾音
            this.playSound(SoundEvents.WOOD_HIT, 1.0F, 1.0F);

            // パーティクル効果（サーバー側で実行）
            spawnHitParticles();

            if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) result;
                Entity entity = entityHit.getEntity();

                if (entity instanceof LivingEntity livingEntity && this.getOwner() instanceof TungSahurEntity) {
                    // ダメージを与える
                    float damage = 8.0F;
                    livingEntity.hurt(this.damageSources().thrown(this, this.getOwner()), damage);

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

    private void spawnHitParticles() {
        if (!this.level().isClientSide) {
            ItemStack batStack = new ItemStack(getDefaultItem());

            // 破壊パーティクル
            for (int i = 0; i < 8; ++i) {
                double velocityX = (this.random.nextFloat() - 0.5D) * 0.08D;
                double velocityY = (this.random.nextFloat() - 0.5D) * 0.08D;
                double velocityZ = (this.random.nextFloat() - 0.5D) * 0.08D;

                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, batStack),
                        this.getX(), this.getY(), this.getZ(),
                        velocityX, velocityY, velocityZ);
            }

            // 煙パーティクル
            for (int i = 0; i < 5; i++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5) * 2.0;
                double y = this.getY() + this.random.nextDouble();
                double z = this.getZ() + (this.random.nextDouble() - 0.5) * 2.0;

                this.level().addParticle(ParticleTypes.SMOKE,
                        x, y, z, 0.0, 0.1, 0.0);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 3) {
            // クライアント側での破壊パーティクル
            for (int i = 0; i < 8; ++i) {
                ItemStack batStack = new ItemStack(getDefaultItem());
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, batStack),
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

        // 一定時間後に消滅
        if (this.tickCount > 200) { // 10秒
            if (!this.level().isClientSide) {
                this.discard();
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // レンダリング距離を制限
        return distance < 4096.0D; // 64ブロック
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
        return false; // 投擲物はダメージを受けない
    }
}