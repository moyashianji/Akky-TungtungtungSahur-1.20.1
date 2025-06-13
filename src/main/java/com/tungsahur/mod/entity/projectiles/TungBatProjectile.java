// TungBatProjectile.java - 投擲物に軌跡パーティクル追加
package com.tungsahur.mod.entity.projectiles;

import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
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
    public void tick() {
        super.tick();

        // 飛行中の軌跡パーティクル
        if (!this.level().isClientSide) {
            spawnTrailParticles();
        }

        // 一定時間後に消滅
        if (this.tickCount > 200) { // 10秒
            if (!this.level().isClientSide) {
                this.discard();
            }
        }
    }

    private void spawnTrailParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            // 火の軌跡
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY(), this.getZ(),
                    2, 0.1, 0.1, 0.1, 0.02);

            // 煙の軌跡
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.05, 0.05, 0.05, 0.01);

            // 暗黒エネルギーの軌跡
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            // 回転に合わせたスパーク
            if (this.tickCount % 3 == 0) {
                double angle = this.tickCount * 0.5;
                double offsetX = Math.cos(angle) * 0.3;
                double offsetZ = Math.sin(angle) * 0.3;

                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.getX() + offsetX, this.getY(), this.getZ() + offsetZ,
                        1, 0.0, 0.0, 0.0, 0.1);
            }

            // 速度が高い時の追加パーティクル
            double speed = this.getDeltaMovement().length();
            if (speed > 0.5) {
                for (int i = 0; i < 3; i++) {
                    double trailX = this.getX() - this.getDeltaMovement().x * i * 0.3;
                    double trailY = this.getY() - this.getDeltaMovement().y * i * 0.3;
                    double trailZ = this.getZ() - this.getDeltaMovement().z * i * 0.3;

                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            trailX, trailY, trailZ, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            // 着弾音
            this.playSound(SoundEvents.WOOD_HIT, 1.0F, 1.0F);
            this.playSound(SoundEvents.GENERIC_EXPLODE, 0.5F, 1.5F);

            // 着弾パーティクル効果
            spawnImpactParticles();

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

                    // エンティティヒット時の追加パーティクル
                    spawnEntityHitParticles(livingEntity);
                }
            }

            this.discard();
        }
    }

    private void spawnImpactParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            ItemStack batStack = new ItemStack(getDefaultItem());

            // 中心爆発
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(), 2, 0.3, 0.3, 0.3, 0.0);

            // 破壊パーティクル
            for (int i = 0; i < 15; ++i) {
                double velocityX = (this.random.nextFloat() - 0.5D) * 0.3D;
                double velocityY = this.random.nextFloat() * 0.4D + 0.1D;
                double velocityZ = (this.random.nextFloat() - 0.5D) * 0.3D;

                serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, batStack),
                        this.getX(), this.getY(), this.getZ(),
                        1, velocityX, velocityY, velocityZ, 0.1);
            }

            // 放射状の火花
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                double velocityX = Math.cos(angle) * 0.6;
                double velocityZ = Math.sin(angle) * 0.6;

                serverLevel.sendParticles(ParticleTypes.FLAME,
                        this.getX(), this.getY(), this.getZ(),
                        1, velocityX, 0.3, velocityZ, 0.1);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.getX(), this.getY(), this.getZ(),
                        1, velocityX * 0.5, 0.2, velocityZ * 0.5, 0.0);
            }

            // 煙の雲
            for (int i = 0; i < 10; i++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5) * 2.0;
                double y = this.getY() + this.random.nextDouble();
                double z = this.getZ() + (this.random.nextDouble() - 0.5) * 2.0;

                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, y, z, 1, 0.0, 0.2, 0.0, 0.05);
            }

            // 地面のクラック風パーティクル
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double x = this.getX() + Math.cos(angle) * 1.5;
                double z = this.getZ() + Math.sin(angle) * 1.5;

                serverLevel.sendParticles(ParticleTypes.LAVA,
                        x, this.getY(), z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void spawnEntityHitParticles(LivingEntity target) {
        if (level() instanceof ServerLevel serverLevel) {
            // 血しぶき風パーティクル
            for (int i = 0; i < 20; i++) {
                double velocityX = (serverLevel.random.nextDouble() - 0.5) * 0.8;
                double velocityY = serverLevel.random.nextDouble() * 0.8 + 0.2;
                double velocityZ = (serverLevel.random.nextDouble() - 0.5) * 0.8;

                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                        1, velocityX, velocityY, velocityZ, 0.0);
            }

            // ターゲット周りの衝撃波
            for (int ring = 1; ring <= 3; ring++) {
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    double radius = ring * 0.5;
                    double x = target.getX() + Math.cos(angle) * radius;
                    double z = target.getZ() + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            x, target.getY() + 0.5, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }

            // 暗黒エネルギーの爆発
            for (int i = 0; i < 15; i++) {
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
                double offsetY = serverLevel.random.nextDouble() * 2.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;

                serverLevel.sendParticles(ParticleTypes.SOUL,
                        target.getX() + offsetX, target.getY() + offsetY, target.getZ() + offsetZ,
                        1, 0.0, 0.3, 0.0, 0.0);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
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


