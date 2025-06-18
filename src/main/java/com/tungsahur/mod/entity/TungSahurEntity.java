// TungSahurEntity.java - 滑らかな動きと壁登りの改良版
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().then("throw", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMP_ANIM = RawAnimation.begin().then("jump_attack", Animation.LoopType.PLAY_ONCE);

    // フィールド
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int attackCooldown = 0;
    private int throwCooldown = 0;
    private int jumpCooldown = 0;
    private int animationTimer = 0;
    private boolean isCurrentlyAttacking = false;
    private boolean isCurrentlyThrowing = false;
    private boolean isCurrentlyJumping = false;
    private boolean forceEquipBat = false;

    // 滑らかな動きのための変数
    private Vec3 lastTickPosition = Vec3.ZERO;
    private Vec3 smoothVelocity = Vec3.ZERO;
    private double currentSpeed = 0.0;
    private boolean wasTargeting = false;
    private int ticksSinceLastMovement = 0;

    // 壁登り用変数
    private boolean isWallClimbing = false;
    private Direction climbDirection = null;
    private int climbTicks = 0;
    private Vec3 climbStartPos = Vec3.ZERO;
    private boolean canPhaseClimb = false;

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.xpReward = 15;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        // スポーン時に適切な進化段階を設定
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);

            int appropriateStage;
            if (reason == MobSpawnType.COMMAND) {
                // /summonコマンドで召喚された場合
                if (dayData.isActive()) {
                    // ゲームが始まっている場合は現在の日数に応じたステージ
                    appropriateStage = calculateStageFromDayCount(dayData.getDayCount());
                } else {
                    // ゲームが始まっていない場合はステージ0（1日目相当）
                    appropriateStage = 0;
                }
            } else {
                // 自然スポーンの場合は現在の日数に応じたステージ
                appropriateStage = calculateStageFromDayCount(dayData.getDayCount());
            }

            this.setEvolutionStage(appropriateStage);
            updateStageBasedAttributes();

            // スポーン時のログ出力
            TungSahurMod.LOGGER.info("TungSahur spawned - Reason: {}, Stage: {}, Game Active: {}",
                    reason, appropriateStage, dayData.isActive());
        }

        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    private int calculateStageFromDayCount(int dayCount) {
        int stage = 0;
        if (dayCount >= 2) {
            stage = 2; // 3日目以降：最終形態
        } else if (dayCount >= 1) {
            stage = 1; // 2日目：強化形態
        } else {
            stage = 0; // 1日目：基本形態
        }

        // デバッグログ追加
        if (this.tickCount % 60 == 0) {
            TungSahurMod.LOGGER.debug("Stage calculation - Day count: {}, Calculated stage: {}", dayCount, stage);
        }

        return stage;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(EVOLUTION_STAGE, 0); // 0から開始（1日目）
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
        // より優先度を調整したAIゴール
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TungSahurMeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new TungSahurThrowGoal(this));
        this.goalSelector.addGoal(3, new TungSahurJumpAttackGoal(this));
        this.goalSelector.addGoal(4, new SmoothMoveToTargetGoal(this, 1.0D)); // カスタム移動ゴール
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // ターゲット選択ゴール
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
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
            ensureSingleBatEquipped();
            updateSmoothMovement();
            handleAdvancedClimbing();
        }
    }

    // 滑らかな移動システム
    private void updateSmoothMovement() {
        Vec3 currentPos = this.position();
        Vec3 posDelta = currentPos.subtract(this.lastTickPosition);
        this.currentSpeed = posDelta.length();
        this.lastTickPosition = currentPos;

        // 滑らかな速度補間
        this.smoothVelocity = this.smoothVelocity.scale(0.8).add(posDelta.scale(0.2));

        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (hasTarget) {
            updateTargetBasedMovement(target);
            this.wasTargeting = true;
            this.ticksSinceLastMovement = 0;
        } else {
            updateIdleMovement();
            this.wasTargeting = false;
            this.ticksSinceLastMovement++;
        }
    }

    private void updateTargetBasedMovement(LivingEntity target) {
        double distanceToTarget = this.distanceTo(target);
        double heightDiff = target.getY() - this.getY();

        // 見られている場合は視線による速度制御を優先
        if (isBeingWatched()) {
            // 視線制御中でも最低限の動きは維持
            handleWatchConstrainedMovement(target, distanceToTarget);
            return;
        }

        // 通常時の段階的な速度調整
        if (distanceToTarget > 15.0D) {
            // 遠距離：全力疾走
            setSprinting(true);
            adjustMovementSpeed(0.35D + (getEvolutionStage() * 0.05D));
        } else if (distanceToTarget > 8.0D) {
            // 中距離：プレイヤーの動きに反応
            boolean playerSprinting = target instanceof Player player && player.isSprinting();
            setSprinting(playerSprinting || distanceToTarget > 12.0D);
            adjustMovementSpeed(playerSprinting ? 0.32D : 0.28D + (getEvolutionStage() * 0.03D));
        } else if (distanceToTarget > 4.0D) {
            // 近距離：慎重な接近
            setSprinting(false);
            adjustMovementSpeed(0.22D + (getEvolutionStage() * 0.02D));
        } else {
            // 極近距離：攻撃準備
            setSprinting(false);
            adjustMovementSpeed(0.18D);
        }

        // 高低差がある場合の壁登り判定
        if (heightDiff > 2.5D && this.horizontalCollision && !isBeingWatched()) {
            initializeWallClimbing();
        }
    }

    private void handleWatchConstrainedMovement(LivingEntity target, double distanceToTarget) {
        // 見られている時の特別な移動制御
        setSprinting(false); // 見られている時はスプリント無効

        // 距離に応じた微細な調整（視線制御の上に更に制御を加える）
        if (distanceToTarget < 3.0D) {
            // 極近距離：ほぼ停止状態だが攻撃は可能
            double crawlSpeed = 0.02D + (getEvolutionStage() * 0.005D);
            double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            double finalSpeed = Math.min(currentSpeed, crawlSpeed);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(finalSpeed);

        } else if (distanceToTarget < 6.0D) {
            // 近距離：忍び寄り速度
            double sneakSpeed = 0.05D + (getEvolutionStage() * 0.01D);
            double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            double finalSpeed = Math.min(currentSpeed, sneakSpeed);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(finalSpeed);

        } else {
            // 中距離以上：視線による基本制御のみ適用
            // updateWatchedStatus()で既に制御されているのでそのまま
        }

        // 見られている時は壁登りを中止
        if (this.isWallClimbing) {
            stopWallClimbing();
        }
    }

    private void updateIdleMovement() {
        setSprinting(false);
        double idleSpeed = 0.15D + (this.ticksSinceLastMovement > 100 ? 0.05D : 0.0D);
        adjustMovementSpeed(idleSpeed);
    }

    private void adjustMovementSpeed(double targetSpeed) {
        double currentBaseSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        double smoothedSpeed = currentBaseSpeed * 0.7 + targetSpeed * 0.3; // 滑らかな補間
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(smoothedSpeed);
    }

    // 高度な壁登りシステム
    private void handleAdvancedClimbing() {
        if (this.isWallClimbing) {
            performWallClimbing();
        } else if (canPhaseClimb && getEvolutionStage() >= 1) {
            checkForWallClimbOpportunity();
        }
    }

    private void initializeWallClimbing() {
        if (!canClimbWalls() || isBeingWatched()) return;

        Direction bestDirection = findBestClimbDirection();
        if (bestDirection != null) {
            this.isWallClimbing = true;
            this.climbDirection = bestDirection;
            this.climbTicks = 0;
            this.climbStartPos = this.position();
            this.canPhaseClimb = true;
            setClimbing(true);

            // 登攀開始音
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.6F, 1.2F);
        }
    }

    private Direction findBestClimbDirection() {
        BlockPos entityPos = this.blockPosition();
        LivingEntity target = this.getTarget();
        Direction bestDirection = null;
        double bestScore = -1.0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isValidClimbingWall(entityPos, direction)) {
                double score = calculateClimbScore(direction, target);
                if (score > bestScore) {
                    bestScore = score;
                    bestDirection = direction;
                }
            }
        }

        return bestDirection;
    }

    private boolean isValidClimbingWall(BlockPos entityPos, Direction direction) {
        BlockPos wallPos = entityPos.relative(direction);
        BlockState wallBlock = this.level().getBlockState(wallPos);

        // 固体ブロックか確認
        if (!wallBlock.isSolid()) return false;

        // 登攀可能な高さをチェック（少なくとも3ブロック以上）
        int wallHeight = 0;
        for (int y = 0; y < 8; y++) {
            BlockPos checkPos = wallPos.above(y);
            if (this.level().getBlockState(checkPos).isSolid()) {
                wallHeight++;
            } else {
                break;
            }
        }

        // 壁の上部に着地スペースがあるかチェック
        BlockPos topPos = wallPos.above(wallHeight);
        boolean hasLandingSpace = this.level().getBlockState(topPos).isAir() &&
                this.level().getBlockState(topPos.above()).isAir();

        return wallHeight >= 3 && hasLandingSpace;
    }

    private double calculateClimbScore(Direction direction, LivingEntity target) {
        if (target == null) return 0.0;

        Vec3 directionVec = Vec3.atLowerCornerOf(direction.getNormal());
        Vec3 toTarget = target.position().subtract(this.position()).normalize();

        // ターゲットへの方向との一致度
        double alignment = directionVec.dot(toTarget);

        // 壁の高さボーナス
        BlockPos wallPos = this.blockPosition().relative(direction);
        int wallHeight = getWallHeight(wallPos);
        double heightBonus = Math.min(wallHeight * 0.1, 0.5);

        return alignment + heightBonus;
    }

    private int getWallHeight(BlockPos wallBase) {
        int height = 0;
        for (int y = 0; y < 15; y++) {
            if (this.level().getBlockState(wallBase.above(y)).isSolid()) {
                height++;
            } else {
                break;
            }
        }
        return height;
    }

    private void performWallClimbing() {
        this.climbTicks++;

        if (this.climbDirection == null || !isValidClimbingWall(this.blockPosition(), this.climbDirection)) {
            stopWallClimbing();
            return;
        }

        // 雲のような滑らかな壁登り移動
        Vec3 climbVelocity = calculateClimbVelocity();
        this.setDeltaMovement(climbVelocity);

        // 登攀パーティクル効果
        if (this.climbTicks % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
            spawnClimbingParticles(serverLevel);
        }

        // 登攀完了判定
        if (hasReachedWallTop() || this.climbTicks > 100) {
            completeWallClimbing();
        }
    }

    private Vec3 calculateClimbVelocity() {
        // 基本的な上昇速度
        double upwardSpeed = 0.15D + (getEvolutionStage() * 0.05D);

        // 壁に沿った移動
        Vec3 wallDirection = Vec3.atLowerCornerOf(this.climbDirection.getNormal());
        double wallSpeed = 0.05D;

        // ターゲットへの微調整
        Vec3 targetAdjustment = Vec3.ZERO;
        if (this.getTarget() != null) {
            Vec3 toTarget = this.getTarget().position().subtract(this.position());
            targetAdjustment = toTarget.normalize().scale(0.02D);
        }

        return new Vec3(
                wallDirection.x * wallSpeed + targetAdjustment.x,
                upwardSpeed,
                wallDirection.z * wallSpeed + targetAdjustment.z
        );
    }

    private void spawnClimbingParticles(ServerLevel serverLevel) {
        // 壁を登る際のパーティクル効果
        Vec3 wallOffset = Vec3.atLowerCornerOf(this.climbDirection.getNormal()).scale(0.3);
        double particleX = this.getX() + wallOffset.x;
        double particleY = this.getY() + this.random.nextDouble() * this.getBbHeight();
        double particleZ = this.getZ() + wallOffset.z;

        serverLevel.sendParticles(ParticleTypes.CLOUD,
                particleX, particleY, particleZ,
                2, 0.1, 0.1, 0.1, 0.02);

        // 爪跡のようなパーティクル
        serverLevel.sendParticles(ParticleTypes.CRIT,
                particleX, particleY, particleZ,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    private boolean hasReachedWallTop() {
        if (this.climbDirection == null) return false;

        BlockPos currentPos = this.blockPosition();
        BlockPos wallPos = currentPos.relative(this.climbDirection);

        // 現在位置の壁が終わったか
        return !this.level().getBlockState(wallPos).isSolid() &&
                this.level().getBlockState(currentPos.above()).isAir();
    }

    private void completeWallClimbing() {
        // 壁登り完了時の強力なジャンプ
        Vec3 jumpDirection = Vec3.ZERO;
        if (this.getTarget() != null) {
            jumpDirection = this.getTarget().position().subtract(this.position()).normalize();
        }

        double jumpPower = 0.7D + (getEvolutionStage() * 0.15D);
        this.setDeltaMovement(
                jumpDirection.x * 0.4D,
                jumpPower,
                jumpDirection.z * 0.4D
        );

        this.hasImpulse = true;
        stopWallClimbing();

        // 完了音
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.5F);
    }

    private void stopWallClimbing() {
        this.isWallClimbing = false;
        this.climbDirection = null;
        this.climbTicks = 0;
        this.canPhaseClimb = false;
        setClimbing(false);
    }

    private void checkForWallClimbOpportunity() {
        if (this.getTarget() == null || this.horizontalCollision) {
            double heightDiff = this.getTarget().getY() - this.getY();
            if (heightDiff > 2.5D && !isBeingWatched()) {
                initializeWallClimbing();
            }
        }
    }

    // オーバーライドメソッド
    @Override
    public boolean onClimbable() {
        return this.isWallClimbing || super.onClimbable();
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // 見られている時の特別な処理
        if (isBeingWatched() && this.isWallClimbing) {
            stopWallClimbing(); // 見られたら壁登りを中止
        }
    }

    // バット1本装備の保証
    private void ensureSingleBatEquipped() {
        ItemStack mainHand = this.getMainHandItem();
        ItemStack offHand = this.getOffhandItem();

        if (mainHand.isEmpty() || !mainHand.is(ModItems.TUNG_SAHUR_BAT.get()) || forceEquipBat) {
            ItemStack batStack = createEvolutionBat();
            this.setItemInHand(InteractionHand.MAIN_HAND, batStack);
            forceEquipBat = false;
        }

        if (!offHand.isEmpty()) {
            this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private ItemStack createEvolutionBat() {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        tag.putInt("EvolutionStage", this.getEvolutionStage());
        tag.putBoolean("EntityBat", true);

        switch (getEvolutionStage()) {
            case 1 -> {
                tag.putInt("Damage", 12);
                tag.putString("BatType", "Enhanced");
            }
            case 2 -> {
                tag.putInt("Damage", 18);
                tag.putString("BatType", "Legendary");
                tag.putBoolean("Cursed", true);
            }
            default -> {
                tag.putInt("Damage", 8);
                tag.putString("BatType", "Normal");
            }
        }

        return batStack;
    }

    // 進化システム - 自動更新とコマンド対応
    private void updateEvolutionStage() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            int dayCount = dayData.getDayCount();

            int targetStage = calculateStageFromDayCount(dayCount);

            // 現在のステージと異なる場合のみ更新
            if (targetStage != this.getEvolutionStage()) {
                int oldstage = this.getEvolutionStage();
                this.setEvolutionStage(targetStage);
                updateStageBasedAttributes();

                // 進化時の特殊効果
                onEvolutionChange(oldstage, targetStage, serverLevel);

                // デバッグログ
                TungSahurMod.LOGGER.debug("TungSahur evolved: {} -> {} (Day: {})",
                        oldstage, targetStage, dayCount + 1);
            }
        }
    }

    /**
     * 進化時の特殊効果とアナウンス
     */
    private void onEvolutionChange(int oldStage, int newStage, ServerLevel level) {
        // 進化パーティクル効果
        level.sendParticles(ParticleTypes.SOUL,
                this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);

        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(),
                15, 0.3, 0.3, 0.3, 0.05);

        // 進化音
        level.playSound(null, this.blockPosition(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.5F);

        // 近くのプレイヤーにメッセージ
        String stageMessage = switch (newStage) {
            case 0 -> "§c§lTung Sahurが基本形態で現れた...";
            case 1 -> "§c§lTung Sahurが進化した！ 投擲攻撃とジャンプ攻撃が解禁された...";
            case 2 -> "§4§lTung Sahurが最終形態に進化した！！ 非常に危険だ...";
            default -> "§c§lTung Sahurが変化した...";
        };

        // 半径32ブロック内のプレイヤーにメッセージ送信
        level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D))
                .forEach(player -> player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(stageMessage)));
    }

    /**
     * 外部からの強制進化（コマンド等で使用）
     */
    public void forceUpdateToCurrentDay() {
        if (this.level() instanceof ServerLevel serverLevel) {
            DayCountSavedData dayData = DayCountSavedData.get(serverLevel);
            int currentDayStage = calculateStageFromDayCount(dayData.getDayCount());

            if (currentDayStage != this.getEvolutionStage()) {
                int oldStage = this.getEvolutionStage();
                this.setEvolutionStage(currentDayStage);
                updateStageBasedAttributes();
                onEvolutionChange(oldStage, currentDayStage, serverLevel);
            }
        }
    }

    private void updateStageBasedAttributes() {
        int stage = this.getEvolutionStage();

        switch (stage) {
            case 0 -> {
                this.setScaleFactor(1.0F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0D);
            }
            case 1 -> {
                this.setScaleFactor(1.15F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D);
            }
            case 2 -> {
                this.setScaleFactor(1.3F);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(85.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(15.0D);
            }
        }

        this.setHealth(this.getMaxHealth());
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions baseDimensions = super.getDimensions(pose);
        float scale = this.getScaleFactor();
        return baseDimensions.scale(scale);
    }

    // その他の重要なメソッド（攻撃、クールダウン、監視状態等）は前回のコードと同様

    // ゲッター・セッター
    public int getEvolutionStage() { return this.entityData.get(EVOLUTION_STAGE); }
    public void setEvolutionStage(int stage) { this.entityData.set(EVOLUTION_STAGE, Math.max(0, stage)); }

    public float getScaleFactor() { return this.entityData.get(SCALE_FACTOR); }
    public void setScaleFactor(float scale) {
        this.entityData.set(SCALE_FACTOR, scale);
        this.refreshDimensions();
    }

    public boolean isSprinting() { return this.entityData.get(IS_SPRINTING); }
    public void setSprinting(boolean sprinting) { this.entityData.set(IS_SPRINTING, sprinting); }

    public boolean isAttacking() { return this.entityData.get(IS_ATTACKING); }
    public void setAttacking(boolean attacking) { this.entityData.set(IS_ATTACKING, attacking); }

    public boolean isThrowing() { return this.entityData.get(IS_THROWING); }
    public void setThrowing(boolean throwing) { this.entityData.set(IS_THROWING, throwing); }

    public boolean isJumping() { return this.entityData.get(IS_JUMPING); }
    public void setJumping(boolean jumping) { this.entityData.set(IS_JUMPING, jumping); }

    public boolean isBeingWatched() { return this.entityData.get(IS_BEING_WATCHED); }
    public void setBeingWatched(boolean watched) { this.entityData.set(IS_BEING_WATCHED, watched); }

    public boolean isClimbing() { return this.entityData.get(IS_CLIMBING); }
    public void setClimbing(boolean climbing) { this.entityData.set(IS_CLIMBING, climbing); }

    // 能力チェック
    public boolean canAttack() { return attackCooldown <= 0; }
    public boolean canThrow() { return throwCooldown <= 0 && getEvolutionStage() >= 1; }
    public boolean canJump() { return jumpCooldown <= 0 && getEvolutionStage() >= 1 && this.onGround(); }
    public boolean canClimbWalls() { return this.getEvolutionStage() >= 1; }

    // 攻撃メソッド（前回のコードから）
    public void performMeleeAttack(LivingEntity target) {
        if (attackCooldown <= 0) {
            setAttacking(true);
            animationTimer = 20;
            isCurrentlyAttacking = true;

            float baseDamage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            target.hurt(this.damageSources().mobAttack(this), baseDamage);

            applyBatEffects(target);

            attackCooldown = 25 - (getEvolutionStage() * 3);
            forceEquipBat = true;
        }
    }

    public void performThrowAttack(LivingEntity target) {
        if (throwCooldown <= 0 && getEvolutionStage() >= 1) {
            setThrowing(true);
            animationTimer = 30;
            isCurrentlyThrowing = true;

            TungBatProjectile projectile = new TungBatProjectile(this.level(), this);
            projectile.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

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
                case 1 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                case 2 -> {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                }
            }
        }
    }

    // アップデート系メソッド
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
        boolean wasWatched = isBeingWatched();
        boolean isWatched = false;
        double closestWatchDistance = Double.MAX_VALUE;
        Player closestWatchingPlayer = null;

        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(20.0D))) {
            double distance = player.distanceTo(this);
            if (distance > 20.0D) continue;

            // より正確な視線判定
            Vec3 playerEyePos = player.getEyePosition();
            Vec3 playerLookVector = player.getLookAngle();
            Vec3 toEntity = this.getEyePosition().subtract(playerEyePos).normalize();

            // 視線の角度判定（より厳密に）
            double dot = playerLookVector.dot(toEntity);
            double angleThreshold = Math.max(0.7D - (distance * 0.02D), 0.4D); // 距離に応じて判定範囲調整

            if (dot > angleThreshold) {
                // レイキャスト判定で障害物チェック
                if (hasDirectLineOfSight(playerEyePos, this.getEyePosition())) {
                    isWatched = true;
                    if (distance < closestWatchDistance) {
                        closestWatchDistance = distance;
                        closestWatchingPlayer = player;
                    }
                }
            }
        }

        setBeingWatched(isWatched);

        // 視線状態に応じた速度調整
        if (isWatched) {
            applyWatchedSpeedReduction(closestWatchDistance, closestWatchingPlayer);
        } else if (wasWatched) {
            restoreNormalSpeed();
        }
    }

    private boolean hasDirectLineOfSight(Vec3 from, Vec3 to) {
        // 簡易レイキャスト - 障害物があるかチェック
        Vec3 direction = to.subtract(from).normalize();
        double distance = from.distanceTo(to);

        for (double step = 0.5; step < distance; step += 0.5) {
            Vec3 checkPos = from.add(direction.scale(step));
            BlockPos blockPos = new BlockPos((int)checkPos.x, (int)checkPos.y, (int)checkPos.z);

            if (!this.level().getBlockState(blockPos).isAir()) {
                return false; // 障害物がある
            }
        }
        return true;
    }

    private void applyWatchedSpeedReduction(double watchDistance, Player watchingPlayer) {
        if (watchingPlayer == null) return;

        // 距離に応じた速度減少率を計算
        double maxWatchDistance = 16.0D;
        double minWatchDistance = 2.0D;

        // 近づくほど遅くなる計算
        double distanceRatio = Math.max(0.0, Math.min(1.0,
                (watchDistance - minWatchDistance) / (maxWatchDistance - minWatchDistance)));

        // 基本減速率（見られているだけで30%減速）
        double baseReduction = 0.3D;

        // 距離による追加減速（近づくほど更に遅くなる）
        double distanceReduction = (1.0 - distanceRatio) * 0.6D; // 最大60%追加減速

        // プレイヤーがクリエイティブモードの場合はより強い減速
        if (watchingPlayer.isCreative()) {
            distanceReduction *= 1.5D;
        }

        // 最終的な速度倍率を計算
        double totalReduction = baseReduction + distanceReduction;
        double speedMultiplier = Math.max(0.05D, 1.0D - totalReduction); // 最低5%の速度は維持

        // 現在の基本速度を取得して調整
        double currentBaseSpeed = getCurrentBaseSpeedForStage();
        double watchedSpeed = currentBaseSpeed * speedMultiplier;

        // 段階的な速度変更（急激な変化を避ける）
        double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        double smoothedSpeed = currentSpeed * 0.8 + watchedSpeed * 0.2;

        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(smoothedSpeed);

        // デバッグ情報（開発時のみ）
        if (this.tickCount % 20 == 0) { // 1秒毎
            // TungSahurMod.LOGGER.debug("視線追跡: 距離={}, 速度倍率={}, 最終速度={}",
            //     String.format("%.2f", watchDistance),
            //     String.format("%.2f", speedMultiplier),
            //     String.format("%.3f", smoothedSpeed));
        }
    }

    private double getCurrentBaseSpeedForStage() {
        // 進化段階に応じた基本速度を返す
        return switch (getEvolutionStage()) {
            case 0 -> 0.25D;
            case 1 -> 0.28D;
            case 2 -> 0.32D;
            default -> 0.25D;
        };
    }

    private void restoreNormalSpeed() {
        // 視線から外れた時の速度復帰（段階的に）
        double targetSpeed = getCurrentBaseSpeedForStage();
        double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();

        // 速度を徐々に回復
        double recoverySpeed = currentSpeed * 0.7 + targetSpeed * 0.3;
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(recoverySpeed);
    }

    // GeckoLib アニメーション
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<TungSahurEntity> animationState) {
        if (this.isDeadOrDying()) {
            animationState.getController().setAnimation(DEATH_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyAttacking) {
            animationState.getController().setAnimation(PUNCH_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyThrowing) {
            animationState.getController().setAnimation(THROW_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyJumping) {
            animationState.getController().setAnimation(JUMP_ANIM);
            return PlayState.CONTINUE;
        }

        if (this.isWallClimbing) {
            animationState.getController().setAnimation(CLIMBING_ANIM);
            return PlayState.CONTINUE;
        }

        if (this.isSprinting() && animationState.isMoving()) {
            animationState.getController().setAnimation(RUNNING_ANIM);
            return PlayState.CONTINUE;
        }

        if (animationState.isMoving()) {
            animationState.getController().setAnimation(WALKING_ANIM);
            return PlayState.CONTINUE;
        }

        animationState.getController().setAnimation(IDLE_ANIM);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // NBT保存
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EvolutionStage", this.getEvolutionStage());
        tag.putFloat("ScaleFactor", this.getScaleFactor());
        tag.putInt("AttackCooldown", this.attackCooldown);
        tag.putInt("ThrowCooldown", this.throwCooldown);
        tag.putInt("JumpCooldown", this.jumpCooldown);
        tag.putBoolean("IsWallClimbing", this.isWallClimbing);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setEvolutionStage(tag.getInt("EvolutionStage"));
        this.setScaleFactor(tag.getFloat("ScaleFactor"));
        this.attackCooldown = tag.getInt("AttackCooldown");
        this.throwCooldown = tag.getInt("ThrowCooldown");
        this.jumpCooldown = tag.getInt("JumpCooldown");
        this.isWallClimbing = tag.getBoolean("IsWallClimbing");
    }

    // スポーン条件
    public static boolean checkTungSahurSpawnRules(EntityType<TungSahurEntity> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() <= 50 && isDarkEnoughToSpawn(level, pos, random) && checkMobSpawnRules(entityType, level, spawnType, pos, random);
    }

    public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
            return false;
        } else {
            int light = level.getLevel().isThundering() ? level.getLevel().getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
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
        if (isBeingWatched()) {
            amount *= 1.5F;
        }
        return super.hurt(damageSource, amount);
    }

    // カスタム移動ゴール内部クラス
    private static class SmoothMoveToTargetGoal extends Goal {
        private final TungSahurEntity tungSahur;
        private final double speedModifier;
        private LivingEntity target;
        private int delayCounter = 0;

        public SmoothMoveToTargetGoal(TungSahurEntity tungSahur, double speedModifier) {
            this.tungSahur = tungSahur;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.target = this.tungSahur.getTarget();
            if (this.target == null || !this.target.isAlive()) return false;

            double distance = this.tungSahur.distanceTo(this.target);
            return distance > 3.0D && distance < 32.0D;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.target == null || !this.target.isAlive()) return false;

            double distance = this.tungSahur.distanceTo(this.target);
            return distance > 2.0D && distance < 40.0D;
        }

        @Override
        public void start() {
            this.delayCounter = 0;
        }

        @Override
        public void tick() {
            if (this.target == null) return;

            this.tungSahur.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            if (--this.delayCounter <= 0) {
                this.delayCounter = 4 + this.tungSahur.getRandom().nextInt(7);

                double distance = this.tungSahur.distanceTo(this.target);
                if (distance > 3.5D) {
                    this.tungSahur.getNavigation().moveTo(this.target, this.speedModifier);
                }
            }
        }

        @Override
        public void stop() {
            this.target = null;
            this.tungSahur.getNavigation().stop();
        }
    }
}