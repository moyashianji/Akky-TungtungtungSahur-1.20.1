// TungSahurEntity.java - 完全実装版
package com.tungsahur.mod.entity;

import com.tungsahur.mod.TungSahurMod;
import com.tungsahur.mod.entity.goals.*;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class TungSahurEntity extends Monster implements GeoEntity {

    // データアクセサー
    private static final EntityDataAccessor<Integer> DAY_NUMBER = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE_FACTOR = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_WALL_CLIMBING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_BEING_WATCHED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_ATTACKING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_THROWING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_JUMPING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SPRINTING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);

    // アニメーション定数 - 日数に応じて変わる
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().then("death", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE_DAY1_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY2_ANIM = RawAnimation.begin().then("idle2", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY3_ANIM = RawAnimation.begin().then("idle3", Animation.LoopType.LOOP);
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().then("walk", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT_ANIM = RawAnimation.begin().then("sprint", Animation.LoopType.LOOP);
    private static final RawAnimation CLIMBING_ANIM = RawAnimation.begin().then("climbing", Animation.LoopType.LOOP);
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().then("throw", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMP_ATTACK_ANIM = RawAnimation.begin().then("jump_attack", Animation.LoopType.PLAY_ONCE);

    // 内部状態管理
    private int attackCooldown = 0;
    private int throwCooldown = 0;
    private int jumpCooldown = 0;
    private int watchCheckCooldown = 0;
    private Player lastWatchingPlayer = null;

    // GeckoLib
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 15;
        this.setCanPickUpLoot(false);
    }

    // 初期化
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DAY_NUMBER, 1); // デフォルトは1日目
        this.entityData.define(SCALE_FACTOR, 1.0F);
        this.entityData.define(IS_WALL_CLIMBING, false);
        this.entityData.define(IS_BEING_WATCHED, false);
        this.entityData.define(IS_CURRENTLY_ATTACKING, false);
        this.entityData.define(IS_CURRENTLY_THROWING, false);
        this.entityData.define(IS_CURRENTLY_JUMPING, false);
        this.entityData.define(IS_SPRINTING, false);
    }

    @Override
    protected void registerGoals() {
        // 基本AI（優先度の高い順）
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TungSahurMeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(3, new TungSahurJumpAttackGoal(this));
        this.goalSelector.addGoal(4, new TungSahurWallClimbGoal(this));
        this.goalSelector.addGoal(5, new TungSahurAdvancedMoveToTargetGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // ターゲット設定
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.7D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (SCALE_FACTOR.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = getScaleFactor();
        return super.getDimensions(pose).scale(scale);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        // スポーン時に日数を設定
        int currentDay = DayCountSavedData.get((ServerLevel) level.getLevel()).getDayCount();
        setDayNumber(Math.max(1, Math.min(3, currentDay))); // 1-3日目のみ

        // バットを装備
        equipBat();

        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    // ティック処理
    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            updateCooldowns();
            updateWatchStatus();
            updateWallClimbing();
            updateSprintStatus();
        }
    }

    private void updateCooldowns() {
        if (attackCooldown > 0) attackCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (watchCheckCooldown > 0) watchCheckCooldown--;
    }

    private void updateWatchStatus() {
        if (watchCheckCooldown > 0) return;
        watchCheckCooldown = 5; // 5ティック毎にチェック

        Player watchingPlayer = findWatchingPlayer();
        boolean currentlyWatched = watchingPlayer != null;

        if (currentlyWatched != isBeingWatched()) {
            setBeingWatched(currentlyWatched);

            if (currentlyWatched) {
                adjustSpeedForWatching(watchingPlayer);
                lastWatchingPlayer = watchingPlayer;
            } else {
                restoreNormalSpeed();
                lastWatchingPlayer = null;
            }
        } else if (currentlyWatched && watchingPlayer != null) {
            adjustSpeedForWatching(watchingPlayer);
        }
    }

    private Player findWatchingPlayer() {
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D))) {
            if (player.isAlive() && !player.isSpectator() && isPlayerLookingAtMe(player)) {
                return player;
            }
        }
        return null;
    }

    private boolean isPlayerLookingAtMe(Player player) {
        Vec3 playerLook = player.getViewVector(1.0F);
        Vec3 toEntity = this.position().subtract(player.getEyePosition()).normalize();
        double dot = playerLook.dot(toEntity);

        // より厳密な視線判定
        return dot > 0.7D && player.hasLineOfSight(this);
    }

    private void adjustSpeedForWatching(Player watchingPlayer) {
        double distance = this.distanceTo(watchingPlayer);
        double maxWatchDistance = 16.0D;
        double minWatchDistance = 2.0D;

        // 距離による速度減少計算
        double distanceRatio = Math.max(0.0, Math.min(1.0,
                (distance - minWatchDistance) / (maxWatchDistance - minWatchDistance)));

        // 基本減速率（見られているだけで50%減速）
        double baseReduction = 0.5D;

        // 距離による追加減速（近づくほど更に遅くなる）
        double distanceReduction = (1.0 - distanceRatio) * 0.4D; // 最大40%追加減速

        // 最終的な速度倍率を計算
        double totalReduction = baseReduction + distanceReduction;
        double speedMultiplier = Math.max(0.02D, 1.0D - totalReduction); // 最低2%の速度は維持

        // 日数に応じた基本速度
        double baseSpeed = getBaseSpeedForDay();
        double adjustedSpeed = baseSpeed * speedMultiplier;

        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(adjustedSpeed);
    }

    private void restoreNormalSpeed() {
        double normalSpeed = getBaseSpeedForDay();
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(normalSpeed);
    }

    public double getBaseSpeedForDay() {
        return switch (getDayNumber()) {
            case 1 -> 0.25D;
            case 2 -> 0.28D;
            case 3 -> 0.32D;
            default -> 0.25D;
        };
    }

    private void updateWallClimbing() {
        if (isBeingWatched()) {
            setWallClimbing(false);
            return;
        }

        boolean shouldClimb = this.horizontalCollision &&
                this.getTarget() != null &&
                !this.onGround() &&
                this.getDeltaMovement().y <= 0.0D;

        if (shouldClimb && !isWallClimbing()) {
            setWallClimbing(true);
        } else if (!shouldClimb && isWallClimbing()) {
            setWallClimbing(false);
        }

        // 壁登り中の物理処理
        if (isWallClimbing()) {
            performWallClimbing();
        }
    }

    private void performWallClimbing() {
        // 蜘蛛のような壁登り実装
        if (this.horizontalCollision) {
            Vec3 movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x, 0.2D, movement.z);
        }
    }

    private void updateSprintStatus() {
        LivingEntity target = this.getTarget();
        if (target instanceof Player player) {
            boolean playerSprinting = player.isSprinting();
            if (playerSprinting != isSprinting() && !isBeingWatched()) {
                setSprinting(playerSprinting);

                // スプリント状態に応じて速度調整
                double baseSpeed = getBaseSpeedForDay();
                double sprintMultiplier = playerSprinting ? 1.5D : 1.0D;
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * sprintMultiplier);
            }
        }
    }

    // バット装備
    private void equipBat() {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();
        tag.putInt("DayNumber", getDayNumber());
        tag.putBoolean("EntityBat", true);
        this.setItemInHand(InteractionHand.MAIN_HAND, batStack);
    }

    // 攻撃関連メソッド
    public boolean canAttack() {
        return attackCooldown <= 0;
    }

    public boolean canThrow() {
        return throwCooldown <= 0 && getDayNumber() >= 2; // 2日目以降
    }

    public boolean canJumpAttack() {
        return jumpCooldown <= 0 && getDayNumber() >= 3; // 3日目のみ
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    public void setThrowCooldown(int ticks) {
        this.throwCooldown = ticks;
    }

    public void setJumpCooldown(int ticks) {
        this.jumpCooldown = ticks;
    }

    // 投擲攻撃
    public void performThrowAttack(LivingEntity target) {
        if (!canThrow()) return;

        Vec3 direction = target.position().subtract(this.position()).normalize();
        TungBatProjectile projectile = new TungBatProjectile(this.level(), this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double speed = 1.5D + (getDayNumber() * 0.3D);
        projectile.shoot(direction.x, direction.y + 0.1D, direction.z, (float) speed, 1.0F);

        this.level().addFreshEntity(projectile);
        setThrowCooldown(60 + this.random.nextInt(40)); // 3-5秒のクールダウン
        setCurrentlyThrowing(true);

        // サウンド
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8F, 0.8F);
    }

    // ジャンプ攻撃
    public void performJumpAttack(LivingEntity target) {
        if (!canJumpAttack()) return;

        Vec3 direction = target.position().subtract(this.position()).normalize();
        double jumpPower = 1.2D;

        this.setDeltaMovement(direction.x * jumpPower, jumpPower, direction.z * jumpPower);
        setJumpCooldown(100 + this.random.nextInt(60)); // 5-8秒のクールダウン
        setCurrentlyJumping(true);

        // サウンドとパーティクル
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.0F);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // アニメーション制御
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<TungSahurEntity> animationState) {
        if (this.isDeadOrDying()) {
            animationState.getController().setAnimation(DEATH_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyAttacking()) {
            animationState.getController().setAnimation(ATTACK_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyThrowing()) {
            animationState.getController().setAnimation(THROW_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyJumping()) {
            animationState.getController().setAnimation(JUMP_ATTACK_ANIM);
            return PlayState.CONTINUE;
        }

        if (isWallClimbing()) {
            animationState.getController().setAnimation(CLIMBING_ANIM);
            return PlayState.CONTINUE;
        }

        if (isSprinting() && animationState.isMoving()) {
            animationState.getController().setAnimation(SPRINT_ANIM);
            return PlayState.CONTINUE;
        }

        if (animationState.isMoving()) {
            animationState.getController().setAnimation(WALK_ANIM);
            return PlayState.CONTINUE;
        }

        // 日数に応じたIdleアニメーション
        RawAnimation idleAnim = switch (getDayNumber()) {
            case 2 -> IDLE_DAY2_ANIM;
            case 3 -> IDLE_DAY3_ANIM;
            default -> IDLE_DAY1_ANIM;
        };

        animationState.getController().setAnimation(idleAnim);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ゲッター・セッター
    public int getDayNumber() {
        return this.entityData.get(DAY_NUMBER);
    }

    public void setDayNumber(int day) {
        this.entityData.set(DAY_NUMBER, day);
        updateScaleForDay(day);
        updateAttributesForDay(day);
        equipBat();
    }

    private void updateScaleForDay(int day) {
        float scale = switch (day) {
            case 1 -> 1.0F;     // 普通サイズ
            case 2 -> 1.3F;     // 中くらいサイズ
            case 3 -> 1.7F;     // かなり大きい
            default -> 1.0F;
        };
        setScaleFactor(scale);
    }

    private void updateAttributesForDay(int day) {
        // 日数に応じて能力値を調整
        switch (day) {
            case 1:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0D);
                break;
            case 2:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9.0D);
                break;
            case 3:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12.0D);
                break;
        }

        // HPを最大値に設定
        this.setHealth(this.getMaxHealth());
    }

    public float getScaleFactor() {
        return this.entityData.get(SCALE_FACTOR);
    }

    public void setScaleFactor(float scale) {
        this.entityData.set(SCALE_FACTOR, scale);
    }

    public boolean isWallClimbing() {
        return this.entityData.get(IS_WALL_CLIMBING);
    }

    public void setWallClimbing(boolean climbing) {
        this.entityData.set(IS_WALL_CLIMBING, climbing);
    }

    public boolean isBeingWatched() {
        return this.entityData.get(IS_BEING_WATCHED);
    }

    public void setBeingWatched(boolean watched) {
        this.entityData.set(IS_BEING_WATCHED, watched);
    }

    public boolean isCurrentlyAttacking() {
        return this.entityData.get(IS_CURRENTLY_ATTACKING);
    }

    public void setCurrentlyAttacking(boolean attacking) {
        this.entityData.set(IS_CURRENTLY_ATTACKING, attacking);
    }

    public boolean isCurrentlyThrowing() {
        return this.entityData.get(IS_CURRENTLY_THROWING);
    }

    public void setCurrentlyThrowing(boolean throwing) {
        this.entityData.set(IS_CURRENTLY_THROWING, throwing);
    }

    public boolean isCurrentlyJumping() {
        return this.entityData.get(IS_CURRENTLY_JUMPING);
    }

    public void setCurrentlyJumping(boolean jumping) {
        this.entityData.set(IS_CURRENTLY_JUMPING, jumping);
    }

    @Override
    public boolean isSprinting() {
        return this.entityData.get(IS_SPRINTING);
    }

    public void setSprinting(boolean sprinting) {
        this.entityData.set(IS_SPRINTING, sprinting);
    }

    // NBT保存
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DayNumber", getDayNumber());
        tag.putFloat("ScaleFactor", getScaleFactor());
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("ThrowCooldown", throwCooldown);
        tag.putInt("JumpCooldown", jumpCooldown);
        tag.putBoolean("IsWallClimbing", isWallClimbing());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDayNumber(tag.getInt("DayNumber"));
        setScaleFactor(tag.getFloat("ScaleFactor"));
        attackCooldown = tag.getInt("AttackCooldown");
        throwCooldown = tag.getInt("ThrowCooldown");
        jumpCooldown = tag.getInt("JumpCooldown");
        setWallClimbing(tag.getBoolean("IsWallClimbing"));
    }

    // スポーン条件
    public static boolean checkTungSahurSpawnRules(EntityType<TungSahurEntity> entityType,
                                                   ServerLevelAccessor level,
                                                   MobSpawnType spawnType,
                                                   BlockPos pos,
                                                   RandomSource random) {
        return pos.getY() <= 50 &&
                isDarkEnoughToSpawn(level, pos, random) &&
                checkMobSpawnRules(entityType, level, spawnType, pos, random);
    }

    public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
            return false;
        } else {
            int light = level.getLevel().isThundering() ?
                    level.getLevel().getMaxLocalRawBrightness(pos, 10) :
                    level.getMaxLocalRawBrightness(pos);
            return light <= random.nextInt(8);
        }
    }

    // サウンド
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // 見られている時はダメージ増加
        if (isBeingWatched()) {
            amount *= 1.3F;
        }
        return super.hurt(damageSource, amount);
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        // プレイヤーに効果を付与させないための処理
        // このエンティティ自体は効果を受けられる
        return true;
    }
}