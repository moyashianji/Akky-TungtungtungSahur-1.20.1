// TungSahurEntity.java - 完全版（全メソッド実装）
package com.tungsahur.mod.entity;

import com.tungsahur.mod.entity.goals.*;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

public class TungSahurEntity extends Monster implements GeoEntity {
    // データアクセサー
    private static final EntityDataAccessor<Integer> EVOLUTION_STAGE = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE_FACTOR = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_SPRINTING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ATTACKING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_THROWING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_JUMPING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_BEING_WATCHED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CLIMBING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);

    // アニメーション定数
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().then("death", Animation.LoopType.PLAY_ONCE);

    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation WALKING_ANIM = RawAnimation.begin().then("walk", Animation.LoopType.LOOP);
    private static final RawAnimation RUNNING_ANIM = RawAnimation.begin().then("sprint", Animation.LoopType.LOOP);
    private static final RawAnimation CLIMBING_ANIM = RawAnimation.begin().then("climbing", Animation.LoopType.LOOP);
    private static final RawAnimation PUNCH_ANIM = RawAnimation.begin().then("punch_right", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation BAT_SWING_ANIM = RawAnimation.begin().then("bat_swing", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation BAT_THROW_ANIM = RawAnimation.begin().then("bat_throw", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMPING_ANIM = RawAnimation.begin().then("jumping", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE1_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE2_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE3_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // 状態管理
    private int attackCooldown = 0;
    private int throwCooldown = 0;
    private int jumpCooldown = 0;
    private boolean isCurrentlyAttacking = false;
    private boolean isCurrentlyThrowing = false;
    private boolean isCurrentlyJumping = false;
    private int animationTimer = 0;
    private boolean forceEquipBat = false;
    private int batCheckTimer = 0;

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.xpReward = 15;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // Stage1でスポーンするようにデフォルト値を設定
        this.entityData.define(EVOLUTION_STAGE, 1);
        this.entityData.define(SCALE_FACTOR, 1.0F);
        this.entityData.define(IS_SPRINTING, false);
        this.entityData.define(IS_ATTACKING, false);
        this.entityData.define(IS_THROWING, false);
        this.entityData.define(IS_JUMPING, false);
        this.entityData.define(IS_BEING_WATCHED, false);
        this.entityData.define(IS_CLIMBING, false);
    }

    @Override
    protected void registerGoals() {
        // 移動・基本行動ゴール（優先度調整）
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TungSahurMeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(3, new TungSahurJumpAttackGoal(this));
        this.goalSelector.addGoal(4, new TungSahurClimbGoal(this));
        this.goalSelector.addGoal(5, new TungSahurSmartMoveToTargetGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new ImprovedRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // ターゲット選択ゴール
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            updateEvolutionStage();
            updateCooldowns();
            updateWatchedStatus();
            updateAnimationTimers();
            ensureBatEquipped();
            handleAutoJump();
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        // スポーン時に必ずstage1に設定
        this.setEvolutionStage(1);
        this.setScaleFactor(1.0F);

        // 初期装備としてバットを装備
        ItemStack bat = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        if (!bat.hasTag()) {
            bat.getOrCreateTag();
        }
        bat.getTag().putBoolean("TungSahurOwned", true);
        bat.getTag().putInt("TungSahurStage", 1);
        this.setItemSlot(EquipmentSlot.MAINHAND, bat);

        return result;
    }

    private void handleAutoJump() {
        if (!this.onGround() || this.getTarget() == null) return;

        if (this.horizontalCollision && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
            Vec3 lookDirection = this.getLookAngle();
            BlockPos frontPos = this.blockPosition().offset((int)lookDirection.x, 0, (int)lookDirection.z);
            BlockPos aboveFrontPos = frontPos.above();

            if (!this.level().getBlockState(frontPos).isAir() &&
                    this.level().getBlockState(aboveFrontPos).isAir() &&
                    this.level().getBlockState(aboveFrontPos.above()).isAir()) {

                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.42D, 0));
                this.hasImpulse = true;
            }
        }
    }

    private void ensureBatEquipped() {
        batCheckTimer++;
        if (batCheckTimer % 20 == 0 || forceEquipBat) { // 1秒ごとにチェック
            ItemStack mainHand = this.getMainHandItem();
            if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get()) || forceEquipBat) {
                ItemStack bat = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());

                if (!bat.hasTag()) {
                    bat.getOrCreateTag();
                }
                bat.getTag().putBoolean("TungSahurOwned", true);
                bat.getTag().putInt("TungSahurStage", this.getEvolutionStage());

                this.setItemSlot(EquipmentSlot.MAINHAND, bat);
                forceEquipBat = false;
            }
        }
    }

    private void updateEvolutionStage() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            int dayCount = dayData.getDayCount();

            int targetStage;
            if (dayCount >= 3) {
                targetStage = 2;
            } else if (dayCount >= 2) {
                targetStage = 1;
            } else {
                targetStage = 1; // 最小値を1に設定
            }

            if (targetStage != this.getEvolutionStage()) {
                this.setEvolutionStage(targetStage);
                updateStageBasedAttributes();
            }
        }
    }

    private void updateStageBasedAttributes() {
        int stage = this.getEvolutionStage();

        switch (stage) {
            case 1 -> {
                this.setScaleFactor(1.0F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D);
            }
            case 2 -> {
                this.setScaleFactor(1.15F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(75.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12.0D);
            }
            default -> {
                this.setScaleFactor(1.0F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D);
            }
        }

        this.setHealth(this.getMaxHealth());
    }

    private void updateCooldowns() {
        if (attackCooldown > 0) attackCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
    }

    private void updateAnimationTimers() {
        if (animationTimer > 0) {
            animationTimer--;
            if (animationTimer <= 0) {
                isCurrentlyAttacking = false;
                isCurrentlyThrowing = false;
                isCurrentlyJumping = false;
                setAttacking(false);
                setThrowing(false);
                setJumping(false);
            }
        }
    }

    private void updateWatchedStatus() {
        boolean wasWatched = this.isBeingWatched();
        boolean isWatched = false;

        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D))) {
            if (player.hasLineOfSight(this)) {
                Vec3 lookVec = player.getLookAngle().normalize();
                Vec3 directionToThis = this.position().subtract(player.position()).normalize();
                double dotProduct = lookVec.dot(directionToThis);

                if (dotProduct > 0.5D) {
                    isWatched = true;
                    break;
                }
            }
        }

        this.setBeingWatched(isWatched);

        if (isWatched && !wasWatched) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                    this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 0.3D
            );
        } else if (!isWatched && wasWatched) {
            updateStageBasedAttributes();
        }
    }

    // 攻撃メソッド
    public void performMeleeAttack(LivingEntity target) {
        if (attackCooldown <= 0) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

            if (getEvolutionStage() >= 1) {
                damage *= 1.5F;
            }
            if (getEvolutionStage() >= 2) {
                damage *= 1.8F;
            }

            boolean hasWeapon = !this.getMainHandItem().isEmpty();

            if (hasWeapon) {
                setAttacking(true);
                animationTimer = 25;
                playBatSwingAnimation();
            } else {
                setAttacking(true);
                animationTimer = 20;
                playPunchAnimation();
            }

            isCurrentlyAttacking = true;
            attackCooldown = 40 - (getEvolutionStage() * 5);

            target.hurt(this.damageSources().mobAttack(this), damage);

            if (hasWeapon) {
                applyBatEffects(target);
            }
        }
    }

    public void performThrowAttack(LivingEntity target) {
        if (throwCooldown <= 0 && getEvolutionStage() >= 1) {
            setThrowing(true);
            animationTimer = 30;
            isCurrentlyThrowing = true;
            playThrowAnimation();

            // 投擲物作成
            TungBatProjectile projectile = new TungBatProjectile(this.level(), this);

            double dx = target.getX() - this.getX();
            double dy = target.getY(0.5D) - projectile.getY();
            double dz = target.getZ() - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            projectile.shoot(dx, dy + distance * 0.15D, dz, 1.4F, 1.0F);
            this.level().addFreshEntity(projectile);

            throwCooldown = 100 - (getEvolutionStage() * 10);
            forceEquipBat = true;
        }
    }

    public void performJumpAttack(LivingEntity target) {
        if (jumpCooldown <= 0 && getEvolutionStage() >= 1 && this.onGround()) {
            setJumping(true);
            animationTimer = 35;
            isCurrentlyJumping = true;
            playJumpAnimation();

            Vec3 direction = target.position().subtract(this.position()).normalize();
            double jumpForce = 0.8D + (getEvolutionStage() * 0.2D);

            this.setDeltaMovement(
                    direction.x * jumpForce,
                    0.6D + (getEvolutionStage() * 0.1D),
                    direction.z * jumpForce
            );

            jumpCooldown = 120 - (getEvolutionStage() * 15);
        }
    }

    private void applyBatEffects(LivingEntity target) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.2, 0.2, 0.2, 0.1);

            switch (getEvolutionStage()) {
                case 1:
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                    break;
                case 2:
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    break;
            }
        }
    }

    // アニメーション再生メソッド
    private void playPunchAnimation() {
        // GeckoLibアニメーション制御で処理
    }

    private void playBatSwingAnimation() {
        // GeckoLibアニメーション制御で処理
    }

    private void playThrowAnimation() {
        // GeckoLibアニメーション制御で処理
    }

    private void playJumpAnimation() {
        // GeckoLibアニメーション制御で処理
    }

    // 壁登り機能
    @Override
    protected void jumpFromGround() {
        super.jumpFromGround();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY(), this.getZ(),
                    5, 0.3, 0.1, 0.3, 0.1);
        }
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing() || super.onClimbable();
    }

    public boolean canClimbWalls() {
        return this.getEvolutionStage() >= 1;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.horizontalCollision && this.getTarget() != null) {
            double heightDiff = this.getTarget().getY() - this.getY();
            if (heightDiff > 1.0D && !isBeingWatched()) {
                this.setDeltaMovement(
                        this.getDeltaMovement().x,
                        0.2D,
                        this.getDeltaMovement().z
                );
                setClimbing(true);
            } else {
                setClimbing(false);
            }
        } else {
            setClimbing(false);
        }
    }

    // 能力チェックメソッド
    public boolean canAttack() {
        return attackCooldown <= 0;
    }

    public boolean canThrow() {
        return throwCooldown <= 0 && getEvolutionStage() >= 1;
    }

    public boolean canJump() {
        return jumpCooldown <= 0 && getEvolutionStage() >= 1 && this.onGround();
    }

    // ゲッター・セッター
    public int getEvolutionStage() {
        return this.entityData.get(EVOLUTION_STAGE);
    }

    public void setEvolutionStage(int stage) {
        this.entityData.set(EVOLUTION_STAGE, Math.max(1, stage));
    }

    public float getScaleFactor() {
        return this.entityData.get(SCALE_FACTOR);
    }

    public void setScaleFactor(float scale) {
        this.entityData.set(SCALE_FACTOR, scale);
        this.refreshDimensions();
    }

    public boolean isSprinting() {
        return this.entityData.get(IS_SPRINTING);
    }

    public void setSprinting(boolean sprinting) {
        this.entityData.set(IS_SPRINTING, sprinting);
    }

    public boolean isAttacking() {
        return this.entityData.get(IS_ATTACKING);
    }

    public void setAttacking(boolean attacking) {
        this.entityData.set(IS_ATTACKING, attacking);
    }

    public boolean isThrowing() {
        return this.entityData.get(IS_THROWING);
    }

    public void setThrowing(boolean throwing) {
        this.entityData.set(IS_THROWING, throwing);
    }

    public boolean isJumping() {
        return this.entityData.get(IS_JUMPING);
    }

    public void setJumping(boolean jumping) {
        this.entityData.set(IS_JUMPING, jumping);
    }

    public boolean isBeingWatched() {
        return this.entityData.get(IS_BEING_WATCHED);
    }

    public void setBeingWatched(boolean watched) {
        this.entityData.set(IS_BEING_WATCHED, watched);
    }

    public boolean isClimbing() {
        return this.entityData.get(IS_CLIMBING);
    }

    public void setClimbing(boolean climbing) {
        this.entityData.set(IS_CLIMBING, climbing);
    }

    // NBT保存・読み込み
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("EvolutionStage", this.getEvolutionStage());
        compound.putFloat("ScaleFactor", this.getScaleFactor());
        compound.putInt("AttackCooldown", attackCooldown);
        compound.putInt("ThrowCooldown", throwCooldown);
        compound.putInt("JumpCooldown", jumpCooldown);
        compound.putBoolean("IsBeingWatched", this.isBeingWatched());
        compound.putBoolean("IsClimbing", this.isClimbing());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setEvolutionStage(Math.max(1, compound.getInt("EvolutionStage")));
        this.setScaleFactor(compound.getFloat("ScaleFactor"));
        attackCooldown = compound.getInt("AttackCooldown");
        throwCooldown = compound.getInt("ThrowCooldown");
        jumpCooldown = compound.getInt("JumpCooldown");
        this.setBeingWatched(compound.getBoolean("IsBeingWatched"));
        this.setClimbing(compound.getBoolean("IsClimbing"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // GeckoLib アニメーション制御
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnimController));
        controllers.add(new AnimationController<>(this, "action", 0, this::actionAnimController));
        controllers.add(new AnimationController<>(this, "death", 0, this::deathAnimController));
    }

    private PlayState movementAnimController(AnimationState<TungSahurEntity> state) {
        // 死亡中またはアクション中は移動アニメーション停止
        if (this.isDeadOrDying() || isCurrentlyAttacking || isCurrentlyThrowing || isCurrentlyJumping) {
            return PlayState.STOP;
        }

        // 壁登り中
        if (isClimbing()) {
            return state.setAndContinue(CLIMBING_ANIM);
        }

        // 移動中のアニメーション
        if (state.isMoving()) {
            // ターゲットに応じた走行判定
            boolean shouldSprint = shouldSprintToTarget();

            if (shouldSprint || this.isSprinting()) {
                this.setSprinting(true);
                return state.setAndContinue(RUNNING_ANIM);
            } else {
                this.setSprinting(false);
                return state.setAndContinue(WALKING_ANIM);
            }
        }

        // 待機アニメーション（進化段階に応じて変更）
        this.setSprinting(false);
        return state.setAndContinue(getIdleAnimationByStage());
    }

    private PlayState actionAnimController(AnimationState<TungSahurEntity> state) {
        // 死亡中は停止
        if (this.isDeadOrDying()) {
            return PlayState.STOP;
        }

        if (isCurrentlyThrowing) {
            return state.setAndContinue(BAT_THROW_ANIM);
        }

        if (isCurrentlyJumping) {
            return state.setAndContinue(JUMPING_ANIM);
        }

        if (isCurrentlyAttacking) {
            ItemStack mainHand = this.getMainHandItem();
            if (mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
                return state.setAndContinue(BAT_SWING_ANIM);
            } else {
                return state.setAndContinue(PUNCH_ANIM);
            }
        }

        return PlayState.STOP;
    }

    private PlayState deathAnimController(AnimationState<TungSahurEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH_ANIM);
        }
        return PlayState.STOP;
    }

    /**
     * 進化段階に応じた待機アニメーションを取得
     */
    private RawAnimation getIdleAnimationByStage() {
        return switch (this.getEvolutionStage()) {
            case 2 -> IDLE3_ANIM;  // 第三形態
            case 1 -> IDLE2_ANIM;  // 第二形態
            default -> IDLE1_ANIM; // 第一形態
        };
    }

    /**
     * ターゲットに応じてスプリントすべきかどうかを判定
     */
    private boolean shouldSprintToTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) return false;

        // ターゲットとの距離
        double distance = this.distanceTo(target);

        // ターゲットがプレイヤーで走っている場合
        if (target instanceof Player player) {
            // プレイヤーが走っている（スプリント中）場合
            if (player.isSprinting()) {
                return true;
            }

            // プレイヤーの移動速度が一定以上の場合
            double playerSpeed = player.getDeltaMovement().horizontalDistanceSqr();
            if (playerSpeed > 0.05D) { // 通常歩行より速い
                return true;
            }
        }

        // 距離が離れている場合（5ブロック以上）
        if (distance > 5.0D) {
            return true;
        }

        // 高低差がある場合
        double heightDiff = Math.abs(target.getY() - this.getY());
        if (heightDiff > 2.0D) {
            return true;
        }

        // ターゲットが逃げている場合（前回の位置より遠くなった）
        CompoundTag nbt = this.getPersistentData();
        if (nbt.contains("LastTargetDistance")) {
            double lastDistance = nbt.getDouble("LastTargetDistance");
            if (distance > lastDistance + 1.0D) { // 1ブロック以上離れた
                nbt.putDouble("LastTargetDistance", distance);
                return true;
            }
            nbt.putDouble("LastTargetDistance", distance);
        } else {
            nbt.putDouble("LastTargetDistance", distance);
        }

        // 見られていない場合は積極的に
        if (!this.isBeingWatched()) {
            return distance > 3.0D;
        }

        return false;
    }

    // スポーンルール
    public static boolean checkTungSahurSpawnRules(EntityType<TungSahurEntity> entityType,
                                                   ServerLevelAccessor level, MobSpawnType spawnType,
                                                   BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(entityType, level, spawnType, pos, random) &&
                level.getBrightness(LightLayer.BLOCK, pos) <= 7;
    }
}

/**
 * 改善されたランダム徘徊ゴール
 */
class ImprovedRandomStrollGoal extends RandomStrollGoal {
    public ImprovedRandomStrollGoal(PathfinderMob mob, double speedModifier) {
        super(mob, speedModifier);
    }

    @Override
    protected Vec3 getPosition() {
        Vec3 basePos = super.getPosition();
        if (basePos == null) return null;

        BlockPos targetPos = BlockPos.containing(basePos);
        int heightDiff = targetPos.getY() - this.mob.blockPosition().getY();
        if (heightDiff <= 1 && heightDiff >= -3) {
            return basePos;
        }

        return null;
    }
}