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

package com.db.spatiotemporal_turbulence.client;

import com.db.spatiotemporal_turbulence.SpatiotemporalTurbulence;
import com.db.spatiotemporal_turbulence.registry.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端（仅物理客户端加载）的模组总线事件监听器。
 * <p>实体渲染器必须在 {@link EntityRenderersEvent.RegisterRenderers} 里注册，
 * 这也是 NeoForge 唯一支持的实体渲染器注册时机。</p>
 */
@EventBusSubscriber(modid = SpatiotemporalTurbulence.MODID, value = Dist.CLIENT)
public final class VampireClientEvents {
    private VampireClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VAMPIRE.get(), VampireRenderer::new);
    }
}
