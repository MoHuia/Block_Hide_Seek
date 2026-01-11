package com.mohuia.block_hide_seek.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DecoyRenderer extends EntityRenderer<DecoyEntity> {

    public DecoyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DecoyEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getDisguiseBlock();
        if (state == null || state.isAir()) return;

        poseStack.pushPose();

        // 1. 实体旋转 (实体系统的 yaw 通常是反向的)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-entityYaw));

        // 2. ✅ 调用通用渲染组件，实现 100% 完美的复刻
        DisguiseRenderHelper.renderDisguiseBlock(
                state,
                poseStack,
                buffer,
                entity.level(),
                entity.blockPosition(),
                packedLight
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png");
    }
}
