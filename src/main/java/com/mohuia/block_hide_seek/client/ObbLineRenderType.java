package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

public class ObbLineRenderType extends RenderType {

    private ObbLineRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * ✅ 推荐先用这个：永远可见（不被遮挡），基本不会“消失/频闪”
     */
    public static final RenderType OBB_LINE_NO_DEPTH = create(
            "obb_line_no_depth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                    .setLineState(new LineStateShard(OptionalDouble.of(1.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING) // 防Z-fighting
                    .setDepthTestState(NO_DEPTH_TEST)          // 关键：不深度测试
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );

    /**
     * ✅ 如果你想要“被遮挡也能看起来稳定”，用这个（仍建议保留 VIEW_OFFSET_Z_LAYERING）
     */
    public static final RenderType OBB_LINE_DEPTH = create(
            "obb_line_depth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                    .setLineState(new LineStateShard(OptionalDouble.of(1.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );
}
