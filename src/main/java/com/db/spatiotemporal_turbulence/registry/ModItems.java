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

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组所有物品的注册表。
 * <p>注册物品时使用 {@code ModItems.ITEMS}，例如：</p>
 * <pre>{@code
 * public static final DeferredItem<Item> MAGIC_SCROLL =
 *         ModItems.ITEMS.registerSimpleItem("magic_scroll");
 * }</pre>
 * <p>需要自定义属性时用 {@code ITEMS.registerSimpleItem("name", p -> p.stacksTo(1))}；
 * 需要自定义物品类时用 {@code ITEMS.register("name", MyItem::new)}。</p>
 * <p>方块对应的 BlockItem 不在这里手写，统一由 {@link ModBlocks#registerWithItem} 一并登记。</p>
 */
public final class ModItems {
    /**
     * 物品注册表，由 {@link ModRegistry#register(net.neoforged.bus.api.IEventBus)} 挂到模组事件总线。
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpatiotemporalTurbulence.MODID);

    private ModItems() {
    }
}
