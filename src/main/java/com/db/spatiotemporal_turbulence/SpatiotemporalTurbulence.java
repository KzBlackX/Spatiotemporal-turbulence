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

package com.db.spatiotemporal_turbulence;

import org.slf4j.Logger;

import com.db.spatiotemporal_turbulence.registry.ModRegistry;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 模组主类。
 * <p>这里只做三件事：注册内容、注册游戏事件监听、注册配置。具体逻辑分别放在
 * {@link ModRegistry}、{@link BlackHoleHandler} 与 {@link Config} 中。</p>
 */
@Mod(SpatiotemporalTurbulence.MODID)
public class SpatiotemporalTurbulence {
    /** 模组 id，必须与 META-INF/neoforge.mods.toml 中的 modId 一致。 */
    public static final String MODID = "spatiotemporal_turbulence";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SpatiotemporalTurbulence(IEventBus modEventBus, ModContainer modContainer) {
        // 注册方块、物品、结构等内容（新增注册表时只需改 ModRegistry）
        ModRegistry.register(modEventBus);

        // 黑洞吸入逻辑：玩家进入主世界后经过配置的等待时间被吸入并传送到中世纪维度
        NeoForge.EVENT_BUS.addListener(BlackHoleHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(BlackHoleHandler::onPlayerLogout);

        // 本模组的配置，写入 spatiotemporal_turbulence-local.toml
        modContainer.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
    }
}
