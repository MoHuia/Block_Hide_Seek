package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ConfigScreen extends Screen {
    private final List<BlockState> whitelist;

    // 窗口尺寸
    private static final int GUI_WIDTH = 196;
    private static final int GUI_HEIGHT = 230;

    // 布局常量
    private int guiLeft;
    private int guiTop;

    // 滚动相关变量
    private float scrollOffs; // 0.0f ~ 1.0f (0% 到 100%)
    private boolean isScrolling;
    private static final int VISIBLE_ROWS = 4; // 可见行数
    private static final int ROW_HEIGHT = 18;  // 每行高度

    public ConfigScreen(List<BlockState> whitelist) {
        super(Component.literal("配置躲藏方块"));
        this.whitelist = whitelist;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
    }

    // --- 核心逻辑：滚动与输入 ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { // 1.20.1 改为 delta (第三个参数)
        // 只有在白名单区域才允许滚动
        if (!needsScrollBars()) return false;

        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;
        // 如果内容很多，每一格滚轮滚动的比例
        float step = 1.0f / ((float) (this.getTotalRows() - VISIBLE_ROWS) + 0.5f);

        // delta > 0 是向上滚，offset 变小；delta < 0 是向下滚
        // 注意：不同版本 delta 正负方向可能不同，通常向上是正
        this.scrollOffs = Mth.clamp(this.scrollOffs - (float)delta * step, 0.0f, 1.0f);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling) {
            int scrollBarTop = guiTop + 40;
            int scrollBarBottom = scrollBarTop + VISIBLE_ROWS * ROW_HEIGHT;
            float trackHeight = (float)(scrollBarBottom - scrollBarTop);

            // 计算鼠标移动的比例
            this.scrollOffs = ((float)mouseY - (float)scrollBarTop - 7.5f) / (trackHeight - 15.0f);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0f, 1.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 处理滚动条点击
        int scrollBarX = guiLeft + 17 + 9 * 18 + 4;
        int scrollBarTop = guiTop + 40;
        int scrollBarHeight = VISIBLE_ROWS * ROW_HEIGHT;

        if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                mouseY >= scrollBarTop && mouseY <= scrollBarTop + scrollBarHeight) {
            this.isScrolling = needsScrollBars();
            return true;
        }

        // 2. 处理白名单区域点击 (考虑滚动偏移)
        int listX = guiLeft + 17;
        int listY = guiTop + 40;

        // 检查是否点击在可视区域内
        if (mouseX >= listX && mouseX < listX + 9 * 18 && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * 18) {
            int scrollPixel = getScrollPixels();
            // 还原鼠标点击对应的真实Y坐标
            double relativeY = mouseY - listY + scrollPixel;

            int col = (int) (mouseX - listX) / 18;
            int row = (int) (relativeY / 18);

            int index = row * 9 + col;
            if (index >= 0 && index < whitelist.size()) {
                PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SToggleWhitelist(whitelist.get(index)));
                playClickSound();
                return true;
            }
        }

        // 3. 处理背包点击 (这部分不需要滚动)
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            int invStartX = guiLeft + 17;
            int invStartY = guiTop + 140;

            // 主背包
            for (int i = 9; i < 36; i++) {
                int col = (i % 9);
                int row = (i / 9) - 1;
                int x = invStartX + col * 18;
                int y = invStartY + row * 18;
                if (isHovering(x, y, mouseX, mouseY)) {
                    tryAddBlock(player.getInventory().getItem(i));
                    return true;
                }
            }
            // 快捷栏
            int hotbarY = invStartY + 58;
            for (int i = 0; i < 9; i++) {
                int x = invStartX + i * 18;
                if (isHovering(x, hotbarY, mouseX, mouseY)) {
                    tryAddBlock(player.getInventory().getItem(i));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isScrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- 渲染逻辑 ---

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        // 1. 绘制主窗口背景
        gfx.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, 0xFF000000);
        gfx.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF303030);

        // 2. 标题
        gfx.drawCenteredString(this.font, "躲猫猫 - 方块白名单配置", this.width / 2, guiTop + 8, 0xFFFFFF);
        gfx.drawString(this.font, "已添加列表 (可滚动):", guiLeft + 10, guiTop + 25, 0xAAAAAA, false);
        gfx.drawString(this.font, "你的背包 (点击添加):", guiLeft + 10, guiTop + 125, 0xAAAAAA, false);

        // ==========================================
        //         区域 A: 白名单 (带滚动)
        // ==========================================
        int listX = guiLeft + 17;
        int listY = guiTop + 40;
        int listWidth = 9 * 18;
        int listHeight = VISIBLE_ROWS * 18;

        // 2.1 绘制列表背景框 (黑底)
        gfx.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, 0xFF101010);

        // 2.2 开启剪裁 (Scissor) - 只在列表框内渲染
        // 注意：enableScissor 使用的是窗口坐标，需要一定的换算，但在 1.20+ GuiGraphics 封装较好
        gfx.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int scrollPixel = getScrollPixels();
        int startRow = scrollPixel / 18;
        // 多画一行以防滚动一半的情况
        int endRow = (scrollPixel + listHeight) / 18 + 1;

        for (int i = 0; i < whitelist.size(); i++) {
            int col = i % 9;
            int row = i / 9;

            // 性能优化：只绘制视野内的
            if (row < startRow || row > endRow) continue;

            int x = listX + col * 18;
            int y = listY + row * 18 - scrollPixel; // 减去滚动偏移

            renderSlotBox(gfx, x, y);

            ItemStack stack = new ItemStack(whitelist.get(i).getBlock());
            gfx.renderItem(stack, x + 1, y + 1);

            // 悬停高亮 (仅当鼠标也在可视范围内时)
            if (mouseX >= listX && mouseX < listX + listWidth &&
                    mouseY >= listY && mouseY < listY + listHeight &&
                    isHovering(x, y, mouseX, mouseY)) {
                gfx.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
            }
        }

        // 2.3 关闭剪裁
        gfx.disableScissor();

        // 2.4 绘制滚动条
        drawScrollBar(gfx, listX + listWidth + 4, listY, listHeight);

        // ==========================================
        //         区域 B: 玩家背包 (固定)
        // ==========================================
        renderInventory(gfx, mouseX, mouseY);

        // ==========================================
        //         最后绘制 Tooltips (防止被剪裁)
        // ==========================================
        renderListTooltips(gfx, mouseX, mouseY, listX, listY, listWidth, listHeight, scrollPixel);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderInventory(GuiGraphics gfx, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int invStartX = guiLeft + 17;
        int invStartY = guiTop + 140;

        // 主背包
        for (int i = 9; i < 36; i++) {
            int col = (i % 9);
            int row = (i / 9) - 1;
            int x = invStartX + col * 18;
            int y = invStartY + row * 18;

            renderSlotBox(gfx, x, y);
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                gfx.renderItem(stack, x + 1, y + 1);
                if (stack.getItem() instanceof BlockItem && isHovering(x, y, mouseX, mouseY)) {
                    gfx.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
                    gfx.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }
        }
        // 快捷栏
        int hotbarY = invStartY + 58;
        for (int i = 0; i < 9; i++) {
            int x = invStartX + i * 18;
            renderSlotBox(gfx, x, hotbarY);
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                gfx.renderItem(stack, x + 1, hotbarY + 1);
                if (stack.getItem() instanceof BlockItem && isHovering(x, hotbarY, mouseX, mouseY)) {
                    gfx.fill(x, hotbarY, x + 18, hotbarY + 18, 0x80FFFFFF);
                    gfx.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }
        }
    }

    // 单独分离出 Tooltip 渲染，保证它在所有图层最上面，且不被 enableScissor 切掉
    private void renderListTooltips(GuiGraphics gfx, int mouseX, int mouseY, int listX, int listY, int w, int h, int scrollPixel) {
        // 必须在可视区域内才显示 Tooltip
        if (mouseX < listX || mouseX >= listX + w || mouseY < listY || mouseY >= listY + h) return;

        int relativeY = mouseY - listY + scrollPixel;
        int col = (mouseX - listX) / 18;
        int row = relativeY / 18;
        int index = row * 9 + col;

        if (index >= 0 && index < whitelist.size()) {
            ItemStack stack = new ItemStack(whitelist.get(index).getBlock());
            gfx.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    private void drawScrollBar(GuiGraphics gfx, int x, int y, int height) {
        // 滚动条槽背景
        gfx.fill(x, y, x + 6, y + height, 0xFF000000);

        if (!needsScrollBars()) return;

        // 计算滑块高度
        int totalHeight = getTotalRows() * ROW_HEIGHT;
        int visibleHeight = VISIBLE_ROWS * ROW_HEIGHT;
        // 滑块大小 (比例)
        int barHeight = (int) ((float) (visibleHeight * visibleHeight) / (float) totalHeight);
        barHeight = Mth.clamp(barHeight, 32, height - 8);

        // 计算滑块位置
        int barTop = (int) (this.scrollOffs * (float) (height - barHeight)) + y;

        // 绘制滑块 (灰色)
        gfx.fill(x, barTop, x + 6, barTop + barHeight, 0xFF808080);
        gfx.fill(x, barTop, x + 5, barTop + barHeight - 1, 0xFFC0C0C0);
    }

    // --- 辅助方法 ---

    private int getTotalRows() {
        return (whitelist.size() + 8) / 9; // 向上取整
    }

    private boolean needsScrollBars() {
        return getTotalRows() > VISIBLE_ROWS;
    }

    private int getScrollPixels() {
        if (!needsScrollBars()) return 0;
        int totalHeight = getTotalRows() * ROW_HEIGHT;
        int visibleHeight = VISIBLE_ROWS * ROW_HEIGHT;
        return (int) ((totalHeight - visibleHeight) * this.scrollOffs);
    }

    private void renderSlotBox(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, 0xFF202020);
        gfx.fill(x, y, x + 18, y + 1, 0xFF101010);
        gfx.fill(x, y, x + 1, y + 18, 0xFF101010);
        gfx.fill(x, y + 17, x + 18, y + 18, 0xFF505050);
        gfx.fill(x + 17, y, x + 18, y + 18, 0xFF505050);
    }

    private boolean isHovering(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private void tryAddBlock(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SToggleWhitelist(blockItem.getBlock().defaultBlockState()));
            playClickSound();
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
