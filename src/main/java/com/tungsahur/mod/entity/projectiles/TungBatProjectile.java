// TungBatProjectile.java - 地面破壊機能付き強化版
package com.tungsahur.mod.entity.projectiles;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.ModEntities;
import com.tungsahur.mod.entity.TungSahurEntity;
import com.tungsahur.mod.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.*;

public class TungBatProjectile extends ThrowableItemProjectile {

    // データアクセサー
    private static final EntityDataAccessor<Integer> THROWER_DAY_NUMBER =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_HOMING =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DAMAGE_MULTIPLIER =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_EXPLOSIVE =
            SynchedEntityData.defineId(TungBatProjectile.class, EntityDataSerializers.BOOLEAN);

    // 強化された設定
    private int maxLifetime = 200; // 10秒（従来の2倍）
    private LivingEntity homingTarget;
    private int homingStartDelay = 15; // ホーミング開始遅延
    private float homingStrength = 0.12F; // ホーミング強度向上
    private int destructionRadius = 2; // 破壊半径
    private int pierceCount = 0; // 貫通回数
    private final Set<Entity> hitEntities = new HashSet<>(); // 既にヒットしたエンティティ

    // 破壊可能なブロックリスト
    private static final Set<Block> DESTRUCTIBLE_BLOCKS = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
            Blocks.CLAY, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA,
            Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA,
            Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA,
            Blocks.SNOW_BLOCK, Blocks.SNOW, Blocks.ICE,
            Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
            Blocks.TUFF, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.GLASS, Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS
    );

    // 強固で破壊不可能なブロック
    private static final Set<Block> UNBREAKABLE_BLOCKS = Set.of(
            Blocks.BEDROCK, Blocks.BARRIER, Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW,
            Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
            Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK,
            Blocks.END_STONE, Blocks.END_PORTAL_FRAME
    );

    public TungBatProjectile(EntityType<? extends TungBatProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(false);
    }

    public TungBatProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.TUNG_BAT_PROJECTILE.get(), shooter, level);

        if (shooter instanceof TungSahurEntity tungSahur) {
            initializeFromTungSahur(tungSahur);
        }

        this.setNoGravity(getThrowerDayNumber() >= 2);
    }

    private void initializeFromTungSahur(TungSahurEntity tungSahur) {
        int dayNumber = tungSahur.getDayNumber();
        this.setThrowerDayNumber(dayNumber);
        this.setDamageMultiplier(calculateDamageMultiplier(dayNumber));
        this.maxLifetime = calculateLifetime(dayNumber);

        // 日数に応じた特殊能力
        switch (dayNumber) {
            case 1:
                this.destructionRadius = 1;
                this.pierceCount = 0;
                break;
            case 2:
                this.destructionRadius = 2;
                this.pierceCount = 1;
                this.setHoming(true);
                this.homingTarget = tungSahur.getTarget();
                this.homingStrength = 0.08F;
                break;
            case 3:
                this.destructionRadius = 3;
                this.pierceCount = 2;
                this.setHoming(true);
                this.setExplosive(true);
                this.homingTarget = tungSahur.getTarget();
                this.homingStrength = 0.15F;
                break;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(THROWER_DAY_NUMBER, 1);
        this.entityData.define(IS_HOMING, false);
        this.entityData.define(DAMAGE_MULTIPLIER, 1.0F);
        this.entityData.define(IS_EXPLOSIVE, false);
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
            performEnhancedHoming();
        }

        // 強化された軌跡効果
        spawnEnhancedTrailParticles();

        // 飛行音（より迫力のある音）
        if (this.tickCount % 6 == 0) {
            playEnhancedFlightSound();
        }

        // 速度維持（空気抵抗を無効化）
        maintainVelocity();
    }

    private void performEnhancedHoming() {
        if (this.homingTarget == null || !this.homingTarget.isAlive()) {
            this.setHoming(false);
            return;
        }

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 targetPosition = this.homingTarget.getEyePosition().add(0, -0.3, 0); // 胴体を狙う
        Vec3 targetDirection = targetPosition.subtract(this.position()).normalize();

        // より強力なホーミング
        Vec3 newVelocity = currentVelocity.normalize().lerp(targetDirection, this.homingStrength);
        double speed = Math.max(currentVelocity.length(), 1.5D); // 最低速度保証

        this.setDeltaMovement(newVelocity.scale(speed));

        // ホーミング時の特殊パーティクル
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 3 == 0) {
            spawnHomingParticles(serverLevel);
        }
    }

    private void maintainVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        double currentSpeed = velocity.length();

        // 最低速度を維持
        double minSpeed = 0.8D + (getThrowerDayNumber() * 0.3D);
        if (currentSpeed < minSpeed) {
            this.setDeltaMovement(velocity.normalize().scale(minSpeed));
        }
    }

    private void spawnEnhancedTrailParticles() {
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

        // 日数に応じた軌跡の強化
        int particleCount = getThrowerDayNumber() * 2;

        for (int i = 0; i < particleCount; i++) {
            // バットの軌跡
            this.level().addParticle(ParticleTypes.CRIT,
                    pos.x + (random.nextDouble() - 0.5) * 0.3,
                    pos.y + (random.nextDouble() - 0.5) * 0.3,
                    pos.z + (random.nextDouble() - 0.5) * 0.3,
                    -velocity.x * 0.1, -velocity.y * 0.1, -velocity.z * 0.1);
        }

        // 3日目は特殊な火花エフェクト
        if (getThrowerDayNumber() >= 3 && random.nextFloat() < 0.7F) {
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y, pos.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1);
        }
    }

    private void spawnServerTrailParticles(ServerLevel serverLevel) {
        Vec3 pos = this.position();

        // バットアイテムの破片
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        ItemParticleOption itemParticle = new ItemParticleOption(ParticleTypes.ITEM, batStack);

        serverLevel.sendParticles(itemParticle,
                pos.x, pos.y, pos.z,
                2, 0.15, 0.15, 0.15, 0.05);

        // 速度に応じた煙
        double speed = this.getDeltaMovement().length();
        if (speed > 1.0D) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z,
                    1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void spawnHomingParticles(ServerLevel serverLevel) {
        Vec3 pos = this.position();

        // ホーミング中の魔法的パーティクル
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                pos.x, pos.y, pos.z,
                3, 0.2, 0.2, 0.2, 0.1);

        if (getThrowerDayNumber() >= 3) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y, pos.z,
                    2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private void playEnhancedFlightSound() {
        float pitch = 0.6F + (float) this.getDeltaMovement().length() * 0.8F;
        float volume = 0.4F + getThrowerDayNumber() * 0.2F;

        // 基本飛行音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE,
                volume, pitch);

        // 3日目は追加の不気味音
        if (getThrowerDayNumber() >= 3 && this.random.nextFloat() < 0.4F) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE,
                    volume * 0.6F, pitch * 1.5F);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hitEntity = result.getEntity();

        // 既にヒットしたエンティティはスキップ（貫通制御）
        if (this.hitEntities.contains(hitEntity)) {
            return;
        }

        if (hitEntity instanceof LivingEntity livingEntity && hitEntity != this.getOwner()) {
            // ダメージ計算
            float damage = calculateEnhancedDamage();

            // ダメージ適用
            boolean hitSuccessful = livingEntity.hurt(
                    this.damageSources().thrown(this, this.getOwner()), damage);

            if (hitSuccessful) {
                // ヒットエンティティを記録
                this.hitEntities.add(hitEntity);

                // 日数に応じた特殊効果
                applyEnhancedDayEffects(livingEntity);

                // 強化されたノックバック
                applyEnhancedKnockback(livingEntity);

                // ヒット時の強化エフェクト
                spawnEnhancedHitEffects(livingEntity);

                TungSahurMod.LOGGER.debug("TungBatProjectile 強化ヒット: {} に {}ダメージ (貫通残り: {})",
                        hitEntity.getClass().getSimpleName(), damage, this.pierceCount);

                // 貫通チェック
                if (this.pierceCount > 0) {
                    this.pierceCount--;
                    return; // 貫通して飛び続ける
                }
            }
        }

        // 貫通回数を使い切った場合は消滅
        onImpact(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // 地面破壊処理
        performGroundDestruction(result);

        // 衝撃処理
        onImpact(result.getLocation());

        super.onHitBlock(result);
    }

    private void performGroundDestruction(BlockHitResult result) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        BlockPos hitPos = result.getBlockPos();
        Vec3 impactPos = result.getLocation();

        // 破壊音（衝撃音）
        serverLevel.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                1.0F + getThrowerDayNumber() * 0.3F, 0.8F);

        // 範囲破壊実行
        performRadialDestruction(serverLevel, hitPos, impactPos);

        // 破壊エフェクト
        spawnDestructionEffects(serverLevel, impactPos);

        TungSahurMod.LOGGER.debug("TungBat地面破壊: 中心={}, 半径={}, 日数={}",
                hitPos, this.destructionRadius, getThrowerDayNumber());
    }

    private void performRadialDestruction(ServerLevel serverLevel, BlockPos center, Vec3 impactPos) {
        List<BlockPos> destroyedBlocks = new ArrayList<>();

        // 半径範囲内のブロックを破壊
        for (int x = -destructionRadius; x <= destructionRadius; x++) {
            for (int y = -destructionRadius; y <= destructionRadius; y++) {
                for (int z = -destructionRadius; z <= destructionRadius; z++) {
                    BlockPos checkPos = center.offset(x, y, z);
                    double distance = Math.sqrt(x*x + y*y + z*z);

                    // 球形の破壊範囲
                    if (distance <= destructionRadius) {
                        if (canDestroyBlock(serverLevel, checkPos)) {
                            BlockState state = serverLevel.getBlockState(checkPos);

                            // ブロック破壊
                            serverLevel.destroyBlock(checkPos, true, this);
                            destroyedBlocks.add(checkPos);

                            // 破壊パーティクル
                            spawnBlockDestructionParticles(serverLevel, checkPos, state);
                        }
                    }
                }
            }
        }

        TungSahurMod.LOGGER.debug("ブロック破壊完了: {}個のブロックを破壊", destroyedBlocks.size());
    }

    private boolean canDestroyBlock(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        Block block = state.getBlock();

        // 空気ブロックはスキップ
        if (state.isAir()) return false;

        // 破壊不可能なブロック
        if (UNBREAKABLE_BLOCKS.contains(block)) return false;

        // 破壊可能なブロックリストに含まれている
        if (DESTRUCTIBLE_BLOCKS.contains(block)) return true;

        // その他のブロックは硬度で判定
        float hardness = state.getDestroySpeed(serverLevel, pos);

        // 日数に応じた破壊力
        float maxHardness = switch (getThrowerDayNumber()) {
            case 1 -> 2.0F;  // 石程度まで
            case 2 -> 5.0F;  // 鉄程度まで
            case 3 -> 50.0F; // オブシディアン以外ほぼ全て
            default -> 1.0F;
        };

        return hardness >= 0 && hardness <= maxHardness;
    }

    private void spawnBlockDestructionParticles(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, state);

        serverLevel.sendParticles(blockParticle,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.5, 0.5, 0.5, 0.1);
    }

    private void spawnDestructionEffects(ServerLevel serverLevel, Vec3 impactPos) {
        // 大規模爆発パーティクル
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                impactPos.x, impactPos.y, impactPos.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // 煙雲
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                impactPos.x, impactPos.y, impactPos.z,
                15 + getThrowerDayNumber() * 5, 1.5, 1.0, 1.5, 0.2);

        // 土煙
        serverLevel.sendParticles(ParticleTypes.POOF,
                impactPos.x, impactPos.y, impactPos.z,
                20 + getThrowerDayNumber() * 10, 2.0, 1.0, 2.0, 0.3);

        // 3日目は魂の炎
        if (getThrowerDayNumber() >= 3) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    impactPos.x, impactPos.y, impactPos.z,
                    10, 1.0, 1.0, 1.0, 0.1);
        }
    }

    private void onImpact(Vec3 impactPos) {
        if (isExplosive() && this.level() instanceof ServerLevel serverLevel) {
            // 爆発エフェクト（ブロック破壊なしの演出用）
            spawnExplosionEffects(serverLevel, impactPos);
        }

        this.discard(); // プロジェクタイルを削除
    }

    private void spawnExplosionEffects(ServerLevel serverLevel, Vec3 pos) {
        // 大爆発パーティクル
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // 衝撃波
        for (int i = 0; i < 30; i++) {
            double angle = (i / 30.0) * Math.PI * 2;
            double distance = 3.0 + this.random.nextDouble() * 2.0;
            double x = pos.x + Math.cos(angle) * distance;
            double z = pos.z + Math.sin(angle) * distance;

            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    x, pos.y, z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private float calculateEnhancedDamage() {
        float baseDamage = 8.0F; // 基本ダメージ
        float dayMultiplier = getThrowerDayNumber() * 2.0F; // 日数倍率
        float speedBonus = (float) this.getDeltaMovement().length() * 2.0F; // 速度ボーナス

        return (baseDamage + dayMultiplier + speedBonus) * getDamageMultiplier();
    }

    private void applyEnhancedDayEffects(LivingEntity target) {
        switch (getThrowerDayNumber()) {
            case 2:
                // 鈍化効果
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                break;
            case 3:
                // 鈍化 + 暗闇
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.BLINDNESS, 80, 0));
                break;
        }
    }

    private void applyEnhancedKnockback(LivingEntity target) {
        Vec3 direction = target.position().subtract(this.position()).normalize();
        double knockbackStrength = 1.0D + (getThrowerDayNumber() * 0.5D);

        target.knockback(knockbackStrength, -direction.x, -direction.z);

        // 3日目は上方向のノックバックも追加
        if (getThrowerDayNumber() >= 3) {
            Vec3 velocity = target.getDeltaMovement();
            target.setDeltaMovement(velocity.x, velocity.y + 0.4D, velocity.z);
        }
    }

    private void spawnEnhancedHitEffects(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // 強力なヒットパーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                15 + getThrowerDayNumber() * 5, 0.5, 0.5, 0.5, 0.2);

        // 血のような効果
        serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                pos.x, pos.y, pos.z,
                10, 0.3, 0.3, 0.3, 0.0);

        // ヒット音
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE,
                1.0F + getThrowerDayNumber() * 0.2F, 0.8F);
    }

    private float calculateDamageMultiplier(int dayNumber) {
        return 1.0F + (dayNumber - 1) * 0.5F;
    }

    private int calculateLifetime(int dayNumber) {
        return 120 + (dayNumber * 40); // 日数に応じて生存時間延長
    }

    private void onExpire() {
        // 自然消滅時のエフェクト
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();
            serverLevel.sendParticles(ParticleTypes.POOF,
                    pos.x, pos.y, pos.z,
                    5, 0.3, 0.3, 0.3, 0.1);
        }
        this.discard();
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

    public boolean isExplosive() {
        return this.entityData.get(IS_EXPLOSIVE);
    }

    public void setExplosive(boolean explosive) {
        this.entityData.set(IS_EXPLOSIVE, explosive);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ThrowerDayNumber", getThrowerDayNumber());
        tag.putBoolean("IsHoming", isHoming());
        tag.putFloat("DamageMultiplier", getDamageMultiplier());
        tag.putBoolean("IsExplosive", isExplosive());
        tag.putInt("PierceCount", this.pierceCount);
        tag.putInt("DestructionRadius", this.destructionRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setThrowerDayNumber(tag.getInt("ThrowerDayNumber"));
        setHoming(tag.getBoolean("IsHoming"));
        setDamageMultiplier(tag.getFloat("DamageMultiplier"));
        setExplosive(tag.getBoolean("IsExplosive"));
        this.pierceCount = tag.getInt("PierceCount");
        this.destructionRadius = tag.getInt("DestructionRadius");
    }
}