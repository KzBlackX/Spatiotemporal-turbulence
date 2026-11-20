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
import com.db.spatiotemporal_turbulence.worldgen.CastleCityPiece;
import com.db.spatiotemporal_turbulence.worldgen.CastleCityStructure;
import com.db.spatiotemporal_turbulence.worldgen.dungeon.DungeonPiece;
import com.db.spatiotemporal_turbulence.worldgen.dungeon.DungeonStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组所有世界结构相关的注册表。
 * <p>这里同时登记两种条目：</p>
 * <ul>
 *   <li>{@code StructureType}：结构 JSON 里 {@code "type"} 字段对应的编解码器类型；</li>
 *   <li>{@code StructurePieceType}：结构片段在区块存档里序列化/反序列化时使用的类型。
 *       游戏存档时 {@code StructurePiece#createTag} 会强制要求该类型已注册，否则直接抛异常，
 *       所以自定义 {@code StructurePiece} 必须一并注册片段类型。</li>
 * </ul>
 * <p>新增结构时，按照本类的写法各加一个（或一组）字段，并在
 * {@link ModRegistry#register(net.neoforged.bus.api.IEventBus)} 里挂到模组事件总线。</p>
 */
public final class ModStructures {
    /**
     * 结构类型注册表，对应 {@code data/spatiotemporal_turbulence/worldgen/structure/*.json} 里的
     * {@code "type"} 字段。
     */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, SpatiotemporalTurbulence.MODID);

    /**
     * 结构片段类型注册表，对应区块存档中结构片段 NBT 里的 {@code "id"} 字段。
     */
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SpatiotemporalTurbulence.MODID);

    /**
     * 「城堡内的大型城市」结构类型，注册名 {@code spatiotemporal_turbulence:castle_city}。
     */
    public static final DeferredHolder<StructureType<?>, StructureType<CastleCityStructure>> CASTLE_CITY =
            STRUCTURE_TYPES.register("castle_city", () -> () -> CastleCityStructure.CODEC);

    /**
     * 城堡城市片段的类型，用于存档序列化；反序列化时直接从 NBT 还原片段。
     */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> CASTLE_CITY_PIECE =
            STRUCTURE_PIECE_TYPES.register("castle_city", () -> (context, tag) -> new CastleCityPiece(tag));

    /**
     * 「地下地牢」结构类型，注册名 {@code spatiotemporal_turbulence:dungeon}。
     */
    public static final DeferredHolder<StructureType<?>, StructureType<DungeonStructure>> DUNGEON =
            STRUCTURE_TYPES.register("dungeon", () -> () -> DungeonStructure.CODEC);

    /**
     * 地牢片段的类型，用于存档序列化；反序列化时直接从 NBT 还原片段。
     */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> DUNGEON_PIECE =
            STRUCTURE_PIECE_TYPES.register("dungeon", () -> (context, tag) -> new DungeonPiece(tag));

    private ModStructures() {
    }
}
