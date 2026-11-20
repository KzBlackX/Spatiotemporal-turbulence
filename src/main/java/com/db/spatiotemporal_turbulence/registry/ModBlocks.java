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

import java.util.function.Function;

import com.db.spatiotemporal_turbulence.SpatiotemporalTurbulence;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组所有方块的注册表。
 * <p>新增方块统一走 {@link #registerWithItem}，它会同时登记同名 BlockItem，
 * 避免出现"方块存在但拿不到物品"的漏注册：</p>
 * <pre>{@code
 * public static final DeferredBlock<Block> BLACK_HOLE_CORE =
 *         ModBlocks.registerWithItem("black_hole_core", Block::new);
 * }</pre>
 * <p>需要自定义方块类时把类构造器传进去即可：
 * {@code registerWithItem("altar", AltarBlock::new)}；
 * 需要改属性时用三参数版本（第三个参数是属性修改器）。</p>
 */
public final class ModBlocks {
    /**
     * 方块注册表，由 {@link ModRegistry#register(net.neoforged.bus.api.IEventBus)} 挂到模组事件总线。
     */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SpatiotemporalTurbulence.MODID);

    private ModBlocks() {
    }

    /**
     * 注册一个方块，并自动登记对应的 BlockItem。
     *
     * @param name    注册名，必须是小写字母、数字与下划线
     * @param factory 方块构造器，参数是已由注册系统填好 id 的属性对象
     * @return 方块对应的延迟持有者
     */
    public static <B extends Block> DeferredBlock<B> registerWithItem(String name,
            Function<BlockBehaviour.Properties, ? extends B> factory) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory);
        ModItems.ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }
}
