package com.mohuia.block_hide_seek.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import static com.mohuia.block_hide_seek.client.vfx.ModRenderTypes.OBJ_FOG;

public final class ObjRender {

    public static void renderFollowPlayer(PoseStack ps,
                                          MultiBufferSource buffers,
                                          Minecraft mc,
                                          float partialTick) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (VfxAssets.ARROW_TRAIL == null) return;

        // ✅ 连续时间
        float time = p.tickCount + partialTick;

        // ✅ 必须使用同一个 BufferSource
        if (!(buffers instanceof MultiBufferSource.BufferSource buf)) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        double t = (double) partialTick;
        double px = lerp(t, p.xo, p.getX());
        double py = lerp(t, p.yo, p.getY());
        double pz = lerp(t, p.zo, p.getZ());

        Vec3 look = p.getViewVector(partialTick);

        double x = px + look.x * 1.5;
        double y = py + p.getEyeHeight() + look.y * 1.5 + 0.2;
        double z = pz + look.z * 1.5;

        ps.pushPose();
        ps.translate(x, y, z);

        float s = 0.25f;
        ps.scale(s, s, s);


        // ✅ 在 flush 前 set uniform
        ModRenderTypes.ObjFogUniforms.setupObjFogUniforms(time, 1.0f, 2.0f, 0.25f);


        VertexConsumer vc = buf.getBuffer(OBJ_FOG);

        float r = 0.55f, g = 0.85f, b = 1.0f, a = 0.75f;
        int fullBright = 0xF000F0;

        VfxAssets.ARROW_TRAIL.render(ps, vc, r, g, b, a, fullBright);

        ps.popPose();

        // ✅ 用同一个 buf flush
        buf.endBatch(OBJ_FOG);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
