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

import java.util.ArrayList;
import java.util.List;

import com.db.spatiotemporal_turbulence.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * 「城堡内的大型城市」的结构片段：96x96 的整座城市完全由代码放置方块生成（不使用 NBT 模板）。
 * <p>布局（局部坐标，原点为片段包围盒的西北角）：</p>
 * <ul>
 *   <li>先把 96x96 范围按中心地表高度整平：基准面以上清空、基准面铺土、向下填实；</li>
 *   <li>外围 2 格厚、9 格高的石砖城墙，顶部带城垛；</li>
 *   <li>四角 9x9 塔楼（高出城墙 4 格），南侧 5 格宽城门，城门两侧各一座 7x7 塔楼；</li>
 *   <li>城内按 16 格间距铺碎石/粗土十字街道，把内部分成若干街区，每个街区随机放 1~3 栋房屋；</li>
 *   <li>城市中心是 15x15、墙高 12 格的城堡主楼（keep），四角带小塔，大门朝南。</li>
 * </ul>
 * <p>所有随机都来自构造时从 {@link WorldgenRandom} 抽出的种子 {@code layoutSeed}，
 * 每次 {@code postProcess} 都重新创建同种子的 {@link RandomSource}，因此：</p>
 * <ul>
 *   <li>同一存档、同一位置生成的城市完全一致（可复现）；</li>
 *   <li>片段从存档 NBT 反序列化后再次放置也不会变形（布局种子写在 NBT 里）。</li>
 * </ul>
 * <p>所有方块放置都通过 {@link #placeBlock}/{@link #getBlock} 等父类方法完成，
 * 它们会用传入的 {@code chunkBB} 裁剪，保证跨区块时只写当前区块、绝不越界。</p>
 */
public class CastleCityPiece extends StructurePiece {
    /** 城市边长（格）。 */
    public static final int SIZE = 96;
    /** 包围盒在基准面以上的高度（格）。实际建筑最高点远低于此值，留出余量给地形适应。 */
    public static final int MAX_BUILD_HEIGHT = 24;

    /** 城墙厚度（格）。 */
    private static final int WALL_THICKNESS = 2;
    /** 城墙高度（格）。 */
    private static final int WALL_HEIGHT = 9;
    /** 四角塔楼边长（格）。 */
    private static final int CORNER_TOWER_SIZE = 9;
    /** 四角塔楼平台高出城墙顶部的高度（格）。 */
    private static final int CORNER_TOWER_EXTRA = 4;
    /** 城门宽度（格）。 */
    private static final int GATE_WIDTH = 5;
    /** 城门塔楼边长（格）。 */
    private static final int GATE_TOWER_SIZE = 7;
    /** 城门塔楼平台高出城墙顶部的高度（格）。 */
    private static final int GATE_TOWER_EXTRA = 4;
    /** 城门洞高度（格）。 */
    private static final int GATE_HEIGHT = 5;
    /** 城堡主楼边长（格）。 */
    private static final int KEEP_SIZE = 15;
    /** 城堡主楼墙高（格）。 */
    private static final int KEEP_HEIGHT = 12;
    /** 主楼四角小塔边长（格）。 */
    private static final int KEEP_TOWER_SIZE = 3;
    /** 主楼四角小塔高出主楼墙顶的高度（格）。 */
    private static final int KEEP_TOWER_EXTRA = 4;
    /** 主楼大门宽度（格）。 */
    private static final int KEEP_DOOR_WIDTH = 3;
    /** 主楼大门高度（格）。 */
    private static final int KEEP_DOOR_HEIGHT = 4;

    /** 街道网格线（格），街区间距 16 格。 */
    private static final int[] STREET_LINES = {16, 32, 48, 64, 80};
    /** 普通街道宽度（格）。 */
    private static final int STREET_WIDTH = 2;
    /** 正中那条主干道所在的网格线；它比普通街道宽，正好对齐城门与主楼大门。 */
    private static final int MAIN_AVENUE_LINE = 48;
    /** 主干道宽度（格）。 */
    private static final int MAIN_AVENUE_WIDTH = 5;

    /** 城内可用范围（避开城墙内侧）。 */
    private static final int INTERIOR_MIN = WALL_THICKNESS;
    private static final int INTERIOR_MAX = SIZE - WALL_THICKNESS - 1;
    /** 基准面以上需要清空的高度（用于削平山丘）。 */
    private static final int CLEAR_HEIGHT = 40;
    /** 基准面以下向下填实的最大深度（格）。 */
    private static final int FILL_DEPTH = 24;

    /** 每个街区最多尝试放置的房屋数量。 */
    private static final int MAX_HOUSES_PER_BLOCK = 3;
    /** 每栋房屋放置时最多尝试的随机位置次数。 */
    private static final int HOUSE_PLACE_TRIES = 16;
    /** 房屋边长范围（格）。 */
    private static final int HOUSE_MIN_SIZE = 7;
    private static final int HOUSE_MAX_SIZE = 11;
    /** 房屋石砖墙高度（格）。 */
    private static final int HOUSE_WALL_HEIGHT = 3;

    /** X 方向的街道掩码（局部坐标）。 */
    private static final boolean[] STREET_X = buildStreetMask();
    /** Z 方向的街道掩码（局部坐标）。 */
    private static final boolean[] STREET_Z = buildStreetMask();

    /** 布局随机种子：决定街道材质、房屋位置/大小/朝向等。 */
    private final long layoutSeed;

    /**
     * 正常生成流程使用的构造器。
     *
     * @param random   结构生成随机源（同一结构起点可复现）
     * @param x        结构包围盒最小 X（区块最小角）
     * @param surfaceY 中心地表高度（地表最上面那块实心方块的 Y）
     * @param z        结构包围盒最小 Z（区块最小角）
     */
    public CastleCityPiece(WorldgenRandom random, int x, int surfaceY, int z) {
        super(ModStructures.CASTLE_CITY_PIECE.get(), 0,
                new BoundingBox(x, surfaceY, z, x + SIZE - 1, surfaceY + MAX_BUILD_HEIGHT, z + SIZE - 1));
        // 朝向为空：父类按世界绝对坐标放置方块，不做镜像/旋转。
        this.setOrientation(null);
        this.layoutSeed = random.nextLong();
    }

    /**
     * 从区块存档 NBT 反序列化时使用的构造器。
     *
     * @param tag 结构片段 NBT
     */
    public CastleCityPiece(CompoundTag tag) {
        super(ModStructures.CASTLE_CITY_PIECE.get(), tag);
        this.setOrientation(null);
        this.layoutSeed = tag.getLongOr("LayoutSeed", 0L);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // 布局种子必须写入存档：重新载入后要能重建出完全一样的城市。
        tag.putLong("LayoutSeed", this.layoutSeed);
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BlockPos referencePos) {
        // 每个区块都从同一个种子重新开始，保证随机序列与区块无关。
        RandomSource layoutRandom = RandomSource.create(this.layoutSeed);

        // 1. 整平 96x96 范围
        this.flatten(level, chunkBB);
        // 2. 铺十字街道
        this.paveStreets(level, chunkBB, layoutRandom);
        // 3. 外围城墙（含城垛）
        this.buildWalls(level, chunkBB, layoutRandom);
        // 4. 四角塔楼
        this.buildCornerTowers(level, chunkBB, layoutRandom);
        // 5. 南侧城门与城门塔楼
        this.buildGate(level, chunkBB, layoutRandom);
        // 6. 中心广场
        this.pavePlaza(level, chunkBB, layoutRandom);
        // 7. 城堡主楼
        this.buildKeep(level, chunkBB, layoutRandom);
        // 8. 各街区房屋
        for (HousePlan house : this.planHouses(layoutRandom)) {
            this.buildHouse(level, chunkBB, house, layoutRandom);
        }
    }

    // ==================== 坐标与放置工具 ====================

    /** 包围盒最小 X（局部坐标 0 对应的世界 X）。 */
    private int originX() {
        return this.boundingBox.minX();
    }

    /** 包围盒最小 Z（局部坐标 0 对应的世界 Z）。 */
    private int originZ() {
        return this.boundingBox.minZ();
    }

    /** 基准面：地表最上面那块实心方块的 Y。 */
    private int floorY() {
        return this.boundingBox.minY();
    }

    /** 城市地面：基准面之上第一层空气，建筑与城墙都站在这一层。 */
    private int groundY() {
        return this.boundingBox.minY() + 1;
    }

    /** 局部坐标放置单个方块（自动换算到世界坐标，并用 chunkBB 裁剪）。 */
    private void set(WorldGenLevel level, BoundingBox chunkBB, int lx, int y, int lz, BlockState state) {
        this.placeBlock(level, state, this.originX() + lx, y, this.originZ() + lz, chunkBB);
    }

    /** 局部坐标填充长方体（含边界）。 */
    private void fillBox(WorldGenLevel level, BoundingBox chunkBB, int lx0, int y0, int lz0, int lx1, int y1, int lz1,
            BlockState state) {
        for (int y = y0; y <= y1; y++) {
            for (int lx = lx0; lx <= lx1; lx++) {
                for (int lz = lz0; lz <= lz1; lz++) {
                    this.set(level, chunkBB, lx, y, lz, state);
                }
            }
        }
    }

    /** 局部坐标清空长方体。 */
    private void clearBox(WorldGenLevel level, BoundingBox chunkBB, int lx0, int y0, int lz0, int lx1, int y1,
            int lz1) {
        this.fillBox(level, chunkBB, lx0, y0, lz0, lx1, y1, lz1, Blocks.AIR.defaultBlockState());
    }

    /** 带随机风化的石砖（大部分石砖，少量裂纹/苔石砖）。 */
    private static BlockState weatheredBrick(RandomSource random) {
        int roll = random.nextInt(16);
        if (roll == 0) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (roll == 1) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    // ==================== 街道网格 ====================

    /** 某条街道网格线的起点（局部坐标）。 */
    private static int streetMin(int line) {
        int width = line == MAIN_AVENUE_LINE ? MAIN_AVENUE_WIDTH : STREET_WIDTH;
        return line - width / 2;
    }

    /** 某条街道网格线的终点（局部坐标）。 */
    private static int streetMax(int line) {
        int width = line == MAIN_AVENUE_LINE ? MAIN_AVENUE_WIDTH : STREET_WIDTH;
        return streetMin(line) + width - 1;
    }

    private static boolean[] buildStreetMask() {
        boolean[] mask = new boolean[SIZE];
        for (int line : STREET_LINES) {
            for (int i = streetMin(line); i <= streetMax(line); i++) {
                if (i >= 0 && i < SIZE) {
                    mask[i] = true;
                }
            }
        }
        return mask;
    }

    /** 把 [INTERIOR_MIN, INTERIOR_MAX] 按街道切成若干街区区间。 */
    private static List<int[]> blockIntervals() {
        List<int[]> intervals = new ArrayList<>();
        int start = INTERIOR_MIN;
        for (int line : STREET_LINES) {
            int intervalEnd = streetMin(line) - 1;
            if (start <= intervalEnd) {
                intervals.add(new int[] {start, intervalEnd});
            }
            start = streetMax(line) + 1;
        }
        if (start <= INTERIOR_MAX) {
            intervals.add(new int[] {start, INTERIOR_MAX});
        }
        return intervals;
    }

    // ==================== 1. 整平 ====================

    /**
     * 按基准面整平 96x96 范围：基准面以上清空、基准面铺泥土、基准面向下用泥土/石头填实。
     * <p>循环范围会先与 {@code chunkBB} 求交，只处理当前区块里的格子，避免做 36 倍无用功。</p>
     */
    private void flatten(WorldGenLevel level, BoundingBox chunkBB) {
        int lx0 = Math.max(0, chunkBB.minX() - this.originX());
        int lx1 = Math.min(SIZE - 1, chunkBB.maxX() - this.originX());
        int lz0 = Math.max(0, chunkBB.minZ() - this.originZ());
        int lz1 = Math.min(SIZE - 1, chunkBB.maxZ() - this.originZ());
        if (lx0 > lx1 || lz0 > lz1) {
            return;
        }

        int floor = this.floorY();
        int ground = this.groundY();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        for (int lx = lx0; lx <= lx1; lx++) {
            for (int lz = lz0; lz <= lz1; lz++) {
                int wx = this.originX() + lx;
                int wz = this.originZ() + lz;
                // 基准面以上清空（连山顶也削平）
                for (int y = ground; y <= floor + CLEAR_HEIGHT; y++) {
                    if (!this.getBlock(level, wx, y, wz, chunkBB).isAir()) {
                        this.placeBlock(level, air, wx, y, wz, chunkBB);
                    }
                }
                // 基准面铺土
                this.placeBlock(level, dirt, wx, floor, wz, chunkBB);
                // 向下填实：上几层泥土，更深处石头，直到碰到实心方块
                for (int y = floor - 1; y >= floor - FILL_DEPTH; y--) {
                    if (!this.isReplaceableByStructures(this.getBlock(level, wx, y, wz, chunkBB))) {
                        break;
                    }
                    this.placeBlock(level, y >= floor - 5 ? dirt : stone, wx, y, wz, chunkBB);
                }
            }
        }
    }

    // ==================== 2. 街道 ====================

    /** 铺十字街道：碎石/粗土路面。 */
    private void paveStreets(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int floor = this.floorY();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        for (int lx = INTERIOR_MIN; lx <= INTERIOR_MAX; lx++) {
            for (int lz = INTERIOR_MIN; lz <= INTERIOR_MAX; lz++) {
                if (!STREET_X[lx] && !STREET_Z[lz]) {
                    continue;
                }
                BlockState state = random.nextInt(3) == 0 ? coarseDirt : gravel;
                this.set(level, chunkBB, lx, floor, lz, state);
            }
        }
    }

    /** 主楼周围铺一片石砖广场。 */
    private void pavePlaza(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int floor = this.floorY();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        // 广场正好覆盖城中心被跳过的 2x2 个街区
        for (int lx = 33; lx <= 62; lx++) {
            for (int lz = 33; lz <= 62; lz++) {
                int roll = random.nextInt(4);
                BlockState state = roll == 0 ? bricks : (roll == 1 ? cobble : gravel);
                this.set(level, chunkBB, lx, floor, lz, state);
            }
        }
    }

    // ==================== 3. 城墙 ====================

    /** 外围城墙：2 格厚、9 格高，顶部隔一格放城垛。 */
    private void buildWalls(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int ground = this.groundY();
        int crenelY = ground + WALL_HEIGHT;
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();

        for (int y = ground; y < ground + WALL_HEIGHT; y++) {
            // 南北两面墙（各 2 格厚）
            for (int lx = 0; lx < SIZE; lx++) {
                this.set(level, chunkBB, lx, y, 0, weatheredBrick(random));
                this.set(level, chunkBB, lx, y, 1, weatheredBrick(random));
                this.set(level, chunkBB, lx, y, SIZE - 1, weatheredBrick(random));
                this.set(level, chunkBB, lx, y, SIZE - 2, weatheredBrick(random));
            }
            // 东西两面墙（各 2 格厚，角上已由南北墙覆盖，跳过重复部分）
            for (int lz = 2; lz <= SIZE - 3; lz++) {
                this.set(level, chunkBB, 0, y, lz, weatheredBrick(random));
                this.set(level, chunkBB, 1, y, lz, weatheredBrick(random));
                this.set(level, chunkBB, SIZE - 1, y, lz, weatheredBrick(random));
                this.set(level, chunkBB, SIZE - 2, y, lz, weatheredBrick(random));
            }
        }

        // 城垛：外沿每隔一格放一块
        for (int i = 0; i < SIZE; i++) {
            if ((i & 1) == 0) {
                this.set(level, chunkBB, i, crenelY, 0, bricks);
                this.set(level, chunkBB, i, crenelY, SIZE - 1, bricks);
            }
            if (i >= 2 && i <= SIZE - 3 && (i & 1) == 0) {
                this.set(level, chunkBB, 0, crenelY, i, bricks);
                this.set(level, chunkBB, SIZE - 1, crenelY, i, bricks);
            }
        }
    }

    // ==================== 4. 四角塔楼 ====================

    /** 四角各一座 9x9 塔楼，平台高出城墙 4 格。 */
    private void buildCornerTowers(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int platformY = this.groundY() + WALL_HEIGHT + CORNER_TOWER_EXTRA;
        int[][] corners = {
                {0, 0},
                {SIZE - CORNER_TOWER_SIZE, 0},
                {0, SIZE - CORNER_TOWER_SIZE},
                {SIZE - CORNER_TOWER_SIZE, SIZE - CORNER_TOWER_SIZE}
        };
        for (int[] corner : corners) {
            this.buildTower(level, chunkBB, random, corner[0], corner[1], CORNER_TOWER_SIZE, platformY);
        }
    }

    /**
     * 通用的空心塔楼：铺石砖地板、2 格厚塔身、封顶平台、顶部城垛。
     *
     * @param lx0       塔楼最小 X（局部坐标）
     * @param lz0       塔楼最小 Z（局部坐标）
     * @param size      塔楼边长
     * @param platformY 塔顶平台所在的 Y
     */
    private void buildTower(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int lx0, int lz0, int size,
            int platformY) {
        int ground = this.groundY();
        int floor = this.floorY();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        int thickness = size >= 7 ? 2 : 1;

        // 塔内地板
        this.fillBox(level, chunkBB, lx0, floor, lz0, lx0 + size - 1, floor, lz0 + size - 1, bricks);
        // 塔身
        for (int y = ground; y < platformY; y++) {
            for (int lx = 0; lx < size; lx++) {
                for (int lz = 0; lz < size; lz++) {
                    boolean border = lx < thickness || lx >= size - thickness || lz < thickness
                            || lz >= size - thickness;
                    if (border) {
                        this.set(level, chunkBB, lx0 + lx, y, lz0 + lz, weatheredBrick(random));
                    }
                }
            }
        }
        // 塔顶平台
        this.fillBox(level, chunkBB, lx0, platformY, lz0, lx0 + size - 1, platformY, lz0 + size - 1, bricks);
        // 塔顶城垛
        this.crenellate(level, chunkBB, lx0, lz0, size, platformY + 1, bricks);
    }

    /** 给一个方形区域的外沿隔一格放一块城垛。 */
    private void crenellate(WorldGenLevel level, BoundingBox chunkBB, int lx0, int lz0, int size, int y,
            BlockState state) {
        for (int i = 0; i < size; i++) {
            if ((i & 1) != 0) {
                continue;
            }
            this.set(level, chunkBB, lx0 + i, y, lz0, state);
            this.set(level, chunkBB, lx0 + i, y, lz0 + size - 1, state);
            this.set(level, chunkBB, lx0, y, lz0 + i, state);
            this.set(level, chunkBB, lx0 + size - 1, y, lz0 + i, state);
        }
    }

    // ==================== 5. 城门 ====================

    /** 南侧城门：5 格宽、5 格高的门洞，门洞两侧各一座 7x7 塔楼。 */
    private void buildGate(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int ground = this.groundY();
        int gateMinX = this.keepX0() + KEEP_SIZE / 2 - GATE_WIDTH / 2;
        int gateMaxX = gateMinX + GATE_WIDTH - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();

        // 在南北两面城墙上开出门洞（5 格宽 x 5 格高）
        for (int y = ground; y < ground + GATE_HEIGHT; y++) {
            for (int lx = gateMinX; lx <= gateMaxX; lx++) {
                this.set(level, chunkBB, lx, y, SIZE - 1, air);
                this.set(level, chunkBB, lx, y, SIZE - 2, air);
            }
        }
        // 门洞上方用錾制石砖做一点门楣装饰
        for (int lx = gateMinX; lx <= gateMaxX; lx++) {
            this.set(level, chunkBB, lx, ground + GATE_HEIGHT, SIZE - 1, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            this.set(level, chunkBB, lx, ground + GATE_HEIGHT, SIZE - 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        // 门洞地面铺石砖
        for (int lz = SIZE - 2; lz < SIZE; lz++) {
            for (int lx = gateMinX; lx <= gateMaxX; lx++) {
                this.set(level, chunkBB, lx, this.floorY(), lz, bricks);
            }
        }

        // 门洞两侧的塔楼
        int platformY = ground + WALL_HEIGHT + GATE_TOWER_EXTRA;
        int leftTowerX = gateMinX - GATE_TOWER_SIZE;
        int rightTowerX = gateMaxX + 1;
        int towerZ = SIZE - 2 - GATE_TOWER_SIZE;
        this.buildTower(level, chunkBB, random, leftTowerX, towerZ, GATE_TOWER_SIZE, platformY);
        this.buildTower(level, chunkBB, random, rightTowerX, towerZ, GATE_TOWER_SIZE, platformY);
    }

    // ==================== 6. 城堡主楼 ====================

    /** 主楼最小 X（局部坐标）：城市正中心。 */
    private int keepX0() {
        return SIZE / 2 - KEEP_SIZE / 2;
    }

    /** 主楼最小 Z（局部坐标）：城市正中心。 */
    private int keepZ0() {
        return SIZE / 2 - KEEP_SIZE / 2;
    }

    /** 中心城堡主楼：15x15、墙高 12 格，四角小塔，大门朝南。 */
    private void buildKeep(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
        int ground = this.groundY();
        int floor = this.floorY();
        int x0 = this.keepX0();
        int z0 = this.keepZ0();
        int x1 = x0 + KEEP_SIZE - 1;
        int z1 = z0 + KEEP_SIZE - 1;
        int wallTop = ground + KEEP_HEIGHT - 1;
        int platformY = ground + KEEP_HEIGHT;
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();

        // 地板
        this.fillBox(level, chunkBB, x0, floor, z0, x1, floor, z1, bricks);
        // 把主楼内部清空（整平阶段已经清过，这里再保险一次）
        this.clearBox(level, chunkBB, x0 + 2, ground, z0 + 2, x1 - 2, wallTop, z1 - 2);

        // 墙体：2 格厚，高 12 格
        for (int y = ground; y <= wallTop; y++) {
            for (int lx = 0; lx < KEEP_SIZE; lx++) {
                for (int lz = 0; lz < KEEP_SIZE; lz++) {
                    boolean wall = lx < 2 || lx >= KEEP_SIZE - 2 || lz < 2 || lz >= KEEP_SIZE - 2;
                    if (wall) {
                        this.set(level, chunkBB, x0 + lx, y, z0 + lz, weatheredBrick(random));
                    }
                }
            }
        }

        // 南侧大门：3 格宽、4 格高，穿透 2 格厚的南墙
        int doorMinX = x0 + KEEP_SIZE / 2 - KEEP_DOOR_WIDTH / 2;
        for (int y = ground; y < ground + KEEP_DOOR_HEIGHT; y++) {
            for (int lx = doorMinX; lx < doorMinX + KEEP_DOOR_WIDTH; lx++) {
                this.set(level, chunkBB, lx, y, z1, Blocks.AIR.defaultBlockState());
                this.set(level, chunkBB, lx, y, z1 - 1, Blocks.AIR.defaultBlockState());
            }
        }

        // 屋顶平台 + 城垛
        this.fillBox(level, chunkBB, x0, platformY, z0, x1, platformY, z1, bricks);
        this.crenellate(level, chunkBB, x0, z0, KEEP_SIZE, platformY + 1, bricks);

        // 四角小塔：实心 3x3，比主楼墙顶高出 4 格，塔顶再放四角城垛
        int towerTop = ground + KEEP_HEIGHT + KEEP_TOWER_EXTRA - 1;
        int[][] corners = {
                {x0, z0},
                {x1 - KEEP_TOWER_SIZE + 1, z0},
                {x0, z1 - KEEP_TOWER_SIZE + 1},
                {x1 - KEEP_TOWER_SIZE + 1, z1 - KEEP_TOWER_SIZE + 1}
        };
        for (int[] corner : corners) {
            this.fillBox(level, chunkBB, corner[0], ground, corner[1],
                    corner[0] + KEEP_TOWER_SIZE - 1, towerTop, corner[1] + KEEP_TOWER_SIZE - 1, bricks);
            this.crenellate(level, chunkBB, corner[0], corner[1], KEEP_TOWER_SIZE, towerTop + 1, bricks);
        }

        // 主楼中央放一支火把照明（地板是实心石砖，火把可以正常站立）
        this.set(level, chunkBB, x0 + KEEP_SIZE / 2, ground, z0 + KEEP_SIZE / 2, Blocks.TORCH.defaultBlockState());
    }

    // ==================== 7. 房屋 ====================

    /** 规划全城房屋；所有随机抽取顺序固定，保证每个区块重新执行时结果一致。 */
    private List<HousePlan> planHouses(RandomSource random) {
        List<int[]> xIntervals = blockIntervals();
        List<int[]> zIntervals = blockIntervals();
        List<Rect> reserved = reservedRects();
        List<HousePlan> houses = new ArrayList<>();

        for (int[] xs : xIntervals) {
            for (int[] zs : zIntervals) {
                int bx0 = xs[0];
                int bx1 = xs[1];
                int bz0 = zs[0];
                int bz1 = zs[1];
                // 与保留区（主楼、塔楼、城门等）冲突的街区留空
                if (intersectsAny(reserved, bx0, bz0, bx1, bz1)) {
                    continue;
                }

                int want = 1 + random.nextInt(MAX_HOUSES_PER_BLOCK);
                for (int index = 0; index < want; index++) {
                    if (!this.tryPlanHouse(random, bx0, bz0, bx1, bz1, reserved, houses)) {
                        // 空间不够，本街区不再放更多房屋
                        break;
                    }
                }
            }
        }
        return houses;
    }

    /** 在一个街区内随机尝试放下一栋房屋。 */
    private boolean tryPlanHouse(RandomSource random, int bx0, int bz0, int bx1, int bz1, List<Rect> reserved,
            List<HousePlan> houses) {
        int blockWidth = bx1 - bx0 + 1;
        int blockDepth = bz1 - bz0 + 1;
        for (int attempt = 0; attempt < HOUSE_PLACE_TRIES; attempt++) {
            int size = HOUSE_MIN_SIZE + random.nextInt(HOUSE_MAX_SIZE - HOUSE_MIN_SIZE + 1);
            int spanX = blockWidth - size + 1;
            int spanZ = blockDepth - size + 1;
            if (spanX <= 0 || spanZ <= 0) {
                return false;
            }
            int hx0 = bx0 + random.nextInt(spanX);
            int hz0 = bz0 + random.nextInt(spanZ);
            int hx1 = hx0 + size - 1;
            int hz1 = hz0 + size - 1;
            if (intersectsAny(reserved, hx0, hz0, hx1, hz1)) {
                continue;
            }
            boolean overlap = false;
            for (HousePlan other : houses) {
                if (other.intersects(hx0, hz0, hx1, hz1)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) {
                continue;
            }
            boolean darkOakRoof = random.nextBoolean();
            houses.add(new HousePlan(hx0, hz0, size, darkOakRoof));
            return true;
        }
        return false;
    }

    /** 不允许盖房子的保留区：主楼、四角塔楼、城门塔楼与城门通道（坐标已外扩，留出一点空地）。 */
    private List<Rect> reservedRects() {
        List<Rect> rects = new ArrayList<>();
        int keepX0 = this.keepX0();
        int keepZ0 = this.keepZ0();
        // 主楼外扩 2 格
        rects.add(new Rect(keepX0 - 2, keepZ0 - 2, keepX0 + KEEP_SIZE + 1, keepZ0 + KEEP_SIZE + 1));
        // 四角塔楼
        int far = SIZE - CORNER_TOWER_SIZE;
        rects.add(new Rect(0, 0, CORNER_TOWER_SIZE - 1, CORNER_TOWER_SIZE - 1));
        rects.add(new Rect(far, 0, SIZE - 1, CORNER_TOWER_SIZE - 1));
        rects.add(new Rect(0, far, CORNER_TOWER_SIZE - 1, SIZE - 1));
        rects.add(new Rect(far, far, SIZE - 1, SIZE - 1));
        // 城门塔楼与门洞通道
        int gateMinX = keepX0 + KEEP_SIZE / 2 - GATE_WIDTH / 2;
        int gateMaxX = gateMinX + GATE_WIDTH - 1;
        int gateTowerZ = SIZE - 2 - GATE_TOWER_SIZE;
        rects.add(new Rect(gateMinX - GATE_TOWER_SIZE, gateTowerZ, gateMinX - 1, SIZE - 1));
        rects.add(new Rect(gateMaxX + 1, gateTowerZ, gateMaxX + GATE_TOWER_SIZE, SIZE - 1));
        rects.add(new Rect(gateMinX - 1, gateTowerZ, gateMaxX + 1, SIZE - 1));
        return rects;
    }

    /** 程序化盖一栋房子：石砖墙 + 深色橡木/云杉木坡屋顶，门朝随机一侧，墙上开窗。 */
    private void buildHouse(WorldGenLevel level, BoundingBox chunkBB, HousePlan plan, RandomSource random) {
        int ground = this.groundY();
        int floor = this.floorY();
        int x0 = plan.x0();
        int z0 = plan.z0();
        int x1 = x0 + plan.size() - 1;
        int z1 = z0 + plan.size() - 1;
        int wallHeight = HOUSE_WALL_HEIGHT;
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 地板：石砖
        this.fillBox(level, chunkBB, x0, floor, z0, x1, floor, z1, Blocks.STONE_BRICKS.defaultBlockState());
        // 四角原木柱
        for (int y = ground; y < ground + wallHeight; y++) {
            this.set(level, chunkBB, x0, y, z0, log);
            this.set(level, chunkBB, x1, y, z0, log);
            this.set(level, chunkBB, x0, y, z1, log);
            this.set(level, chunkBB, x1, y, z1, log);
        }
        // 石砖墙
        for (int y = ground; y < ground + wallHeight; y++) {
            for (int lx = x0 + 1; lx < x1; lx++) {
                this.set(level, chunkBB, lx, y, z0, weatheredBrick(random));
                this.set(level, chunkBB, lx, y, z1, weatheredBrick(random));
            }
            for (int lz = z0 + 1; lz < z1; lz++) {
                this.set(level, chunkBB, x0, y, lz, weatheredBrick(random));
                this.set(level, chunkBB, x1, y, lz, weatheredBrick(random));
            }
        }
        // 窗户（玻璃）
        int windowY = ground + 1;
        for (int offset : new int[] {2, plan.size() - 3}) {
            if (offset <= 0 || offset >= plan.size() - 1) {
                continue;
            }
            this.set(level, chunkBB, x0 + offset, windowY, z0, glass);
            this.set(level, chunkBB, x0 + offset, windowY, z1, glass);
            this.set(level, chunkBB, x0, windowY, z0 + offset, glass);
            this.set(level, chunkBB, x1, windowY, z0 + offset, glass);
        }
        // 大门：随机朝一面，1 格宽 2 格高
        int doorSide = random.nextInt(4);
        int mid = plan.size() / 2;
        switch (doorSide) {
            case 0 -> {
                this.set(level, chunkBB, x0 + mid, ground, z0, air);
                this.set(level, chunkBB, x0 + mid, ground + 1, z0, air);
            }
            case 1 -> {
                this.set(level, chunkBB, x0 + mid, ground, z1, air);
                this.set(level, chunkBB, x0 + mid, ground + 1, z1, air);
            }
            case 2 -> {
                this.set(level, chunkBB, x0, ground, z0 + mid, air);
                this.set(level, chunkBB, x0, ground + 1, z0 + mid, air);
            }
            default -> {
                this.set(level, chunkBB, x1, ground, z0 + mid, air);
                this.set(level, chunkBB, x1, ground + 1, z0 + mid, air);
            }
        }
        // 屋内火把
        this.set(level, chunkBB, x0 + mid, ground, z0 + mid, Blocks.TORCH.defaultBlockState());
        // 坡屋顶
        this.buildRoof(level, chunkBB, plan, ground + wallHeight);
    }

    /** 简单坡屋顶：逐层内缩，每层边缘放楼梯，内部铺木板。 */
    private void buildRoof(WorldGenLevel level, BoundingBox chunkBB, HousePlan plan, int roofBaseY) {
        int x0 = plan.x0();
        int z0 = plan.z0();
        int x1 = x0 + plan.size() - 1;
        int z1 = z0 + plan.size() - 1;

        BlockState planks = plan.darkOakRoof() ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState stairsNorth = (plan.darkOakRoof() ? Blocks.DARK_OAK_STAIRS : Blocks.SPRUCE_STAIRS)
                .defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState stairsSouth = stairsNorth.setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState stairsWest = stairsNorth.setValue(StairBlock.FACING, Direction.WEST);
        BlockState stairsEast = stairsNorth.setValue(StairBlock.FACING, Direction.EAST);

        int layers = Math.min(3, Math.max(1, (plan.size() - 3) / 2));
        for (int layer = 0; layer < layers; layer++) {
            int y = roofBaseY + layer;
            int xa = x0 + layer;
            int xb = x1 - layer;
            int za = z0 + layer;
            int zb = z1 - layer;
            if (xa > xb || za > zb) {
                break;
            }
            // 先铺满木板，再把四边换成朝外倾斜的楼梯
            this.fillBox(level, chunkBB, xa, y, za, xb, y, zb, planks);
            for (int lx = xa; lx <= xb; lx++) {
                this.set(level, chunkBB, lx, y, za, stairsNorth);
                this.set(level, chunkBB, lx, y, zb, stairsSouth);
            }
            for (int lz = za; lz <= zb; lz++) {
                this.set(level, chunkBB, xa, y, lz, stairsWest);
                this.set(level, chunkBB, xb, y, lz, stairsEast);
            }
        }
    }

    // ==================== 内部数据结构 ====================

    private static boolean intersectsAny(List<Rect> rects, int x0, int z0, int x1, int z1) {
        for (Rect rect : rects) {
            if (rect.intersects(x0, z0, x1, z1)) {
                return true;
            }
        }
        return false;
    }

    /** 局部坐标的平面矩形（含边界）。 */
    private record Rect(int x0, int z0, int x1, int z1) {
        boolean intersects(int otherX0, int otherZ0, int otherX1, int otherZ1) {
            return this.x0 <= otherX1 && this.x1 >= otherX0 && this.z0 <= otherZ1 && this.z1 >= otherZ0;
        }
    }

    /** 规划好的一栋房屋。 */
    private record HousePlan(int x0, int z0, int size, boolean darkOakRoof) {
        boolean intersects(int otherX0, int otherZ0, int otherX1, int otherZ1) {
            int x1 = this.x0 + this.size - 1;
            int z1 = this.z0 + this.size - 1;
            return this.x0 <= otherX1 && x1 >= otherX0 && this.z0 <= otherZ1 && z1 >= otherZ0;
        }
    }
}
