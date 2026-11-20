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

package com.db.spatiotemporal_turbulence.worldgen.dungeon;

import java.util.Optional;

import com.db.spatiotemporal_turbulence.registry.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * 「地下地牢」结构：一整座埋在地下的地牢，完整落在一个 64x64 的 {@link DungeonPiece}
 * 里（单 piece 结构，不使用 NBT 模板）。
 * <p>与 {@code CastleCityStructure} 的区别在于：地牢在地下，所以不能用地表高度当基准面，
 * 也<b>不能</b>沿用 {@link SinglePieceStructure} 默认实现里「低于海平面就放弃生成」的检查
 * （那套检查是给地表结构用的，会把地下结构全部拒掉）。因此这里完全覆写
 * {@link #findGenerationPoint(Structure.GenerationContext)}：</p>
 * <ul>
 *   <li>先用 {@link #getLowestY(Structure.GenerationContext, int, int)} 取 64x64 范围四角中
 *       最低的地表高度作为「地表参考高度」，避免地牢顶部在山体里穿出地面；</li>
 *   <li>基准面 = 地表参考高度 − 24~37 格（基础埋深）− 0~3 格（随机抖动），
 *       也就是地牢地板位于地表以下 <b>24~40 格</b>；抖动让每个地牢的埋深都不一样；</li>
 *   <li>最后把基准面夹到有效世界高度内（上面留出片段自身高度，下面不穿世界底部）。</li>
 * </ul>
 * <p>与城堡城市一样，基准高度必须在生成结构起点时一次性算好写进片段的包围盒：
 * 64x64 的地牢横跨约 5x5 个区块，{@code postProcess} 里现查高度图的话，相邻区块未必已生成，
 * 不同区块算出的基准高度可能不一致，地牢就会被切歪。</p>
 */
public class DungeonStructure extends SinglePieceStructure {
    /** 结构 JSON 使用的编解码器。 */
    public static final MapCodec<DungeonStructure> CODEC = Structure.simpleCodec(DungeonStructure::new);

    /** 地牢在 X 方向的边长（格）。 */
    public static final int WIDTH = 64;
    /** 地牢在 Z 方向的边长（格）。 */
    public static final int DEPTH = 64;

    /** 基础埋深下限（格）：地牢基准面至少在地表以下这么多格。 */
    private static final int MIN_DEPTH = 24;
    /** 基础埋深上限（格）：加上 {@link #MAX_DEPTH_JITTER} 后总埋深不超过 40 格。 */
    private static final int MAX_DEPTH = 40;
    /** 附加随机抖动上限（格）：0~3 格，让不同地牢的高度错开。 */
    private static final int MAX_DEPTH_JITTER = 3;

    /** 默认基准面 Y，仅用于兜底构造器；实际放置时由 {@link #findGenerationPoint} 传入真实高度。 */
    private static final int FALLBACK_BASE_Y = 32;

    public DungeonStructure(Structure.StructureSettings settings) {
        // 正常流程不会用到这个构造器：下面覆写的 findGenerationPoint 会自己 new 片段并传入真实基准高度。
        // 但 SinglePieceStructure 的构造器要求一个 PieceConstructor，所以这里给一个可用的兜底实现。
        super((random, x, z) -> new DungeonPiece(random, x, FALLBACK_BASE_Y, z), WIDTH, DEPTH, settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // 只保留与 SinglePieceStructure 默认实现一致的「生物群系检查」；刻意不做「低于海平面就放弃」的检查。
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();

        // 地表参考高度：64x64 范围四角世界表面高度的最小值。
        int surfaceY = getLowestY(context, WIDTH, DEPTH);

        // 所有随机都来自 context.random()：同一地牢起点每次生成结果都一致。
        RandomSource random = context.random();
        // 基础埋深 24~37 格（MAX_DEPTH - MIN_DEPTH - MAX_DEPTH_JITTER = 13 → nextInt(14)），
        // 再加上 0~3 格抖动，最终基准面位于地表以下 24~40 格。
        int depth = MIN_DEPTH + random.nextInt(MAX_DEPTH - MIN_DEPTH - MAX_DEPTH_JITTER + 1);
        int jitter = random.nextInt(MAX_DEPTH_JITTER + 1);
        int baseY = surfaceY - depth - jitter;

        // 夹到有效世界高度内：下面不穿世界底部，上面给片段自身高度留出余量。
        int minY = context.heightAccessor().getMinY();
        int maxY = context.heightAccessor().getMaxY();
        baseY = Mth.clamp(baseY, minY + 4, maxY - DungeonPiece.PIECE_HEIGHT - 4);

        DungeonPiece piece = new DungeonPiece(context.random(), chunkPos.getMinBlockX(), baseY, chunkPos.getMinBlockZ());
        // 结构起点取区块中心（与单 piece 结构的默认行为一致），Y 用地下基准面，
        // 这样 findValidGenerationPoint 的生物群系检查也会在地牢所在的 Y 上做。
        return Optional.of(new Structure.GenerationStub(new BlockPos(centerX, baseY, centerZ),
                builder -> builder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DUNGEON.get();
    }
}
