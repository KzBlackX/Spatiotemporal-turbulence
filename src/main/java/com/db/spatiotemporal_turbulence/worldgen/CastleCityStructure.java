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

package com.db.spatiotemporal_turbulence.worldgen;

import java.util.Optional;

import com.db.spatiotemporal_turbulence.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * 「城堡内的大型城市」结构：一整座被城墙围起来的城市，完整落在一个 96x96 的
 * {@link CastleCityPiece} 里（单 piece 结构，不使用 NBT 模板）。
 * <p>结构本身只负责选点：在区块中心用 {@link Heightmap.Types#WORLD_SURFACE_WG}
 * 取地表高度作为城市的基准面，然后把整座城市交给片段去程序化放置方块。</p>
 * <p>为什么这里要覆写 {@link #findGenerationPoint(Structure.GenerationContext)}：
 * {@link SinglePieceStructure} 默认实现会把「区块最小角」直接交给片段构造器，拿不到地形高度；
 * 而本结构高达 96x96，横跨 6x6 个区块，片段不能在 {@code postProcess} 里现查高度图
 * （那时相邻区块未必已生成，多个区块查到的基准高度可能不一致）。因此在生成结构起点时
 * 用 {@code ChunkGenerator#getFirstOccupiedHeight(..., WORLD_SURFACE_WG, ...)} 一次性算出
 * 基准高度，写进片段的包围盒里，保证每个区块拿到的都是同一个基准面。</p>
 */
public class CastleCityStructure extends SinglePieceStructure {
    /** 结构 JSON 使用的编解码器。 */
    public static final MapCodec<CastleCityStructure> CODEC = Structure.simpleCodec(CastleCityStructure::new);

    /** 城市在 X 方向的边长（格）。 */
    public static final int WIDTH = 96;
    /** 城市在 Z 方向的边长（格）。 */
    public static final int DEPTH = 96;

    /** 默认高度，仅用于兜底构造器；实际放置时由 {@link #findGenerationPoint} 传入真实地表高度。 */
    private static final int FALLBACK_SURFACE_Y = 64;

    public CastleCityStructure(Structure.StructureSettings settings) {
        // 正常流程不会用到这个构造器：下面覆写的 findGenerationPoint 会自己 new 片段并传入真实高度。
        // 但 SinglePieceStructure 的构造器要求一个 PieceConstructor，所以这里给一个可用的兜底实现。
        super((random, x, z) -> new CastleCityPiece(random, x, FALLBACK_SURFACE_Y, z), WIDTH, DEPTH, settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // 与 SinglePieceStructure 默认实现一致的检查：生物群系在不在此柱列、以及不泡在海里。
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        if (getLowestY(context, WIDTH, DEPTH) < context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        // 区块中心的地表高度（WORLD_SURFACE_WG，取"第一个被占据的格子"，即地表最上面那块实心方块）
        int surfaceY = context.chunkGenerator()
                .getFirstOccupiedHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());

        CastleCityPiece piece = new CastleCityPiece(context.random(), chunkPos.getMinBlockX(), surfaceY,
                chunkPos.getMinBlockZ());
        // 结构起点位置取区块中心，与单 piece 结构的默认行为保持一致
        return Optional.of(new Structure.GenerationStub(new BlockPos(centerX, surfaceY, centerZ),
                builder -> builder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.CASTLE_CITY.get();
    }
}
