package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ObbDebugRender {

    // ✅ 每tick缓存一次，render只读
    private static final Map<UUID, VirtualOBB> OBB_CACHE = new HashMap<>();

    /**
     * 每 tick（20Hz）更新一次所有可见玩家的 OBB，减少 render 压力
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        OBB_CACHE.clear();

        for (Player p : mc.level.players()) {
            ObbUtil.getPlayerObb(p).ifPresent(obb -> OBB_CACHE.put(p.getUUID(), obb));
        }
    }

    /**
     * 画线框：在 AFTER_ENTITIES 阶段画
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        // ✅ 只有本地按了 F3+B 才显示
        if (!dispatcher.shouldRenderHitBoxes()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // ✅ 推荐：先用 NO_DEPTH 彻底排除“消失/频闪”问题
        VertexConsumer vc = buffers.getBuffer(ObbLineRenderType.OBB_LINE_NO_DEPTH);

        for (VirtualOBB obb : OBB_CACHE.values()) {
            drawObb(obb, poseStack, vc, cam);
        }

        // ✅ endBatch 对应你的 RenderType
        buffers.endBatch(ObbLineRenderType.OBB_LINE_NO_DEPTH);
    }

    private static void drawObb(VirtualOBB obb, PoseStack poseStack, VertexConsumer vc, Vec3 cam) {
        Vec3[] edges = obb.getWireframeEdgesCached();

        var mat = poseStack.last().pose();

        // ✅ 白色
        float r = 1f, g = 1f, b = 1f, a = 1f;

        // ✅ 避免 new Vec3：直接做坐标减法
        double cx = cam.x, cy = cam.y, cz = cam.z;

        for (int i = 0; i < edges.length; i += 2) {
            Vec3 p0 = edges[i];
            Vec3 p1 = edges[i + 1];

            float x0 = (float) (p0.x - cx);
            float y0 = (float) (p0.y - cy);
            float z0 = (float) (p0.z - cz);

            float x1 = (float) (p1.x - cx);
            float y1 = (float) (p1.y - cy);
            float z1 = (float) (p1.z - cz);

            vc.vertex(mat, x0, y0, z0).color(r, g, b, a).endVertex();
            vc.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
        }
    }
}
