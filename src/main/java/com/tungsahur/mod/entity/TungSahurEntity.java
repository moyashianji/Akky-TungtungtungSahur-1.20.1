// TungSahurEntity.java - マルチプレイヤー対応版（ターゲット自動切り替え機能付き）
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Comparator;

public class TungSahurEntity extends Monster implements GeoEntity {

    // 既存のデータアクセサー（変更なし）
    private static final EntityDataAccessor<Integer> DAY_NUMBER = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE_FACTOR = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_WALL_CLIMBING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_BEING_WATCHED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_ATTACKING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_THROWING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CURRENTLY_JUMPING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SPRINTING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> CLIMBING_FLAGS =
            SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BYTE);

    // 死亡アニメーション用
    private static final EntityDataAccessor<Boolean> IS_DEATH_ANIMATION_PLAYING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEATH_ANIMATION_COMPLETED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);

    // ★★★ マルチプレイヤー対応用の新しい変数 ★★★
    private int targetScanCooldown = 0;           // レガシー：スマートGoalで処理されるため不要だが互換性のため残す
    private int lastTargetUpdateTime = 0;         // 最後にターゲットを更新した時間
    private Player previousTarget = null;         // 前回のターゲット
    private static final int TARGET_SCAN_INTERVAL = 20; // レガシー定数
    private static final int TARGET_PRIORITY_RANGE = 32; // レガシー定数
    private static final int MAX_TARGET_RANGE = 64;      // レガシー定数

    // 既存のアニメーション定数（変更なし）
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().then("death", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE_DAY1_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY2_ANIM = RawAnimation.begin().then("idle2", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY3_ANIM = RawAnimation.begin().then("idle3", Animation.LoopType.LOOP);

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().then("walk", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT_ANIM = RawAnimation.begin().then("sprint", Animation.LoopType.LOOP);
    private static final RawAnimation WALK2_ANIM = RawAnimation.begin().then("walk2", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT2_ANIM = RawAnimation.begin().then("sprint2", Animation.LoopType.LOOP);
    private static final RawAnimation WALK3_ANIM = RawAnimation.begin().then("walk3", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT3_ANIM = RawAnimation.begin().then("sprint3", Animation.LoopType.LOOP);

    private static final RawAnimation CLIMBING_ANIM = RawAnimation.begin().then("climbing", Animation.LoopType.LOOP);
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().then("shoot_attack", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMP_ATTACK_ANIM = RawAnimation.begin().then("jump_attack", Animation.LoopType.PLAY_ONCE);

    // 既存のAI状態管理変数（変更なし）
    private int attackCooldown = 0;
    private int throwTimer = 0;
    private int jumpTimer = 0;
    private int watchCheckCooldown = 0;
    private Player lastWatchingPlayer = null;

    // ジャンプ攻撃の状態管理
    private boolean isExecutingJump = false;
    private boolean wasInAir = false;
    private int jumpPhase = 0;

    // 投擲攻撃の状態管理
    private boolean isExecutingThrow = false;
    private int throwPhase = 0;
    private int throwChargeTime = 0;

    // AI行動間隔
    private static final int THROW_INTERVAL_MIN = 80;
    private static final int THROW_INTERVAL_MAX = 160;
    private static final int JUMP_INTERVAL_MIN = 200;
    private static final int JUMP_INTERVAL_MAX = 300;

    private int nextThrowTime = 0;
    private int nextJumpTime = 0;

    // 死亡アニメーション制御用変数
    private int deathAnimationTimer = 0;
    private static final int DEATH_ANIMATION_LENGTH = 40;
    private boolean deathAnimationStarted = false;

    // アタックアニメーション制御用
    private boolean isAttackAnimationPlaying = false;
    private int attackAnimationCooldown = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TungSahurEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 15;
        this.setCanPickUpLoot(false);
        this.setMaxUpStep(2.0F);

        // AI タイマー初期化
        resetThrowTimer();
        resetJumpTimer();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DAY_NUMBER, 1);
        this.entityData.define(SCALE_FACTOR, 1.0F);
        this.entityData.define(IS_WALL_CLIMBING, false);
        this.entityData.define(IS_BEING_WATCHED, false);
        this.entityData.define(IS_CURRENTLY_ATTACKING, false);
        this.entityData.define(IS_CURRENTLY_THROWING, false);
        this.entityData.define(IS_CURRENTLY_JUMPING, false);
        this.entityData.define(IS_SPRINTING, false);
        this.entityData.define(CLIMBING_FLAGS, (byte)0);
        this.entityData.define(IS_DEATH_ANIMATION_PLAYING, false);
        this.entityData.define(DEATH_ANIMATION_COMPLETED, false);
    }

    // ★★★ 改良されたターゲット選択システム ★★★
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 壁登り
        this.goalSelector.addGoal(1, new TungSahurSpiderClimbGoal(this));

        // 近接攻撃（壁登り中でない場合のみ）
        this.goalSelector.addGoal(2, new TungSahurMeleeAttackGoal(this, 1.0D, false) {
            @Override
            public boolean canUse() {
                if (TungSahurEntity.this.isWallClimbing()) return false;
                return super.canUse();
            }
        });

        // 移動Goal（壁登り中でない場合のみ）
        this.goalSelector.addGoal(3, new TungSahurAdvancedMoveToTargetGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                if (TungSahurEntity.this.isWallClimbing()) return false;
                return super.canUse();
            }
        });

        // その他の基本Goal
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // ★マルチプレイヤー対応の改良されたターゲット設定★
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        // カスタムスマートターゲットGoalを使用
        this.targetSelector.addGoal(2, new TungSahurSmartTargetGoal(this));
    }

    /**
     * ★★★ 最適なターゲットを見つけて設定する新メソッド ★★★
     */
    private void findAndSetBestTarget() {
        if (this.level().isClientSide) return;

        List<Player> availablePlayers = this.level().getEntitiesOfClass(Player.class,
                new AABB(this.blockPosition()).inflate(MAX_TARGET_RANGE));

        // 生きているプレイヤーのみフィルタリング
        List<Player> alivePlayers = availablePlayers.stream()
                .filter(Player::isAlive)
                .filter(player -> !player.isSpectator())
                .filter(player -> !player.isCreative()) // クリエイティブモードは除外
                .toList();

        if (alivePlayers.isEmpty()) {
            // ターゲット可能なプレイヤーがいない場合
            if (this.getTarget() != null) {
                TungSahurMod.LOGGER.debug("利用可能なプレイヤーなし - ターゲット解除");
                this.setTarget(null);
                this.previousTarget = null;
            }
            return;
        }

        // 現在のターゲットの妥当性をチェック
        Player currentTarget = this.getTarget() instanceof Player ? (Player) this.getTarget() : null;

        if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isSpectator()) {
            double currentDistance = this.distanceTo(currentTarget);

            // 現在のターゲットが範囲内にいる場合、より近いプレイヤーがいるかチェック
            if (currentDistance <= MAX_TARGET_RANGE) {
                Player closestPlayer = findClosestPlayer(alivePlayers);
                if (closestPlayer != null) {
                    double closestDistance = this.distanceTo(closestPlayer);

                    // 現在のターゲットより15ブロック以上近いプレイヤーがいる場合のみ切り替え
                    if (closestDistance + 15.0D < currentDistance) {
                        switchToNewTarget(closestPlayer, "より近いプレイヤーに切り替え");
                        return;
                    }
                }

                // 現在のターゲットを維持
                return;
            }
        }

        // 新しいターゲットを選択
        Player bestTarget = findClosestPlayer(alivePlayers);
        if (bestTarget != null) {
            switchToNewTarget(bestTarget, "新ターゲット選択");
        }
    }

    /**
     * 最も近いプレイヤーを見つける
     */
    private Player findClosestPlayer(List<Player> players) {
        return players.stream()
                .min(Comparator.comparingDouble(this::distanceTo))
                .orElse(null);
    }

    /**
     * ターゲットを切り替える
     */
    private void switchToNewTarget(Player newTarget, String reason) {
        Player oldTarget = this.getTarget() instanceof Player ? (Player) this.getTarget() : null;

        this.setTarget(newTarget);
        this.previousTarget = oldTarget;
        this.lastTargetUpdateTime = this.tickCount;

        TungSahurMod.LOGGER.debug("ターゲット切り替え: {} -> {} (理由: {}, 距離: {}ブロック)",
                oldTarget != null ? oldTarget.getName().getString() : "なし",
                newTarget.getName().getString(),
                reason,
                String.format("%.1f", this.distanceTo(newTarget)));

        // ナビゲーションをリセットして新しいターゲットに向かう
        this.getNavigation().stop();

        // 特殊攻撃を中断
        if (this.isExecutingThrow) {
            stopThrowAttack();
        }
        if (this.isExecutingJump) {
            stopJumpAttack();
        }
    }

    @Override
    public void tick() {
        // 死亡アニメーション中の特別処理
        if (deathAnimationStarted && deathAnimationTimer < DEATH_ANIMATION_LENGTH) {
            deathAnimationTimer++;
            this.setDeltaMovement(Vec3.ZERO);
            this.baseTick();

            if (deathAnimationTimer >= DEATH_ANIMATION_LENGTH) {
                TungSahurMod.LOGGER.debug("★死亡アニメーション完了 - 次のtickで実際の死亡処理★");
                this.setIsDeathAnimationPlaying(false);
                this.die(this.getLastDamageSource() != null ? this.getLastDamageSource() : this.damageSources().generic());
            }
            return;
        }

        // 通常のtick処理
        super.tick();

        // ★ターゲットスキャンクールダウンの更新★
        if (this.targetScanCooldown > 0) {
            this.targetScanCooldown--;
        }

        // アタックアニメーション完了チェック
        if (isAttackAnimationPlaying && !isCurrentlyAttacking()) {
            isAttackAnimationPlaying = false;
            TungSahurMod.LOGGER.debug("攻撃アニメーション完了 - 即座に次回攻撃可能");
        }

        if (!this.level().isClientSide) {
            ensureBatEquipped();
            this.setClimbing(this.horizontalCollision);

            // ★追加: 緊急ターゲット確認（現在のターゲットが無効な場合）★
            checkTargetValidity();
        }
    }

    /**
     * ★★★ ターゲットの妥当性を緊急チェック ★★★
     */
    private void checkTargetValidity() {
        LivingEntity currentTarget = this.getTarget();

        if (currentTarget instanceof Player player) {
            // プレイヤーが死亡、スペクテイター、範囲外の場合
            if (!player.isAlive() || player.isSpectator() || this.distanceTo(player) > MAX_TARGET_RANGE) {
                TungSahurMod.LOGGER.debug("現在のターゲット無効 - 緊急再選択: {} (生存={}, スペクテイター={}, 距離={})",
                        player.getName().getString(), player.isAlive(), player.isSpectator(), this.distanceTo(player));

                // 即座に新しいターゲットを探す
                this.targetScanCooldown = 0;
                findAndSetBestTarget();
            }
        } else if (currentTarget == null) {
            // ターゲットがnullの場合、定期的に新しいターゲットを探す
            if (this.tickCount % (TARGET_SCAN_INTERVAL * 2) == 0) { // 2秒間隔
                findAndSetBestTarget();
            }
        }
    }

    @Override
    public void aiStep() {
        if (deathAnimationStarted) {
            this.baseTick();
            return;
        }

        // 壁登り中の特別処理
        if (this.isWallClimbing()) {
            this.fallDistance = 0.0F;
            this.setAirSupply(this.getMaxAirSupply());

            if (!this.level().isClientSide) {
                updateWatchStatus();
            }

            this.baseTick();
            return;
        }

        // 通常のAI処理
        super.aiStep();

        if (!this.level().isClientSide) {
            updateAllTimers();
            updateWatchStatus();
            updateSprintStatus();
            handleSpecialAttacks();
            handleJumpLanding();
        }
    }

    // ★★★ 以下、既存のメソッドはすべてそのまま維持 ★★★

    @Override
    public void die(DamageSource damageSource) {
        if (!deathAnimationStarted) {
            deathAnimationStarted = true;
            this.setIsDeathAnimationPlaying(true);
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);
            TungSahurMod.LOGGER.debug("★死亡アニメーション開始★ - HP=1に設定して実際の死亡を遅延");
            this.setHealth(1.0F);
            return;
        }

        if (deathAnimationTimer >= DEATH_ANIMATION_LENGTH) {
            TungSahurMod.LOGGER.debug("★死亡アニメーション完了 - 実際の死亡処理実行★");
            if (this.level() instanceof ServerLevel _level) {
                ItemEntity entityToSpawn = new ItemEntity(_level, this.getX(), this.getY(), this.getZ(), new ItemStack(ModItems.TUNG_SAHUR_BAT.get()));
                entityToSpawn.setPickUpDelay(10);
                _level.addFreshEntity(entityToSpawn);
            }
            this.setIsDeathAnimationPlaying(false);
            this.discard();
            return;
        }
    }

    // === 既存のメソッドをすべて維持 ===
    // (updateAllTimers, handleSpecialAttacks, 等のメソッドはすべて元のコードと同じ)

    private void updateAllTimers() {
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            if (this.attackCooldown == 0) {
                TungSahurMod.LOGGER.debug("攻撃クールダウン完了 - 再攻撃可能");
            }
        }

        this.throwTimer++;
        this.jumpTimer++;
        if (this.watchCheckCooldown > 0) this.watchCheckCooldown--;

        if (this.isExecutingThrow) {
            handleThrowExecution();
        } else if (isCurrentlyThrowing()) {
            setCurrentlyThrowing(false);
        }

        if (this.isExecutingJump) {
            handleJumpExecution();
        } else if (isCurrentlyJumping()) {
            setCurrentlyJumping(false);
        }

        if (isCurrentlyAttacking()) {
            if (this.tickCount % 200 == 0) {
                TungSahurMod.LOGGER.debug("攻撃状態の安全リセット実行");
                setCurrentlyAttacking(false);
            }
        }
    }

    // === 既存のメソッドを継続 ===
    // (handleSpecialAttacks, shouldExecuteThrow, shouldExecuteJump, startThrowAttack, startJumpAttack,
    //  handleThrowExecution, handleJumpExecution, handleJumpLanding, performJumpLandingDamage,
    //  executeThrowProjectile, stopThrowAttack, stopJumpAttack, resetThrowTimer, resetJumpTimer,
    //  canAttack, updateWatchStatus, findWatchingPlayer, isPlayerLookingAtMe, adjustSpeedForWatching,
    //  restoreNormalSpeed, getBaseSpeedForDay, updateSprintStatus, equipBat, 等)

    private void handleSpecialAttacks() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.isExecutingThrow || this.isExecutingJump) return;

        double distance = this.distanceTo(target);

        if (this.getDayNumber() >= 2 && this.throwTimer >= this.nextThrowTime) {
            if (shouldExecuteThrow(target, distance)) {
                startThrowAttack(target);
            }
        }

        if (this.getDayNumber() >= 3 && this.jumpTimer >= this.nextJumpTime) {
            if (shouldExecuteJump(target, distance)) {
                startJumpAttack(target);
            }
        }
    }

    private boolean shouldExecuteThrow(LivingEntity target, double distance) {
        if (distance < 5.0D || distance > 20.0D) return false;
        if (!this.hasLineOfSight(target)) return false;
        if (this.isBeingWatched() && this.random.nextFloat() < 0.5F) return false;
        return true;
    }

    private boolean shouldExecuteJump(LivingEntity target, double distance) {
        if (distance < 4.0D || distance > 15.0D) return false;
        double heightDiff = target.getY() - this.getY();
        if (heightDiff >= 2.0D) return true;
        if (this.isBeingWatched() && this.random.nextFloat() < 0.3F) return false;
        return distance >= 6.0D;
    }

    private void startThrowAttack(LivingEntity target) {
        this.isExecutingThrow = true;
        this.throwPhase = 0;
        this.throwChargeTime = switch (this.getDayNumber()) {
            case 2 -> 15;
            case 3 -> 10;
            default -> 20;
        };

        this.setCurrentlyThrowing(true);
        this.getNavigation().stop();

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.CROSSBOW_LOADING_START, SoundSource.HOSTILE, 0.6F, 1.2F);

        TungSahurMod.LOGGER.debug("投擲攻撃開始: 距離={}", this.distanceTo(target));
    }

    private void startJumpAttack(LivingEntity target) {
        this.isExecutingJump = true;
        this.jumpPhase = 0;
        this.wasInAir = false;

        this.setCurrentlyJumping(true);
        this.getNavigation().stop();

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.7F, 1.3F);

        TungSahurMod.LOGGER.debug("ジャンプ攻撃開始: 距離={}", this.distanceTo(target));
    }

    private void handleThrowExecution() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            stopThrowAttack();
            return;
        }

        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        switch (this.throwPhase) {
            case 0:
                this.throwChargeTime--;
                if (this.throwChargeTime % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    Vec3 pos = this.position().add(0, this.getBbHeight() * 0.8, 0);
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.02);
                }

                if (this.throwChargeTime <= 0) {
                    this.throwPhase = 1;
                    this.throwChargeTime = 8;
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.CROSSBOW_LOADING_END, SoundSource.HOSTILE, 0.7F, 1.0F);
                }
                break;

            case 1:
                this.throwChargeTime--;
                if (this.throwChargeTime % 3 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    Vec3 direction = target.position().subtract(this.position()).normalize();
                    Vec3 particlePos = this.position().add(direction.scale(2.0)).add(0, this.getBbHeight() * 0.8, 0);
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            particlePos.x, particlePos.y, particlePos.z, 1, 0.1, 0.1, 0.1, 0.02);
                }

                if (this.throwChargeTime <= 0) {
                    executeThrowProjectile(target);
                    stopThrowAttack();
                    resetThrowTimer();
                }
                break;
        }
    }

    private void handleJumpExecution() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            stopJumpAttack();
            return;
        }

        switch (this.jumpPhase) {
            case 0:
                this.jumpPhase++;

                Vec3 direction = target.position().subtract(this.position()).normalize();
                double jumpPower = 1.0D;
                double distance = this.distanceTo(target);
                double speedMultiplier = Math.min(1.5D, Math.max(0.8D, distance / 8.0D));

                Vec3 jumpVec = direction.scale(speedMultiplier).add(0, jumpPower, 0);
                this.setDeltaMovement(jumpVec);
                this.wasInAir = true;

                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.8F, 1.0F);

                TungSahurMod.LOGGER.debug("ジャンプ実行: ベクトル={}", jumpVec);
                break;

            case 1:
                if (this.level() instanceof ServerLevel serverLevel && this.random.nextInt(3) == 0) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            this.getX(), this.getY() + this.getBbHeight() * 0.3, this.getZ(),
                            1, 0.1, 0.1, 0.1, 0.01);
                }
                break;
        }
    }

    private void handleJumpLanding() {
        if (this.isExecutingJump && this.jumpPhase == 1 && this.wasInAir && this.onGround()) {
            this.jumpPhase = 2;
            performJumpLandingDamage();
            stopJumpAttack();
            resetJumpTimer();
        }
    }

    private void performJumpLandingDamage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        serverLevel.sendParticles(ParticleTypes.CLOUD,
                this.getX(), this.getY(), this.getZ(), 3, 0.5, 0.1, 0.5, 0.1);

        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * 2 * Math.PI;
            double radius = 2.0;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    x, this.getY() + 0.1, z, 1, 0.05, 0.05, 0.05, 0.05);
        }

        AABB damageArea = new AABB(this.blockPosition()).inflate(2.0);
        List<Entity> nearbyEntities = serverLevel.getEntities(this, damageArea);

        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity living && entity != this) {
                double distance = this.distanceTo(living);
                if (distance <= 2.0) {
                    float damage;
                    if (living instanceof Player) {
                        damage = switch (getDayNumber()) {
                            case 1 -> 8.0F;
                            case 2 -> 12.0F;
                            case 3 -> 16.0F;
                            default -> 6.0F;
                        };
                    } else {
                        float baseDamage = switch (getDayNumber()) {
                            case 1 -> 10.0F;
                            case 2 -> 15.0F;
                            case 3 -> 20.0F;
                            default -> 8.0F;
                        };
                        damage = (float) (baseDamage * (1.0D - distance / 2.0D));
                        damage = Math.max(damage, 4.0F);
                    }

                    living.hurt(this.damageSources().mobAttack(this), damage);

                    Vec3 knockback = living.position().subtract(this.position()).normalize();
                    double knockbackStrength = living instanceof Player ? 0.1D : 0.2D;
                    living.setDeltaMovement(living.getDeltaMovement().add(
                            knockback.x * knockbackStrength, 0.08D, knockback.z * knockbackStrength));

                    TungSahurMod.LOGGER.debug("ジャンプ着地ダメージ: {}に{}ダメージ",
                            living.getClass().getSimpleName(), damage);
                }
            }
        }

        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.3F, 0.8F);
    }

    private void executeThrowProjectile(LivingEntity target) {
        Vec3 direction = target.position().subtract(this.position()).normalize();
        TungBatProjectile projectile = new TungBatProjectile(this.level(), this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double speed = 1.6D + (getDayNumber() * 0.3D);
        projectile.shoot(direction.x, direction.y + 0.1D, direction.z, (float) speed, 1.0F);

        this.level().addFreshEntity(projectile);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.9F, 0.9F);

        TungSahurMod.LOGGER.debug("投擲実行完了");
    }

    private void stopThrowAttack() {
        this.isExecutingThrow = false;
        this.throwPhase = 0;
        this.throwChargeTime = 0;
        this.level().scheduleTick(this.blockPosition(),
                this.level().getBlockState(this.blockPosition()).getBlock(), 10);
    }

    private void stopJumpAttack() {
        this.isExecutingJump = false;
        this.jumpPhase = 0;
        this.wasInAir = false;
        this.level().scheduleTick(this.blockPosition(),
                this.level().getBlockState(this.blockPosition()).getBlock(), 10);
    }

    private void resetThrowTimer() {
        this.throwTimer = 0;
        this.nextThrowTime = THROW_INTERVAL_MIN + this.random.nextInt(THROW_INTERVAL_MAX - THROW_INTERVAL_MIN);

        if (this.getDayNumber() >= 3) {
            this.nextThrowTime = (int) (this.nextThrowTime * 0.7);
        }

        TungSahurMod.LOGGER.debug("次回投擲: {}tick後", this.nextThrowTime);
    }

    private void resetJumpTimer() {
        this.jumpTimer = 0;
        this.nextJumpTime = JUMP_INTERVAL_MIN + this.random.nextInt(JUMP_INTERVAL_MAX - JUMP_INTERVAL_MIN);
        TungSahurMod.LOGGER.debug("次回ジャンプ: {}tick後", this.nextJumpTime);
    }

    public boolean canAttack() {
        boolean cooldownOK = this.attackCooldown <= 0;
        boolean notThrowingOK = !this.isExecutingThrow;
        boolean notJumpingOK = !this.isExecutingJump;
        boolean notClimbingOK = !this.isWallClimbing();

        boolean result = cooldownOK && notThrowingOK && notJumpingOK && notClimbingOK;

        if (!result && this.tickCount % 20 == 0) {
            TungSahurMod.LOGGER.debug("canAttack=false: クールダウン={}, 投擲中={}, ジャンプ中={}, 壁登り中={}",
                    this.attackCooldown, this.isExecutingThrow, this.isExecutingJump, this.isWallClimbing());
        }

        return result;
    }

    private void updateWatchStatus() {
        if (watchCheckCooldown > 0) return;
        watchCheckCooldown = 5;

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
        return dot > 0.7D && player.hasLineOfSight(this);
    }

    private void adjustSpeedForWatching(Player watchingPlayer) {
        double distance = this.distanceTo(watchingPlayer);
        double maxWatchDistance = 16.0D;
        double minWatchDistance = 2.0D;

        double distanceRatio = Math.max(0.0, Math.min(1.0,
                (distance - minWatchDistance) / (maxWatchDistance - minWatchDistance)));

        double baseReduction = 0.5D;
        double distanceReduction = (1.0 - distanceRatio) * 0.4D;
        double totalReduction = baseReduction + distanceReduction;
        double speedMultiplier = Math.max(0.02D, 1.0D - totalReduction);

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

    private void updateSprintStatus() {
        LivingEntity target = this.getTarget();
        if (target instanceof Player player) {
            boolean playerSprinting = player.isSprinting();
            if (playerSprinting != isSprinting() && !isBeingWatched() && !isExecutingThrow && !isExecutingJump) {
                setSprinting(playerSprinting);

                double baseSpeed = getBaseSpeedForDay();
                double sprintMultiplier = playerSprinting ? 1.5D : 1.0D;
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * sprintMultiplier);
            }
        }
    }

    // === 以下、既存のメソッドを継続 ===
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && this.deathAnimationTimer < DEATH_ANIMATION_LENGTH) {
            return;
        }
        super.remove(reason);
    }

    public void forceResetAttackAnimation() {
        this.isAttackAnimationPlaying = false;
        this.attackAnimationCooldown = 0;
        this.setCurrentlyAttacking(false);
        TungSahurMod.LOGGER.debug("★攻撃アニメーション強制リセット★ - 新しいアニメーション準備完了");
    }

    private void ensureBatEquipped() {
        ItemStack mainHandItem = this.getItemInHand(InteractionHand.MAIN_HAND);

        if (mainHandItem.isEmpty() || !mainHandItem.is(ModItems.TUNG_SAHUR_BAT.get())) {
            equipBat();
            TungSahurMod.LOGGER.debug("TungSahur {} にバット装備: Day{}", this.getId(), this.getDayNumber());
        }

        ItemStack offHandItem = this.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandItem.isEmpty()) {
            this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    public void setDayNumber(int day) {
        this.entityData.set(DAY_NUMBER, day);
        updateScaleForDay(day);
        updateAttributesForDay(day);
        equipBat();
    }

    public boolean isClimbing() {
        return (this.entityData.get(CLIMBING_FLAGS) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte flags = this.entityData.get(CLIMBING_FLAGS);
        if (climbing) {
            flags = (byte)(flags | 1);
        } else {
            flags = (byte)(flags & -2);
        }
        this.entityData.set(CLIMBING_FLAGS, flags);
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Player;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
        TungSahurMod.LOGGER.debug("攻撃クールダウン設定: {}tick ({}秒)", ticks, ticks / 20.0F);
    }

    public int getAttackCooldown() {
        return this.attackCooldown;
    }

    private void updateScaleForDay(int day) {
        float scale = switch (day) {
            case 1 -> 1.0F;
            case 2 -> 1.3F;
            case 3 -> 1.7F;
            default -> 1.0F;
        };
        setScaleFactor(scale);
    }

    private void updateAttributesForDay(int day) {
        switch (day) {
            case 1:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0D);
                break;
            case 2:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(250.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.5D);
                break;
            case 3:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0D);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0D);
                break;
        }
        this.setHealth(this.getMaxHealth());
    }

    private void equipBat() {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();

        int dayNumber = getDayNumber();
        tag.putInt("DayNumber", dayNumber);
        tag.putBoolean("EntityBat", true);
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 63);

        switch (dayNumber) {
            case 1:
                tag.putString("DisplayName", "§7TungSahur's Bat (Day 1)");
                break;
            case 2:
                tag.putString("DisplayName", "§cTungSahur's Enhanced Bat (Day 2)");
                tag.putBoolean("Enchanted", true);
                break;
            case 3:
                tag.putString("DisplayName", "§5TungSahur's Ultimate Bat (Day 3)");
                tag.putBoolean("Enchanted", true);
                tag.putInt("CustomModelData", 999);
                break;
            default:
                tag.putString("DisplayName", "§8TungSahur's Mysterious Bat");
                break;
        }

        this.setItemInHand(InteractionHand.MAIN_HAND, batStack);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<TungSahurEntity> animationState) {
        String currentAnimName = animationState.getController().getCurrentAnimation() != null
                ? animationState.getController().getCurrentAnimation().animation().name() : "";

        if (this.isDeathAnimationPlaying()) {
            if (!currentAnimName.equals("death")) {
                TungSahurMod.LOGGER.debug("死亡アニメーション開始");
                animationState.getController().forceAnimationReset();
                animationState.getController().setAnimation(DEATH_ANIM);
            }
            return PlayState.CONTINUE;
        }

        if (isAttackAnimationPlaying) {
            AnimationController.State controllerState = animationState.getController().getAnimationState();

            if (controllerState.equals(AnimationController.State.STOPPED) || !currentAnimName.equals("attack")) {
                isAttackAnimationPlaying = false;
                setCurrentlyAttacking(false);
                TungSahurMod.LOGGER.debug("attackアニメーション完了 - 攻撃状態リセット");
            } else {
                return PlayState.CONTINUE;
            }
        }

        if (isCurrentlyAttacking() && !isAttackAnimationPlaying) {
            TungSahurMod.LOGGER.debug("★攻撃アニメーション強制開始★");
            animationState.getController().forceAnimationReset();
            animationState.getController().setAnimation(ATTACK_ANIM);
            isAttackAnimationPlaying = true;
            return PlayState.CONTINUE;
        }

        if (isAttackAnimationPlaying) {
            return PlayState.CONTINUE;
        }

        if (currentAnimName.equals("shoot_attack")) {
            AnimationController.State controllerState = animationState.getController().getAnimationState();
            if (controllerState.equals(AnimationController.State.STOPPED)) {
                setCurrentlyThrowing(false);
                TungSahurMod.LOGGER.debug("投擲アニメーション完了");
            } else {
                return PlayState.CONTINUE;
            }
        }

        if (currentAnimName.equals("jump_attack")) {
            AnimationController.State controllerState = animationState.getController().getAnimationState();
            if (controllerState.equals(AnimationController.State.STOPPED)) {
                setCurrentlyJumping(false);
                TungSahurMod.LOGGER.debug("ジャンプアニメーション完了");
            } else {
                return PlayState.CONTINUE;
            }
        }

        if (isCurrentlyThrowing()) {
            if (!currentAnimName.equals("shoot_attack")) {
                animationState.getController().forceAnimationReset();
            }
            animationState.getController().setAnimation(THROW_ANIM);
            return PlayState.CONTINUE;
        }

        if (isCurrentlyJumping()) {
            if (!currentAnimName.equals("jump_attack")) {
                animationState.getController().forceAnimationReset();
            }
            animationState.getController().setAnimation(JUMP_ATTACK_ANIM);
            return PlayState.CONTINUE;
        }

        if (isWallClimbing()) {
            animationState.getController().setAnimation(CLIMBING_ANIM);
            return PlayState.CONTINUE;
        }

        if (isSprinting() && animationState.isMoving()) {
            RawAnimation sprintAnim = switch (getDayNumber()) {
                case 2 -> SPRINT2_ANIM;
                case 3 -> SPRINT3_ANIM;
                default -> SPRINT_ANIM;
            };
            animationState.getController().setAnimation(sprintAnim);
            return PlayState.CONTINUE;
        }

        if (animationState.isMoving()) {
            RawAnimation walkAnim = switch (getDayNumber()) {
                case 2 -> WALK2_ANIM;
                case 3 -> WALK3_ANIM;
                default -> WALK_ANIM;
            };
            animationState.getController().setAnimation(walkAnim);
            return PlayState.CONTINUE;
        }

        RawAnimation idleAnim = switch (getDayNumber()) {
            case 2 -> IDLE_DAY2_ANIM;
            case 3 -> IDLE_DAY3_ANIM;
            default -> IDLE_DAY1_ANIM;
        };

        animationState.getController().setAnimation(idleAnim);
        return PlayState.CONTINUE;
    }

    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // === ゲッター・セッター ===
    public int getDayNumber() {
        return this.entityData.get(DAY_NUMBER);
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

    public boolean isDeathAnimationPlaying() {
        return this.entityData.get(IS_DEATH_ANIMATION_PLAYING);
    }

    public void setIsDeathAnimationPlaying(boolean playing) {
        this.entityData.set(IS_DEATH_ANIMATION_PLAYING, playing);
    }

    public boolean isDeathAnimationCompleted() {
        return this.entityData.get(DEATH_ANIMATION_COMPLETED);
    }

    public void setDeathAnimationCompleted(boolean completed) {
        this.entityData.set(DEATH_ANIMATION_COMPLETED, completed);
    }

    @Override
    public boolean isSprinting() {
        return this.entityData.get(IS_SPRINTING);
    }

    public void setSprinting(boolean sprinting) {
        this.entityData.set(IS_SPRINTING, sprinting);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        int currentDay = DayCountSavedData.get((ServerLevel) level.getLevel()).getDayCount();
        setDayNumber(Math.max(1, Math.min(3, currentDay)));
        equipBat();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DayNumber", getDayNumber());
        tag.putFloat("ScaleFactor", getScaleFactor());
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("ThrowTimer", throwTimer);
        tag.putInt("JumpTimer", jumpTimer);
        tag.putInt("NextThrowTime", nextThrowTime);
        tag.putInt("NextJumpTime", nextJumpTime);
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted);
        tag.putInt("DeathAnimationTimer", deathAnimationTimer);

        // ★マルチプレイヤー対応用データ★
        tag.putInt("TargetScanCooldown", targetScanCooldown);
        tag.putInt("LastTargetUpdateTime", lastTargetUpdateTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDayNumber(tag.getInt("DayNumber"));
        setScaleFactor(tag.getFloat("ScaleFactor"));
        attackCooldown = tag.getInt("AttackCooldown");
        throwTimer = tag.getInt("ThrowTimer");
        jumpTimer = tag.getInt("JumpTimer");
        nextThrowTime = tag.getInt("NextThrowTime");
        nextJumpTime = tag.getInt("NextJumpTime");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTimer = tag.getInt("DeathAnimationTimer");

        // ★マルチプレイヤー対応用データ★
        targetScanCooldown = tag.getInt("TargetScanCooldown");
        lastTargetUpdateTime = tag.getInt("LastTargetUpdateTime");
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("tungsahurmod:tungtungsahur"));
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
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (this.isWallClimbing()) {
            return;
        }
        super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHitIn) {
        super.dropCustomDeathLoot(source, looting, recentlyHitIn);
        this.spawnAtLocation(new ItemStack(ModItems.TUNG_SAHUR_BAT.get()));
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isWallClimbing()) {
            if (this.getDeltaMovement().y <= 0.0D) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            }
        }
        super.travel(travelVector);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (deathAnimationStarted) {
            TungSahurMod.LOGGER.debug("死亡アニメーション中 - ダメージ無効");
            return false;
        }

        if (isBeingWatched()) {
            amount *= 1.3F;
        }

        return super.hurt(damageSource, amount);
    }

    public String getDebugInfo() {
        LivingEntity currentTarget = this.getTarget();
        String targetInfo = currentTarget instanceof Player player ? player.getName().getString() :
                currentTarget != null ? currentTarget.getClass().getSimpleName() : "なし";

        return String.format("TungSahur[Day=%d, ThrowIn=%d, JumpIn=%d, ExecThrow=%s, ExecJump=%s, Target=%s, TargetDistance=%.1f]",
                getDayNumber(), Math.max(0, nextThrowTime - throwTimer),
                Math.max(0, nextJumpTime - jumpTimer), isExecutingThrow, isExecutingJump,
                targetInfo, currentTarget != null ? this.distanceTo(currentTarget) : -1.0);
    }
}