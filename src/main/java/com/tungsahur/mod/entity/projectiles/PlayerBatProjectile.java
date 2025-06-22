// PlayerBatProjectile.java - プレイヤー専用バット投擲
package com.tungsahur.mod.entity.projectiles;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class PlayerBatProjectile extends ThrowableItemProjectile {

    // データアクセサー（既存のTungBatProjectileと重複しないように調整）
    private static final EntityDataAccessor<Integer> BAT_POWER =
            SynchedEntityData.defineId(PlayerBatProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_CURSED =
            SynchedEntityData.defineId(PlayerBatProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_BLOODSTAINED =
            SynchedEntityData.defineId(PlayerBatProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ORIGINAL_BAT_DATA =
            SynchedEntityData.defineId(PlayerBatProjectile.class, EntityDataSerializers.STRING);

    // 設定
    private int maxLifetime = 100; // 5秒
    private ItemStack originalBatStack;

    public PlayerBatProjectile(EntityType<? extends PlayerBatProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(false);
    }

    public PlayerBatProjectile(Level level, LivingEntity shooter, ItemStack batStack) {
        super(ModEntities.TUNG_BAT_PROJECTILE.get(), shooter, level);
        this.originalBatStack = batStack.copy();
        initializeFromBatStack(batStack);
    }

    /**
     * バットアイテムから初期化
     */
    private void initializeFromBatStack(ItemStack batStack) {
        CompoundTag tag = batStack.getTag();
        if (tag != null) {
            int power = 1;
            if (tag.contains("DayNumber")) {
                power = tag.getInt("DayNumber");
            }

            this.setBatPower(power);
            this.setCursed(tag.getBoolean("Cursed"));
            this.setBloodstained(tag.getBoolean("Bloodstained"));

            // 威力に応じて生存時間調整
            this.maxLifetime = 100 + (power * 20);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BAT_POWER, 1);
        this.entityData.define(IS_CURSED, false);
        this.entityData.define(IS_BLOODSTAINED, false);
        this.entityData.define(ORIGINAL_BAT_DATA, "");
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

        // 軌跡パーティクル
        spawnTrailParticles();

        // 飛行音
        if (this.tickCount % 8 == 0) {
            playFlightSound();
        }

        // 重力の適用（バットは弧を描いて飛ぶ）
        if (!this.isNoGravity()) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x, motion.y - 0.03D, motion.z);
        }
    }

    /**
     * エンティティへのヒット処理
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hitEntity = result.getEntity();

        if (hitEntity instanceof LivingEntity target && this.getOwner() instanceof Player player) {
            // ダメージ計算
            float damage = calculateDamage();

            // ダメージ適用
            target.hurt(this.damageSources().thrown(this, player), damage);

            // 特殊効果の適用
            applySpecialEffects(target, player);

            // ヒットエフェクト
            spawnHitEffects(target);

            // バットを地面にドロップ
            dropBatItem();

            TungSahurMod.LOGGER.debug("プレイヤーバット投擲ヒット: {} に {} ダメージ",
                    target.getClass().getSimpleName(), damage);
        }

        this.discard();
    }

    /**
     * ブロックへのヒット処理
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        // 着地音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);

        // 着地パーティクル
        spawnLandingEffects();

        // バットをドロップ
        dropBatItem();

        super.onHitBlock(result);
    }

    /**
     * ダメージ計算
     */
    private float calculateDamage() {
        float baseDamage = 4.0F; // 基本投擲ダメージ
        float powerBonus = getBatPower() * 1.5F; // 威力ボーナス
        float speedBonus = (float) this.getDeltaMovement().length() * 2.0F; // 速度ボーナス

        float totalDamage = baseDamage + powerBonus + speedBonus;

        // 特殊効果ボーナス
        if (isCursed()) {
            totalDamage *= 1.3F; // 呪いボーナス30%
        }

        if (isBloodstained()) {
            totalDamage *= 1.2F; // 血痕ボーナス20%
        }

        return totalDamage;
    }

    /**
     * 特殊効果の適用
     */
    private void applySpecialEffects(LivingEntity target, Player player) {
        RandomSource random = this.level().getRandom();

        // 呪われたバットの効果
        if (isCursed() && random.nextFloat() < 0.4F) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 1));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.CONFUSION, 80, 0));
        }

        // 血まみれバットの効果
        if (isBloodstained() && random.nextFloat() < 0.3F) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WITHER, 60, 0));
        }

        // 高威力バットの効果
        if (getBatPower() >= 3 && random.nextFloat() < 0.2F) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        }

        // ノックバック
        Vec3 direction = target.position().subtract(this.position()).normalize();
        double knockbackStrength = 0.5D + (getBatPower() * 0.2D);
        target.knockback(knockbackStrength, -direction.x, -direction.z);
    }

    /**
     * バットアイテムのドロップ
     */
    private void dropBatItem() {
        if (this.originalBatStack != null && !this.originalBatStack.isEmpty()) {
            // 元のバットアイテムをドロップ
            this.spawnAtLocation(this.originalBatStack.copy());
        } else {
            // デフォルトバットをドロップ
            ItemStack defaultBat = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
            this.spawnAtLocation(defaultBat);
        }
    }

    /**
     * 軌跡パーティクル
     */
    private void spawnTrailParticles() {
        if (this.level().isClientSide) {
            spawnClientTrailParticles();
        } else if (this.level() instanceof ServerLevel serverLevel) {
            spawnServerTrailParticles(serverLevel);
        }
    }

    private void spawnClientTrailParticles() {
        RandomSource random = this.level().getRandom();
        Vec3 pos = this.position();
        Vec3 velocity = this.getDeltaMovement();

        // 基本軌跡
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.CRIT,
                    pos.x + (random.nextDouble() - 0.5) * 0.2,
                    pos.y + (random.nextDouble() - 0.5) * 0.2,
                    pos.z + (random.nextDouble() - 0.5) * 0.2,
                    -velocity.x * 0.1, -velocity.y * 0.1, -velocity.z * 0.1);
        }

        // 特殊効果の軌跡
        if (isCursed() && random.nextFloat() < 0.5F) {
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y, pos.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1);
        }

        if (isBloodstained() && random.nextFloat() < 0.3F) {
            this.level().addParticle(ParticleTypes.DAMAGE_INDICATOR,
                    pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    private void spawnServerTrailParticles(ServerLevel serverLevel) {
        Vec3 pos = this.position();

        // バットアイテムの破片
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        ItemParticleOption itemParticle = new ItemParticleOption(ParticleTypes.ITEM, batStack);

        serverLevel.sendParticles(itemParticle,
                pos.x, pos.y, pos.z,
                1, 0.1, 0.1, 0.1, 0.02);
    }

    /**
     * ヒットエフェクト
     */
    private void spawnHitEffects(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // ヒットパーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                8, 0.3, 0.3, 0.3, 0.1);

        // 特殊効果パーティクル
        if (isCursed()) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y, pos.z,
                    5, 0.2, 0.2, 0.2, 0.05);
        }

        if (isBloodstained()) {
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    pos.x, pos.y, pos.z,
                    3, 0.15, 0.15, 0.15, 0.0);
        }

        // ヒット音
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                0.8F, 1.0F + (getBatPower() * 0.1F));
    }

    /**
     * 着地エフェクト
     */
    private void spawnLandingEffects() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.position();

        serverLevel.sendParticles(ParticleTypes.POOF,
                pos.x, pos.y, pos.z,
                5, 0.2, 0.2, 0.2, 0.1);
    }

    /**
     * 飛行音
     */
    private void playFlightSound() {
        float pitch = 0.8F + (float) this.getDeltaMovement().length() * 0.5F;
        float volume = 0.3F;

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * 自然消滅
     */
    private void onExpire() {
        // 消滅エフェクト
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();
            serverLevel.sendParticles(ParticleTypes.POOF,
                    pos.x, pos.y, pos.z,
                    3, 0.2, 0.2, 0.2, 0.1);
        }

        // バットをドロップ
        dropBatItem();

        this.discard();
    }

    // ゲッター・セッター
    public int getBatPower() {
        return this.entityData.get(BAT_POWER);
    }

    public void setBatPower(int power) {
        this.entityData.set(BAT_POWER, power);
    }

    public boolean isCursed() {
        return this.entityData.get(IS_CURSED);
    }

    public void setCursed(boolean cursed) {
        this.entityData.set(IS_CURSED, cursed);
    }

    public boolean isBloodstained() {
        return this.entityData.get(IS_BLOODSTAINED);
    }

    public void setBloodstained(boolean bloodstained) {
        this.entityData.set(IS_BLOODSTAINED, bloodstained);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BatPower", getBatPower());
        tag.putBoolean("IsCursed", isCursed());
        tag.putBoolean("IsBloodstained", isBloodstained());

        if (this.originalBatStack != null) {
            CompoundTag batTag = new CompoundTag();
            this.originalBatStack.save(batTag);
            tag.put("OriginalBatStack", batTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBatPower(tag.getInt("BatPower"));
        setCursed(tag.getBoolean("IsCursed"));
        setBloodstained(tag.getBoolean("IsBloodstained"));

        if (tag.contains("OriginalBatStack")) {
            this.originalBatStack = ItemStack.of(tag.getCompound("OriginalBatStack"));
        }
    }
}