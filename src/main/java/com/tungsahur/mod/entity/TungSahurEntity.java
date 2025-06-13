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
        this.goalSelector.addGoal(1, new TungSahurAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(3, new TungSahurClimbGoal(this));
        this.goalSelector.addGoal(4, new TungSahurWatchPlayerGoal(this, 1.0D, 32.0F));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8D));

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
            updateEvolutionStage();
            updatePlayerWatchStatus();
            updateMovementSpeed();
            updateDrumSound();
            updateClimbingState();
        }
    }

    private void updateEvolutionStage() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData data = DayCountSavedData.get(serverLevel);
            int currentDay = data.getDayCount();
            int newStage = Math.min(currentDay, 2);

            if (newStage != getEvolutionStage()) {
                setEvolutionStage(newStage);
                evolveToStage(newStage);
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
                multiplier = 0.1D + (distance / 16.0D) * 0.4D;
            } else {
                multiplier = 0.5D;
            }
        } else {
            multiplier = 1.5D + (getEvolutionStage() * 0.5D);
        }

        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * multiplier);
    }

    private void updateDrumSound() {
        drumSoundTimer++;
        if (drumSoundTimer >= 80) { // 4秒ごと
            playDrumSound();
            drumSoundTimer = 0;
        }
    }

    private void playDrumSound() {
        this.level().playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.get(),
                SoundSource.HOSTILE, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    private void updateClimbingState() {
        this.isClimbing = this.horizontalCollision;
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing || super.onClimbable();
    }

    private void evolveToStage(int stage) {
        float newScale = 1.0F + (stage * 0.5F);
        setScaleFactor(newScale);

        // 進化時の効果音
        this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.0F, 0.5F);

        // ステータス強化
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

            throwTimer = 60; // 3秒のクールダウン
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

    public static boolean canSpawn(EntityType<TungSahurEntity> entityType, LevelAccessor world,
                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // LevelAccessorをServerLevelAccessorにキャスト
        if (!(world instanceof ServerLevelAccessor serverWorld)) {
            return false;
        }

        // ServerLevelAccessorをLevelにキャスト（getDayTime用）
        if (!(world instanceof Level level)) {
            return false;
        }

        // 夜間のみスポーン
        long worldTime = level.getDayTime() % 24000;
        boolean isNight = worldTime >= 13000 && worldTime <= 23000;

        // 暗い場所のみ
        boolean isDark = world.getBrightness(LightLayer.BLOCK, pos) == 0;

        // プレイヤーが近くにいる
        Player nearestPlayer = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0, false);

        // ゾンビより低い確率
        boolean rarityCheck = random.nextFloat() < 0.05F;

        return isNight && isDark && nearestPlayer != null && rarityCheck &&
                Monster.checkMonsterSpawnRules(entityType, serverWorld, spawnType, pos, random);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getScaleFactor());
    }

    @Override
    public void refreshDimensions() {
        super.refreshDimensions();
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
        // スポーン時に現在の日数に応じて進化
        if (world instanceof ServerLevel serverLevel) {
            DayCountSavedData data = DayCountSavedData.get(serverLevel);
            int currentDay = data.getDayCount();
            setEvolutionStage(Math.min(currentDay, 2));
            evolveToStage(getEvolutionStage());
        }

        // バットを手に持たせる
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.TUNG_SAHUR_BAT.get()));

        // バットのドロップ確率を設定（低確率）
        this.setDropChance(EquipmentSlot.MAINHAND, 0.1F);

        // 進化段階に応じてバットの見た目を強化
        ItemStack batStack = this.getMainHandItem();
        enhanceBatForEvolution(batStack, getEvolutionStage());

        return super.finalizeSpawn(world, difficulty, spawnType, spawnData, tag);
    }
    /**
     * 進化段階に応じてバットを強化
     */
    private void enhanceBatForEvolution(ItemStack batStack, int evolutionStage) {
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }

        // 進化段階をバットに記録
        batStack.getTag().putInt("TungSahurStage", evolutionStage);

        // 段階に応じた特殊効果
        switch (evolutionStage) {
            case 1 -> {
                batStack.getTag().putBoolean("Bloodstained", true);
                batStack.getTag().putInt("KillCount", 10); // 血痕レベル1
            }
            case 2 -> {
                batStack.getTag().putBoolean("Cursed", true);
                batStack.getTag().putInt("KillCount", 25); // 血痕レベル2.5
                batStack.getTag().putBoolean("DarkEnergy", true);
            }
        }
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
        if (drumSoundTimer > 70) {
            return state.setAndContinue(DRUM_ANIM);
        } else if (throwTimer > 50) {
            return state.setAndContinue(THROW_ANIM);
        }
        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    /**
     * 攻撃時のバット演出強化
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);

        if (result && !this.level().isClientSide) {
            // バット攻撃時の特殊演出
            performBatAttackEffects(target);

            // バットにキル数を追加
            ItemStack batStack = this.getMainHandItem();
            if (batStack.getItem() == ModItems.TUNG_SAHUR_BAT.get()) {
                incrementBatKillCount(batStack);
            }
        }

        return result;
    }

    /**
     * バット攻撃時の特殊演出
     */
    private void performBatAttackEffects(Entity target) {
        if (this.level() instanceof ServerLevel serverLevel) {
            // バットからの衝撃波
            spawnBatImpactParticles(serverLevel, target);

            // 進化段階に応じた追加効果
            int stage = getEvolutionStage();
            switch (stage) {
                case 1 -> spawnStage2BatEffects(serverLevel, target);
                case 2 -> spawnStage3BatEffects(serverLevel, target);
            }

            // バット専用攻撃音
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 1.0F, 0.8F);
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.8F, 1.2F);
        }
    }

    /**
     * バット衝撃時のパーティクル
     */
    private void spawnBatImpactParticles(ServerLevel serverLevel, Entity target) {
        // バットの位置を計算
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // バットからの衝撃パーティクル
        for (int i = 0; i < 15; i++) {
            double angle = i * Math.PI / 7.5;
            double velocityX = Math.cos(angle) * 0.8;
            double velocityZ = Math.sin(angle) * 0.8;

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    batX, batY, batZ, 1, velocityX, 0.3, velocityZ, 0.1);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    batX, batY, batZ, 1, velocityX * 0.5, 0.2, velocityZ * 0.5, 0.0);
        }

        // ターゲットへの直撃パーティクル
        for (int i = 0; i < 10; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 1.0;
            double offsetY = serverLevel.random.nextDouble() * target.getBbHeight();
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 1.0;

            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX() + offsetX, target.getY() + offsetY, target.getZ() + offsetZ,
                    1, 0.0, 0.2, 0.0, 0.0);
        }
    }

    /**
     * ステージ2バット効果
     */
    private void spawnStage2BatEffects(ServerLevel serverLevel, Entity target) {
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // 血の飛散パーティクル
        for (int i = 0; i < 20; i++) {
            double velocityX = (serverLevel.random.nextDouble() - 0.5) * 1.2;
            double velocityY = serverLevel.random.nextDouble() * 0.8 + 0.2;
            double velocityZ = (serverLevel.random.nextDouble() - 0.5) * 1.2;

            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    batX, batY, batZ, 1, velocityX, velocityY, velocityZ, 0.1);
        }

        // 暗黒エネルギーの放出
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double x = batX + Math.cos(angle) * 1.5;
            double z = batZ + Math.sin(angle) * 1.5;

            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x, batY, z, 1, 0.0, 0.3, 0.0, 0.0);
        }
    }

    /**
     * ステージ3バット効果
     */
    private void spawnStage3BatEffects(ServerLevel serverLevel, Entity target) {
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // 呪いの爆発
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                batX, batY, batZ, 3, 0.5, 0.5, 0.5, 0.0);

        // 暗黒の稲妻
        for (int i = 0; i < 12; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 2.0;
            double offsetY = serverLevel.random.nextDouble() * 3.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    batX + offsetX, batY + offsetY, batZ + offsetZ,
                    1, 0.0, 0.0, 0.0, 0.3);
        }

        // 呪いのオーラ拡散
        for (int ring = 1; ring <= 4; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = ring * 1.2;
                double x = batX + Math.cos(angle) * radius;
                double z = batZ + Math.sin(angle) * radius;
                double y = batY + ring * 0.2;

                serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                        x, y, z, 1, 0.0, 0.1, 0.0, 0.0);
            }
        }

        // 最終段階の恐怖音
        serverLevel.playSound(null, this.blockPosition(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /**
     * バットのキル数増加
     */
    private void incrementBatKillCount(ItemStack batStack) {
        if (!batStack.hasTag()) {
            batStack.getOrCreateTag();
        }
        int currentKills = batStack.getTag().getInt("KillCount");
        batStack.getTag().putInt("KillCount", currentKills + 1);

        // 一定キル数で特殊効果
        if (currentKills > 0 && currentKills % 5 == 0) {
            spawnBatEvolutionParticles();
        }
    }

    /**
     * バット進化時のパーティクル
     */
    private void spawnBatEvolutionParticles() {
        if (this.level() instanceof ServerLevel serverLevel) {
            double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
            double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
            double batY = this.getY() + 1.5;

            // バットが強化される演出
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI / 10;
                double radius = 2.0;
                double x = batX + Math.cos(angle) * radius;
                double z = batZ + Math.sin(angle) * radius;
                double y = batY + Math.sin(angle * 2) * 0.5;

                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        x, y, z, 1, 0.0, 0.2, 0.0, 0.0);
            }

            // 強化音
            serverLevel.playSound(null, this.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 0.5F, 1.5F);
        }
    }

    /**
     * 投擲攻撃時のバット放出演出
     */
    public void performStylishThrowAttack(LivingEntity target) {
        if (this.level() instanceof ServerLevel serverLevel) {
            // バットを投げる前の演出
            spawnThrowPreparationParticles(serverLevel);
        }

        // 実際の投擲攻撃
        this.performThrowAttack(target);

        if (this.level() instanceof ServerLevel serverLevel) {
            // バット投擲後の演出
            spawnThrowReleaseParticles(serverLevel);
        }
    }

    /**
     * 投擲準備のパーティクル
     */
    private void spawnThrowPreparationParticles(ServerLevel serverLevel) {
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // バット周りの充電エネルギー
        for (int i = 0; i < 15; i++) {
            double angle = i * Math.PI / 7.5;
            double radius = 1.0 + Math.sin(this.tickCount * 0.3) * 0.3;
            double x = batX + Math.cos(angle) * radius;
            double z = batZ + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    x, batY, z, 1, (batX - x) * 0.1, 0.0, (batZ - z) * 0.1, 0.0);
        }
    }

    /**
     * 投擲放出のパーティクル
     */
    private void spawnThrowReleaseParticles(ServerLevel serverLevel) {
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // 放出時の爆発
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                batX, batY, batZ, 1, 0.0, 0.0, 0.0, 0.0);

        // エネルギー放出
        for (int i = 0; i < 10; i++) {
            double velocityX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            double velocityY = serverLevel.random.nextDouble() * 0.3;
            double velocityZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;

            serverLevel.sendParticles(ParticleTypes.SOUL,
                    batX, batY, batZ, 1, velocityX, velocityY, velocityZ, 0.0);
        }
    }

    /**
     * 常時バット周りのオーラ
     */
    public void updateBatAura() {
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 10 == 0) {
            ItemStack batStack = this.getMainHandItem();
            if (batStack.getItem() == ModItems.TUNG_SAHUR_BAT.get()) {
                spawnBatAuraParticles(serverLevel, batStack);
            }
        }
    }

    /**
     * バットオーラのパーティクル
     */
    private void spawnBatAuraParticles(ServerLevel serverLevel, ItemStack batStack) {
        double batX = this.getX() + Math.sin(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batZ = this.getZ() + Math.cos(Math.toRadians(-this.getYRot() + 90)) * 0.8;
        double batY = this.getY() + 1.5;

        // 基本オーラ
        for (int i = 0; i < 2; i++) {
            double angle = this.tickCount * 0.1 + i * Math.PI;
            double radius = 0.5;
            double x = batX + Math.cos(angle) * radius;
            double z = batZ + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x, batY, z, 1, 0.0, 0.05, 0.0, 0.0);
        }

        // 進化段階に応じた追加オーラ
        if (batStack.hasTag()) {
            boolean cursed = batStack.getTag().getBoolean("Cursed");
            boolean darkEnergy = batStack.getTag().getBoolean("DarkEnergy");

            if (cursed || darkEnergy) {
                // 暗黒エネルギーのオーラ
                serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                        batX, batY, batZ, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}