// TungSahurEntity.java - 完全改修版（アニメーション対応・新攻撃システム）
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

    // アニメーション定義
    protected static final RawAnimation IDLE1_ANIM = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation IDLE2_ANIM = RawAnimation.begin().thenLoop("idle2");
    protected static final RawAnimation IDLE3_ANIM = RawAnimation.begin().thenLoop("idle3");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation SPRINT_ANIM = RawAnimation.begin().thenLoop("sprint");
    protected static final RawAnimation PUNCH_RIGHT_ANIM = RawAnimation.begin().thenPlay("punch_right");
    protected static final RawAnimation BAT_SWING_ANIM = RawAnimation.begin().thenPlay("bat_swing");
    protected static final RawAnimation BAT_THROW_ANIM = RawAnimation.begin().thenPlay("bat_throw");
    protected static final RawAnimation JUMPING_ANIM = RawAnimation.begin().thenPlay("jumping");
    protected static final RawAnimation CLIMBING_ANIM = RawAnimation.begin().thenLoop("climbing"); // 壁登りアニメーション
    protected static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlay("death");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // 攻撃タイマー
    private int attackCooldown = 0;
    private int throwCooldown = 0;
    private int jumpCooldown = 0;

    // アニメーション状態
    private int animationTimer = 0;
    private boolean isCurrentlyAttacking = false;
    private boolean isCurrentlyThrowing = false;
    private boolean isCurrentlyJumping = false;

    // バット装備管理
    private int batCheckTimer = 0;
    private boolean forceEquipBat = false;

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(EVOLUTION_STAGE, 0);
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
        // 移動・基本行動ゴール
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new TungSahurMeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(4, new TungSahurJumpAttackGoal(this));
        this.goalSelector.addGoal(5, new TungSahurClimbGoal(this)); // 壁登りゴール追加
        this.goalSelector.addGoal(6, new TungSahurSmartMoveToTargetGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // ターゲット選択ゴール
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
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
            updateBatEquipment();
            updateAnimationTimers();
            updatePlayerWatchStatus(); // プレイヤー視線チェック
            updateMovementSpeed(); // 速度更新（視線状態を考慮）
        }
    }

    private void updatePlayerWatchStatus() {
        Player nearestPlayer = this.level().getNearestPlayer(this, 32.0D);
        boolean isWatched = false;

        if (nearestPlayer != null) {
            isWatched = isPlayerLookingAt(nearestPlayer);
        }

        // 視線状態をデータに保存
        this.entityData.set(IS_BEING_WATCHED, isWatched);

        // 視線状態の変化に応じた効果
        if (isWatched && nearestPlayer != null) {
            // 見られている時の効果
            double distance = this.distanceTo(nearestPlayer);

            // 近すぎる場合は少し後退する行動
            if (distance < 3.0D && this.getRandom().nextFloat() < 0.1F) {
                Vec3 awayDirection = this.position().subtract(nearestPlayer.position()).normalize();
                Vec3 retreatPos = this.position().add(awayDirection.scale(1.5D));
                this.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, 0.3D);
            }

            // 見られている時のパーティクル効果（控えめに）
            if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 40 == 0) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX(), this.getY() + this.getBbHeight() * 0.8, this.getZ(),
                        1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private void updateEvolutionStage() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            int currentDay = dayData.getDayCount();
            int newStage = Math.min(currentDay, 2); // 0, 1, 2 (1日目、2日目、3日目)

            if (newStage != getEvolutionStage()) {
                setEvolutionStage(newStage);
                evolveToStage(newStage);
            }
        }
    }

    private void updateBatEquipment() {
        batCheckTimer++;
        if (batCheckTimer >= 20 || forceEquipBat) {
            ItemStack mainHand = this.getMainHandItem();

            if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
                ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
                CompoundTag tag = new CompoundTag();
                tag.putInt("TungSahurStage", getEvolutionStage());
                batStack.setTag(tag);

                this.setItemSlot(EquipmentSlot.MAINHAND, batStack);
                forceEquipBat = false;
            }
            batCheckTimer = 0;
        }
    }

    private void updateAnimationTimers() {
        if (animationTimer > 0) {
            animationTimer--;
            if (animationTimer <= 0) {
                // アニメーション終了時の処理
                setAttacking(false);
                setThrowing(false);
                setJumping(false);
                isCurrentlyAttacking = false;
                isCurrentlyThrowing = false;
                isCurrentlyJumping = false;
            }
        }

        // クールダウン更新
        if (attackCooldown > 0) attackCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
    }

    private void updateMovementSpeed() {
        LivingEntity target = this.getTarget();
        double baseSpeed = 0.25D;
        double multiplier = 1.0D;
        boolean shouldSprint = false;

        // プレイヤーの視線による速度変化（最優先）
        Player nearestPlayer = this.level().getNearestPlayer(this, 32.0D);
        if (nearestPlayer != null && isPlayerLookingAt(nearestPlayer)) {
            double distance = this.distanceTo(nearestPlayer);

            // 視線を受けている間は距離に応じて大幅減速
            if (distance <= 20.0D) {
                // 距離が近いほど遅くなる（0.02～0.15の範囲）
                double distanceRatio = Math.max(0.1D, distance / 20.0D);
                multiplier = 0.02D + (distanceRatio * 0.13D);
                shouldSprint = false;

                // 非常に近い場合はほぼ静止
                if (distance <= 5.0D) {
                    multiplier = 0.01D + (distance / 5.0D) * 0.04D; // 0.01～0.05
                }
            }
        } else {
            // 視線から外れた場合は通常速度またはそれ以上
            if (target != null) {
                double distance = this.distanceTo(target);

                // 視線から外れた時は素早く移動
                if (distance > 8.0D || (getEvolutionStage() >= 1 && distance > 4.0D)) {
                    shouldSprint = true;
                    multiplier = 1.8D + (getEvolutionStage() * 0.3D); // 通常より速い
                } else {
                    multiplier = 1.2D + (getEvolutionStage() * 0.2D);
                }
            } else {
                // ターゲットがない場合
                multiplier = 1.0D;
            }
        }

        setSprinting(shouldSprint);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * multiplier);
    }

    private boolean isPlayerLookingAt(Player player) {
        Vec3 playerEyes = player.getEyePosition();
        Vec3 lookDirection = player.getViewVector(1.0F);
        Vec3 targetPos = this.position().add(0, this.getBbHeight() / 2, 0);
        Vec3 toTarget = targetPos.subtract(playerEyes);

        double distance = toTarget.length();
        if (distance > 32.0D) return false;

        // 視線の角度計算（30度の範囲）
        double dot = lookDirection.normalize().dot(toTarget.normalize());
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));

        // 視線が合っているかつ障害物がない
        return angle < Math.toRadians(30) && this.hasLineOfSight(player);
    }

    private void evolveToStage(int stage) {
        float newScale = 1.0F + (stage * 0.3F);
        setScaleFactor(newScale);

        this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.0F, 0.5F + stage * 0.2F);

        // ステータス調整
        double healthMultiplier = 1.0D + (stage * 0.4D);
        double damageMultiplier = 1.0D + (stage * 0.5D); // 3日目は大幅威力アップ

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D * healthMultiplier);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D * damageMultiplier);
        this.setHealth(this.getMaxHealth());

        forceEquipBat = true;
    }

    // 攻撃メソッド
    public void performMeleeAttack(LivingEntity target) {
        if (attackCooldown <= 0) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

            // 3日目は近接攻撃威力大幅アップ
            if (getEvolutionStage() >= 2) {
                damage *= 1.8F;
            }

            boolean hasWeapon = !this.getMainHandItem().isEmpty();

            if (hasWeapon) {
                setAttacking(true);
                animationTimer = 25; // bat_swing アニメーション時間
                playBatSwingAnimation();
            } else {
                setAttacking(true);
                animationTimer = 20; // punch_right アニメーション時間
                playPunchAnimation();
            }

            isCurrentlyAttacking = true;
            attackCooldown = 40 - (getEvolutionStage() * 5);

            // ダメージ適用
            target.hurt(this.damageSources().mobAttack(this), damage);

            // バット効果適用
            if (hasWeapon) {
                applyBatEffects(target);
            }
        }
    }

    public void performThrowAttack(LivingEntity target) {
        if (throwCooldown <= 0 && getEvolutionStage() >= 1) {
            setThrowing(true);
            animationTimer = 30; // bat_throw アニメーション時間
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
            forceEquipBat = true; // 新しいバットを装備
        }
    }

    public void performJumpAttack(LivingEntity target) {
        if (jumpCooldown <= 0 && getEvolutionStage() >= 1 && this.onGround()) {
            setJumping(true);
            animationTimer = 35; // jumping アニメーション時間
            isCurrentlyJumping = true;
            playJumpAnimation();

            // ジャンプ動作
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
            // パーティクル効果
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.2, 0.2, 0.2, 0.1);

            // 進化段階に応じたデバフ効果
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
        // punch_right アニメーション再生の準備
    }

    private void playBatSwingAnimation() {
        // bat_swing アニメーション再生の準備
    }

    private void playThrowAnimation() {
        // bat_throw アニメーション再生の準備
    }

    private void playJumpAnimation() {
        // jumping アニメーション再生の準備
    }

    // GeckoLib アニメーション制御
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnimController));
        controllers.add(new AnimationController<>(this, "action", 0, this::actionAnimController));
    }

    private PlayState movementAnimController(AnimationState<TungSahurEntity> state) {
        // アクション中は移動アニメーションを停止
        if (isCurrentlyAttacking || isCurrentlyThrowing || isCurrentlyJumping) {
            return PlayState.STOP;
        }

        // 壁登り中のアニメーション
        if (isClimbing()) {
            return state.setAndContinue(CLIMBING_ANIM);
        }

        if (state.isMoving()) {
            if (isSprinting()) {
                return state.setAndContinue(SPRINT_ANIM);
            } else {
                return state.setAndContinue(WALK_ANIM);
            }
        } else {
            // 日ごとの異なるアイドルアニメーション
            switch (getEvolutionStage()) {
                case 1:
                    return state.setAndContinue(IDLE2_ANIM);
                case 2:
                    return state.setAndContinue(IDLE3_ANIM);
                default:
                    return state.setAndContinue(IDLE1_ANIM);
            }
        }
    }

    private PlayState actionAnimController(AnimationState<TungSahurEntity> state) {
        if (isCurrentlyJumping && isJumping()) {
            return state.setAndContinue(JUMPING_ANIM);
        } else if (isCurrentlyThrowing && isThrowing()) {
            return state.setAndContinue(BAT_THROW_ANIM);
        } else if (isCurrentlyAttacking && isAttacking()) {
            // 武器の有無で攻撃アニメーション切り替え
            if (!this.getMainHandItem().isEmpty()) {
                return state.setAndContinue(BAT_SWING_ANIM);
            } else {
                return state.setAndContinue(PUNCH_RIGHT_ANIM);
            }
        }

        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        if (isSprinting()) {
            this.playSound(SoundEvents.ZOMBIE_STEP, 0.8F, 1.2F);
        } else {
            this.playSound(SoundEvents.ZOMBIE_STEP, 0.6F, 1.0F);
        }
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    // スポーン条件
    public static boolean checkTungSahurSpawnRules(EntityType<TungSahurEntity> entityType, LevelAccessor world,
                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!(world instanceof ServerLevelAccessor serverWorld)) {
            return false;
        }

        if (!(world instanceof Level level)) {
            return false;
        }

        long worldTime = level.getDayTime() % 24000;
        boolean isNight = worldTime >= 13000 && worldTime <= 23000;
        boolean isDark = world.getBrightness(LightLayer.BLOCK, pos) == 0;
        Player nearestPlayer = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0, false);
        boolean rarityCheck = random.nextFloat() < 0.08F;

        return isNight && isDark && nearestPlayer != null && rarityCheck &&
                Monster.checkMonsterSpawnRules(entityType, serverWorld, spawnType, pos, random);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getScaleFactor());
    }

    // NBTデータ保存・読み込み
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("EvolutionStage", getEvolutionStage());
        compound.putFloat("ScaleFactor", getScaleFactor());
        compound.putInt("AttackCooldown", attackCooldown);
        compound.putInt("ThrowCooldown", throwCooldown);
        compound.putInt("JumpCooldown", jumpCooldown);
        compound.putBoolean("IsBeingWatched", isBeingWatched());
        compound.putBoolean("IsClimbing", isClimbing());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setEvolutionStage(compound.getInt("EvolutionStage"));
        setScaleFactor(compound.getFloat("ScaleFactor"));
        attackCooldown = compound.getInt("AttackCooldown");
        throwCooldown = compound.getInt("ThrowCooldown");
        jumpCooldown = compound.getInt("JumpCooldown");
        setBeingWatched(compound.getBoolean("IsBeingWatched"));
        setClimbing(compound.getBoolean("IsClimbing"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // ゲッター・セッター
    public int getEvolutionStage() {
        return this.entityData.get(EVOLUTION_STAGE);
    }

    public void setEvolutionStage(int stage) {
        this.entityData.set(EVOLUTION_STAGE, stage);
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

    @Override
    public boolean onClimbable() {
        return isClimbing() || super.onClimbable();
    }

    /**
     * 蜘蛛のような壁登り能力 - horizontalCollisionを利用
     */
    @Override
    public void aiStep() {
        super.aiStep();

        // 壁登り状態の更新
        if (this.horizontalCollision && this.getTarget() != null) {
            double heightDiff = this.getTarget().getY() - this.getY();
            if (heightDiff > 1.0D && !isBeingWatched()) {
                // 壁に接触していて、ターゲットが上にいて、見られていない場合は登る
                this.setDeltaMovement(
                        this.getDeltaMovement().x,
                        0.2D, // 上昇速度
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

    // クールダウン確認メソッド
    public boolean canAttack() {
        return attackCooldown <= 0;
    }

    public boolean canThrow() {
        return throwCooldown <= 0 && getEvolutionStage() >= 1;
    }

    public boolean canJump() {
        return jumpCooldown <= 0 && getEvolutionStage() >= 1 && this.onGround();
    }
}