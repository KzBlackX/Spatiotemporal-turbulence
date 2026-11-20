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

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组配置项，写入 {@code spatiotemporal_turbulence-local.toml}。
 * <p>新增配置项时在这里声明，并在 {@code assets/spatiotemporal_turbulence/lang} 里补上
 * {@code spatiotemporal_turbulence.configuration.<键名>} 的翻译，否则游戏内会直接显示键名。</p>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 玩家进入主世界后，黑洞出现前需要等待的秒数。 */
    public static final ModConfigSpec.IntValue WARMUP_SECONDS = BUILDER
            .comment("玩家进入主世界后，黑洞出现前需要等待的秒数。Seconds the player must stay in the overworld before the black hole appears.")
            .defineInRange("warmupSeconds", 10, 1, 600);

    static final ModConfigSpec SPEC = BUILDER.build();
}
