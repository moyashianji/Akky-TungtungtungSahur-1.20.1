// TungBatProjectile.java - 高精度射撃機能付き強化版
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
import net.minecraft.world.phys.AABB;
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
    private int homingStartDelay = 8; // ホーミング開始遅延を短縮（15→8）
    private float homingStrength = 0.18F; // ホーミング強度を大幅向上（0.12F→0.18F）
    private int destructionRadius = 2; // 破壊半径
    private int pierceCount = 0; // 貫通回数
    private final Set<Entity> hitEntities = new HashSet<>(); // 既にヒットしたエンティティ

    // 新規追加：高精度射撃用
    private Vec3 lastTargetPosition = Vec3.ZERO;
    private Vec3 targetVelocity = Vec3.ZERO;
    private int targetLostTicks = 0;
    private static final int MAX_TARGET_LOST_TICKS = 40; // 2秒間はターゲット位置を記憶
    private static final double PREDICTION_STRENGTH = 2.5D; // 予測射撃の強度
    private static final double HIT_BOX_EXPANSION = 0.8D; // 当たり判定の拡大

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

        // 日数に応じた特殊能力（精度向上）
        switch (dayNumber) {
            case 1:
                this.destructionRadius = 1;
                this.pierceCount = 0;
                this.homingStrength = 0.12F;
                break;
            case 2:
                this.destructionRadius = 2;
                this.pierceCount = 1;
                this.setHoming(true);
                this.homingTarget = tungSahur.getTarget();
                this.homingStrength = 0.16F; // 2日目の精度向上
                this.homingStartDelay = 6; // より早くホーミング開始
                break;
            case 3:
                this.destructionRadius = 3;
                this.pierceCount = 2;
                this.setHoming(true);
                this.setExplosive(true);
                this.homingTarget = tungSahur.getTarget();
                this.homingStrength = 0.22F; // 3日目の最高精度
                this.homingStartDelay = 4; // 最速ホーミング開始
                break;
        }

        // ターゲットの初期位置と速度を記録
        if (this.homingTarget != null) {
            this.lastTargetPosition = this.homingTarget.position();
            this.targetVelocity = this.homingTarget.getDeltaMovement();
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

        // 高精度ホーミング処理
        if (isHoming() && this.tickCount > homingStartDelay) {
            performHighPrecisionHoming();
        }

        // 近距離自動命中チェック
        checkNearbyTargets();

        // 強化された軌跡効果
        spawnEnhancedTrailParticles();

        // 飛行音（より迫力のある音）
        if (this.tickCount % 6 == 0) {
            playEnhancedFlightSound();
        }

        // 速度維持（空気抵抗を無効化）
        maintainVelocity();
    }

    /**
     * 高精度ホーミング処理（予測射撃付き）
     */
    private void performHighPrecisionHoming() {
        // ターゲットの状態更新
        updateTargetTracking();

        Vec3 predictedPosition = calculatePredictedTargetPosition();
        if (predictedPosition == null) return;

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 targetDirection = predictedPosition.subtract(this.position()).normalize();

        // より強力で滑らかなホーミング
        Vec3 newVelocity = currentVelocity.normalize().lerp(targetDirection, this.homingStrength);

        // 速度ブースト（日数に応じて）
        double speed = Math.max(currentVelocity.length(), 1.2D + (getThrowerDayNumber() * 0.4D));

        // ターゲットが近い場合は速度を上げる
        double distanceToTarget = this.position().distanceTo(predictedPosition);
        if (distanceToTarget < 8.0D) {
            speed *= 1.3D; // 近距離では30%速度アップ
        }

        this.setDeltaMovement(newVelocity.scale(speed));

        // ホーミング時の特殊パーティクル
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 2 == 0) {
            spawnHomingParticles(serverLevel);
        }
    }

    /**
     * ターゲット追跡情報の更新
     */
    private void updateTargetTracking() {
        if (this.homingTarget != null && this.homingTarget.isAlive()) {
            // ターゲットの速度を計算
            Vec3 currentPosition = this.homingTarget.position();
            if (!this.lastTargetPosition.equals(Vec3.ZERO)) {
                this.targetVelocity = currentPosition.subtract(this.lastTargetPosition);
            }
            this.lastTargetPosition = currentPosition;
            this.targetLostTicks = 0;
        } else {
            this.targetLostTicks++;
            if (this.targetLostTicks > MAX_TARGET_LOST_TICKS) {
                this.setHoming(false);
            }
        }
    }

    /**
     * 予測射撃でのターゲット位置計算
     */
    private Vec3 calculatePredictedTargetPosition() {
        if (this.homingTarget == null) {
            return this.lastTargetPosition.equals(Vec3.ZERO) ? null : this.lastTargetPosition;
        }

        if (!this.homingTarget.isAlive()) {
            return this.targetLostTicks < MAX_TARGET_LOST_TICKS ? this.lastTargetPosition : null;
        }

        Vec3 currentTargetPos = this.homingTarget.getEyePosition().add(0, -0.2, 0); // 胴体中心を狙う

        // 予測時間（プロジェクタイルがターゲットに到達するまでの時間）
        double distanceToTarget = this.position().distanceTo(currentTargetPos);
        double projectileSpeed = this.getDeltaMovement().length();
        double timeToTarget = distanceToTarget / Math.max(projectileSpeed, 0.1D);

        // ターゲットの予測位置
        Vec3 predictedPosition = currentTargetPos.add(this.targetVelocity.scale(timeToTarget * PREDICTION_STRENGTH));

        // 地面に埋まらないように調整
        double groundY = this.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                new BlockPos((int)predictedPosition.x, 0, (int)predictedPosition.z)).getY();
        if (predictedPosition.y < groundY + 1.0D) {
            predictedPosition = new Vec3(predictedPosition.x, groundY + 1.0D, predictedPosition.z);
        }

        return predictedPosition;
    }

    /**
     * 近距離の自動命中チェック
     */
    private void checkNearbyTargets() {
        if (!isHoming() || this.homingTarget == null) return;

        double expansionRadius = HIT_BOX_EXPANSION;
        AABB expandedBB = this.getBoundingBox().inflate(expansionRadius);

        // 近くのエンティティをチェック
        List<Entity> nearbyEntities = this.level().getEntities(this, expandedBB);
        for (Entity entity : nearbyEntities) {
            if (entity == this.homingTarget && entity instanceof LivingEntity) {
                // 強制的にヒット処理
                this.onHitEntity(new EntityHitResult(entity));
                return;
            }
        }
    }

    private void maintainVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        double currentSpeed = velocity.length();

        // 最低速度を維持（日数に応じて向上）
        double minSpeed = 0.9D + (getThrowerDayNumber() * 0.4D);
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

        // ホーミング中は特殊な軌跡
        if (isHoming() && random.nextFloat() < 0.8F) {
            this.level().addParticle(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y, pos.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1,
                    (random.nextDouble() - 0.5) * 0.1);
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

        // ホーミング中の魔法的パーティクル（より濃密に）
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                pos.x, pos.y, pos.z,
                5, 0.3, 0.3, 0.3, 0.1);

        if (getThrowerDayNumber() >= 3) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y, pos.z,
                    3, 0.15, 0.15, 0.15, 0.05);
        }
    }

    private void playEnhancedFlightSound() {
        float pitch = 0.6F + (float) this.getDeltaMovement().length() * 0.8F;
        float volume = 0.4F + getThrowerDayNumber() * 0.2F;

        // 基本飛行音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE,
                volume, pitch);

        // ホーミング中は特殊音
        if (isHoming() && this.random.nextFloat() < 0.3F) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE,
                    volume * 0.5F, pitch * 1.2F);
        }

        // 3日目は追加の不気味音
        if (getThrowerDayNumber() >= 3 && this.random.nextFloat() < 0.4F) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE,
                    volume * 0.6F, pitch * 1.5F);
        }
    }

    // === ヒット処理 ===
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hitEntity = result.getEntity();

        if (hitEntity instanceof LivingEntity target) {
            // 基本ダメージ（削減済み）
            float damage = calculateEnhancedDamage();

            // プレイヤーにはさらに削減
            if (target instanceof net.minecraft.world.entity.player.Player) {
                damage *= 0.5F; // プレイヤーには半分のダメージ
            }

            // ダメージ適用
            target.hurt(this.damageSources().thrown(this, this.getOwner()), damage);

            // ノックバック（プレイヤーには軽く）
            applyEnhancedKnockback(target);

            // エフェクト（プレイヤーには適用しない）
            applyEnhancedDayEffects(target);

            // ヒットエフェクト
            spawnEnhancedHitEffects(target);

            TungSahurMod.LOGGER.debug("高精度投擲武器ヒット: {} に {} ダメージ",
                    target.getClass().getSimpleName(), damage);
        }

        // 投擲物を削除
        this.discard();
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
        // 大規模爆発パーティクル（TNT風に強化）
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                impactPos.x, impactPos.y, impactPos.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        // 追加の爆発パーティクル
        for (int i = 0; i < 5; i++) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    impactPos.x + (Math.random() - 0.5) * 2,
                    impactPos.y + (Math.random() - 0.5) * 2,
                    impactPos.z + (Math.random() - 0.5) * 2,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // 炎と煙のパーティクル
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                impactPos.x, impactPos.y + 1, impactPos.z,
                20, 1.5, 1.5, 1.5, 0.1);

        serverLevel.sendParticles(ParticleTypes.FLAME,
                impactPos.x, impactPos.y, impactPos.z,
                15, 1.0, 1.0, 1.0, 0.05);

        // 土や石の破片パーティクル
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                impactPos.x, impactPos.y + 2, impactPos.z,
                10, 2.0, 2.0, 2.0, 0.02);
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

    // === ダメージ計算を大幅弱体化 ===
    private float calculateEnhancedDamage() {
        float baseDamage = 2.0F; // 8.0F → 2.0F に大幅削減
        float dayMultiplier = getThrowerDayNumber() * 0.3F; // 2.0F → 0.3F に大幅削減
        float speedBonus = (float) this.getDeltaMovement().length() * 0.3F; // 2.0F → 0.3F に大幅削減

        return (baseDamage + dayMultiplier + speedBonus) * getDamageMultiplier();
    }

    // === プレイヤーへの効果を完全削除 ===
    private void applyEnhancedDayEffects(LivingEntity target) {
        // プレイヤーには一切効果を与えない
        if (target instanceof net.minecraft.world.entity.player.Player) {
            return; // プレイヤーへの効果を完全削除
        }

        // プレイヤー以外への効果も大幅削減
        switch (getThrowerDayNumber()) {
            case 2:
                // 軽い鈍化効果のみ（時間とレベル大幅削減）
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 0)); // 60→20、レベル1→0
                break;
            case 3:
                // 軽い鈍化のみ（暗闇効果削除）
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 0)); // 100→30、レベル2→0
                // 暗闇効果を完全削除
                break;
        }
    }

    // === ノックバックを大幅弱体化 ===
    private void applyEnhancedKnockback(LivingEntity target) {
        // プレイヤーには非常に弱いノックバック
        if (target instanceof net.minecraft.world.entity.player.Player) {
            Vec3 direction = target.position().subtract(this.position()).normalize();
            double knockbackStrength = 0.2D; // 非常に弱いノックバック

            target.knockback(knockbackStrength, -direction.x, -direction.z);
            return;
        }

        // プレイヤー以外への通常ノックバック（それでも削減済み）
        Vec3 direction = target.position().subtract(this.position()).normalize();
        double knockbackStrength = 0.4D + (getThrowerDayNumber() * 0.1D); // 1.0D + 0.5D → 0.4D + 0.1D

        target.knockback(knockbackStrength, -direction.x, -direction.z);

        // 3日目の上方向ノックバックも大幅削減
        if (getThrowerDayNumber() >= 3) {
            Vec3 velocity = target.getDeltaMovement();
            target.setDeltaMovement(velocity.x, velocity.y + 0.15D, velocity.z); // 0.4D → 0.15D
        }
    }

    // === ダメージ倍率を大幅削減 ===
    private float calculateDamageMultiplier(int dayNumber) {
        return 1.0F + (dayNumber - 1) * 0.1F; // 0.5F → 0.1F に大幅削減
    }

    // === ヒットエフェクトを控えめに ===
    private void spawnEnhancedHitEffects(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        // パーティクル量を大幅削減
        serverLevel.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z,
                5 + getThrowerDayNumber(), 0.2, 0.2, 0.2, 0.05); // 15+5 → 5+1、速度削減

        // 血のような効果も大幅削減
        serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                pos.x, pos.y, pos.z,
                3, 0.1, 0.1, 0.1, 0.0); // 10 → 3、範囲削減

        // ヒット音も控えめに
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE,
                0.4F + getThrowerDayNumber() * 0.05F, 0.8F); // 1.0F+0.2F → 0.4F+0.05F
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