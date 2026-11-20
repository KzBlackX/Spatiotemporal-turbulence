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

import net.neoforged.bus.api.IEventBus;

/**
 * 注册入口：把所有 {@code DeferredRegister} 统一挂到模组事件总线。
 * <p>以后新增注册表类型（实体、方块实体、数据组件、创造模式物品栏、声音等）时，
 * 在对应的注册类里声明字段，并在这里补一行 {@code Xxx.REGISTRY.register(modEventBus)} 即可，
 * 模组主类不需要再改动。</p>
 */
public final class ModRegistry {
    private ModRegistry() {
    }

    /**
     * 由模组构造器调用，必须在模组加载阶段执行。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}
