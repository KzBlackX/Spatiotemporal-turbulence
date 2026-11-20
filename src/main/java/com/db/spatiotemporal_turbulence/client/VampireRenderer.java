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
import com.db.spatiotemporal_turbulence.entity.Vampire;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 吸血鬼渲染器。
 * <p>直接复用原版僵尸的 {@link ZombieModel} 与其烘焙层 {@link ModelLayers#ZOMBIE}（经典 64x64
 * 人形贴图布局），只把贴图换成模组自己生成的 {@code textures/entity/vampire.png}，
 * 因此不需要自建模型或追加模型层定义。</p>
 */
@OnlyIn(Dist.CLIENT)
public class VampireRenderer extends HumanoidMobRenderer<Vampire, ZombieRenderState, ZombieModel<ZombieRenderState>> {
    /** 本模组自绘的吸血鬼贴图（64x64，按原版人形皮肤 UV 布局）。 */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(SpatiotemporalTurbulence.MODID, "textures/entity/vampire.png");

    public VampireRenderer(EntityRendererProvider.Context context) {
        // 原版僵尸模型 + 0.5 阴影尺寸；成年/幼年共用同一个模型
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new ZombieRenderState();
    }

    @Override
    public void extractRenderState(Vampire entity, ZombieRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // 有攻击目标时手臂前伸，复用僵尸的攻击手臂动画
        state.isAggressive = entity.getTarget() != null;
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return TEXTURE;
    }
}
