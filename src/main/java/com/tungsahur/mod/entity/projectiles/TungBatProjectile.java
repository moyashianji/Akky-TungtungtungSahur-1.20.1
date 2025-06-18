// TungBatProjectile.java - 完全対応版
package com.tungsahur.mod.entity.projectiles;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class TungBatProjectile extends ThrowableItemProjectile {

    // データアクセサー
    private static final EntityDataAccessor<Integer> THROWER_DAY_NUMBER =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_HOMING =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE_MULTIPLIER =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.FLOAT);

    // 飛行時間とホーミング設定
    private int maxLifetime = 100; // 5秒
    private LivingEntity homingTarget;
    private int homingStartDelay = 10; // 0.5秒後からホーミング開始
    private float homingStrength = 0.05F;

    public TungBatProjectile(EntityType<? extends TungBatProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(false); // 重力の影響を受ける
    }

    public TungBatProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.TUNG_BAT_PROJECTILE.get(), shooter, level);

        if (shooter instanceof TungSahurEntity tungSahur) {
            this.setThrowerDayNumber(tungSahur.getDayNumber());
            this.setDamageMultiplier(calculateDamageMultiplier(tungSahur.getDayNumber()));
            this.maxLifetime = calculateLifetime(tungSahur.getDayNumber());

            // 3日目のバットはホーミング機能付き
            if (tungSahur.getDayNumber() >= 3) {
                this.setHoming(true);
                this.homingTarget = tungSahur.getTarget();
                this.homingStrength = 0.08F;
            }
        }

        this.setNoGravity(getThrowerDayNumber() >= 2); // 2日目以降は重力無効
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(THROWER_DAY_NUMBER, 1);
        this.entityData.define(IS_HOMING, false);
        this.entityData.define(DAMAGE_MULTIPLIER, 1.0F);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.TUNG_SAHUR_BAT.get();
    }

    @Override
    public void tick() {
        super.tick();

        // 生存時間チェック
        if (this.tickCount > maxLifetime) {
            onExpire();
            return;
        }

        // ホーミング処理
        if (isHoming() && this.tickCount > homingStartDelay && this.homingTarget != null) {
            performHoming();
        }

        // パーティクル軌跡
        spawnTrailParticles();

        // 飛行音（定期的に）
        if (this.tickCount % 8 == 0) {
            playFlightSound();
        }

        // 回転エフェクト更新
        updateRotationEffect();
    }

    private void performHoming() {
        if (this.homingTarget == null || !this.homingTarget.isAlive()) {
            this.setHoming(false);
            return;
        }

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 targetDirection = this.homingTarget.getEyePosition().subtract(this.position()).normalize();

        // 現在の速度を維持しつつ、ターゲット方向に徐々に向かう
        Vec3 newVelocity = currentVelocity.lerp(targetDirection.scale(currentVelocity.length()), this.homingStrength);
        this.setDeltaMovement(newVelocity);

        // ホーミング中のパーティクル
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void spawnTrailParticles() {
        if (this.level().isClientSide) {
            // クライアント側パーティクル
            spawnClientTrailParticles();
        } else if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 2 == 0) {
            // サーバー側パーティクル
            spawnServerTrailParticles(serverLevel);
        }
    }

    private void spawnClientTrailParticles() {
        RandomSource random = this.level().getRandom();

        // 基本軌跡パーティクル
        for (int i = 0; i < 2; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.2;
            double offsetY = (random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (random.nextDouble() - 0.5) * 0.2;

            this.level().addParticle(ParticleTypes.CRIT,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    0, 0, 0);
        }

        // 日数に応じた特別なパーティクル
        switch (getThrowerDayNumber()) {
            case 2:
                if (random.nextFloat() < 0.3F) {
                    this.level().addParticle(ParticleTypes.FLAME,
                            this.getX(), this.getY(), this.getZ(),
                            0, 0, 0);
                }
                break;

            case 3:
                if (random.nextFloat() < 0.4F) {
                    this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            this.getX(), this.getY(), this.getZ(),
                            0, 0, 0);
                }
                if (isHoming() && random.nextFloat() < 0.2F) {
                    this.level().addParticle(ParticleTypes.WITCH,
                            this.getX(), this.getY(), this.getZ(),
                            0, 0, 0);
                }
                break;
        }
    }

    private void spawnServerTrailParticles(ServerLevel serverLevel) {
        // バットアイテムの破片パーティクル
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        ItemParticleOption itemParticle = new ItemParticleOption(ParticleTypes.ITEM, batStack);

        serverLevel.sendParticles(itemParticle,
                this.getX(), this.getY(), this.getZ(),
                1, 0.1, 0.1, 0.1, 0.02);

        // 速度に応じた煙パーティクル
        float speed = (float) this.getDeltaMovement().length();
        if (speed > 0.5F) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void playFlightSound() {
        float pitch = 0.8F + (float) this.getDeltaMovement().length() * 0.4F;
        float volume = 0.3F + getThrowerDayNumber() * 0.1F;

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE,
                volume, pitch);
    }

    private void updateRotationEffect() {
        // 回転エフェクトの更新（レンダラーで使用）
        // 実際の回転計算はクライアント側のレンダラーで処理
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hitEntity = result.getEntity();

        if (hitEntity instanceof LivingEntity livingEntity && hitEntity != this.getOwner()) {
            // ダメージ計算
            float damage = calculateDamage();

            // ダメージ適用
            boolean hitSuccessful = livingEntity.hurt(this.damageSources().thrown(this, this.getOwner()), damage);

            if (hitSuccessful) {
                // 日数に応じた特殊効果
                applyDaySpecificEffects(livingEntity);

                // ノックバック効果
                applyKnockback(livingEntity);

                // ヒット時のパーティクルとサウンド
                spawnHitEffects(livingEntity);

                TungSahurMod.LOGGER.debug("TungBatProjectile ヒット: {} に {}ダメージ",
                        hitEntity.getClass().getSimpleName(), damage);
            }
        }

        super.onHitEntity(result);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        // ヒット時の爆発パーティクル
        if (this.level() instanceof ServerLevel serverLevel) {
            spawnImpactParticles(serverLevel, result.getLocation());
        }

        // ヒット音
        float pitch = 1.0F + (getThrowerDayNumber() - 1) * 0.2F;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ITEM_BREAK, SoundSource.HOSTILE,
                0.8F, pitch);

        // プロジェクタイル削除
        this.discard();
    }

    private float calculateDamage() {
        float baseDamage = switch (getThrowerDayNumber()) {
            case 1 -> 4.0F;
            case 2 -> 6.0F;
            case 3 -> 8.0F;
            default -> 4.0F;
        };

        return baseDamage * getDamageMultiplier();
    }

    private void applyDaySpecificEffects(LivingEntity target) {
        // プレイヤーには効果を付与しない（要求仕様）
        if (target instanceof net.minecraft.world.entity.player.Player) {
            return;
        }

        switch (getThrowerDayNumber()) {
            case 2:
                // 2日目：軽いスロウネス
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                break;

            case 3:
                // 3日目：より強力な効果
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WEAKNESS, 60, 0));
                break;
        }
    }

    private void applyKnockback(LivingEntity target) {
        Vec3 direction = target.position().subtract(this.position()).normalize();
        float knockbackStrength = 0.3F + (getThrowerDayNumber() * 0.2F);

        Vec3 knockback = direction.scale(knockbackStrength);
        target.setDeltaMovement(target.getDeltaMovement().add(
                knockback.x, Math.max(0.1D, knockback.y * 0.5D), knockback.z));
        target.hurtMarked = true;
    }

    private void spawnHitEffects(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // 基本ヒットパーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                hitPos.x, hitPos.y, hitPos.z,
                5, 0.2, 0.2, 0.2, 0.1);

        // 日数に応じた特別なパーティクル
        switch (getThrowerDayNumber()) {
            case 2:
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        hitPos.x, hitPos.y, hitPos.z,
                        2, 0.1, 0.1, 0.1, 0.05);
                break;

            case 3:
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        hitPos.x, hitPos.y, hitPos.z,
                        3, 0.2, 0.2, 0.2, 0.08);

                if (isHoming()) {
                    serverLevel.sendParticles(ParticleTypes.END_ROD,
                            hitPos.x, hitPos.y, hitPos.z,
                            2, 0.1, 0.1, 0.1, 0.02);
                }
                break;
        }
    }

    private void spawnImpactParticles(ServerLevel serverLevel, Vec3 impactPos) {
        // 衝撃パーティクル
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                impactPos.x, impactPos.y, impactPos.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // バットの破片
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        ItemParticleOption itemParticle = new ItemParticleOption(ParticleTypes.ITEM, batStack);

        serverLevel.sendParticles(itemParticle,
                impactPos.x, impactPos.y, impactPos.z,
                8, 0.3, 0.3, 0.3, 0.1);
    }

    private void onExpire() {
        // 時間切れ時の処理
        if (this.level() instanceof ServerLevel serverLevel) {
            spawnExpireEffects(serverLevel);
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ITEM_BREAK, SoundSource.HOSTILE,
                0.5F, 1.5F);

        this.discard();
    }

    private void spawnExpireEffects(ServerLevel serverLevel) {
        // 消失時のパーティクル
        serverLevel.sendParticles(ParticleTypes.POOF,
                this.getX(), this.getY(), this.getZ(),
                5, 0.2, 0.2, 0.2, 0.05);
    }

    private float calculateDamageMultiplier(int dayNumber) {
        return switch (dayNumber) {
            case 1 -> 1.0F;
            case 2 -> 1.3F;
            case 3 -> 1.6F;
            default -> 1.0F;
        };
    }

    private int calculateLifetime(int dayNumber) {
        return switch (dayNumber) {
            case 1 -> 80;  // 4秒
            case 2 -> 100; // 5秒
            case 3 -> 120; // 6秒
            default -> 80;
        };
    }

    // ゲッター・セッター
    public int getThrowerDayNumber() {
        return this.entityData.get(THROWER_DAY_NUMBER);
    }

    public void setThrowerDayNumber(int dayNumber) {
        this.entityData.set(THROWER_DAY_NUMBER, dayNumber);
    }

    public boolean isHoming() {
        return this.entityData.get(IS_HOMING);
    }

    public void setHoming(boolean homing) {
        this.entityData.set(IS_HOMING, homing);
    }

    public float getDamageMultiplier() {
        return this.entityData.get(DAMAGE_MULTIPLIER);
    }

    public void setDamageMultiplier(float multiplier) {
        this.entityData.set(DAMAGE_MULTIPLIER, multiplier);
    }

    // NBT保存
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ThrowerDayNumber", getThrowerDayNumber());
        tag.putBoolean("IsHoming", isHoming());
        tag.putFloat("DamageMultiplier", getDamageMultiplier());
        tag.putInt("MaxLifetime", maxLifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setThrowerDayNumber(tag.getInt("ThrowerDayNumber"));
        setHoming(tag.getBoolean("IsHoming"));
        setDamageMultiplier(tag.getFloat("DamageMultiplier"));
        maxLifetime = tag.getInt("MaxLifetime");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}