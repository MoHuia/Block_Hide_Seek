package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.components.ArknightsCard;
import com.mohuia.block_hide_seek.components.TechBackground;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SSelectBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SelectScreen extends Screen {
    private final List<BlockState> sourceOptions;
    private final List<ArknightsCard> cards = new ArrayList<>();
    private TechBackground techBackground;

    // --- 动画常量 ---
    private static final float EXIT_ANIMATION_DURATION = 1.2f;
    private static final float PHASE_HOVER_END = 0.7f / EXIT_ANIMATION_DURATION;
    private static final float PHASE_BLACK_END = 1.0f / EXIT_ANIMATION_DURATION;

    // --- 状态 ---
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
        this.screenOpenedTime = Util.getMillis();
        this.isClosing = false;
        this.selectedIndex = -1;
        this.lastHoveredIndex = -1;

        // 初始化背景组件
        this.techBackground = new TechBackground(this.width, this.height);

        // 初始化卡片
        int gap = 25;
        int totalWidth = sourceOptions.size() * ArknightsCard.WIDTH + (sourceOptions.size() - 1) * gap;
        int startX = (this.width - totalWidth) / 2;
        int centerY = (this.height - ArknightsCard.HEIGHT) / 2;

        for (int i = 0; i < sourceOptions.size(); i++) {
            float cardDelay = i * 0.15f;
            float blockDelay = cardDelay + 0.4f;
            int targetX = startX + i * (ArknightsCard.WIDTH + gap);
            this.cards.add(new ArknightsCard(sourceOptions.get(i), i, targetX, centerY, cardDelay, blockDelay));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        float timeSeconds = (Util.getMillis() - screenOpenedTime) / 1000f;

        // 1. 处理悬停音效
        handleHoverSound();

        // 2. 计算动画进度
        float closingProgress = 0f;
        if (isClosing) {
            closingProgress = Mth.clamp(((Util.getMillis() - closingStartTime) / 1000f) / EXIT_ANIMATION_DURATION, 0f, 1f);
            if (closingProgress >= 1.0f) {
                super.onClose();
                return;
            }
        }

        // 3. 计算透明度层级
        float uiAlpha = 1.0f;
        float blackOverlayAlpha = 0f;
        float bgLayerAlpha = 1.0f;

        if (isClosing) {
            if (closingProgress < PHASE_HOVER_END) {
                uiAlpha = 1.0f;
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

        // 4. 渲染层级
        techBackground.render(gfx, timeSeconds, bgLayerAlpha); // 渲染背景

        if (uiAlpha > 0.01f) {
            renderTitle(gfx, timeSeconds, uiAlpha);
            renderCards(gfx, mouseX, mouseY, timeSeconds, closingProgress, uiAlpha);
        }

        if (blackOverlayAlpha > 0.01f) {
            RenderSystem.enableBlend();
            gfx.fill(0, 0, width, height, (int)(blackOverlayAlpha * 255) << 24);
            RenderSystem.disableBlend();
        }
    }

    private void renderCards(GuiGraphics gfx, int mouseX, int mouseY, float timeSeconds, float closingProgress, float uiAlpha) {
        if (cards.isEmpty()) {
            gfx.drawCenteredString(this.font, "无数据", this.width / 2, this.height / 2, 0xFF5555);
            return;
        }

        // 更新交互状态
        if (!isClosing) {
            for (ArknightsCard card : cards) card.update(mouseX, mouseY);
        }

        // 渲染未选中的卡片
        for (ArknightsCard card : cards) {
            if (isClosing && card.index == selectedIndex) continue;
            card.render(gfx, timeSeconds, closingProgress, uiAlpha, isClosing, false, width, height);
        }

        // 渲染选中的卡片 (确保在最上层)
        if (isClosing && selectedIndex >= 0 && selectedIndex < cards.size()) {
            cards.get(selectedIndex).render(gfx, timeSeconds, closingProgress, uiAlpha, isClosing, true, width, height);
        }
    }

    private void renderTitle(GuiGraphics gfx, float time, float alpha) {
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        gfx.fill(20, 20, 25, 25, 0xFFFF7F27);
        gfx.drawString(this.font, "系统 // 选择", 30, 20, 0xFFFFFFFF, false);

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(this.width / 2.0, 30, 0);

        float titleAlpha = Mth.clamp(time * 2.0f, 0f, 1f);
        int titleColor = ((int)(titleAlpha * 255) << 24) | 0xFFFFFF;

        pose.scale(1.5F, 1.5F, 1.0F);
        gfx.drawCenteredString(this.font, "选择你的伪装", 0, 0, titleColor);
        pose.popPose();

        String subText = isClosing ? "处理中..." : "- 等待输入 -";
        int subColor = isClosing ? 0xFFFF7F27 : 0xFF00D2FF;
        if (isClosing || (int)(time * 2) % 2 == 0) {
            gfx.drawCenteredString(this.font, subText, this.width / 2, 50, subColor);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void handleHoverSound() {
        if (!isClosing) {
            int currentHover = -1;
            for (ArknightsCard card : cards) {
                if (card.isHovered()) {
                    currentHover = card.index;
                    break;
                }
            }
            if (currentHover != -1 && currentHover != lastHoveredIndex) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.3F));
            }
            lastHoveredIndex = currentHover;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosing) return false;
        for (ArknightsCard card : cards) {
            if (card.isHovered()) {
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
        ClientModelHelper.SizeResult r = ClientModelHelper.getSizeResult(state);
        PacketHandler.INSTANCE.sendToServer(new C2SSelectBlock(state, r.modelW, r.modelH, r.obbX, r.obbY, r.obbZ));

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0F));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F));

        this.isClosing = true;
        this.closingStartTime = Util.getMillis();
        this.selectedIndex = index;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
