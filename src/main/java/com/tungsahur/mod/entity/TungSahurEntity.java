// TungSahurEntity.java の修正版 - バット表示を確実にする

package com.tungsahur.mod.entity;

import com.tungsahur.mod.entity.goals.*;
import com.tungsahur.mod.entity.projectiles.TungBatProjectile;
import com.tungsahur.mod.items.ModItems;
import com.tungsahur.mod.saveddata.DayCountSavedData;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

public class TungSahurEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> EVOLUTION_STAGE = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE_FACTOR = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_BEING_WATCHED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);

    // アニメーション定義
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.tung_sahur.idle");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.tung_sahur.walk");
    protected static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.tung_sahur.attack");
    protected static final RawAnimation DRUM_ANIM = RawAnimation.begin().thenPlay("animation.tung_sahur.drum");
    protected static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlay("animation.tung_sahur.throw");
    protected static final RawAnimation CLIMB_ANIM = RawAnimation.begin().thenLoop("animation.tung_sahur.climb");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int drumSoundTimer = 0;
    private int attackTimer = 0;
    private int throwTimer = 0;
    private boolean isClimbing = false;
    private Player targetPlayer = null;
    private int watchTimer = 0;

    // 自然な動きのための追加フィールド
    private int stuckTimer = 0;
    private Vec3 lastPosition = Vec3.ZERO;
    private boolean isStuck = false;

    // バット装備確認用
    private int batCheckTimer = 0;

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .build();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TungSahurAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new TungSahurJumpAttackGoal(this));
        this.goalSelector.addGoal(3, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(4, new TungSahurDrumAttackGoal(this));
        this.goalSelector.addGoal(5, new TungSahurClimbGoal(this));
        this.goalSelector.addGoal(6, new ImprovedWatchPlayerGoal(this, 0.8D, 32.0F));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6D, 120));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(EVOLUTION_STAGE, 0);
        this.entityData.define(SCALE_FACTOR, 1.0F);
        this.entityData.define(IS_BEING_WATCHED, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // より頻繁にバットをチェック
            batCheckTimer++;
            if (batCheckTimer >= 10) { // 0.5秒ごと
                ensureBatEquipped();
                batCheckTimer = 0;
            }

            updateEvolutionStage();
            updatePlayerWatchStatus();
            updateMovementSpeed();
            updateDrumSound();
            updateClimbingState();
            updateStuckDetection();
        }
    }

    /**
     * バットを確実に装備させる（改良版）
     */
    private void ensureBatEquipped() {
        ItemStack mainHand = this.getMainHandItem();

        // バットが装備されていない、または間違ったアイテムの場合
        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get())) {
            ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
            enhanceBatForEvolution(batStack, getEvolutionStage());

            // 装備を設定
            this.setItemSlot(EquipmentSlot.MAINHAND, batStack);

            // ドロップ確率を設定
            this.setDropChance(EquipmentSlot.MAINHAND, 0.15F);

            // 装備変更をクライアントに即座に同期
            if (this.level() instanceof ServerLevel) {
                this.level().broadcastEntityEvent(this, (byte) 55); // カスタムイベント
            }

            // デバッグログ
            if (this.level() instanceof ServerLevel) {
                System.out.println("TungSahur equipped bat: " + batStack.getItem().toString());
            }
        }
    }

    /**
     * 進化段階に応じてバットを強化
     */
    private void enhanceBatForEvolution(ItemStack batStack, int evolutionStage) {
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        batStack.getTag().putInt("TungSahurStage", evolutionStage);

        switch (evolutionStage) {
            case 1 -> {
                batStack.getTag().putBoolean("Bloodstained", true);
                batStack.getTag().putInt("KillCount", 10);
                batStack.getTag().putInt("BloodLevel", 1);
            }
            case 2 -> {
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putInt("KillCount", 25);
                batStack.getTag().putBoolean("DarkEnergy", true);
                batStack.getTag().putInt("BloodLevel", 3);
                batStack.getTag().putBoolean("SoulBound", true);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        super.handleEntityEvent(event);

        // カスタムイベント55: 装備同期
        if (event == 55) {
            // クライアント側で装備を再確認
            if (this.level().isClientSide) {
                // 装備が正しく表示されるよう強制更新
                this.refreshDimensions();
            }
        }
    }

    /**
     * スタック検出と対処
     */
    private void updateStuckDetection() {
        Vec3 currentPos = this.position();

        if (lastPosition.distanceTo(currentPos) < 0.1) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
            isStuck = false;
        }

        lastPosition = currentPos;

        if (stuckTimer > 100) {
            isStuck = true;
            handleStuckSituation();
        }
    }

    /**
     * スタック時の対処
     */
    private void handleStuckSituation() {
        if (this.getTarget() != null) {
            Vec3 targetPos = this.getTarget().position();
            Vec3 direction = targetPos.subtract(this.position()).normalize();

            for (int i = 1; i <= 5; i++) {
                Vec3 newPos = this.position().add(direction.scale(i));
                BlockPos blockPos = BlockPos.containing(newPos);

                if (this.level().isEmptyBlock(blockPos) &&
                        this.level().isEmptyBlock(blockPos.above())) {
                    this.setPos(newPos.x, newPos.y, newPos.z);
                    break;
                }
            }
        }

        stuckTimer = 0;
        isStuck = false;
    }

    private void updateEvolutionStage() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData data = DayCountSavedData.get(serverLevel);
            int currentDay = data.getDayCount();
            int newStage = Math.min(currentDay, 2);

            if (newStage != getEvolutionStage()) {
                setEvolutionStage(newStage);
                evolveToStage(newStage);
                ensureBatEquipped(); // 進化時に確実にバット更新
            }
        }
    }

    private void updatePlayerWatchStatus() {
        Player nearestPlayer = this.level().getNearestPlayer(this, 32.0D);
        boolean isWatched = false;

        if (nearestPlayer != null) {
            isWatched = isPlayerLookingAt(nearestPlayer);
            this.targetPlayer = nearestPlayer;
        }

        setBeingWatched(isWatched);

        if (isWatched) {
            watchTimer++;
        } else {
            watchTimer = 0;
        }
    }

    private boolean isPlayerLookingAt(Player player) {
        Vec3 playerEyes = player.getEyePosition();
        Vec3 lookDirection = player.getViewVector(1.0F);
        Vec3 targetPos = this.position().add(0, this.getBbHeight() / 2, 0);
        Vec3 toTarget = targetPos.subtract(playerEyes);

        double distance = toTarget.length();
        if (distance > 32.0D) return false;

        double dot = lookDirection.normalize().dot(toTarget.normalize());
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));

        return angle < Math.toRadians(30) && this.hasLineOfSight(player);
    }

    private void updateMovementSpeed() {
        double baseSpeed = 0.25D;
        double multiplier = 1.0D;

        if (isBeingWatched() && targetPlayer != null) {
            double distance = this.distanceTo(targetPlayer);
            if (distance < 16.0D) {
                multiplier = 0.15D + (distance / 16.0D) * 0.35D;
            } else {
                multiplier = 0.5D;
            }
        } else {
            multiplier = 1.0D + (getEvolutionStage() * 0.3D);
        }

        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * multiplier);
    }

    private void updateDrumSound() {
        drumSoundTimer++;
        if (drumSoundTimer >= 120) {
            if (this.random.nextFloat() < 0.3F) {
                playDrumSound();
            }
            drumSoundTimer = 0;
        }
    }

    private void playDrumSound() {
        this.level().playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                SoundSource.HOSTILE, 0.8F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    private void updateClimbingState() {
        this.isClimbing = this.horizontalCollision && this.getTarget() != null;
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing || super.onClimbable();
    }

    private void evolveToStage(int stage) {
        float newScale = 1.0F + (stage * 0.5F);
        setScaleFactor(newScale);

        this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.0F, 0.5F);

        double healthMultiplier = 1.0D + (stage * 0.5D);
        double damageMultiplier = 1.0D + (stage * 0.3D);

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D * healthMultiplier);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0D * damageMultiplier);
        this.setHealth(this.getMaxHealth());
    }

    public void performThrowAttack(LivingEntity target) {
        if (throwTimer <= 0) {
            TungBatProjectile projectile = new TungBatProjectile(this.level(), this);

            double dx = target.getX() - this.getX();
            double dy = target.getY(0.5D) - projectile.getY();
            double dz = target.getZ() - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            projectile.shoot(dx, dy + distance * 0.2D, dz, 1.6F, 1.0F);
            this.level().addFreshEntity(projectile);

            throwTimer = 80;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (throwTimer > 0) {
            throwTimer--;
        }
        if (attackTimer > 0) {
            attackTimer--;
        }
    }

    // データアクセサー用メソッド
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

    public boolean isBeingWatched() {
        return this.entityData.get(IS_BEING_WATCHED);
    }

    public void setBeingWatched(boolean watched) {
        this.entityData.set(IS_BEING_WATCHED, watched);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EvolutionStage", getEvolutionStage());
        tag.putFloat("ScaleFactor", getScaleFactor());
        tag.putInt("ThrowTimer", throwTimer);
        tag.putInt("AttackTimer", attackTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setEvolutionStage(tag.getInt("EvolutionStage"));
        setScaleFactor(tag.getFloat("ScaleFactor"));
        throwTimer = tag.getInt("ThrowTimer");
        attackTimer = tag.getInt("AttackTimer");

        // データ読み込み後に即座にバットを装備
        if (!this.level().isClientSide) {
            // 少し遅延させて確実に装備
            this.level().scheduleTick(this.blockPosition(), this.level().getBlockState(this.blockPosition()).getBlock(), 1);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.NOTE_BLOCK_BASEDRUM.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {

        SpawnGroupData result = super.finalizeSpawn(world, difficulty, spawnType, spawnData, tag);

        // スポーン時に進化段階を設定
        if (world instanceof ServerLevel serverLevel) {
            DayCountSavedData data = DayCountSavedData.get(serverLevel);
            int currentDay = data.getDayCount();
            setEvolutionStage(Math.min(currentDay, 2));
            evolveToStage(getEvolutionStage());
        }

        // バットを確実に装備（複数回実行で確実に）
        ensureBatEquipped();

        // 少し遅延させてもう一度確認
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.blockPosition(),
                    serverLevel.getBlockState(this.blockPosition()).getBlock(), 5);
        }

        return result;
    }

    public static boolean canSpawn(EntityType<TungSahurEntity> entityType, LevelAccessor world,
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

    // GeckoLib アニメーション
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnimController));
        controllers.add(new AnimationController<>(this, "attack", 0, this::attackAnimController));
        controllers.add(new AnimationController<>(this, "special", 10, this::specialAnimController));
    }

    private PlayState movementAnimController(AnimationState<TungSahurEntity> state) {
        if (this.isClimbing) {
            return state.setAndContinue(CLIMB_ANIM);
        } else if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIM);
        } else {
            return state.setAndContinue(IDLE_ANIM);
        }
    }

    private PlayState attackAnimController(AnimationState<TungSahurEntity> state) {
        if (this.swinging) {
            return state.setAndContinue(ATTACK_ANIM);
        }
        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    private PlayState specialAnimController(AnimationState<TungSahurEntity> state) {
        if (drumSoundTimer > 100) {
            return state.setAndContinue(DRUM_ANIM);
        } else if (throwTimer > 60) {
            return state.setAndContinue(THROW_ANIM);
        }
        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}