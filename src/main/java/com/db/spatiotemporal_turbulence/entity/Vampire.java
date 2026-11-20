/*
 * Spatiotemporal Turbulence - a Minecraft NeoForge mod.
 * Copyright (C) 2023-2026 Spatiotemporal-turbulence-maker
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <https://www.gnu.org/licenses/>.
 */

package com.db.spatiotemporal_turbulence.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * 吸血鬼：中世纪维度里的敌对生物。
 * <p>行为上相当于一个更凶、更快、更耐打的近战亡灵：会主动锁定玩家并追砍。</p>
 * <p>日光特性没有走原版 {@code burn_in_daylight} 实体标签，因为本模组的中世纪维度类型没有声明
 * {@code minecraft:gameplay/monsters_burn} 环境属性（该属性默认 false），照搬原版标签在这套维度里
 * 不会着火。这里直接复刻旧版僵尸的日光判定：白天 + 头顶可见天空 + 亮度足够；戴头盔、淋雨/泡水、
 * 进粉末雪都会阻止着火。</p>
 */
public class Vampire extends Monster {
    /** 被阳光点燃的秒数，与原版僵尸一致。 */
    private static final float SUN_BURN_SECONDS = 8.0F;

    public Vampire(EntityType<? extends Vampire> type, Level level) {
        super(type, level);
    }

    /**
     * 默认属性：最大生命 24、攻击 4、移速 0.3、跟随距离 32。
     * <p>由 {@code EntityAttributeCreationEvent} 注册到实体类型上。</p>
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        // 水中漂浮，避免在水里沉底
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 近战攻击目标
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        // 随机游走（会主动避开水面）
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        // 看向玩家 / 四处张望
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // 被打就还手，并主动锁定玩家
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 只在服务端结算日光燃烧
        if (this.level().isClientSide()) {
            return;
        }

        if (!this.isSunBurnTick()) {
            return;
        }

        // 戴头盔（任何头部装备）时用装备耐久抵挡阳光，否则着火 8 秒
        ItemStack helmet = this.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            if (helmet.isDamageableItem()) {
                helmet.hurtAndBreak(this.random.nextInt(2), this, EquipmentSlot.HEAD);
            }
        } else {
            this.igniteForSeconds(SUN_BURN_SECONDS);
        }
    }

    /**
     * 白天、暴露在天空下且亮度足够时返回 true。
     * <p>判定逻辑复刻原版僵尸旧版 {@code aiStep}：眼睛位置的光照足够亮且随机通过，
     * 同时水/雨/粉末雪会阻止燃烧（阴影下 {@code canSeeSky} 为 false，也不会燃烧）。
     * 这里用未废弃的原始光照值代替已废弃的 {@code getLightLevelDependentMagicValue}，
     * 阈值 12/15 与旧版「魔法亮度 &gt; 0.5」等价。</p>
     */
    private boolean isSunBurnTick() {
        // 白天（固定时间的维度视为永远不满足，避免在中世纪维度之外误伤）
        if (!this.level().isBrightOutside()) {
            return false;
        }

        BlockPos eyePos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
        float brightness = this.level().getMaxLocalRawBrightness(eyePos) / 15.0F;
        boolean protectedFromSun = this.isInWaterOrRain() || this.isInPowderSnow || this.wasInPowderSnow;
        return brightness > 0.8F
                && this.random.nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F
                && !protectedFromSun
                && this.level().canSeeSky(eyePos);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }
}
