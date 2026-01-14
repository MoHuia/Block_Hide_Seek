package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.render.RadarRenderer;
import com.mohuia.block_hide_seek.client.vfx.ObjRender;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRenderHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // === 原有的前置模组渲染器已注释，避免报错 ===
        // BeaconRenderer.render(poseStack, bufferSource);
        // FocusPointRenderer.render(poseStack, bufferSource);
        // BorderRenderer.render(poseStack, bufferSource);

        // ✅ 调用本地雷达渲染
        RadarRenderer.render(poseStack, bufferSource);
        int fullBright = 0x00F000F0;

        // ✅ 只调用：跟随玩家渲染 OBJ
        //ObjRender.renderFollowPlayer(poseStack, bufferSource, mc, event.getPartialTick());

        // 统一结束批处理 (RadarRenderer 内部已经处理了自己的 endBatch，但这里作为一个良好的习惯，可以 endBatch 其他层)
        // bufferSource.endBatch(ModRenderTypes.NORMAL_LINES);
        bufferSource.endBatch(); // 提交所有剩余的缓冲区

        poseStack.popPose();
    }
}
