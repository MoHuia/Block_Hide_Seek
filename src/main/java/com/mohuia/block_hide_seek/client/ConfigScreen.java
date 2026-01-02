package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SToggleWhitelist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    // --- 1. 布局常量 ---
    private static final int GUI_WIDTH = 242;
    private static final int GUI_HEIGHT = 236;

    // 列表配置
    private static final int LIST_COLS = 12;
    private static final int VISIBLE_ROWS = 4;
    private static final int ROW_HEIGHT = 18;

    // 列表区域高度
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;

    // 布局坐标变量
    private int guiLeft;
    private int guiTop;

    private int listAreaX;
    private int listAreaY;

    private int invAreaX;
    private int invAreaY;

    // 滚动变量
    private float scrollOffs;
    private boolean isScrolling;

    public ConfigScreen(List<BlockState> whitelist) {
        super(Component.literal("配置躲藏方块"));
        // 强制包装成一个新的 ArrayList，确保它是可变的，防止 crash
        this.whitelist = new java.util.ArrayList<>(whitelist);
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        // --- 坐标计算区域 ---
        this.listAreaX = this.guiLeft + 12;
        this.listAreaY = this.guiTop + 30;

        // 游戏规则按钮
        int btnWidth = 120;
        int btnX = this.guiLeft + (GUI_WIDTH - btnWidth) / 2;
        int btnY = this.listAreaY + LIST_HEIGHT + 6;

        this.addRenderableWidget(new DarkFlatButton(btnX, btnY, btnWidth, 18, Component.literal("⚙ 游戏规则设置"), button -> {
            Minecraft.getInstance().setScreen(new GameSettingsScreen(this));
        }));

        // 玩家背包位置
        this.invAreaX = this.guiLeft + (GUI_WIDTH - 162) / 2;
        this.invAreaY = btnY + 18 + 18;
    }



    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!needsScrollBars()) return false;
        float step = 1.0f / ((float) (this.getTotalRows() - VISIBLE_ROWS) + 0.5f);
        this.scrollOffs = Mth.clamp(this.scrollOffs - (float)delta * step, 0.0f, 1.0f);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling) {
            int scrollBarTop = listAreaY;
            int scrollBarBottom = listAreaY + LIST_HEIGHT;
            float trackHeight = (float)(scrollBarBottom - scrollBarTop);
            this.scrollOffs = ((float)mouseY - (float)scrollBarTop - 7.5f) / (trackHeight - 15.0f);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0f, 1.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listWidth = LIST_COLS * 18;

        // 1. 滚动条点击
        int scrollBarX = listAreaX + listWidth + 6;
        if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                mouseY >= listAreaY && mouseY <= listAreaY + LIST_HEIGHT) {
            this.isScrolling = needsScrollBars();
            return true;
        }

        // 2. 白名单列表点击
        if (mouseX >= listAreaX && mouseX < listAreaX + listWidth &&
                mouseY >= listAreaY && mouseY < listAreaY + LIST_HEIGHT) {

            int scrollPixel = getScrollPixels();
            double relativeY = mouseY - listAreaY + scrollPixel;

            int col = (int) (mouseX - listAreaX) / 18;
            int row = (int) (relativeY / 18);

            int index = row * LIST_COLS + col;

            if (index >= 0 && index < whitelist.size()) {
                PacketHandler.INSTANCE.sendToServer(new C2SToggleWhitelist(whitelist.get(index)));
                playClickSound();
                return true;
            }
        }

        // 3. 背包点击
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // 主背包
            for (int i = 9; i < 36; i++) {
                int col = (i % 9);
                int row = (i / 9) - 1;
                int x = invAreaX + col * 18;
                int y = invAreaY + row * 18;
                if (isHovering(x, y, mouseX, mouseY)) {
                    tryAddBlock(player.getInventory().getItem(i));
                    return true;
                }
            }
            // 快捷栏
            int hotbarY = invAreaY + 3 * 18 + 4;
            for (int i = 0; i < 9; i++) {
                int x = invAreaX + i * 18;
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

        // 1. 窗口背景
        gfx.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, 0xFF000000);
        gfx.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF303030);

        // 2. 标题
        gfx.drawCenteredString(this.font, "躲猫猫 - 方块配置", this.width / 2, guiTop + 8, 0xFFFFFF);

        // 3. 文字标签
        gfx.drawString(this.font, "已添加 (" + whitelist.size() + ") - 可滚动:", listAreaX, guiTop + 20, 0xA0A0A0, false);
        gfx.drawString(this.font, "你的背包 (点击添加):", invAreaX, invAreaY - 12, 0xA0A0A0, false);

        // 4. 渲染列表
        renderWhitelistArea(gfx, mouseX, mouseY);

        // 5. 渲染背包
        renderInventory(gfx, mouseX, mouseY);

        // 6. 按钮
        super.render(gfx, mouseX, mouseY, partialTick);

        // 7. Tooltips
        renderListTooltips(gfx, mouseX, mouseY);
    }

    private void renderWhitelistArea(GuiGraphics gfx, int mouseX, int mouseY) {
        int listWidth = LIST_COLS * 18;

        // 背景槽
        gfx.fill(listAreaX - 1, listAreaY - 1, listAreaX + listWidth + 1, listAreaY + LIST_HEIGHT + 1, 0xFF151515);

        // 剪裁
        gfx.enableScissor(listAreaX, listAreaY, listAreaX + listWidth, listAreaY + LIST_HEIGHT);

        int scrollPixel = getScrollPixels();
        int startRow = scrollPixel / 18;
        int endRow = (scrollPixel + LIST_HEIGHT) / 18 + 1;

        for (int i = 0; i < whitelist.size(); i++) {
            int col = i % LIST_COLS;
            int row = i / LIST_COLS;

            if (row < startRow || row > endRow) continue;

            int x = listAreaX + col * 18;
            int y = listAreaY + row * 18 - scrollPixel;

            renderSlotBox(gfx, x, y);
            ItemStack stack = new ItemStack(whitelist.get(i).getBlock());
            gfx.renderItem(stack, x + 1, y + 1);

            if (isHovering(x, y, mouseX, mouseY)) {
                gfx.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
            }
        }
        gfx.disableScissor();

        drawScrollBar(gfx, listAreaX + listWidth + 6, listAreaY, LIST_HEIGHT);
    }

    private void renderInventory(GuiGraphics gfx, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        for (int i = 9; i < 36; i++) {
            int col = (i % 9);
            int row = (i / 9) - 1;
            int x = invAreaX + col * 18;
            int y = invAreaY + row * 18;
            renderInvSlot(gfx, x, y, player.getInventory().getItem(i), mouseX, mouseY);
        }

        int hotbarY = invAreaY + 3 * 18 + 4;
        for (int i = 0; i < 9; i++) {
            int x = invAreaX + i * 18;
            renderInvSlot(gfx, x, hotbarY, player.getInventory().getItem(i), mouseX, mouseY);
        }
    }

    private void renderInvSlot(GuiGraphics gfx, int x, int y, ItemStack stack, int mouseX, int mouseY) {
        renderSlotBox(gfx, x, y);

        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            if (whitelist.contains(blockItem.getBlock().defaultBlockState())) {
                gfx.fill(x + 1, y + 1, x + 17, y + 17, 0x6000AA00);
            }
        }

        if (!stack.isEmpty()) {
            gfx.renderItem(stack, x + 1, y + 1);
            if (stack.getItem() instanceof BlockItem && isHovering(x, y, mouseX, mouseY)) {
                gfx.fill(x, y, x + 18, y + 18, 0x80FFFFFF);
                gfx.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    private void renderListTooltips(GuiGraphics gfx, int mouseX, int mouseY) {
        int listWidth = LIST_COLS * 18;
        if (mouseX < listAreaX || mouseX >= listAreaX + listWidth ||
                mouseY < listAreaY || mouseY >= listAreaY + LIST_HEIGHT) return;

        int relativeY = mouseY - listAreaY + getScrollPixels();
        int col = (mouseX - listAreaX) / 18;
        int row = relativeY / 18;
        int index = row * LIST_COLS + col;

        if (index >= 0 && index < whitelist.size()) {
            ItemStack stack = new ItemStack(whitelist.get(index).getBlock());
            gfx.renderTooltip(this.font, stack, mouseX, mouseY);
        }
    }

    private void drawScrollBar(GuiGraphics gfx, int x, int y, int height) {
        gfx.fill(x, y, x + 6, y + height, 0xFF000000);
        if (!needsScrollBars()) return;
        int totalRows = getTotalRows();
        int totalHeight = totalRows * ROW_HEIGHT;
        int barHeight = Mth.clamp((int) ((float) (LIST_HEIGHT * LIST_HEIGHT) / (float) totalHeight), 32, height - 8);
        int barTop = (int) (this.scrollOffs * (float) (height - barHeight)) + y;
        gfx.fill(x, barTop, x + 6, barTop + barHeight, 0xFF808080);
        gfx.fill(x, barTop, x + 5, barTop + barHeight - 1, 0xFFC0C0C0);
    }

    private int getTotalRows() {
        return (whitelist.size() + LIST_COLS - 1) / LIST_COLS;
    }

    private boolean needsScrollBars() {
        return getTotalRows() > VISIBLE_ROWS;
    }

    private int getScrollPixels() {
        if (!needsScrollBars()) return 0;
        return (int) ((getTotalRows() * ROW_HEIGHT - LIST_HEIGHT) * this.scrollOffs);
    }

    private void renderSlotBox(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, 0xFF252525);
        gfx.fill(x, y, x + 18, y + 1, 0xFF101010);
        gfx.fill(x, y, x + 1, y + 18, 0xFF101010);
        gfx.fill(x, y + 17, x + 18, y + 18, 0xFF404040);
        gfx.fill(x + 17, y, x + 18, y + 18, 0xFF404040);
    }

    private boolean isHovering(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private void tryAddBlock(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            PacketHandler.INSTANCE.sendToServer(new C2SToggleWhitelist(blockItem.getBlock().defaultBlockState()));
            playClickSound();
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class DarkFlatButton extends Button {
        public DarkFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            int bgColor = this.isHoveredOrFocused() ? 0xFF505050 : 0xFF303030;
            gfx.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            gfx.renderOutline(getX(), getY(), width, height, 0xFF000000);
            int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
            gfx.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    // 【新增】用于接收服务端广播的实时更新
    public void updateWhitelist(List<BlockState> newList) {
        this.whitelist.clear();
        this.whitelist.addAll(newList);
    }
}
