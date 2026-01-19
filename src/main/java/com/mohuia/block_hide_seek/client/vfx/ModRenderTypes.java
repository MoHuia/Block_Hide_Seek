package com.mohuia.block_hide_seek.client.vfx;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class ModRenderTypes {

    // 你原来的 OBJ_SOLID_TRANSPARENT 保持不动
    public static final RenderType OBJ_SOLID_TRANSPARENT = /* 你的原实现 */ null;

    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "textures/vfx/fog_noise.png");

    /** 绑定自定义 shader + 噪声贴图的 RenderType */
    public static final RenderType OBJ_FOG = RenderType.create(
            "block_hide_seek_obj_fog",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> VfxShaders.OBJ_FOG))
                    .setTextureState(new RenderStateShard.TextureStateShard(NOISE_TEX, false, false))
                    // 不在这里设置透明/cull/depth（你环境权限问题），渲染时用 RenderSystem 手动开关
                    .createCompositeState(false)
    );

    public class ObjFogUniforms {
        // 缓存上次状态：避免每帧重复 set 和重复日志
        private static Object lastShaderRef = null;

        private static float lastFog = Float.NaN;
        private static float lastScale = Float.NaN;
        private static float lastSpeed = Float.NaN;

        // 可选：只在 uniform 缺失时提示一次
        private static boolean warnedTimeMissing = false;
        private static boolean warnedFogMissing = false;
        private static boolean warnedScaleMissing = false;
        private static boolean warnedSpeedMissing = false;

        public static void setupObjFogUniforms(float time, float fogStrength, float noiseScale, float noiseSpeed) {
            var shader = VfxShaders.OBJ_FOG;
            if (shader == null) return;

            // 只在 shader 实例发生变化时输出一次（比如 F3+T 重载后）
            if (shader != lastShaderRef) {
                System.out.println("🎨 [ObjFog] Shader instance changed: " + shader);

                var uTime = shader.getUniform("GameTime");
                System.out.println(uTime == null
                        ? "❌ [ObjFog] GameTime uniform NOT FOUND"
                        : "✅ [ObjFog] GameTime uniform FOUND");

                lastShaderRef = shader;
            }

            // time 通常每帧都变：直接 set（如果你确定 shader 用到了它）
            var uTime = shader.getUniform("GameTime");
            if (uTime != null) {
                uTime.set(time);
            } else if (!warnedTimeMissing) {
                System.out.println("⚠️ [ObjFog] 'GameTime' uniform is NULL (maybe optimized out / name mismatch).");
                warnedTimeMissing = true;
            }

            // 下面三个一般不会每帧变：只有变化时才 set
            var uFog = shader.getUniform("FogStrength");
            if (uFog != null) {
                if (fogStrength != lastFog) {
                    uFog.set(fogStrength);
                    lastFog = fogStrength;
                }
            } else if (!warnedFogMissing) {
                System.out.println("⚠️ [ObjFog] 'FogStrength' uniform is NULL.");
                warnedFogMissing = true;
            }

            var uScale = shader.getUniform("NoiseScale");
            if (uScale != null) {
                if (noiseScale != lastScale) {
                    uScale.set(noiseScale);
                    lastScale = noiseScale;
                }
            } else if (!warnedScaleMissing) {
                System.out.println("⚠️ [ObjFog] 'NoiseScale' uniform is NULL.");
                warnedScaleMissing = true;
            }

            var uSpeed = shader.getUniform("NoiseSpeed");
            if (uSpeed != null) {
                if (noiseSpeed != lastSpeed) {
                    uSpeed.set(noiseSpeed);
                    lastSpeed = noiseSpeed;
                }
            } else if (!warnedSpeedMissing) {
                System.out.println("⚠️ [ObjFog] 'NoiseSpeed' uniform is NULL.");
                warnedSpeedMissing = true;
            }
        }
    }

    private ModRenderTypes() {}
}
