package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ObbDebugRender {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (!dispatcher.shouldRenderHitBoxes()) return; // F3+B

        // ✅ 关键：partialTick
        float pt = event.getPartialTick();

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(ObbLineRenderType.OBB_LINE_DEPTH); // 建议用 DEPTH + VIEW_OFFSET 更像原版

        for (Player p : mc.level.players()) {
            // capability 取尺寸
            p.getCapability(GameDataProvider.CAP).resolve().ifPresent(data -> {
                float sizeX = data.getAABBX();
                float sizeY = data.getAABBY();
                float sizeZ = data.getAABBZ();

                // ✅ 坐标插值：xo/yo/zo 是上一tick位置
                double x = Mth.lerp(pt, p.xo, p.getX());
                double y = Mth.lerp(pt, p.yo, p.getY());
                double z = Mth.lerp(pt, p.zo, p.getZ());

                // ✅ 朝向插值：yRotO 是上一tick yaw
                float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot());

                // ✅ 轻微膨胀，减少和模型/地面重叠导致的闪（按需调小/调大）
                float eps = 0.0025f;

                Vec3 center = new Vec3(x, y + sizeY * 0.5, z);
                VirtualOBB obb = new VirtualOBB(center, sizeX + eps, sizeY + eps, sizeZ + eps, yaw);

                drawObb(obb, poseStack, vc, cam);
            });
        }

        buffers.endBatch(ObbLineRenderType.OBB_LINE_DEPTH);
    }

    private static void drawObb(VirtualOBB obb, PoseStack poseStack, VertexConsumer vc, Vec3 cam) {
        Vec3[] edges = obb.getWireframeEdgesCached();

        var pose = poseStack.last();
        var mat = pose.pose();
        var normalMat = pose.normal();

        double cx = cam.x, cy = cam.y, cz = cam.z;

        float r = 1f, g = 1f, b = 1f, a = 1f;

        for (int i = 0; i < edges.length; i += 2) {
            Vec3 p0 = edges[i];
            Vec3 p1 = edges[i + 1];

            float x0 = (float) (p0.x - cx);
            float y0 = (float) (p0.y - cy);
            float z0 = (float) (p0.z - cz);

            float x1 = (float) (p1.x - cx);
            float y1 = (float) (p1.y - cy);
            float z1 = (float) (p1.z - cz);

            vc.vertex(mat, x0, y0, z0).color(r, g, b, a).normal(normalMat, 0f, 1f, 0f).endVertex();
            vc.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(normalMat, 0f, 1f, 0f).endVertex();
        }
    }
}
