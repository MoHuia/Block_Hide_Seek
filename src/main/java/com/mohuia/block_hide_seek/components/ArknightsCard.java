package com.mohuia.block_hide_seek.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ArknightsCard {
    public final BlockState state;
    public final int index;
    private final ItemStack itemStack;

    // 布局目标
    private final int targetX, targetY;
    private final float cardDelay;
    private final float blockDelay;

    // 动态属性
    private float currentX, currentY;
    private boolean isHovered;
    private float renderScale = 1.0f;
    private boolean playedEntrySound = false;

    // 常量
    public static final int WIDTH = 80;
    public static final int HEIGHT = 110;
    private static final int COLOR_ACCENT_CYAN = 0xFF00D2FF;
    private static final int COLOR_TEXT_GRAY = 0xFF888888;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    public ArknightsCard(BlockState state, int index, int targetX, int targetY, float cardDelay, float blockDelay) {
        this.state = state;
        this.index = index;
        this.itemStack = new ItemStack(state.getBlock());
        this.targetX = targetX;
        this.targetY = targetY;
        this.cardDelay = cardDelay;
        this.blockDelay = blockDelay;
        // 初始位置在屏幕外
        this.currentX = targetX;
        this.currentY = targetY + 200;
    }

    public void update(double mouseX, double mouseY) {
        this.isHovered = mouseX >= currentX && mouseX < currentX + WIDTH &&
                mouseY >= currentY && mouseY < currentY + HEIGHT;
    }

    public boolean isHovered() { return isHovered; }

    public void render(GuiGraphics gfx, float timeSeconds, float closingProgress, float globalAlpha,
                       boolean isClosing, boolean isSelected, int screenW, int screenH) {

        // --- 1. 播放入场音效 ---
        if (timeSeconds >= cardDelay && !playedEntrySound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.8F));
            playedEntrySound = true;
        }

        // --- 2. 计算位置 ---
        if (isClosing) {
            updateClosingPosition(closingProgress, isSelected, screenW, screenH);
        } else {
            updateEntryPosition(timeSeconds);
        }

        // --- 3. 准备渲染 ---
        float slideProgress = Mth.clamp((timeSeconds - cardDelay) / 0.8f, 0f, 1f); // 0.8f = entry duration
        if (slideProgress <= 0 || globalAlpha <= 0.001f) return;

        RenderSystem.setShaderColor(1f, 1f, 1f, globalAlpha);
        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(currentX, currentY, 0);

        // 中心缩放与旋转
        pose.translate(WIDTH / 2.0f, HEIGHT / 2.0f, 0);
        applyTransformations(pose, timeSeconds, closingProgress, globalAlpha, isClosing, isSelected);
        pose.translate(-WIDTH / 2.0f, -HEIGHT / 2.0f, 0);

        // --- 4. 绘制卡片内容 ---
        drawCardBase(gfx, isClosing, isSelected);
        drawScanLines(gfx, timeSeconds, isClosing, isSelected);
        drawIndex(gfx, isClosing, isSelected);
        drawItem(gfx, pose, timeSeconds, globalAlpha, isClosing);
        drawText(gfx, pose, isClosing, isSelected, globalAlpha);

        RenderSystem.disableBlend();
        pose.popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // --- 内部逻辑拆分 ---

    private void updateEntryPosition(float timeSeconds) {
        float slideProgress = Mth.clamp((timeSeconds - cardDelay) / 0.8f, 0f, 1f);
        float slideEase = slideProgress == 1 ? 1 : 1 - (float)Math.pow(2, -10 * slideProgress); // EaseOutExpo

        // 从左下角飞入
        this.currentX = Mth.lerp(slideEase, -150, targetX);
        this.currentY = Mth.lerp(slideEase, Minecraft.getInstance().getWindow().getGuiScaledHeight() + 100, targetY);
    }

    private void updateClosingPosition(float closingProgress, boolean isSelected, int screenW, int screenH) {
        if (isSelected) {
            // 选中者移动到屏幕中心
            float moveDuration = 0.4f / 1.2f; // phase move end
            float moveProgress = Mth.clamp(closingProgress / moveDuration, 0f, 1f);
            float moveEase = moveProgress < 0.5 ? 4 * moveProgress * moveProgress * moveProgress : 1 - (float)Math.pow(-2 * moveProgress + 2, 3) / 2; // EaseInOutCubic

            float screenCenterX = (screenW - WIDTH) / 2.0f;
            float screenCenterY = (screenH - HEIGHT) / 2.0f;
            this.currentX = Mth.lerp(moveEase, targetX, screenCenterX);
            this.currentY = Mth.lerp(moveEase, targetY, screenCenterY);
        } else {
            // 未选中者下落消失
            float dropDuration = 0.3f / 1.2f;
            float dropProgress = Mth.clamp(closingProgress / dropDuration, 0f, 1f);
            float c1 = 1.70158f; float c3 = c1 + 1;
            float dropEase = c3 * dropProgress * dropProgress * dropProgress - c1 * dropProgress * dropProgress; // EaseInBack

            this.currentX = targetX;
            this.currentY = targetY + dropEase * (screenH + 100);
        }
    }

    private void applyTransformations(PoseStack pose, float time, float closingProgress, float globalAlpha, boolean isClosing, boolean isSelected) {
        float targetScale = (isHovered && !isClosing) ? 1.05f : 1.0f;

        if (isClosing && isSelected) {
            float moveDuration = 0.4f / 1.2f;
            float moveProgress = Mth.clamp(closingProgress / moveDuration, 0f, 1f);
            float scaleEase = 1 - (float)Math.pow(1 - moveProgress, 3); // EaseOutCubic
            targetScale = Mth.lerp(scaleEase, 1.05f, 2.0f);
            if (globalAlpha < 1.0f) targetScale += (1.0f - globalAlpha) * 0.2f; // 渐隐时微弱放大
        } else if (!isClosing && isHovered) {
            float rotateAngle = (float)Math.sin(time * 3.0) * 1.5f;
            pose.mulPose(Axis.ZP.rotationDegrees(rotateAngle));
        }

        this.renderScale = Mth.lerp(0.2f, this.renderScale, targetScale);
        pose.scale(renderScale, renderScale, 1f);
    }

    private void drawCardBase(GuiGraphics gfx, boolean isClosing, boolean isSelected) {
        int bgColor = (isHovered || isSelected) ? 0xE62A2A2A : 0xE61A1A1A;
        int borderColor = (isHovered || isSelected) ? COLOR_ACCENT_CYAN : 0xFF444444;

        if (isClosing && isSelected) {
            borderColor = COLOR_WHITE;
        }

        RenderSystem.enableBlend();
        gfx.fill(0, 0, WIDTH, HEIGHT, bgColor);

        // 边框
        gfx.fill(0, 0, WIDTH, 1, borderColor);
        gfx.fill(0, HEIGHT - 1, WIDTH, HEIGHT, borderColor);

        // 四角装饰
        int cornerLen = 10;
        int cornerColor = (isHovered || isSelected) ? COLOR_WHITE : borderColor;
        gfx.fill(0, 0, 1, cornerLen, cornerColor);
        gfx.fill(WIDTH - 1, 0, WIDTH, cornerLen, cornerColor);
        gfx.fill(0, HEIGHT - cornerLen, 1, HEIGHT, cornerColor);
        gfx.fill(WIDTH - 1, HEIGHT - cornerLen, WIDTH, HEIGHT, cornerColor);
    }

    private void drawScanLines(GuiGraphics gfx, float time, boolean isClosing, boolean isSelected) {
        if ((isHovered && !isClosing) || (isClosing && isSelected)) {
            float scanPos = (time * 0.8f) % 2.0f;
            if (scanPos > 1.0f) scanPos = 2.0f - scanPos;
            int scanY = (int) (scanPos * HEIGHT);
            gfx.fill(1, scanY, WIDTH-1, scanY + 2, 0x4000D2FF);
            gfx.fill(0, 0, 18, 18, COLOR_ACCENT_CYAN);
        } else {
            gfx.fill(0, 0, 18, 18, 0xFF333333);
        }
    }

    private void drawIndex(GuiGraphics gfx, boolean isClosing, boolean isSelected) {
        int indexColor = (isHovered || isSelected) ? 0xFF000000 : 0xFFAAAAAA;
        gfx.drawCenteredString(Minecraft.getInstance().font, String.valueOf(index + 1), 9, 5, indexColor);
    }

    private void drawItem(GuiGraphics gfx, PoseStack pose, float time, float globalAlpha, boolean isClosing) {
        float blockProgress = Mth.clamp((time - blockDelay) / 0.4f, 0f, 1f);
        if (blockProgress <= 0) return;

        float c1 = 1.70158f; float c3 = c1 + 1;
        float blockScale = 1 + c3 * (float)Math.pow(blockProgress - 1, 3) + c1 * (float)Math.pow(blockProgress - 1, 2); // EaseOutBack

        pose.pushPose();
        pose.translate(WIDTH / 2.0f, HEIGHT / 2.0f - 10, 50);
        float finalScale = 3.0F * blockScale;
        if (isClosing && globalAlpha < 1.0f) finalScale *= (globalAlpha * globalAlpha); // 渐隐缩小

        pose.scale(finalScale, finalScale, finalScale);
        if (finalScale > 0.05f) {
            gfx.renderItem(itemStack, -8, -8);
        }
        pose.popPose();
    }

    private void drawText(GuiGraphics gfx, PoseStack pose, boolean isClosing, boolean isSelected, float globalAlpha) {
        Component name = state.getBlock().getName();
        int nameColor = (isHovered || isSelected) ? COLOR_ACCENT_CYAN : COLOR_TEXT_GRAY;
        var font = Minecraft.getInstance().font;

        pose.pushPose();
        pose.translate(WIDTH / 2.0f, HEIGHT - 25, 100);

        int nameWidth = font.width(name);
        float fontScale = nameWidth > WIDTH - 10 ? (float)(WIDTH - 10) / nameWidth : 1.0f;
        if (isClosing && globalAlpha < 1.0f) fontScale *= (0.8f + 0.2f * globalAlpha);

        pose.scale(fontScale, fontScale, 1f);
        gfx.drawCenteredString(font, name, 0, 0, nameColor);
        pose.popPose();

        // 编号 (Hex ID)
        if (fontScale > 0.8f) {
            pose.pushPose();
            pose.translate(WIDTH / 2.0f, HEIGHT - 12, 100);
            pose.scale(0.5f, 0.5f, 1f);
            String hexId = Integer.toHexString(state.hashCode()).toUpperCase();
            gfx.drawCenteredString(font, "编号: " + (hexId.length() > 6 ? hexId.substring(0, 6) : hexId), 0, 0, 0xFF444444);
            pose.popPose();
        }
    }
}
