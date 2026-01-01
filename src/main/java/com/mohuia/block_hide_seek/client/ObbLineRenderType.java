package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

public class ObbLineRenderType extends RenderType {
    private ObbLineRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * ✅ 永远可见（不被遮挡），最适合 debug，不会“消失/闪”
     * 关键点：顶点格式必须是 POSITION_COLOR_NORMAL（跟 lines shader 匹配）
     */
    public static final RenderType OBB_LINE_NO_DEPTH = create(
            "obb_line_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(1.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );

    /**
     * ✅ 需要遮挡效果就用这个
     */
    public static final RenderType OBB_LINE_DEPTH = create(
            "obb_line_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
    );
}