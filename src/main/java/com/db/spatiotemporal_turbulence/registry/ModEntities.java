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

package com.db.spatiotemporal_turbulence.registry;

import com.db.spatiotemporal_turbulence.SpatiotemporalTurbulence;
import com.db.spatiotemporal_turbulence.entity.Vampire;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组所有实体类型的注册表。
 * <p>实体 id 由 {@code DeferredRegister} 的命名空间固定为 {@code spatiotemporal_turbulence:vampire}，
 * 地牢刷怪笼等地图数据会按这个 id 引用，不要随意改名。</p>
 * <p>默认属性与刷怪限制分别通过 {@link EntityAttributeCreationEvent} 与
 * {@link RegisterSpawnPlacementsEvent} 注册；这两个事件都在模组总线上，
 * 所以本类同时用 {@link EventBusSubscriber} 声明为模组总线监听器。</p>
 */
@EventBusSubscriber(modid = SpatiotemporalTurbulence.MODID)
public final class ModEntities {
    /**
     * 实体类型注册表，由 {@link ModRegistry#register(net.neoforged.bus.api.IEventBus)} 挂到模组事件总线。
     */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpatiotemporalTurbulence.MODID);

    /** 吸血鬼实体类型：敌对生物，碰撞箱 0.6 x 1.95，客户端追踪范围 64。 */
    public static final DeferredHolder<EntityType<?>, EntityType<Vampire>> VAMPIRE =
            ENTITY_TYPES.register("vampire", id -> EntityType.Builder.of(Vampire::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(64)
                    .notInPeaceful()
                    // 规格没有要求掉落物；显式关掉战利品表，避免引用不存在的 entities/vampire 战利品表
                    .noLootTable()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntities() {
    }

    /** 注册吸血鬼的默认属性（最大生命 24、攻击 4、移速 0.3、跟随距离 32）。 */
    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(VAMPIRE.get(), Vampire.createAttributes().build());
    }

    /**
     * 注册吸血鬼的刷怪限制：需要地面（ON_GROUND），参考僵尸使用「暗处」判定
     * {@link Monster#checkMonsterSpawnRules}，并按运动方块高度图取刷怪 Y 坐标。
     */
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                VAMPIRE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
