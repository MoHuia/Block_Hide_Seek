package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.packet.C2S.C2SSelectBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SelectScreen extends Screen {
    private final List<BlockState> sourceOptions;
    private final List<Card> cards = new ArrayList<>();
    private final List<UIParticle> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    // --- 布局与风格常量 ---
    private static final int CARD_WIDTH = 80;
    private static final int CARD_HEIGHT = 110;
    private static final int GAP = 25;

    // 配色方案
    private static final int COLOR_BG_DARK = 0xFF121212;
    private static final int COLOR_ACCENT_CYAN = 0xFF00D2FF;
    private static final int COLOR_ACCENT_ORANGE = 0xFFFF7F27;
    private static final int COLOR_TEXT_GRAY = 0xFF888888;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    // --- 动画时间配置 ---
    private static final float ENTRY_ANIMATION_DURATION = 0.8f;
    private static final float EXIT_ANIMATION_DURATION = 1.2f;

    // --- 关键时间点 (归一化 0.0-1.0) ---
    // 0.4秒完成选中卡片移动到中间
    private static final float PHASE_MOVE_END = 0.4f / EXIT_ANIMATION_DURATION;

    // 0.7秒时开始界面变黑/UI消失 (稍微停顿一下让玩家看清)
    private static final float PHASE_HOVER_END = 0.7f / EXIT_ANIMATION_DURATION;

    // 1.0秒时完全变黑 (最后0.2秒留给纯黑过渡)
    private static final float PHASE_BLACK_END = 1.0f / EXIT_ANIMATION_DURATION;

    // --- 状态控制 ---
    private long screenOpenedTime;
    private boolean isClosing = false;
    private long closingStartTime = 0;
    private int selectedIndex = -1;

    private int lastHoveredIndex = -1;

    public SelectScreen(List<BlockState> options) {
        super(Component.literal("选择你的伪装"));
        this.sourceOptions = options;
    }

    @Override
    protected void init() {
        this.cards.clear();
        this.particles.clear();
        this.screenOpenedTime = Util.getMillis();
        this.isClosing = false;
        this.selectedIndex = -1;
        this.lastHoveredIndex = -1;

        int totalWidth = sourceOptions.size() * CARD_WIDTH + (sourceOptions.size() - 1) * GAP;
        int startX = (this.width - totalWidth) / 2;
        int centerY = (this.height - CARD_HEIGHT) / 2;

        for (int i = 0; i < sourceOptions.size(); i++) {
            float cardDelay = i * 0.15f;
            float blockDelay = cardDelay + 0.4f;
            int targetX = startX + i * (CARD_WIDTH + GAP);
            this.cards.add(new Card(sourceOptions.get(i), i, targetX, centerY, cardDelay, blockDelay));
        }

        for(int i=0; i<20; i++) {
            addParticle(random.nextInt(width), random.nextInt(height));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        float timeSeconds = (Util.getMillis() - screenOpenedTime) / 1000f;

        // --- 悬停音效逻辑 ---
        if (!isClosing) {
            int currentHover = -1;
            for (Card card : cards) {
                if (card.isHovered) {
                    currentHover = card.index;
                    break;
                }
            }
            if (currentHover != -1 && currentHover != lastHoveredIndex) {
                // [注意] UI_BUTTON_CLICK 这里保留了 .value()，因为之前报错提示它是 Reference
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.3F));
            }
            lastHoveredIndex = currentHover;
        }

        float closingProgress = 0f;
        if (isClosing) {
            float closingElapsed = (Util.getMillis() - closingStartTime) / 1000f;
            closingProgress = Mth.clamp(closingElapsed / EXIT_ANIMATION_DURATION, 0f, 1f);

            if (closingProgress >= 1.0f) {
                super.onClose();
                return;
            }
        }

        float uiAlpha = 1.0f;
        float blackOverlayAlpha = 0f;
        float bgLayerAlpha = 1.0f;

        if (isClosing) {
            if (closingProgress < PHASE_HOVER_END) {
                uiAlpha = 1.0f;
                blackOverlayAlpha = 0f;
            } else if (closingProgress < PHASE_BLACK_END) {
                float t = (closingProgress - PHASE_HOVER_END) / (PHASE_BLACK_END - PHASE_HOVER_END);
                uiAlpha = 1.0f - t;
                blackOverlayAlpha = t;
            } else {
                float t = (closingProgress - PHASE_BLACK_END) / (1.0f - PHASE_BLACK_END);
                uiAlpha = 0f;
                bgLayerAlpha = 1.0f - t;
                blackOverlayAlpha = 1.0f - t;
            }
        }

        renderTechBackground(gfx, timeSeconds, bgLayerAlpha);
        updateAndRenderParticles(gfx, timeSeconds, uiAlpha);

        if (uiAlpha > 0.01f) {
            PoseStack pose = gfx.pose();
            pose.pushPose();
            RenderSystem.setShaderColor(1f, 1f, 1f, uiAlpha);
            drawStyledTitle(gfx, timeSeconds);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            pose.popPose();
        }

        if (cards.isEmpty()) {
            gfx.drawCenteredString(this.font, "无数据", this.width / 2, this.height / 2, 0xFF5555);
            return;
        }

        if (uiAlpha > 0.01f) {
            for (Card card : cards) {
                if (isClosing && card.index == selectedIndex) continue;

                if (isClosing) updateCardClosingLogic(card, closingProgress);
                else updateCardEntryLogic(card, timeSeconds);

                card.updateInteraction(mouseX, mouseY);
                renderArknightsCard(gfx, card, timeSeconds, closingProgress, uiAlpha);
            }

            if (isClosing && selectedIndex >= 0 && selectedIndex < cards.size()) {
                Card target = cards.get(selectedIndex);
                updateCardClosingLogic(target, closingProgress);
                renderArknightsCard(gfx, target, timeSeconds, closingProgress, uiAlpha);
            }
        }

        if (bgLayerAlpha > 0.01f) {
            RenderSystem.setShaderColor(1f, 1f, 1f, bgLayerAlpha);
            gfx.fillGradient(0, 0, width, 40, 0xCC000000, 0x00000000);
            gfx.fillGradient(0, height - 40, width, height, 0x00000000, 0xCC000000);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        if (blackOverlayAlpha > 0.01f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int alpha = (int)(blackOverlayAlpha * 255);
            gfx.fill(0, 0, width, height, alpha << 24);
            RenderSystem.disableBlend();
        }
    }

    private void renderTechBackground(GuiGraphics gfx, float time, float alpha) {
        if (alpha <= 0.01f) return;

        int alphaInt = (int)(alpha * 255);
        int bgCol = (alphaInt << 24) | (COLOR_BG_DARK & 0x00FFFFFF);

        gfx.fill(0, 0, width, height, bgCol);

        PoseStack pose = gfx.pose();
        pose.pushPose();

        float gridSize = 40;
        float offset = (time * 15) % gridSize;
        int gridBaseAlpha = 0x1F;
        int finalGridAlpha = (int)(gridBaseAlpha * alpha);
        int gridColor = (finalGridAlpha << 24) | 0xFFFFFF;

        for (float x = -offset; x < width; x += gridSize) {
            if (x >= 0) gfx.fill((int)x, 0, (int)x + 1, height, gridColor);
        }
        for (float y = offset; y < height; y += gridSize) {
            gfx.fill(0, (int)y, width, (int)y + 1, gridColor);
        }

        pose.popPose();
    }

    private void addParticle(double x, double y) {
        float life = 2.0f + random.nextFloat() * 3.0f;
        float size = 2.0f + random.nextFloat() * 4.0f;
        float speedY = -10.0f - random.nextFloat() * 20.0f;
        particles.add(new UIParticle(x, y, 0, speedY, size, life));
    }

    private void updateAndRenderParticles(GuiGraphics gfx, float time, float globalAlpha) {
        if (!isClosing && random.nextFloat() < 0.15f) {
            addParticle(random.nextInt(width), height + 10);
        }

        Iterator<UIParticle> it = particles.iterator();
        while (it.hasNext()) {
            UIParticle p = it.next();
            p.update(0.05f);
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

    private void renderArknightsCard(GuiGraphics gfx, Card card, float timeSeconds, float closingProgress, float globalAlpha) {
        // --- 1. 发牌音效逻辑 (保持不变) ---
        if (timeSeconds >= card.cardDelay && !card.playedEntrySound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.8F));
            card.playedEntrySound = true;
        }

        float slideProgress = Mth.clamp((timeSeconds - card.cardDelay) / ENTRY_ANIMATION_DURATION, 0f, 1f);
        if (slideProgress <= 0) return;

        boolean isSelected = (card.index == selectedIndex);
        float finalAlpha = globalAlpha;
        // 阈值稍微调低一点，保证缩放到很小的时候还能看到一点点，避免过早截断
        if (finalAlpha <= 0.001f) return;

        RenderSystem.setShaderColor(1f, 1f, 1f, finalAlpha);

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(card.currentX, card.currentY, 0);
        pose.translate(CARD_WIDTH / 2.0f, CARD_HEIGHT / 2.0f, 0);

        // --- 旋转与缩放 ---
        float hoverScale = (card.isHovered && !isClosing) ? 1.05f : 1.0f;

        if (isClosing && isSelected) {
            float moveDuration = PHASE_MOVE_END;
            float moveProgress = Mth.clamp(closingProgress / moveDuration, 0f, 1f);
            float scaleEase = easeOutCubic(moveProgress);
            hoverScale = Mth.lerp(scaleEase, 1.05f, 2.0f);

            // [新增视觉优化]：在最后渐隐阶段，让卡片整体稍微放大一点点(扩散效果)，配合方块缩小
            if (globalAlpha < 1.0f) {
                // 当 alpha 从 1 降到 0 时，额外放大 0.2 倍
                hoverScale += (1.0f - globalAlpha) * 0.2f;
            }
        } else if (!isClosing && card.isHovered) {
            float rotateAngle = (float)Math.sin(timeSeconds * 3.0) * 1.5f;
            pose.mulPose(Axis.ZP.rotationDegrees(rotateAngle));
        }

        card.renderScale = Mth.lerp(0.2f, card.renderScale, hoverScale);
        pose.scale(card.renderScale, card.renderScale, 1f);

        pose.translate(-CARD_WIDTH / 2.0f, -CARD_HEIGHT / 2.0f, 0);

        // --- 绘制底板 (保持不变) ---
        int bgColor = (card.isHovered || isSelected) ? 0xE62A2A2A : 0xE61A1A1A;
        int borderColor = (card.isHovered || isSelected) ? COLOR_ACCENT_CYAN : 0xFF444444;

        if (isClosing && isSelected) {
            borderColor = COLOR_WHITE;
            if (closingProgress < 0.1f) {
                bgColor = 0xFF404040;
            }
        }

        RenderSystem.enableBlend();
        // 这里的 fill 会自动应用 ShaderColor 的 alpha，所以背景渐隐是正常的
        gfx.fill(0, 0, CARD_WIDTH, CARD_HEIGHT, bgColor);

        gfx.fill(0, 0, CARD_WIDTH, 1, borderColor);
        gfx.fill(0, CARD_HEIGHT - 1, CARD_WIDTH, CARD_HEIGHT, borderColor);

        int cornerLen = 10;
        int cornerColor = (card.isHovered || isSelected) ? COLOR_WHITE : borderColor;

        gfx.fill(0, 0, 1, cornerLen, cornerColor);
        gfx.fill(CARD_WIDTH - 1, 0, CARD_WIDTH, cornerLen, cornerColor);
        gfx.fill(0, CARD_HEIGHT - cornerLen, 1, CARD_HEIGHT, cornerColor);
        gfx.fill(CARD_WIDTH - 1, CARD_HEIGHT - cornerLen, CARD_WIDTH, CARD_HEIGHT, cornerColor);

        // --- 扫描线 (保持不变) ---
        if ((card.isHovered && !isClosing) || (isClosing && isSelected)) {
            float scanSpeed = 0.8f;
            float scanPos = (timeSeconds * scanSpeed) % 2.0f;
            if (scanPos > 1.0f) scanPos = 2.0f - scanPos;

            int scanY = (int) (scanPos * CARD_HEIGHT);
            int scanColor = 0x4000D2FF;
            gfx.fill(1, scanY, CARD_WIDTH-1, scanY + 2, scanColor);
            gfx.fill(0, 0, 18, 18, COLOR_ACCENT_CYAN);
        } else {
            gfx.fill(0, 0, 18, 18, 0xFF333333);
        }

        int indexColor = (card.isHovered || isSelected) ? 0xFF000000 : 0xFFAAAAAA;
        gfx.drawCenteredString(this.font, String.valueOf(card.index + 1), 9, 5, indexColor);

        // --- [核心修改] 物品渲染 ---
        float blockProgress = Mth.clamp((timeSeconds - card.blockDelay) / 0.4f, 0f, 1f);
        float blockScale = easeOutBack(blockProgress);

        if (blockProgress > 0) {
            pose.pushPose();
            pose.translate(CARD_WIDTH / 2.0f, CARD_HEIGHT / 2.0f - 10, 50);

            float finalScale = 3.0F * blockScale;

            // [新增逻辑] 如果正在渐隐关闭 (alpha < 1.0)，强制缩小物品
            // 这样物品会随着背景变淡而变小，直到消失，解决了 renderItem 不透明的问题
            if (isClosing && globalAlpha < 1.0f) {
                // 使用平方插值让缩小过程在视觉上更平滑 (alpha=0.5时 scale=0.25)
                float fadeFactor = globalAlpha * globalAlpha;
                finalScale *= fadeFactor;
            }

            pose.scale(finalScale, finalScale, finalScale);

            // 只有当比例足够大时才渲染，避免渲染极小的噪点
            if (finalScale > 0.05f) {
                gfx.renderItem(card.itemStack, -8, -8);
            }
            pose.popPose();
        }

        // 文字
        Component name = card.state.getBlock().getName();
        // 文字颜色通常能响应 ShaderColor 的 Alpha，所以这里不需要改 scale
        int nameColor = (card.isHovered || isSelected) ? COLOR_ACCENT_CYAN : COLOR_TEXT_GRAY;

        pose.pushPose();
        pose.translate(CARD_WIDTH / 2.0f, CARD_HEIGHT - 25, 100);

        int nameWidth = this.font.width(name);
        float fontScale = 1.0f;
        if (nameWidth > CARD_WIDTH - 10) {
            fontScale = (float)(CARD_WIDTH - 10) / nameWidth;
        }
        pose.scale(fontScale, fontScale, 1f);

        // 可以在这里也加上一点渐隐时的文字缩放，更加统一，可选
        if (isClosing && globalAlpha < 1.0f) {
            // 让文字也稍微变小一点点
            float textFadeScale = 0.8f + (0.2f * globalAlpha);
            pose.scale(textFadeScale, textFadeScale, 1f);
        }

        gfx.drawCenteredString(this.font, name, 0, 0, nameColor);
        pose.popPose();

        if (fontScale > 0.8f) {
            pose.pushPose();
            pose.translate(CARD_WIDTH / 2.0f, CARD_HEIGHT - 12, 100);
            pose.scale(0.5f, 0.5f, 1f);
            gfx.drawCenteredString(this.font, "编号: " + Integer.toHexString(card.state.hashCode()).toUpperCase().substring(0, 6), 0, 0, 0xFF444444);
            pose.popPose();
        }

        RenderSystem.disableBlend();
        pose.popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }


    private void drawStyledTitle(GuiGraphics gfx, float time) {
        gfx.fill(20, 20, 25, 25, COLOR_ACCENT_ORANGE);
        gfx.drawString(this.font, "系统 // 选择", 30, 20, COLOR_WHITE, false);

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(this.width / 2.0, 30, 0);

        float titleAlpha = Mth.clamp(time * 2.0f, 0f, 1f);
        int titleColor = ((int)(titleAlpha * 255) << 24) | 0xFFFFFF;

        pose.scale(1.5F, 1.5F, 1.0F);
        gfx.drawCenteredString(this.font, "选择你的伪装", 0, 0, titleColor);
        pose.popPose();

        if (!isClosing && (int)(time * 2) % 2 == 0) {
            gfx.drawCenteredString(this.font, "- 等待输入 -", this.width / 2, 50, COLOR_ACCENT_CYAN);
        } else if (isClosing) {
            gfx.drawCenteredString(this.font, "处理中...", this.width / 2, 50, COLOR_ACCENT_ORANGE);
        }
    }

    private void updateCardEntryLogic(Card card, float timeSeconds) {
        float slideProgress = Mth.clamp((timeSeconds - card.cardDelay) / ENTRY_ANIMATION_DURATION, 0f, 1f);
        float slideEase = easeOutExpo(slideProgress);

        float startX = -150;
        float startY = this.height + 100;

        card.currentX = Mth.lerp(slideEase, startX, card.targetX);
        card.currentY = Mth.lerp(slideEase, startY, card.targetY);
    }

    private void updateCardClosingLogic(Card card, float closingProgress) {
        if (card.index == selectedIndex) {
            float moveDuration = PHASE_MOVE_END;
            float moveProgress = Mth.clamp(closingProgress / moveDuration, 0f, 1f);
            float moveEase = easeInOutCubic(moveProgress);

            float screenCenterX = (this.width - CARD_WIDTH) / 2.0f;
            float screenCenterY = (this.height - CARD_HEIGHT) / 2.0f;

            card.currentX = Mth.lerp(moveEase, card.targetX, screenCenterX);
            card.currentY = Mth.lerp(moveEase, card.targetY, screenCenterY);
        } else {
            float dropDuration = 0.3f / EXIT_ANIMATION_DURATION;
            float dropProgress = Mth.clamp(closingProgress / dropDuration, 0f, 1f);
            float dropEase = easeInBack(dropProgress);
            card.currentX = card.targetX;
            card.currentY = card.targetY + dropEase * (this.height + 100);
        }
    }

    private float easeOutExpo(float x) {
        return x == 1 ? 1 : 1 - (float)Math.pow(2, -10 * x);
    }

    private float easeInBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return c3 * x * x * x - c1 * x * x;
    }

    private float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float)Math.pow(x - 1, 3) + c1 * (float)Math.pow(x - 1, 2);
    }

    private float easeOutCubic(float x) {
        return 1 - (float)Math.pow(1 - x, 3);
    }

    private float easeInOutCubic(float x) {
        return x < 0.5 ? 4 * x * x * x : 1 - (float)Math.pow(-2 * x + 2, 3) / 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosing) return false;

        for (Card card : cards) {
            if (card.isHovered) {
                selectBlock(card.state, card.index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isClosing) return false;

        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int idx = keyCode - GLFW.GLFW_KEY_1;
            if (idx >= 0 && idx < cards.size()) {
                selectBlock(cards.get(idx).state, idx);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectBlock(BlockState state, int index) {
        // 1) 一次计算拿到：真实尺寸(OBB) + 策略尺寸(玩家碰撞)
        ClientModelHelper.SizeResult r = ClientModelHelper.getSizeResult(state);

        // 2) 发送带有：伪装方块 + 玩家尺寸(modelW/H) + OBB真实尺寸(x/y/z)
        PacketHandler.INSTANCE.sendToServer(
                new C2SSelectBlock(state, r.modelW, r.modelH, r.obbX, r.obbY, r.obbZ)
        );

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0F));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F));

        this.isClosing = true;
        this.closingStartTime = Util.getMillis();
        this.selectedIndex = index;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= 内部类 =================

    private static class Card {
        final BlockState state;
        final ItemStack itemStack;
        final int index;
        final int targetX, targetY;
        final float cardDelay;
        final float blockDelay;

        float currentX, currentY;
        boolean isHovered;
        float renderScale = 1.0f;

        boolean playedEntrySound = false;

        public Card(BlockState state, int index, int targetX, int targetY, float cardDelay, float blockDelay) {
            this.state = state;
            this.itemStack = new ItemStack(state.getBlock());
            this.index = index;
            this.targetX = targetX;
            this.targetY = targetY;
            this.cardDelay = cardDelay;
            this.blockDelay = blockDelay;
        }

        public void updateInteraction(double mouseX, double mouseY) {
            this.isHovered = mouseX >= currentX && mouseX < currentX + CARD_WIDTH &&
                    mouseY >= currentY && mouseY < currentY + CARD_HEIGHT;
        }
    }

    private static class UIParticle {
        double x, y;
        double speedX, speedY;
        float size;
        float life, maxLife;

        public UIParticle(double x, double y, double speedX, double speedY, float size, float maxLife) {
            this.x = x;
            this.y = y;
            this.speedX = speedX;
            this.speedY = speedY;
            this.size = size;
            this.life = maxLife;
            this.maxLife = maxLife;
        }

        public void update(float dt) {
            x += speedX * dt;
            y += speedY * dt;
            life -= dt;
        }

        public boolean isAlive() {
            return life > 0;
        }

        public float getAlpha() {
            return Mth.clamp(life / maxLife, 0f, 1f) * 0.5f;
        }
    }
}
