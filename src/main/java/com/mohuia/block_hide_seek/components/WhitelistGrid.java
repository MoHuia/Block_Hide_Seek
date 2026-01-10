package com.mohuia.block_hide_seek.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class WhitelistGrid {
    private final int x;
    private final int y;
    private final List<BlockState> whitelist = new ArrayList<>();
    private final Consumer<BlockState> onBlockClicked;

    // 常量
    private static final int COLS = 12;
    private static final int VISIBLE_ROWS = 4;
    private static final int ROW_HEIGHT = 18;
    private static final int WIDTH = COLS * 18;
    private static final int HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;

    // 状态
    private float scrollOffs;
    private boolean isScrolling;

    public WhitelistGrid(int x, int y, List<BlockState> initialList, Consumer<BlockState> onBlockClicked) {
        this.x = x;
        this.y = y;
        this.onBlockClicked = onBlockClicked;
        this.updateList(initialList);
    }

    public void updateList(List<BlockState> newList) {
        this.whitelist.clear();
        this.whitelist.addAll(newList);
    }

    public void render(GuiGraphics gfx, int mouseX, int mouseY) {
        // 背景
        gfx.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xFF151515);

        // 剪裁区域渲染内容
        gfx.enableScissor(x, y, x + WIDTH, y + HEIGHT);
        int scrollPixel = getScrollPixels();
        int startRow = scrollPixel / ROW_HEIGHT;
        int endRow = (scrollPixel + HEIGHT) / ROW_HEIGHT + 1;

        for (int i = 0; i < whitelist.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            if (row < startRow || row > endRow) continue;

            int drawX = x + col * 18;
            int drawY = y + row * 18 - scrollPixel;

            renderSlotBox(gfx, drawX, drawY);
            ItemStack stack = new ItemStack(whitelist.get(i).getBlock());
            gfx.renderItem(stack, drawX + 1, drawY + 1);

            if (isHovering(drawX, drawY, mouseX, mouseY)) {
                gfx.fill(drawX, drawY, drawX + 18, drawY + 18, 0x80FFFFFF);
            }
        }
        gfx.disableScissor();

        // 滚动条
        drawScrollBar(gfx, x + WIDTH + 6, y, HEIGHT);
    }

    public void renderTooltips(GuiGraphics gfx, int mouseX, int mouseY) {
        if (!isHovering(x, y, mouseX, mouseY, WIDTH, HEIGHT)) return;

        int relativeY = mouseY - y + getScrollPixels();
        int col = (mouseX - x) / 18;
        int row = relativeY / 18;
        int index = row * COLS + col;

        if (index >= 0 && index < whitelist.size()) {
            ItemStack stack = new ItemStack(whitelist.get(index).getBlock());
            gfx.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 滚动条点击
        int scrollBarX = x + WIDTH + 6;
        if (isHovering(scrollBarX, y, mouseX, mouseY, 6, HEIGHT)) {
            this.isScrolling = needsScrollBars();
            return true;
        }

        // 2. 列表点击
        if (isHovering(x, y, mouseX, mouseY, WIDTH, HEIGHT)) {
            int scrollPixel = getScrollPixels();
            double relativeY = mouseY - y + scrollPixel;
            int col = (int) (mouseX - x) / 18;
            int row = (int) (relativeY / 18);
            int index = row * COLS + col;

            if (index >= 0 && index < whitelist.size()) {
                onBlockClicked.accept(whitelist.get(index));
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseY, double startY) {
        if (this.isScrolling) {
            float trackHeight = (float) HEIGHT;
            this.scrollOffs = ((float) mouseY - (float) startY - 7.5f) / (trackHeight - 15.0f);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0f, 1.0f);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double delta) {
        if (!needsScrollBars()) return false;
        float step = 1.0f / ((float) (getTotalRows() - VISIBLE_ROWS) + 0.5f);
        this.scrollOffs = Mth.clamp(this.scrollOffs - (float) delta * step, 0.0f, 1.0f);
        return true;
    }

    public void mouseReleased() {
        this.isScrolling = false;
    }

    // --- 内部辅助方法 ---

    private int getTotalRows() { return (whitelist.size() + COLS - 1) / COLS; }
    private boolean needsScrollBars() { return getTotalRows() > VISIBLE_ROWS; }
    private int getScrollPixels() { return needsScrollBars() ? (int) ((getTotalRows() * ROW_HEIGHT - HEIGHT) * this.scrollOffs) : 0; }

    private void renderSlotBox(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, 0xFF252525);
        gfx.fill(x, y, x + 18, y + 1, 0xFF101010);
        gfx.fill(x, y, x + 1, y + 18, 0xFF101010);
        gfx.fill(x, y + 17, x + 18, y + 18, 0xFF404040);
        gfx.fill(x + 17, y, x + 18, y + 18, 0xFF404040);
    }

    private void drawScrollBar(GuiGraphics gfx, int x, int y, int height) {
        gfx.fill(x, y, x + 6, y + height, 0xFF000000);
        if (!needsScrollBars()) return;
        int totalHeight = getTotalRows() * ROW_HEIGHT;
        int barHeight = Mth.clamp((int) ((float) (HEIGHT * HEIGHT) / (float) totalHeight), 32, height - 8);
        int barTop = (int) (this.scrollOffs * (float) (height - barHeight)) + y;
        gfx.fill(x, barTop, x + 6, barTop + barHeight, 0xFF808080);
        gfx.fill(x, barTop, x + 5, barTop + barHeight - 1, 0xFFC0C0C0);
    }

    private boolean isHovering(int x, int y, double mouseX, double mouseY) { return isHovering(x, y, mouseX, mouseY, 18, 18); }
    private boolean isHovering(int x, int y, double mouseX, double mouseY, int w, int h) { return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h; }
}
