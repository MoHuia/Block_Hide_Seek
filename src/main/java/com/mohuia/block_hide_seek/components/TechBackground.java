package com.mohuia.block_hide_seek.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TechBackground {
    private final List<UIParticle> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final int width;
    private final int height;

    private static final int COLOR_BG_DARK = 0xFF121212;
    private static final int COLOR_ACCENT_CYAN = 0xFF00D2FF;

    public TechBackground(int width, int height) {
        this.width = width;
        this.height = height;
        // 初始生成一些粒子
        for(int i = 0; i < 20; i++) {
            addParticle(random.nextInt(width), random.nextInt(height));
        }
    }

    public void render(GuiGraphics gfx, float timeSeconds, float globalAlpha) {
        if (globalAlpha <= 0.01f) return;

        // 1. 渲染深色底色
        int alphaInt = (int)(globalAlpha * 255);
        int bgCol = (alphaInt << 24) | (COLOR_BG_DARK & 0x00FFFFFF);
        gfx.fill(0, 0, width, height, bgCol);

        // 2. 渲染网格
        PoseStack pose = gfx.pose();
        pose.pushPose();

        float gridSize = 40;
        float offset = (timeSeconds * 15) % gridSize;
        int gridBaseAlpha = 0x1F;
        int finalGridAlpha = (int)(gridBaseAlpha * globalAlpha);
        int gridColor = (finalGridAlpha << 24) | 0xFFFFFF;

        for (float x = -offset; x < width; x += gridSize) {
            if (x >= 0) gfx.fill((int)x, 0, (int)x + 1, height, gridColor);
        }
        for (float y = offset; y < height; y += gridSize) {
            gfx.fill(0, (int)y, width, (int)y + 1, gridColor);
        }

        pose.popPose();

        // 3. 更新并渲染粒子
        updateAndRenderParticles(gfx, globalAlpha);
    }

    private void updateAndRenderParticles(GuiGraphics gfx, float globalAlpha) {
        // 随机生成新粒子
        if (random.nextFloat() < 0.15f) {
            addParticle(random.nextInt(width), height + 10);
        }

        Iterator<UIParticle> it = particles.iterator();
        while (it.hasNext()) {
            UIParticle p = it.next();
            p.update(0.05f); // dt
            if (!p.isAlive()) {
                it.remove();
                continue;
            }

            float alpha = p.getAlpha() * globalAlpha;
            if (alpha <= 0.01f) continue;

            int color = ((int)(alpha * 255) << 24) | 0x00FFFFFF;
            if (random.nextFloat() < 0.1f) color = ((int)(alpha * 255) << 24) | (COLOR_ACCENT_CYAN & 0x00FFFFFF);

            gfx.fill((int)p.x, (int)p.y, (int)(p.x + p.size), (int)(p.y + p.size), color);
        }
    }

    private void addParticle(double x, double y) {
        float life = 2.0f + random.nextFloat() * 3.0f;
        float size = 2.0f + random.nextFloat() * 4.0f;
        float speedY = -10.0f - random.nextFloat() * 20.0f;
        particles.add(new UIParticle(x, y, 0, speedY, size, life));
    }

    // 简单的粒子内部类
    private static class UIParticle {
        double x, y, speedY;
        float size, life, maxLife;

        public UIParticle(double x, double y, double speedX, double speedY, float size, float maxLife) {
            this.x = x;
            this.y = y;
            this.speedY = speedY;
            this.size = size;
            this.life = maxLife;
            this.maxLife = maxLife;
        }

        public void update(float dt) {
            y += speedY * dt;
            life -= dt;
        }

        public boolean isAlive() { return life > 0; }
        public float getAlpha() { return Mth.clamp(life / maxLife, 0f, 1f) * 0.5f; }
    }
}
