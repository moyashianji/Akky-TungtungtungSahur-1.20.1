// TungSahurEntity.java - 完璧動作版（ダメージ削減・プレイヤー効果削除完全版）
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
    private static final EntityDataAccessor<Byte> CLIMBING_FLAGS =
            SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BYTE);

    // 既存のアニメーション定数（変更なし）
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().then("death", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE_DAY1_ANIM = RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY2_ANIM = RawAnimation.begin().then("idle2", Animation.LoopType.LOOP);
    private static final RawAnimation IDLE_DAY3_ANIM = RawAnimation.begin().then("idle3", Animation.LoopType.LOOP);

    // 既存のアニメーション（変更なし）
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().then("walk", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT_ANIM = RawAnimation.begin().then("sprint", Animation.LoopType.LOOP);

    // 新しく追加する日数別アニメーション
    private static final RawAnimation WALK2_ANIM = RawAnimation.begin().then("walk2", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT2_ANIM = RawAnimation.begin().then("sprint2", Animation.LoopType.LOOP);
    private static final RawAnimation WALK3_ANIM = RawAnimation.begin().then("walk3", Animation.LoopType.LOOP);
    private static final RawAnimation SPRINT3_ANIM = RawAnimation.begin().then("sprint3", Animation.LoopType.LOOP);

    // 既存のアニメーション（変更なし）
    private static final RawAnimation CLIMBING_ANIM = RawAnimation.begin().then("climbing", Animation.LoopType.LOOP);
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().then("shoot_attack", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation JUMP_ATTACK_ANIM = RawAnimation.begin().then("jump_attack", Animation.LoopType.PLAY_ONCE);

    // 完全なAI状態管理
    private int attackCooldown = 0;
    private int throwTimer = 0;
    private int jumpTimer = 0;
    private int watchCheckCooldown = 0;
    private Player lastWatchingPlayer = null;

    // ジャンプ攻撃の状態管理
    private boolean isExecutingJump = false;
    private boolean wasInAir = false;
    private int jumpPhase = 0; // 0=準備, 1=ジャンプ中, 2=着地処理

    // 投擲攻撃の状態管理
    private boolean isExecutingThrow = false;
    private int throwPhase = 0; // 0=チャージ, 1=エイム, 2=投擲
    private int throwChargeTime = 0;

    // AI行動間隔
    private static final int THROW_INTERVAL_MIN = 80;  // 4秒
    private static final int THROW_INTERVAL_MAX = 160; // 8秒
    private static final int JUMP_INTERVAL_MIN = 200;  // 10秒
    private static final int JUMP_INTERVAL_MAX = 300;  // 15秒

    private int nextThrowTime = 0;
    private int nextJumpTime = 0;
    // 死亡アニメーション制御用の新しいデータアクセサー
    private static final EntityDataAccessor<Boolean> IS_DEATH_ANIMATION_PLAYING = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEATH_ANIMATION_COMPLETED = SynchedEntityData.defineId(TungSahurEntity.class, EntityDataSerializers.BOOLEAN);

    // 死亡アニメーション制御用変数
    private int deathAnimationTimer = 0;
    private static final int DEATH_ANIMATION_LENGTH = 40; // 2秒（40tick）
    private boolean deathAnimationStarted = false; // 名前変更: deathStarted → deathAnimationStarted

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
        // 死亡アニメーション用データ定義
        this.entityData.define(IS_DEATH_ANIMATION_PLAYING, false);
        this.entityData.define(DEATH_ANIMATION_COMPLETED, false);

    }

    @Override
    public void die(DamageSource damageSource) {
        // 死亡アニメーションがまだ開始されていない場合
        if (!deathAnimationStarted) {
            deathAnimationStarted = true;
            this.setIsDeathAnimationPlaying(true);

            // 移動停止とAI停止
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.goalSelector.removeAllGoals(goal -> true); // 全てのGoalを削除
            this.targetSelector.removeAllGoals(goal -> true); // 全てのTargetGoalを削除

            TungSahurMod.LOGGER.debug("★死亡アニメーション開始★ - HP=1に設定して実際の死亡を遅延");

            // HPを1に設定して即死を防ぐ（重要！）
            this.setHealth(1.0F);
            return; // ここで終了、super.die()は呼ばない
        }

        // 死亡アニメーションが完了している場合のみ実際の死亡処理
        if (deathAnimationTimer >= DEATH_ANIMATION_LENGTH) {
            TungSahurMod.LOGGER.debug("★死亡アニメーション完了 - 実際の死亡処理実行★");
            if (this.level() instanceof ServerLevel _level) {
                ItemEntity entityToSpawn = new ItemEntity(_level, this.getX(), this.getY(), this.getZ(), new ItemStack(ModItems.TUNG_SAHUR_BAT.get()));
                entityToSpawn.setPickUpDelay(10);
                _level.addFreshEntity(entityToSpawn);
            }

            // 強制的にエンティティを削除
            this.setIsDeathAnimationPlaying(false);
            this.discard(); // remove()ではなくdiscard()を使用
            return; // super.die()は呼ばない（無限ループ防止）
        }
    }
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }
    @Override
    public void tick() {
        // ★★★ 死亡アニメーション中の特別処理 ★★★
        if (deathAnimationStarted && deathAnimationTimer < DEATH_ANIMATION_LENGTH) {
            deathAnimationTimer++;

            // 移動を完全停止
            this.setDeltaMovement(Vec3.ZERO);

            // 基本的なエンティティ更新のみ実行
            this.baseTick();

            // アニメーション完了チェック
            if (deathAnimationTimer >= DEATH_ANIMATION_LENGTH) {
                TungSahurMod.LOGGER.debug("★死亡アニメーション完了 - 次のtickで実際の死亡処理★");
                this.setIsDeathAnimationPlaying(false);

                // 実際の死亡処理を実行
                this.die(this.getLastDamageSource() != null ? this.getLastDamageSource() : this.damageSources().generic());
            }

            return; // 他の処理をスキップ
        }

        // 通常のtick処理（既存のコードはそのまま）
        super.tick();

        // アタックアニメーション完了チェック（クールダウンなし版）
        if (isAttackAnimationPlaying && !isCurrentlyAttacking()) {
            isAttackAnimationPlaying = false;
            TungSahurMod.LOGGER.debug("攻撃アニメーション完了 - 即座に次回攻撃可能");
        }

        if (!this.level().isClientSide) {
            ensureBatEquipped();
            this.setClimbing(this.horizontalCollision);

            // 攻撃状況デバッグ（3秒間隔）
            if (this.tickCount % 60 == 0) { // 3秒ごと
                LivingEntity target = this.getTarget();
                if (target != null) {
                    double distance = this.distanceTo(target);
                }
            }

            // クールダウン完了時の特別ログ
            if (this.attackCooldown == 1) { // 次のtickで0になる
                TungSahurMod.LOGGER.debug("★攻撃クールダウン完了予告★ - 次回攻撃可能");
            }
        }
    }
    @Override
    public void remove(RemovalReason reason) {
        // 死亡による削除の場合、アニメーション完了まで待機
        if (reason == RemovalReason.KILLED && this.deathAnimationTimer < DEATH_ANIMATION_LENGTH) {
            return; // 削除を阻止
        }
        super.remove(reason);
    }
    /**
     * ★攻撃アニメーションを強制的にリセット（新規追加）★
     */
    public void forceResetAttackAnimation() {
        // アニメーション制御フラグをリセット
        this.isAttackAnimationPlaying = false;
        this.attackAnimationCooldown = 0; // クールダウンも削除

        // 攻撃状態をいったんリセット（新しいアニメーション開始のため）
        this.setCurrentlyAttacking(false);

        TungSahurMod.LOGGER.debug("★攻撃アニメーション強制リセット★ - 新しいアニメーション準備完了");
    }
    /**
     * バットが装備されていることを確実にする
     */
    private void ensureBatEquipped() {
        ItemStack mainHandItem = this.getItemInHand(InteractionHand.MAIN_HAND);

        // メインハンドにバットが装備されていない場合、装備する
        if (mainHandItem.isEmpty() || !mainHandItem.is(ModItems.TUNG_SAHUR_BAT.get())) {
            equipBat();
            TungSahurMod.LOGGER.debug("TungSahur {} にバット装備: Day{}", this.getId(), this.getDayNumber());
        }

        // オフハンドは常に空にする
        ItemStack offHandItem = this.getItemInHand(InteractionHand.OFF_HAND);
        if (!offHandItem.isEmpty()) {
            this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }


    // setDayNumberメソッドも修正
    public void setDayNumber(int day) {
        this.entityData.set(DAY_NUMBER, day);
        updateScaleForDay(day);
        updateAttributesForDay(day);
        equipBat(); // 日数変更時にバットを再装備
    }
    // 6. 壁登り状態の判定メソッド（蜘蛛と全く同じ）
    public boolean isClimbing() {
        return (this.entityData.get(CLIMBING_FLAGS) & 1) != 0;
    }

    // 7. 壁登り状態の設定メソッド（蜘蛛と全く同じ）
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
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // シンプル壁登りGoalを最優先
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

        // ターゲット設定
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
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

    // === 大幅弱体化した属性設定 ===
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D) // 20.0D → 150.0D
                .add(Attributes.MOVEMENT_SPEED, 0.35D) // 0.25D → 0.35D
                .add(Attributes.ATTACK_DAMAGE, 12.0D) // 2.0D → 12.0D
                .add(Attributes.FOLLOW_RANGE, 64.0D) // 32.0D → 64.0D
                .add(Attributes.ARMOR, 6.0D) // 0.5D → 6.0D
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D); // 0.1D → 0.8D
    }


    /**
     * ★攻撃クールダウン設定メソッド（新規追加）★
     */
    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
        TungSahurMod.LOGGER.debug("攻撃クールダウン設定: {}tick ({}秒)", ticks, ticks / 20.0F);
    }

    /**
     * ★攻撃クールダウン取得メソッド（新規追加）★
     */
    public int getAttackCooldown() {
        return this.attackCooldown;
    }


    @Override
    public void aiStep() {
        if (deathAnimationStarted) {
            this.baseTick();
            return;
        }

        // 壁登り中の特別処理
        if (this.isWallClimbing()) {
            // 落下ダメージ無効化
            this.fallDistance = 0.0F;

            // 窒息ダメージを無効化（壁にめり込み対策）
            this.setAirSupply(this.getMaxAirSupply());

            // 基本的なAI処理のみ実行
            if (!this.level().isClientSide) {
                updateWatchStatus(); // 見られているかのチェックのみ継続
            }

            // 通常のaiStepを呼ばずに、最小限の処理のみ
            this.baseTick(); // 基本的なエンティティ更新
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


    private void updateAllTimers() {
        // 攻撃クールダウンを最優先で減算
        if (this.attackCooldown > 0) {
            this.attackCooldown--;

            // クールダウン完了時のログ
            if (this.attackCooldown == 0) {
                TungSahurMod.LOGGER.debug("攻撃クールダウン完了 - 再攻撃可能");
            }
        }

        // 他のタイマー
        this.throwTimer++;
        this.jumpTimer++;
        if (this.watchCheckCooldown > 0) this.watchCheckCooldown--;

        // 投擲状態の管理
        if (this.isExecutingThrow) {
            handleThrowExecution();
        } else if (isCurrentlyThrowing()) {
            setCurrentlyThrowing(false);
        }

        // ジャンプ状態の管理
        if (this.isExecutingJump) {
            handleJumpExecution();
        } else if (isCurrentlyJumping()) {
            setCurrentlyJumping(false);
        }

        // 攻撃アニメーション状態の安全リセット（10秒後）
        if (isCurrentlyAttacking()) {
            if (this.tickCount % 200 == 0) { // 10秒ごとにチェック
                TungSahurMod.LOGGER.debug("攻撃状態の安全リセット実行");
                setCurrentlyAttacking(false);
            }
        }
    }
    /**
     * 特殊攻撃の管理
     */
    private void handleSpecialAttacks() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.isExecutingThrow || this.isExecutingJump) return;

        double distance = this.distanceTo(target);

        // 投擲攻撃の判定（2日目以降）
        if (this.getDayNumber() >= 2 && this.throwTimer >= this.nextThrowTime) {
            if (shouldExecuteThrow(target, distance)) {
                startThrowAttack(target);
            }
        }

        // ジャンプ攻撃の判定（3日目のみ）
        if (this.getDayNumber() >= 3 && this.jumpTimer >= this.nextJumpTime) {
            if (shouldExecuteJump(target, distance)) {
                startJumpAttack(target);
            }
        }
    }

    /**
     * 投擲攻撃の実行判定
     */
    private boolean shouldExecuteThrow(LivingEntity target, double distance) {
        // 距離条件：5-20ブロック
        if (distance < 5.0D || distance > 20.0D) return false;

        // 視線チェック
        if (!this.hasLineOfSight(target)) return false;

        // 見られている時は50%確率
        if (this.isBeingWatched() && this.random.nextFloat() < 0.5F) return false;

        return true;
    }

    /**
     * ジャンプ攻撃の実行判定
     */
    private boolean shouldExecuteJump(LivingEntity target, double distance) {
        // 距離条件：4-15ブロック
        if (distance < 4.0D || distance > 15.0D) return false;

        // 高低差がある場合優先
        double heightDiff = target.getY() - this.getY();
        if (heightDiff >= 2.0D) return true;

        // 見られている時は30%確率
        if (this.isBeingWatched() && this.random.nextFloat() < 0.3F) return false;

        return distance >= 6.0D; // 中距離以上で実行
    }

    /**
     * 投擲攻撃開始
     */
    private void startThrowAttack(LivingEntity target) {
        this.isExecutingThrow = true;
        this.throwPhase = 0;
        this.throwChargeTime = switch (this.getDayNumber()) {
            case 2 -> 15; // 0.75秒
            case 3 -> 10; // 0.5秒
            default -> 20; // 1秒
        };

        this.setCurrentlyThrowing(true);
        this.getNavigation().stop();

        // チャージ開始音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.CROSSBOW_LOADING_START, SoundSource.HOSTILE, 0.6F, 1.2F);

        TungSahurMod.LOGGER.debug("投擲攻撃開始: 距離={}", this.distanceTo(target));
    }

    /**
     * ジャンプ攻撃開始
     */
    private void startJumpAttack(LivingEntity target) {
        this.isExecutingJump = true;
        this.jumpPhase = 0;
        this.wasInAir = false;

        this.setCurrentlyJumping(true);
        this.getNavigation().stop();

        // 準備音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.7F, 1.3F);

        TungSahurMod.LOGGER.debug("ジャンプ攻撃開始: 距離={}", this.distanceTo(target));
    }

    /**
     * 投擲攻撃の実行処理
     */
    private void handleThrowExecution() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            stopThrowAttack();
            return;
        }

        // ターゲット注視
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        switch (this.throwPhase) {
            case 0: // チャージフェーズ
                this.throwChargeTime--;
                if (this.throwChargeTime % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    // チャージパーティクル
                    Vec3 pos = this.position().add(0, this.getBbHeight() * 0.8, 0);
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.02);
                }

                if (this.throwChargeTime <= 0) {
                    this.throwPhase = 1;
                    this.throwChargeTime = 8; // エイム時間

                    // チャージ完了音
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.CROSSBOW_LOADING_END, SoundSource.HOSTILE, 0.7F, 1.0F);
                }
                break;

            case 1: // エイムフェーズ
                this.throwChargeTime--;
                if (this.throwChargeTime % 3 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    // エイムパーティクル
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

    /**
     * ジャンプ攻撃の実行処理
     */
    private void handleJumpExecution() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            stopJumpAttack();
            return;
        }

        switch (this.jumpPhase) {
            case 0: // 準備フェーズ（15tick = 0.75秒）
                this.jumpPhase++;

                // ジャンプ実行
                Vec3 direction = target.position().subtract(this.position()).normalize();
                double jumpPower = 1.0D;
                double distance = this.distanceTo(target);
                double speedMultiplier = Math.min(1.5D, Math.max(0.8D, distance / 8.0D));

                Vec3 jumpVec = direction.scale(speedMultiplier).add(0, jumpPower, 0);
                this.setDeltaMovement(jumpVec);
                this.wasInAir = true;

                // ジャンプ音
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.8F, 1.0F);

                TungSahurMod.LOGGER.debug("ジャンプ実行: ベクトル={}", jumpVec);
                break;

            case 1: // ジャンプ中
                // 着地チェックは handleJumpLanding() で処理
                if (this.level() instanceof ServerLevel serverLevel && this.random.nextInt(3) == 0) {
                    // 飛行軌跡パーティクル
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            this.getX(), this.getY() + this.getBbHeight() * 0.3, this.getZ(),
                            1, 0.1, 0.1, 0.1, 0.01);
                }
                break;
        }
    }

    /**
     * ジャンプ着地の処理
     */
    private void handleJumpLanding() {
        if (this.isExecutingJump && this.jumpPhase == 1 && this.wasInAir && this.onGround()) {
            // 着地！
            this.jumpPhase = 2;
            performJumpLandingDamage();
            stopJumpAttack();
            resetJumpTimer();
        }
    }

    /**
     * ジャンプ着地ダメージ（大幅弱体化）
     */
    private void performJumpLandingDamage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // 着地エフェクト
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                this.getX(), this.getY(), this.getZ(), 3, 0.5, 0.1, 0.5, 0.1);

        // 衝撃波パーティクル（範囲縮小）
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * 2 * Math.PI;
            double radius = 2.0; // 3.0 → 2.0 に縮小
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    x, this.getY() + 0.1, z, 1, 0.05, 0.05, 0.05, 0.05);
        }

        // 範囲ダメージ（ダメージ強化版）
        AABB damageArea = new AABB(this.blockPosition()).inflate(2.0); // 3.0 → 2.0
        List<Entity> nearbyEntities = serverLevel.getEntities(this, damageArea);

        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity living && entity != this) {
                double distance = this.distanceTo(living);
                if (distance <= 2.0) { // 3.0 → 2.0
                    // 日数に応じたダメージ強化
                    float damage;
                    if (living instanceof Player) {
                        // プレイヤーへのダメージを日数に応じて大幅増加
                        damage = switch (getDayNumber()) {
                            case 1 -> 8.0F;   // 1日目: 8ダメージ (1.0F→8.0F)
                            case 2 -> 12.0F;  // 2日目: 12ダメージ
                            case 3 -> 16.0F;  // 3日目: 16ダメージ
                            default -> 6.0F;
                        };
                    } else {
                        // その他エンティティへのダメージも強化
                        float baseDamage = switch (getDayNumber()) {
                            case 1 -> 10.0F;  // 1日目: 10ダメージ
                            case 2 -> 15.0F;  // 2日目: 15ダメージ
                            case 3 -> 20.0F;  // 3日目: 20ダメージ
                            default -> 8.0F;
                        };
                        damage = (float) (baseDamage * (1.0D - distance / 2.0D));
                        damage = Math.max(damage, 4.0F); // 最低4ダメージ (2.0F→4.0F)
                    }

                    living.hurt(this.damageSources().mobAttack(this), damage);

                    // 非常に軽いノックバック（プレイヤーには特に軽く）
                    Vec3 knockback = living.position().subtract(this.position()).normalize();
                    double knockbackStrength = living instanceof Player ? 0.1D : 0.2D; // プレイヤーにはさらに軽く
                    living.setDeltaMovement(living.getDeltaMovement().add(
                            knockback.x * knockbackStrength, 0.08D, knockback.z * knockbackStrength)); // 0.15D → 0.08D

                    TungSahurMod.LOGGER.debug("ジャンプ着地ダメージ: {}に{}ダメージ",
                            living.getClass().getSimpleName(), damage);
                }
            }
        }

        // 着地音
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.3F, 0.8F); // 0.5F → 0.3F
    }

    /**
     * 投擲実行
     */
    private void executeThrowProjectile(LivingEntity target) {
        Vec3 direction = target.position().subtract(this.position()).normalize();
        TungBatProjectile projectile = new TungBatProjectile(this.level(), this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double speed = 1.6D + (getDayNumber() * 0.3D);
        projectile.shoot(direction.x, direction.y + 0.1D, direction.z, (float) speed, 1.0F);

        this.level().addFreshEntity(projectile);

        // 投擲音
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.9F, 0.9F);

        TungSahurMod.LOGGER.debug("投擲実行完了");
    }






    /**
     * 攻撃停止処理
     */
    private void stopThrowAttack() {
        this.isExecutingThrow = false;
        this.throwPhase = 0;
        this.throwChargeTime = 0;

        // アニメーション状態を遅延リセット
        this.level().scheduleTick(this.blockPosition(),
                this.level().getBlockState(this.blockPosition()).getBlock(), 10);
    }

    private void stopJumpAttack() {
        this.isExecutingJump = false;
        this.jumpPhase = 0;
        this.wasInAir = false;

        // アニメーション状態を遅延リセット
        this.level().scheduleTick(this.blockPosition(),
                this.level().getBlockState(this.blockPosition()).getBlock(), 10);
    }

    /**
     * タイマーリセット
     */
    private void resetThrowTimer() {
        this.throwTimer = 0;
        this.nextThrowTime = THROW_INTERVAL_MIN + this.random.nextInt(THROW_INTERVAL_MAX - THROW_INTERVAL_MIN);

        // 3日目は頻度アップ
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

    // === 攻撃可能チェック ===
    public boolean canAttack() {
        boolean cooldownOK = this.attackCooldown <= 0;
        boolean notThrowingOK = !this.isExecutingThrow;
        boolean notJumpingOK = !this.isExecutingJump;
        boolean notClimbingOK = !this.isWallClimbing(); // 壁登り中は攻撃不可

        boolean result = cooldownOK && notThrowingOK && notJumpingOK && notClimbingOK;

        // デバッグログ（攻撃不可時のみ）
        if (!result && this.tickCount % 20 == 0) { // 1秒間隔でログ
            TungSahurMod.LOGGER.debug("canAttack=false: クールダウン={}, 投擲中={}, ジャンプ中={}, 壁登り中={}",
                    this.attackCooldown, this.isExecutingThrow, this.isExecutingJump, this.isWallClimbing());
        }

        return result;
    }

    // === 既存のシステム（監視、スプリント等）===
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
    // === バット装備 ===
    private void equipBat() {
        ItemStack batStack = new ItemStack(ModItems.TUNG_SAHUR_BAT.get());
        CompoundTag tag = batStack.getOrCreateTag();
        System.out.println("batqueee");
        // 日数に応じたカスタマイズ
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

        // ドロップ確率を0に設定（エンティティ用バットはドロップしない）
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    // === アニメーション制御 ===
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    // predicate メソッドの修正
// 2. アタックアニメーション制御用の変数を追加
    private boolean isAttackAnimationPlaying = false;
    private int attackAnimationCooldown = 0;

    /**
     * ★★★ 死亡アニメーション対応版 predicate ★★★
     */
    private PlayState predicate(AnimationState<TungSahurEntity> animationState) {
        // 現在再生中のアニメーションをチェック
        String currentAnimName = animationState.getController().getCurrentAnimation() != null
                ? animationState.getController().getCurrentAnimation().animation().name() : "";

        // ★★★ 最優先: 死亡アニメーション ★★★
        if (this.isDeathAnimationPlaying()) {
            if (!currentAnimName.equals("death")) {
                TungSahurMod.LOGGER.debug("死亡アニメーション開始");
                animationState.getController().forceAnimationReset();
                animationState.getController().setAnimation(DEATH_ANIM);
            }
            return PlayState.CONTINUE;
        }

        // ★アタックアニメーション完了チェック★
        if (isAttackAnimationPlaying) {
            // アニメーションコントローラーの状態をチェック
            AnimationController.State controllerState = animationState.getController().getAnimationState();

            // アニメーションが停止状態になった場合のみ完了とみなす
            if (controllerState.equals(AnimationController.State.STOPPED) || !currentAnimName.equals("attack")) {
                isAttackAnimationPlaying = false;
                setCurrentlyAttacking(false); // 攻撃状態をリセット
                TungSahurMod.LOGGER.debug("attackアニメーション完了 - 攻撃状態リセット");
            } else {
                // まだ再生中は継続
                return PlayState.CONTINUE;
            }
        }

        // ★重要: 攻撃アニメーション開始判定（クールダウン削除）★
        if (isCurrentlyAttacking() && !isAttackAnimationPlaying) {
            // ★毎回強制的にアニメーションを再開★
            TungSahurMod.LOGGER.debug("★攻撃アニメーション強制開始★");
            animationState.getController().forceAnimationReset();
            animationState.getController().setAnimation(ATTACK_ANIM);
            isAttackAnimationPlaying = true;
            return PlayState.CONTINUE;
        }

        // ★アタックアニメーション再生中は他を完全にブロック★
        if (isAttackAnimationPlaying) {
            return PlayState.CONTINUE;
        }

        // ★その他の特殊アニメーション★
        // 投擲アニメーション完了チェック
        if (currentAnimName.equals("shoot_attack")) {
            AnimationController.State controllerState = animationState.getController().getAnimationState();
            if (controllerState.equals(AnimationController.State.STOPPED)) {
                setCurrentlyThrowing(false);
                TungSahurMod.LOGGER.debug("投擲アニメーション完了");
            } else {
                return PlayState.CONTINUE;
            }
        }

        // ジャンプアニメーション完了チェック
        if (currentAnimName.equals("jump_attack")) {
            AnimationController.State controllerState = animationState.getController().getAnimationState();
            if (controllerState.equals(AnimationController.State.STOPPED)) {
                setCurrentlyJumping(false);
                TungSahurMod.LOGGER.debug("ジャンプアニメーション完了");
            } else {
                return PlayState.CONTINUE;
            }
        }

        // ★新しいアニメーション開始判定★
        // 投擲アニメーション
        if (isCurrentlyThrowing()) {
            if (!currentAnimName.equals("shoot_attack")) {
                animationState.getController().forceAnimationReset();
            }
            animationState.getController().setAnimation(THROW_ANIM);
            return PlayState.CONTINUE;
        }

        // ジャンプアニメーション
        if (isCurrentlyJumping()) {
            if (!currentAnimName.equals("jump_attack")) {
                animationState.getController().forceAnimationReset();
            }
            animationState.getController().setAnimation(JUMP_ATTACK_ANIM);
            return PlayState.CONTINUE;
        }

        // ★通常のアニメーション★
        // 壁登りアニメーション
        if (isWallClimbing()) {
            animationState.getController().setAnimation(CLIMBING_ANIM);
            return PlayState.CONTINUE;
        }

        // 走りアニメーション - 日数に応じて変更
        if (isSprinting() && animationState.isMoving()) {
            RawAnimation sprintAnim = switch (getDayNumber()) {
                case 2 -> SPRINT2_ANIM;
                case 3 -> SPRINT3_ANIM;
                default -> SPRINT_ANIM;
            };
            animationState.getController().setAnimation(sprintAnim);
            return PlayState.CONTINUE;
        }

        // 歩きアニメーション - 日数に応じて変更
        if (animationState.isMoving()) {
            RawAnimation walkAnim = switch (getDayNumber()) {
                case 2 -> WALK2_ANIM;
                case 3 -> WALK3_ANIM;
                default -> WALK_ANIM;
            };
            animationState.getController().setAnimation(walkAnim);
            return PlayState.CONTINUE;
        }

        // アイドルアニメーション
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


    private void updateScaleForDay(int day) {
        float scale = switch (day) {
            case 1 -> 1.0F;
            case 2 -> 1.3F;
            case 3 -> 1.7F;
            default -> 1.0F;
        };
        setScaleFactor(scale);
    }

    // === 大幅弱体化した日数別属性 ===
    private void updateAttributesForDay(int day) {
        switch (day) {
            case 1:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D); // 30.0D → 20.0D
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0D); // 4.0D → 2.0D
                break;
            case 2:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(150.0D); // 40.0D → 25.0D
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.5D); // 5.0D → 2.5D
                break;
            case 3:
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D); // 50.0D → 30.0D
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0D); // 6.0D → 3.0D
                break;
        }
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
    public boolean isDeathAnimationPlaying() {
        return this.entityData.get(IS_DEATH_ANIMATION_PLAYING);
    }
    public void setIsDeathAnimationPlaying(boolean playing) {
        this.entityData.set(IS_DEATH_ANIMATION_PLAYING, playing);
    }
    public boolean isDeathAnimationCompleted() { return this.entityData.get(DEATH_ANIMATION_COMPLETED); }
    public void setDeathAnimationCompleted(boolean completed) { this.entityData.set(DEATH_ANIMATION_COMPLETED, completed); }
    @Override
    public boolean isSprinting() {
        return this.entityData.get(IS_SPRINTING);
    }

    public void setSprinting(boolean sprinting) {
        this.entityData.set(IS_SPRINTING, sprinting);
    }

    // === その他の既存メソッド ===
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

        // ★★★ 死亡アニメーション状態も読み込み ★★★
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted); // 名前変更
        tag.putInt("DeathAnimationTimer", deathAnimationTimer);

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

        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted"); // 名前変更
        deathAnimationTimer = tag.getInt("DeathAnimationTimer");
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

    // === スパイダーと同じonClimbableの実装 ===
    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // 壁登り中は落下ダメージなし（スパイダーと同じ）
        if (this.isWallClimbing()) {
            return;
        }
        super.checkFallDamage(y, onGround, state, pos);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        // TungSahurは落下ダメージを受けない
        return false;
    }
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHitIn) {
        super.dropCustomDeathLoot(source, looting, recentlyHitIn);
        this.spawnAtLocation(new ItemStack(ModItems.TUNG_SAHUR_BAT.get()));
    }

    // === 重力制御（スパイダーと同じ） ===
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isWallClimbing()) {
            // 壁登り中の移動処理（スパイダーと同じ）
            if (this.getDeltaMovement().y <= 0.0D) {
                // 下降を防ぐ
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            }
        }
        super.travel(travelVector);
    }


    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // 死亡アニメーション中はダメージを受けない
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
        return String.format("TungSahur[Day=%d, ThrowIn=%d, JumpIn=%d, ExecThrow=%s, ExecJump=%s]",
                getDayNumber(), Math.max(0, nextThrowTime - throwTimer),
                Math.max(0, nextJumpTime - jumpTimer), isExecutingThrow, isExecutingJump);
    }
}