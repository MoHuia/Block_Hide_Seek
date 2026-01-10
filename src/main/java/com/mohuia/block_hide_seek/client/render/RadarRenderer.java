package com.mohuia.block_hide_seek.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.Iterator;

// ✅ 所有的引用都指向本地包
public class RadarRenderer extends RenderType {

    public RadarRenderer(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    // ✅ 定义自己的 RenderType，不再依赖 ModRenderTypes
    public static final RenderType RADAR_PASS = create(
            "block_hide_seek_radar_pass",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false)
    );

    private static final long EXPAND_MS = 1000L;
    private static final long HOLD_MS   = 2000L;
    private static final long FADE_MS   = 1000L;
    private static final long TOTAL_MS  = EXPAND_MS + HOLD_MS + FADE_MS;

    private static final double PULSE_RADIUS = 20.0;
    private static final long PULSE_EXPAND_MS = 500L;
    private static final long PULSE_HOLD_MS   = 1500L;
    private static final long PULSE_FADE_MS   = 1000L;
    private static final long PULSE_TOTAL_MS  = PULSE_EXPAND_MS + PULSE_HOLD_MS + PULSE_FADE_MS;

    private static final double RANGE_START_MAX_DIST = 5.0;
    private static final double RANGE_END_MAX_DIST = 30.0;
    private static final double RANGE_START_OUTER = 3.0;
    private static final double RANGE_END_OUTER = 30.0;
    private static final double RANGE_START_INNER = 0.0;
    private static final double RANGE_END_INNER = 25.0;

    private static final float COLOR_START_R = 0.1f;
    private static final float COLOR_START_G = 0.2f;
    private static final float COLOR_START_B = 0.1f;
    private static final float COLOR_END_R = 0.0f;
    private static final float COLOR_END_G = 0.05f;
    private static final float COLOR_END_B = 0.0f;

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        GeometryCache cache = GeometryCache.getInstance();
        if (cache.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getEyeY();
        double eyeZ = mc.player.getZ();
        long now = System.currentTimeMillis();

        VertexConsumer builder = bufferSource.getBuffer(RADAR_PASS);
        Matrix4f matrix = poseStack.last().pose();

        for (GeometryCache.CacheEntry entry : cache.getCacheQueue()) {
            long elapsed = now - entry.createTime;
            if (elapsed < 0) elapsed = 0;
            if (elapsed >= TOTAL_MS) continue;

            double tExpand = clamp01((double) elapsed / (double) EXPAND_MS);
            tExpand = smoothstep(tExpand);

            float energyMain;
            if (elapsed <= EXPAND_MS + HOLD_MS) {
                energyMain = 1.0f;
            } else {
                double tFade = (double) (elapsed - (EXPAND_MS + HOLD_MS)) / (double) FADE_MS;
                energyMain = (float) (1.0 - smoothstep(clamp01(tFade)));
            }

            double maxDist = lerp(RANGE_START_MAX_DIST, RANGE_END_MAX_DIST, tExpand);
            double outerRad = lerp(RANGE_START_OUTER, RANGE_END_OUTER, tExpand);
            double innerRad = lerp(RANGE_START_INNER, RANGE_END_INNER, tExpand);

            float rr = lerpFloat(COLOR_START_R, COLOR_END_R, (float) tExpand);
            float rg = lerpFloat(COLOR_START_G, COLOR_END_G, (float) tExpand);
            float rb = lerpFloat(COLOR_START_B, COLOR_END_B, (float) tExpand);

            // A) 触发 Pulse
            if (entry.targets != null && !entry.targets.isEmpty()) {
                for (GeometryCache.ScanTarget t : entry.targets) {
                    if (!t.triggered && now >= t.triggerMs) {
                        t.triggered = true;
                        entry.pulses.add(new GeometryCache.Pulse(t.x, t.y, t.z, t.triggerMs));
                    }
                }
            }

            // B) 渲染主层
            var mainShader = new RaderEnergyShader()
                    .range(maxDist, outerRad, innerRad)
                    .color(rr, rg, rb)
                    .deadColor(0.02f, 0.2f, 0.02f)
                    .decayPow(2.4, 1.8)
                    .energy(energyMain)
                    .useCenterForShading(true)
                    .abyssRadius(innerRad);

            var mainSpot = QuadFxAPI.spot()
                    .eye(eyeX, eyeY, eyeZ)
                    .center(entry.originX, entry.originY, entry.originZ)
                    .shader(mainShader)
                    .maxDist(1024.0)
                    .detail(1)
                    .clear();

            cache.renderCache(mainSpot, 32.0, entry);
            mainSpot.render(builder, matrix, true);

            // C) 渲染 Pulse
            if (entry.pulses != null && !entry.pulses.isEmpty()) {
                Iterator<GeometryCache.Pulse> it = entry.pulses.iterator();
                while (it.hasNext()) {
                    GeometryCache.Pulse p = it.next();
                    long age = now - p.startMs;
                    if (age >= PULSE_TOTAL_MS) {
                        it.remove();
                        continue;
                    }

                    double tPulseExpand = clamp01((double) age / (double) PULSE_EXPAND_MS);
                    tPulseExpand = smoothstep(tPulseExpand);

                    float energyPulse;
                    long pulseHoldEnd = PULSE_EXPAND_MS + PULSE_HOLD_MS;
                    if (age <= pulseHoldEnd) energyPulse = 1.0f;
                    else {
                        double tFade = (double) (age - pulseHoldEnd) / (double) PULSE_FADE_MS;
                        energyPulse = (float) (1.0 - smoothstep(clamp01(tFade)));
                    }

                    double pulseOuter = lerp(0.6, PULSE_RADIUS, tPulseExpand);
                    double pulseInner = Math.max(0.0, pulseOuter - 1.2);

                    var pulseShader = new RaderEnergyShader()
                            .range(PULSE_RADIUS, pulseOuter, pulseInner)
                            .color(0.60f, 0.02f, 0.01f)
                            .deadColor(0.02f, 0.10f, 0.02f)
                            .decayPow(2.6, 3.2)
                            .energy(energyPulse)
                            .useCenterForShading(true)
                            .abyssRadius(0.0);

                    var pulseSpot = QuadFxAPI.spot()
                            .eye(eyeX, eyeY, eyeZ)
                            .center(p.x, p.y, p.z)
                            .shader(pulseShader)
                            .maxDist(1024.0)
                            .detail(1)
                            .clear();

                    double limit = PULSE_RADIUS + 1.5;
                    double maxSq = limit * limit;
                    for (QuadFxAPI.QuadJob job : entry.quads) {
                        double dx = job.cx - p.x;
                        double dy = job.cy - p.y;
                        double dz = job.cz - p.z;
                        if (dx*dx + dy*dy + dz*dz <= maxSq) {
                            pulseSpot.quad(job);
                        }
                    }
                    pulseSpot.render(builder, matrix, true);
                }
            }
        }

        // 结束批次
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RADAR_PASS);
        }
    }

    private static double lerp(double start, double end, double t) { return start + (end - start) * t; }
    private static float lerpFloat(float start, float end, float t) { return start + (end - start) * t; }
    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
}
