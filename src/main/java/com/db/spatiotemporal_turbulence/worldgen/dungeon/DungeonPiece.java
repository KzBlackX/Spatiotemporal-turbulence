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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.db.spatiotemporal_turbulence.registry.ModStructures;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * 「地下地牢」的结构片段：64x64 的地下地牢完全由代码放置方块生成（不使用 NBT 模板）。
 * <p>布局（局部坐标，原点为片段包围盒的西北角）：</p>
 * <ul>
 *   <li>中央一间 15x15 的大厅；</li>
 *   <li>东、南、西、北四个方位里随机挑 2~4 个方位各放一间 9x9~15x15 的侧室，
 *       因此房间总数（含大厅）为 3~5 间；每间墙高 4~5 格；</li>
 *   <li>大厅与每间侧室之间用一条 3 格宽、3 格高的走廊相连（走廊自带侧壁与顶盖，
 *       并在两端房间的墙上掏出 3x3 的门洞）；</li>
 *   <li>房间里放 2~3 个吸血鬼刷怪笼、1~2 个战利品箱，以及少量火把照明；</li>
 *   <li>房间地板用圆石/苔石/苔石砖/石砖混合铺设，墙壁与天花板用圆石/苔石砖。</li>
 * </ul>
 * <p>所有随机都来自构造时从 {@link WorldgenRandom} 抽出的种子 {@code layoutSeed}，
 * 每次 {@code postProcess} 都重新创建同种子的 {@link RandomSource} 并一次性规划出整套布局，
 * 因此：</p>
 * <ul>
 *   <li>同一存档、同一位置生成的地牢完全一致（可复现）；</li>
 *   <li>片段从存档 NBT 反序列化后再次放置也不会变形（布局种子写在 NBT 里）；</li>
 *   <li>地牢横跨多个区块时，每个区块独立重算出的布局完全相同，不会出现接缝错位。</li>
 * </ul>
 * <p>所有方块放置都通过 {@link #placeBlock}/{@link #getBlock} 等父类方法完成，
 * 它们会用传入的 {@code chunkBB} 裁剪，保证跨区块时只写当前区块、绝不越界。</p>
 */
public class DungeonPiece extends StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 地牢边长（格）：整个地牢落在一个 64x64 的地下空间内。 */
    public static final int SIZE = 64;
    /** 包围盒在基准面以上的高度（格）。最高只用到基准面 + 6（墙高 5 + 天花板 1），留出余量。 */
    public static final int PIECE_HEIGHT = 8;

    /** 房间距 64x64 边界的最小距离（格）。 */
    private static final int MARGIN = 4;
    /** 中央大厅边长（格）。 */
    private static final int HALL_SIZE = 15;
    /** 地牢中心坐标（局部坐标）。 */
    private static final int CENTER = SIZE / 2;

    /** 侧室边长范围（格）。 */
    private static final int ROOM_MIN_SIZE = 9;
    private static final int ROOM_MAX_SIZE = 15;
    /** 房间墙高范围（格）。 */
    private static final int WALL_MIN_HEIGHT = 4;
    private static final int WALL_MAX_HEIGHT = 5;
    /** 走廊宽度 / 高度（格）。 */
    private static final int CORRIDOR_WIDTH = 3;
    private static final int CORRIDOR_HEIGHT = 3;
    /** 房间总数范围（含中央大厅）。 */
    private static final int MIN_ROOMS = 3;
    private static final int MAX_ROOMS = 5;
    /** 刷怪笼 / 箱子数量范围。 */
    private static final int MIN_SPAWNERS = 2;
    private static final int MAX_SPAWNERS = 3;
    private static final int MIN_CHESTS = 1;
    private static final int MAX_CHESTS = 2;

    /** 侧室方位：北（-Z）、南（+Z）、西（-X）、东（+X）。 */
    private static final int SIDE_NORTH = 0;
    private static final int SIDE_SOUTH = 1;
    private static final int SIDE_WEST = 2;
    private static final int SIDE_EAST = 3;

    /** 刷怪笼里写死的实体 id：吸血鬼由实体模块注册，地牢这边只按字符串 id 查注册表。 */
    private static final String VAMPIRE_ENTITY_ID = "spatiotemporal_turbulence:vampire";
    /** 上面那个字符串 id 解析出的 {@link Identifier}。 */
    private static final Identifier VAMPIRE_ID = Identifier.parse(VAMPIRE_ENTITY_ID);

    /** 布局随机种子：决定侧室方位/大小/墙高、刷怪笼与箱子位置、地板材质等。 */
    private final long layoutSeed;

    /**
     * 正常生成流程使用的构造器。
     *
     * @param random 结构生成随机源（同一结构起点可复现）
     * @param x      结构包围盒最小 X（区块最小角）
     * @param baseY  地下基准面（地牢地板所在的 Y）
     * @param z      结构包围盒最小 Z（区块最小角）
     */
    public DungeonPiece(WorldgenRandom random, int x, int baseY, int z) {
        super(ModStructures.DUNGEON_PIECE.get(), 0,
                new BoundingBox(x, baseY, z, x + SIZE - 1, baseY + PIECE_HEIGHT - 1, z + SIZE - 1));
        // 朝向为空：父类按世界绝对坐标放置方块，不做镜像/旋转。
        this.setOrientation(null);
        this.layoutSeed = random.nextLong();
    }

    /**
     * 从区块存档 NBT 反序列化时使用的构造器。
     *
     * @param tag 结构片段 NBT
     */
    public DungeonPiece(CompoundTag tag) {
        super(ModStructures.DUNGEON_PIECE.get(), tag);
        this.setOrientation(null);
        this.layoutSeed = tag.getLongOr("LayoutSeed", 0L);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // 布局种子必须写入存档：重新载入后要能重建出完全一样的地牢。
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
        // 每个区块都从同一个种子重新开始，保证规划出的布局与区块无关。
        RandomSource layoutRandom = RandomSource.create(this.layoutSeed);
        Layout layout = this.planLayout(layoutRandom);

        // 1. 盖房间（地板、四壁、天花板、掏空内部）
        for (Room room : layout.rooms()) {
            this.buildRoom(level, chunkBB, room, layoutRandom);
        }
        // 2. 挖走廊（地板、侧壁、顶盖、掏空 3 格高，顺带在房间墙上开出门洞）
        for (Corridor corridor : layout.corridors()) {
            this.buildCorridor(level, chunkBB, corridor, layoutRandom);
        }
        // 3. 火把照明
        this.placeTorches(level, chunkBB, layout);
        // 4. 吸血鬼刷怪笼
        this.placeSpawners(level, chunkBB, layout.spawnerPositions(), random);
        // 5. 战利品箱
        this.placeChests(level, chunkBB, layout.chestPositions(), random);
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

    /** 地牢地板所在的 Y（即基准面）。 */
    private int floorY() {
        return this.boundingBox.minY();
    }

    /** 地板之上第一层空气的 Y：房间地面、刷怪笼/箱子的落位高度。 */
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

    /** 局部坐标清空长方体（用洞穴空气，避免地下被水/流体回填后变成水潭）。 */
    private void clearBox(WorldGenLevel level, BoundingBox chunkBB, int lx0, int y0, int lz0, int lx1, int y1,
            int lz1) {
        this.fillBox(level, chunkBB, lx0, y0, lz0, lx1, y1, lz1, CAVE_AIR);
    }

    /** 房间地板：碎石 + 苔石 + 圆石 + 苔石砖混合。 */
    private static BlockState floorBlock(RandomSource random) {
        int roll = random.nextInt(20);
        if (roll < 5) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (roll < 11) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (roll < 17) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    }

    /** 墙壁：圆石为主，混一些苔石/石砖。 */
    private static BlockState wallBlock(RandomSource random) {
        int roll = random.nextInt(20);
        if (roll < 12) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (roll < 17) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (roll < 19) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    }

    /** 天花板：圆石为主，混少量苔石/石砖。 */
    private static BlockState ceilingBlock(RandomSource random) {
        int roll = random.nextInt(8);
        if (roll == 0) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (roll == 1) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    // ==================== 1. 布局规划 ====================

    /**
     * 一次性规划整套布局；所有随机抽取顺序固定，因此每个区块重新执行时结果完全一致。
     *
     * @param random 由 {@code layoutSeed} 重建的随机源
     * @return 房间、走廊以及刷怪笼/箱子位置
     */
    private Layout planLayout(RandomSource random) {
        int ground = this.groundY();

        // 中央大厅：位置固定在地牢正中心，只有墙高随机。
        Room hall = new Room(CENTER - HALL_SIZE / 2, CENTER - HALL_SIZE / 2, HALL_SIZE, randomWallHeight(random));

        // 侧室数量 2~4 间 → 房间总数 3~5 间。
        int sideCount = (MIN_ROOMS - 1) + random.nextInt(MAX_ROOMS - MIN_ROOMS + 1);

        // 把四个方位洗牌后取前 sideCount 个，保证每次地牢朝向不同。
        int[] sides = {SIDE_NORTH, SIDE_SOUTH, SIDE_WEST, SIDE_EAST};
        for (int i = sides.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = sides[i];
            sides[i] = sides[j];
            sides[j] = tmp;
        }

        List<Room> rooms = new ArrayList<>();
        List<Corridor> corridors = new ArrayList<>();
        rooms.add(hall);
        for (int i = 0; i < sideCount; i++) {
            int size = ROOM_MIN_SIZE + random.nextInt(ROOM_MAX_SIZE - ROOM_MIN_SIZE + 1);
            Room room = createSideRoom(sides[i], size, randomWallHeight(random));
            rooms.add(room);
            corridors.add(createCorridor(sides[i], room, hall));
        }

        // 刷怪笼 2~3 个：优先放在大厅和靠前的侧室正中央。
        int spawnerCount = MIN_SPAWNERS + random.nextInt(MAX_SPAWNERS - MIN_SPAWNERS + 1);
        List<BlockPos> spawnerPositions = new ArrayList<>();
        for (int i = 0; i < spawnerCount && i < rooms.size(); i++) {
            Room room = rooms.get(i);
            spawnerPositions.add(new BlockPos(room.centerX(), ground, room.centerZ()));
        }

        // 箱子 1~2 个：放在最后几间房间的内角（紧贴两面墙，方便用父类 createChest 自动定向）。
        int chestCount = MIN_CHESTS + random.nextInt(MAX_CHESTS - MIN_CHESTS + 1);
        List<BlockPos> chestPositions = new ArrayList<>();
        for (int i = 0; i < chestCount; i++) {
            Room room = rooms.get(rooms.size() - 1 - i);
            boolean oppositeCorner = (i & 1) == 1;
            int lx = oppositeCorner ? room.x1() - 1 : room.x0() + 1;
            int lz = oppositeCorner ? room.z1() - 1 : room.z0() + 1;
            chestPositions.add(new BlockPos(lx, ground, lz));
        }

        return new Layout(rooms, corridors, spawnerPositions, chestPositions);
    }

    /** 随机墙高（4~5 格）。 */
    private static int randomWallHeight(RandomSource random) {
        return WALL_MIN_HEIGHT + random.nextInt(WALL_MAX_HEIGHT - WALL_MIN_HEIGHT + 1);
    }

    /** 按方位造一间侧室：贴着 64x64 边界留 {@link #MARGIN} 格，另一轴与地牢中心对齐。 */
    private static Room createSideRoom(int side, int size, int wallHeight) {
        return switch (side) {
            // 北：贴着 z = MARGIN 一侧；x 轴居中。
            case SIDE_NORTH -> new Room(CENTER - size / 2, MARGIN, size, wallHeight);
            // 南：贴着 z = SIZE - MARGIN - 1 一侧；x 轴居中。
            case SIDE_SOUTH -> new Room(CENTER - size / 2, SIZE - MARGIN - size, size, wallHeight);
            // 西：贴着 x = MARGIN 一侧；z 轴居中。
            case SIDE_WEST -> new Room(MARGIN, CENTER - size / 2, size, wallHeight);
            // 东：贴着 x = SIZE - MARGIN - 1 一侧；z 轴居中。
            default -> new Room(SIZE - MARGIN - size, CENTER - size / 2, size, wallHeight);
        };
    }

    /**
     * 造一条从大厅连到侧室的 3 格宽走廊。
     * <p>走廊矩形包含两端房间墙体所在的格子，因此挖空时正好在两侧墙上掏出 3x3 门洞。</p>
     */
    private static Corridor createCorridor(int side, Room room, Room hall) {
        // 走廊在垂直于自身走向的那一轴上固定占 3 格，并与地牢中心线对齐。
        int halfWidth = CORRIDOR_WIDTH / 2;
        int crossMin = CENTER - halfWidth;
        int crossMax = crossMin + CORRIDOR_WIDTH - 1;
        return switch (side) {
            // 北 / 南：走廊沿 Z 轴延伸，X 方向占 crossMin..crossMax。
            case SIDE_NORTH -> new Corridor(crossMin, room.z1(), crossMax, hall.z0());
            case SIDE_SOUTH -> new Corridor(crossMin, hall.z1(), crossMax, room.z0());
            // 西 / 东：走廊沿 X 轴延伸，Z 方向占 crossMin..crossMax。
            case SIDE_WEST -> new Corridor(room.x1(), crossMin, hall.x0(), crossMax);
            default -> new Corridor(hall.x1(), crossMin, room.x0(), crossMax);
        };
    }

    // ==================== 2. 房间 ====================

    /** 盖一间房间：先掏空内部，再铺地板、砌四壁、封天花板。 */
    private void buildRoom(WorldGenLevel level, BoundingBox chunkBB, Room room, RandomSource random) {
        int floor = this.floorY();
        int wallTop = floor + room.wallHeight();
        int ceilingY = wallTop + 1;

        // 掏空房间内部（墙壁内侧的所有方块）
        this.clearBox(level, chunkBB, room.x0() + 1, floor + 1, room.z0() + 1,
                room.x1() - 1, wallTop, room.z1() - 1);

        // 地板：铺满整个房间底面（含墙脚）
        for (int lx = room.x0(); lx <= room.x1(); lx++) {
            for (int lz = room.z0(); lz <= room.z1(); lz++) {
                this.set(level, chunkBB, lx, floor, lz, floorBlock(random));
            }
        }

        // 四壁：一圈 1 格厚的墙
        for (int y = floor; y <= wallTop; y++) {
            for (int lx = room.x0(); lx <= room.x1(); lx++) {
                this.set(level, chunkBB, lx, y, room.z0(), wallBlock(random));
                this.set(level, chunkBB, lx, y, room.z1(), wallBlock(random));
            }
            for (int lz = room.z0() + 1; lz < room.z1(); lz++) {
                this.set(level, chunkBB, room.x0(), y, lz, wallBlock(random));
                this.set(level, chunkBB, room.x1(), y, lz, wallBlock(random));
            }
        }

        // 天花板：封住房间顶部
        for (int lx = room.x0(); lx <= room.x1(); lx++) {
            for (int lz = room.z0(); lz <= room.z1(); lz++) {
                this.set(level, chunkBB, lx, ceilingY, lz, ceilingBlock(random));
            }
        }
    }

    // ==================== 3. 走廊 ====================

    /** 挖一条走廊：铺地板、砌侧壁与顶盖、掏空 3 格高，顺带开出两端门洞。 */
    private void buildCorridor(WorldGenLevel level, BoundingBox chunkBB, Corridor corridor, RandomSource random) {
        int floor = this.floorY();
        int corridorTop = floor + CORRIDOR_HEIGHT;
        int ceilingY = corridorTop + 1;

        // 地板
        for (int lx = corridor.x0(); lx <= corridor.x1(); lx++) {
            for (int lz = corridor.z0(); lz <= corridor.z1(); lz++) {
                this.set(level, chunkBB, lx, floor, lz, floorBlock(random));
            }
        }

        if (corridor.alongZ()) {
            // 沿 Z 轴延伸：两侧壁在 X 方向上，各往外一层
            for (int y = floor; y <= corridorTop; y++) {
                for (int lz = corridor.z0(); lz <= corridor.z1(); lz++) {
                    this.set(level, chunkBB, corridor.x0() - 1, y, lz, wallBlock(random));
                    this.set(level, chunkBB, corridor.x1() + 1, y, lz, wallBlock(random));
                }
            }
            // 顶盖
            for (int lx = corridor.x0() - 1; lx <= corridor.x1() + 1; lx++) {
                for (int lz = corridor.z0(); lz <= corridor.z1(); lz++) {
                    this.set(level, chunkBB, lx, ceilingY, lz, ceilingBlock(random));
                }
            }
        } else {
            // 沿 X 轴延伸：两侧壁在 Z 方向上，各往外一层
            for (int y = floor; y <= corridorTop; y++) {
                for (int lx = corridor.x0(); lx <= corridor.x1(); lx++) {
                    this.set(level, chunkBB, lx, y, corridor.z0() - 1, wallBlock(random));
                    this.set(level, chunkBB, lx, y, corridor.z1() + 1, wallBlock(random));
                }
            }
            // 顶盖
            for (int lx = corridor.x0(); lx <= corridor.x1(); lx++) {
                for (int lz = corridor.z0() - 1; lz <= corridor.z1() + 1; lz++) {
                    this.set(level, chunkBB, lx, ceilingY, lz, ceilingBlock(random));
                }
            }
        }

        // 掏空走廊主体（含两端房间墙体所在的格子，顺势掏出 3 格宽、3 格高的门洞）
        this.clearBox(level, chunkBB, corridor.x0(), floor + 1, corridor.z0(),
                corridor.x1(), corridorTop, corridor.z1());
    }

    // ==================== 4. 照明 ====================

    /** 每间房间两角各一支火把，每条走廊中点一支火把。 */
    private void placeTorches(WorldGenLevel level, BoundingBox chunkBB, Layout layout) {
        int ground = this.groundY();
        BlockState torch = Blocks.TORCH.defaultBlockState();

        for (Room room : layout.rooms()) {
            // 房间内部的两个对角（离墙 1 格），地板是实心的，火把可以正常站立
            this.set(level, chunkBB, room.x0() + 2, ground, room.z0() + 2, torch);
            this.set(level, chunkBB, room.x1() - 2, ground, room.z1() - 2, torch);
        }

        for (Corridor corridor : layout.corridors()) {
            int midX = (corridor.x0() + corridor.x1()) / 2;
            int midZ = (corridor.z0() + corridor.z1()) / 2;
            this.set(level, chunkBB, midX, ground, midZ, torch);
        }
    }

    // ==================== 5. 刷怪笼 ====================

    /**
     * 放置吸血鬼刷怪笼：先放 {@code minecraft:spawner} 方块，再从方块实体写死实体 id。
     * <p>实体 id 固定为 {@value #VAMPIRE_ENTITY_ID}；这里通过注册表反查它的
     * {@link EntityType}，再调用 {@link SpawnerBlockEntity#setEntityId(EntityType, RandomSource)}
     * ——这正是原版地牢/下界要塞刷怪笼的写法（会写进方块实体的
     * {@code SpawnData.entity.id}）。</p>
     */
    private void placeSpawners(WorldGenLevel level, BoundingBox chunkBB, List<BlockPos> positions, RandomSource random) {
        EntityType<?> vampireType = BuiltInRegistries.ENTITY_TYPE.getValue(VAMPIRE_ID);
        for (BlockPos pos : positions) {
            // 布局里的坐标是局部坐标（0..63），必须加上包围盒原点才是世界坐标
            BlockPos world = new BlockPos(this.originX() + pos.getX(), pos.getY(), this.originZ() + pos.getZ());
            if (!chunkBB.isInside(world)) {
                continue;
            }
            level.setBlock(world, Blocks.SPAWNER.defaultBlockState(), 2);
            if (level.getBlockEntity(world) instanceof SpawnerBlockEntity spawner) {
                if (vampireType != null) {
                    spawner.setEntityId(vampireType, random);
                } else {
                    // 只可能在该实体尚未注册时发生（实体由另一个模块负责），此时刷怪笼先留空。
                    LOGGER.warn("找不到实体 {}，位于 {} 的刷怪笼未写入刷怪数据", VAMPIRE_ENTITY_ID, world);
                }
            }
        }
    }

    // ==================== 6. 战利品箱 ====================

    /** 放置战利品箱：战利品表固定为 {@code minecraft:chests/simple_dungeon}。 */
    private void placeChests(WorldGenLevel level, BoundingBox chunkBB, List<BlockPos> positions, RandomSource random) {
        for (BlockPos pos : positions) {
            // 同上：局部坐标 -> 世界坐标
            int worldX = this.originX() + pos.getX();
            int worldZ = this.originZ() + pos.getZ();
            // 父类的 createChest 会用 chunkBB 裁剪、按周围方块自动给箱子定向，并写入战利品表。
            this.createChest(level, chunkBB, random, worldX, pos.getY(), worldZ,
                    BuiltInLootTables.SIMPLE_DUNGEON);
        }
    }

    // ==================== 内部数据结构 ====================

    /** 一间房间：局部坐标的方形区域 + 墙高。 */
    private record Room(int x0, int z0, int size, int wallHeight) {
        int x1() {
            return this.x0 + this.size - 1;
        }

        int z1() {
            return this.z0 + this.size - 1;
        }

        int centerX() {
            return this.x0 + this.size / 2;
        }

        int centerZ() {
            return this.z0 + this.size / 2;
        }
    }

    /** 一条走廊：局部坐标的水平矩形（含两端房间墙体所在的格子）。 */
    private record Corridor(int x0, int z0, int x1, int z1) {
        /** 走廊是否沿 Z 轴延伸（否则沿 X 轴延伸）。 */
        boolean alongZ() {
            return this.z1 - this.z0 > this.x1 - this.x0;
        }
    }

    /** 规划好的整套布局。刷怪笼/箱子位置用世界坐标（Y 为地板之上第一层）。 */
    private record Layout(List<Room> rooms, List<Corridor> corridors, List<BlockPos> spawnerPositions,
            List<BlockPos> chestPositions) {
    }
}
