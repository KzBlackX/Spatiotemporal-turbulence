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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 黑洞吸入机制。
 * <p>玩家在主世界停留配置的等待时间（{@code warmupSeconds}，默认 10 秒）后会被黑洞吸入，
 * 吸入阶段持续 {@value #SUCTION_TICKS} tick，随后被传送到本模组的中世纪维度。
 * 状态全部保存在服务端静态容器中，按玩家 UUID 区分。</p>
 */
public final class BlackHoleHandler {
    // 中世纪维度对应的 level 键，与 data/spatiotemporal_turbulence/dimension/medieval.json 中的 type 一致
    public static final ResourceKey<Level> MEDIEVAL = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath(SpatiotemporalTurbulence.MODID, "medieval"));

    // 配置尚未加载时使用的默认等待时间（200 tick = 10 秒）
    private static final int FALLBACK_WARMUP_TICKS = 200;
    // 吸入阶段持续的总 tick 数
    private static final int SUCTION_TICKS = 60;
    // 每 tick 向黑洞中心移动的剩余距离比例
    private static final double PULL_FACTOR = 0.18D;
    // 每 tick 绘制粒子环的分段数
    private static final int RING_SEGMENTS = 14;
    // 传送目标在水平方向上的随机范围（-1000..1000）
    private static final int SPAWN_RANGE = 1000;

    // 玩家 UUID -> 在主世界已经累计的 tick 数
    private static final Map<UUID, Integer> OVERWORLD_TICKS = new HashMap<>();
    // 玩家 UUID -> 当前正在进行的吸入状态
    private static final Map<UUID, Suction> ACTIVE_SUCTIONS = new HashMap<>();
    // 已经完成传送的玩家 UUID（保留标记，之后不再触发；登出时也不清理）
    private static final Set<UUID> TELEPORTED = new HashSet<>();

    private BlackHoleHandler() {
    }

    /**
     * 从配置读取触发前需要在主世界累计的 tick 数（秒数 × 20）。
     * <p>配置项在服务端启动阶段才加载，读取失败时退回默认值，避免在事件里抛异常刷屏。</p>
     */
    private static int warmupTicks() {
        try {
            return Math.max(1, Config.WARMUP_SECONDS.getAsInt()) * 20;
        } catch (IllegalStateException e) {
            return FALLBACK_WARMUP_TICKS;
        }
    }

    /**
     * 玩家每 tick 结束时调用（服务端与客户端都会触发，这里只处理服务端玩家）。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();

        // 已经传送过的玩家不再参与黑洞吸入
        if (TELEPORTED.contains(playerId)) {
            return;
        }

        // 正在吸入中：继续推进吸入阶段
        Suction suction = ACTIVE_SUCTIONS.get(playerId);
        if (suction != null) {
            tickSuction(player, playerId, suction);
            return;
        }

        // 只有主世界才累计时间，地狱/末地/中世纪等其他维度一律不触发
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            OVERWORLD_TICKS.remove(playerId);
            return;
        }

        int ticks = OVERWORLD_TICKS.getOrDefault(playerId, 0) + 1;
        if (ticks < warmupTicks()) {
            OVERWORLD_TICKS.put(playerId, ticks);
            return;
        }

        // 达到触发条件：固定黑洞中心为“玩家脚下上方 1.0 格”，之后中心不再跟随玩家
        OVERWORLD_TICKS.remove(playerId);
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        ACTIVE_SUCTIONS.put(playerId, new Suction(center));
    }

    /**
     * 玩家登出时清理计时与吸入状态；已传送标记保留。
     */
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        OVERWORLD_TICKS.remove(playerId);
        ACTIVE_SUCTIONS.remove(playerId);
    }

    /**
     * 推进一 tick 的吸入阶段：绘制漩涡粒子、播放音效、把玩家拉向黑洞中心。
     */
    private static void tickSuction(ServerPlayer player, UUID playerId, Suction suction) {
        suction.ticks++;

        // 玩家在吸入过程中死亡：放弃本次吸入，避免把死亡状态传送到其他维度
        if (player.isDeadOrDying()) {
            ACTIVE_SUCTIONS.remove(playerId);
            return;
        }

        // 吸入阶段结束，执行维度传送
        if (suction.ticks >= SUCTION_TICKS) {
            finishSuction(player, playerId);
            return;
        }

        float progress = suction.ticks / (float) SUCTION_TICKS;
        spawnVortexParticles(player.level(), suction.center, suction.ticks, progress);
        pullTowardCenter(player, suction.center);

        // 每 10 tick 播放一次传送门环境音
        if (suction.ticks % 10 == 0) {
            ServerLevel level = player.level();
            level.playSound(null, suction.center.x, suction.center.y, suction.center.z,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.8F + progress * 0.4F);
        }
    }

    /**
     * 以黑洞中心为圆心绘制一圈 PORTAL 粒子，并在中心补少量 SQUID_INK 粒子。
     * 半径随吸入进度从 2.0 收缩到 0.25，同时带整体旋转与上下起伏。
     */
    private static void spawnVortexParticles(ServerLevel level, Vec3 center, int tick, float progress) {
        // 半径随进度线性收缩
        double radius = 2.0D - 1.75D * progress;
        // 旋转角速度随进度加快，形成加速盘旋的观感
        double rotation = tick * (0.25D + 0.5D * progress);
        // 上下起伏的幅度随进度减弱
        double wave = 0.35D * (1.0D - progress);

        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = rotation + i * (Math.PI * 2.0D / RING_SEGMENTS);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + Math.sin(angle * 2.0D + tick * 0.35D) * wave;
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // 黑洞中心补少量 SQUID_INK 粒子，制造暗色核心
        level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 3, 0.2D, 0.2D, 0.2D, 0.01D);
    }

    /**
     * 用 teleportTo 逐 tick 把玩家向黑洞中心牵引（服务端对玩家不宜依赖 setDeltaMovement）。
     * 每 tick 走剩余距离的 {@value #PULL_FACTOR}。
     */
    private static void pullTowardCenter(ServerPlayer player, Vec3 center) {
        Vec3 position = player.position();
        Vec3 next = position.add(center.subtract(position).scale(PULL_FACTOR));
        player.teleportTo(next.x, next.y, next.z);
    }

    /**
     * 吸入结束：把玩家随机传送到中世纪维度的地表，并设置该维度的重生点。
     */
    private static void finishSuction(ServerPlayer player, UUID playerId) {
        // 先移除吸入状态，保证无论传送是否成功玩家都不会被卡在吸入阶段
        ACTIVE_SUCTIONS.remove(playerId);

        ServerLevel medieval = player.level().getServer().getLevel(MEDIEVAL);
        if (medieval == null) {
            SpatiotemporalTurbulence.LOGGER.error("黑洞吸入失败：找不到维度 {}，放弃传送玩家 {}",
                    MEDIEVAL.identifier(), player.getUUID());
            return;
        }

        // 在中世纪维度 -1000..1000 的范围内随机挑选水平坐标
        RandomSource random = medieval.getRandom();
        int spawnX = random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE;
        int spawnZ = random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE;

        // 目标区块可能尚未生成，先强制加载，保证高度图能取到真实地表高度
        medieval.getChunk(spawnX >> 4, spawnZ >> 4);
        BlockPos spawn = medieval.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(spawnX, 0, spawnZ));

        float yRot = player.getYRot();
        float xRot = player.getXRot();

        // 传送到中世纪维度的地表
        player.teleport(new TeleportTransition(
                medieval,
                Vec3.atBottomCenterOf(spawn),
                Vec3.ZERO,
                yRot,
                xRot,
                TeleportTransition.PLAY_PORTAL_SOUND));

        // 设置重生点，避免玩家死亡后被打回主世界
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(MEDIEVAL, spawn, yRot, xRot), false), false);

        // 标记已传送，之后不再触发
        TELEPORTED.add(playerId);
    }

    /**
     * 单个玩家正在进行中的吸入状态：黑洞中心在触发时固定，ticks 记录已经过的吸入 tick 数。
     */
    private static final class Suction {
        private final Vec3 center;
        private int ticks;

        private Suction(Vec3 center) {
            this.center = center;
        }
    }
}
