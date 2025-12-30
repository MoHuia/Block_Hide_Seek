package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SelectScreen extends Screen {
    private final List<BlockState> options;

    // --- 布局配置 ---
    private static final int CARD_WIDTH = 80;   // 卡片宽度
    private static final int CARD_HEIGHT = 100; // 卡片高度
    private static final int GAP = 20;          // 卡片间距

    // 动画相关
    private float time = 0;

    public SelectScreen(List<BlockState> options) {
        super(Component.literal("选择伪装"));
        this.options = options;
    }

    @Override
    protected void init() {
        // 不需要初始化按钮，全部自定义绘制
    }

    @Override
    public void tick() {
        super.tick();
        time += 0.1f; // 呼吸效果变量
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 1. 绘制全屏深色渐变背景
        this.renderBackground(gfx);
        gfx.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0000000);

        // 2. 绘制大标题 (上移)
        drawTitle(gfx);

        if (options.isEmpty()) {
            gfx.drawCenteredString(this.font, "没有可用的伪装...", this.width / 2, this.height / 2, 0xFF5555);
            return;
        }

        // --- 计算整体布局 ---
        int totalWidth = options.size() * CARD_WIDTH + (options.size() - 1) * GAP;
        int startX = (this.width - totalWidth) / 2;

        // 【核心修复】计算卡片起始 Y 坐标
        // 1. 尝试居中：(this.height - CARD_HEIGHT) / 2
        // 2. 但为了防止标题重叠，强制它至少要在 Y=80 以下
        // 3. 再加 10px 的视觉偏置，让重心稍微靠下一点点，更好看
        int centerY = (this.height - CARD_HEIGHT) / 2 + 10;
        int startY = Math.max(80, centerY);

        // --- 3. 循环绘制卡片 ---
        for (int i = 0; i < options.size(); i++) {
            BlockState state = options.get(i);
            int x = startX + i * (CARD_WIDTH + GAP);
            int y = startY;

            // 检测悬停
            boolean isHovered = mouseX >= x && mouseX < x + CARD_WIDTH &&
                    mouseY >= y && mouseY < y + CARD_HEIGHT;

            renderCard(gfx, x, y, i, state, isHovered);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawTitle(GuiGraphics gfx) {
        PoseStack pose = gfx.pose();
        pose.pushPose();

        // 【修复】标题上移到 Y=25 (之前是40)
        pose.translate(this.width / 2.0, 25, 0);
        pose.scale(2.0F, 2.0F, 2.0F);

        Component title = Component.literal("选择你的伪装");
        gfx.drawCenteredString(this.font, title, 0, 0, 0xFFFFAA00); // 金色

        pose.popPose();

        // 【修复】副标题上移到 Y=50 (之前是70)
        gfx.drawCenteredString(this.font, "点击卡片 或 按下数字键", this.width / 2, 50, 0xFFAAAAAA);
    }

    private void renderCard(GuiGraphics gfx, int x, int y, int index, BlockState state, boolean isHovered) {
        PoseStack pose = gfx.pose();

        // --- 动画变换 ---
        pose.pushPose();

        float scale = isHovered ? 1.05F : 1.0F;
        if (isHovered) {
            float centerX = x + CARD_WIDTH / 2.0f;
            float centerY = y + CARD_HEIGHT / 2.0f;
            pose.translate(centerX, centerY, 0);
            pose.scale(scale, scale, 1.0f);
            pose.translate(-centerX, -centerY, 0);
        }

        // 1. 背景板
        int bgColor = isHovered ? 0xFF404040 : 0xFF202020;
        int borderColor = isHovered ? 0xFFFFAA00 : 0xFF505050;

        gfx.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor);
        // 边框
        gfx.fill(x, y, x + CARD_WIDTH, y + 1, borderColor); // Top
        gfx.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor); // Bottom
        gfx.fill(x, y, x + 1, y + CARD_HEIGHT, borderColor); // Left
        gfx.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor); // Right

        // 2. 绘制巨大的方块图标
        ItemStack stack = new ItemStack(state.getBlock());
        pose.pushPose();
        {
            float itemX = x + CARD_WIDTH / 2.0f;
            // 【修复】图标再稍微往上挪一点 (-15)，给下面的字腾出空间
            float itemY = y + CARD_HEIGHT / 2.0f - 15;

            pose.translate(itemX, itemY, 0);
            pose.scale(3.0F, 3.0F, 3.0F); // 3倍放大
            pose.translate(-8, -8, 0);    // 修正中心

            gfx.renderItem(stack, 0, 0);
        }
        pose.popPose();

        // 3. 绘制方块名称
        Component name = state.getBlock().getName();
        int nameColor = isHovered ? 0xFFFFFFFF : 0xFFAAAAAA;

        pose.pushPose();
        float textX = x + CARD_WIDTH / 2.0f;
        // 文字位置固定在底部
        float textY = y + CARD_HEIGHT - 20;
        pose.translate(textX, textY, 0);

        // 如果名字太长，自动缩小字体
        int nameWidth = this.font.width(name);
        if (nameWidth > CARD_WIDTH - 10) {
            float fontScale = (float)(CARD_WIDTH - 10) / nameWidth;
            pose.scale(fontScale, fontScale, 1.0f);
        }

        gfx.drawCenteredString(this.font, name, 0, 0, nameColor);
        pose.popPose();

        // 4. 左上角数字角标
        gfx.fill(x, y, x + 14, y + 14, borderColor);
        gfx.drawCenteredString(this.font, String.valueOf(index + 1), x + 7, y + 3, 0xFF000000);

        pose.popPose(); // 结束动画变换
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (options.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int totalWidth = options.size() * CARD_WIDTH + (options.size() - 1) * GAP;
        int startX = (this.width - totalWidth) / 2;
        int centerY = (this.height - CARD_HEIGHT) / 2 + 10;
        int startY = Math.max(80, centerY); // 保持和 render 里的计算逻辑一致

        for (int i = 0; i < options.size(); i++) {
            int x = startX + i * (CARD_WIDTH + GAP);
            int y = startY;

            if (mouseX >= x && mouseX < x + CARD_WIDTH &&
                    mouseY >= y && mouseY < y + CARD_HEIGHT) {

                selectBlock(options.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index >= 0 && index < options.size()) {
                selectBlock(options.get(index));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectBlock(BlockState state) {
        PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SSelectBlock(state));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
