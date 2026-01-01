package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ObbDebugRender {

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        // ✅ 只有本地按了 F3+B 才会 true
        if (!dispatcher.shouldRenderHitBoxes()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        // ✅ 全服玩家（客户端能看到的玩家实体）
        for (Player p : mc.level.players()) {
            ObbUtil.getPlayerObb(p).ifPresent(obb -> drawObb(obb, poseStack, vc, cam));
        }

        buffers.endBatch(RenderType.lines());
    }

    private void drawObb(VirtualOBB obb, PoseStack poseStack, VertexConsumer vc, Vec3 cam) {
        Vec3[] edges = obb.getWireframeEdges();

        PoseStack.Pose pose = poseStack.last();
        var mat = pose.pose();
        var normal = pose.normal();

        float r = 1f, g = 1f, b = 1f, a = 1f;

        for (int i = 0; i < edges.length; i += 2) {
            Vec3 p0 = edges[i].subtract(cam);
            Vec3 p1 = edges[i + 1].subtract(cam);

            vc.vertex(mat, (float) p0.x, (float) p0.y, (float) p0.z)
                    .color(r, g, b, a)
                    .normal(normal, 0f, 1f, 0f)
                    .endVertex();

            vc.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z)
                    .color(r, g, b, a)
                    .normal(normal, 0f, 1f, 0f)
                    .endVertex();
        }
    }
}
