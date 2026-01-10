package com.mohuia.block_hide_seek.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ScrollableDropdown extends AbstractWidget {

    private final List<String> options = new ArrayList<>();
    private final Consumer<String> onSelect;
    private String selected;

    private boolean isOpen = false;
    private double scrollAmount = 0;
    private boolean isDraggingScroll = false;

    // 样式常量
    private static final int ITEM_HEIGHT = 16;
    private static final int MAX_VISIBLE_COUNT = 5;
    private static final int SCROLL_BAR_WIDTH = 6;
    private static final String EMPTY_HINT = "§7<暂无可用地图>";

    public ScrollableDropdown(int x, int y, int width, int height, String initialValue, List<String> rawOptions, Consumer<String> onSelect) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.selected = initialValue == null ? "" : initialValue;

        // 初始化数据
        setOptions(rawOptions);
    }

    /**
     * ✅ 优化：动态更新选项，无需重建控件
     */
    public void setOptions(List<String> newOptions) {
        this.options.clear();
        if (newOptions != null && !newOptions.isEmpty()) {
            this.options.addAll(newOptions);
        } else {
            this.options.add(EMPTY_HINT);
        }

        // 如果当前选中的项在新列表中不存在，重置选中状态
        // 注意：忽略占位符
        if (!this.options.contains(selected) && !selected.isEmpty()) {
            // 也可以选择不清除，保留旧值显示，视需求而定。这里选择保留以防误操作清除配置。
            // 如果需要强制清除无效选项，取消下面注释：
            // this.selected = "";
        }

        updateMessage();
        clampScroll(); // 防止滚动条越界
    }

    private void updateMessage() {
        String display = selected.isEmpty() ? "请选择..." : selected;
        // 文本过长截断
        if (Minecraft.getInstance().font.width(display) > width - 20) {
            display = Minecraft.getInstance().font.plainSubstrByWidth(display, width - 25) + "...";
        }
        this.setMessage(Component.literal(display));
    }

    public String getSelected() {
        return selected;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        this.isOpen = open;
        if (open) {
            scrollToSelected();
        } else {
            isDraggingScroll = false;
        }
    }

    private void scrollToSelected() {
        int index = options.indexOf(selected);
        if (index > 0) {
            this.scrollAmount = index * ITEM_HEIGHT;
            clampScroll();
        }
    }

    // ================= 渲染逻辑 =================

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused() || isOpen;

        // 背景与边框
        gfx.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
        gfx.renderOutline(getX(), getY(), width, height, hovered ? 0xFFFFFFFF : 0xFFAAAAAA);

        // 文字
        int textColor = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        gfx.drawString(Minecraft.getInstance().font, this.getMessage(), getX() + 6, getY() + (height - 8) / 2, textColor, false);

        // 箭头图标
        gfx.drawString(Minecraft.getInstance().font, isOpen ? "▲" : "▼", getX() + width - 12, getY() + (height - 8) / 2, 0xFF888888, false);
    }

    public void renderDropdownList(GuiGraphics gfx, int mouseX, int mouseY) {
        if (!isOpen) return;

        int listX = getX();
        int listY = getY() + height;
        int viewportHeight = Math.min(options.size(), MAX_VISIBLE_COUNT) * ITEM_HEIGHT;
        int totalContentHeight = options.size() * ITEM_HEIGHT;
        boolean needScroll = totalContentHeight > viewportHeight;

        // 提升渲染层级 (Z-Level 500)
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 500);

        // 列表背景
        gfx.fill(listX, listY, listX + width, listY + viewportHeight, 0xFF151515);
        gfx.renderOutline(listX, listY, width, viewportHeight, 0xFF888888);

        // 滚动条背景
        if (needScroll) {
            int barX = listX + width - SCROLL_BAR_WIDTH - 1;
            gfx.fill(barX, listY + 1, barX + SCROLL_BAR_WIDTH, listY + viewportHeight - 1, 0xFF222222);
        }

        // ✅ 开启裁剪 (Scissor)
        int contentWidth = needScroll ? width - SCROLL_BAR_WIDTH - 2 : width - 2;
        gfx.enableScissor(listX + 1, listY + 1, listX + 1 + contentWidth, listY + viewportHeight - 1);

        gfx.pose().pushPose();
        gfx.pose().translate(0, -scrollAmount, 0);

        // 循环渲染可见项
        int startIdx = (int) (scrollAmount / ITEM_HEIGHT);
        int endIdx = Math.min(options.size(), startIdx + MAX_VISIBLE_COUNT + 1);

        for (int i = startIdx; i < endIdx; i++) {
            String text = options.get(i);
            int itemY = listY + (i * ITEM_HEIGHT);

            // 悬停判定 (必须在裁剪区域内)
            boolean isHovered = mouseX >= listX && mouseX < listX + contentWidth &&
                    mouseY >= listY && mouseY <= listY + viewportHeight &&
                    mouseY + scrollAmount >= listY + (i * ITEM_HEIGHT) &&
                    mouseY + scrollAmount < listY + ((i + 1) * ITEM_HEIGHT);

            if (isHovered) {
                gfx.fill(listX + 1, itemY, listX + contentWidth, itemY + ITEM_HEIGHT, 0xFF444444);
            }

            int color = text.equals(selected) ? 0xFF55FF55 : 0xFFDDDDDD;
            if (text.startsWith("§7<")) color = 0xFF888888; // 占位符颜色

            gfx.drawString(Minecraft.getInstance().font, text, listX + 5, itemY + 4, color, false);
        }

        gfx.pose().popPose(); // 恢复位移
        gfx.disableScissor(); // ✅ 关闭裁剪

        // 滚动条滑块
        if (needScroll) {
            int barX = listX + width - SCROLL_BAR_WIDTH - 1;
            int trackH = viewportHeight - 2;
            int barY = listY + 1;

            float ratio = (float) viewportHeight / totalContentHeight;
            int thumbH = Math.max(10, (int) (trackH * ratio));
            float scrollRatio = (float) scrollAmount / (totalContentHeight - viewportHeight);
            int thumbY = barY + (int) ((trackH - thumbH) * scrollRatio);

            int thumbColor = isDraggingScroll ? 0xFFFFFFFF : 0xFFAAAAAA;
            if (mouseX >= barX && mouseX <= barX + SCROLL_BAR_WIDTH && mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                thumbColor = 0xFFDDDDDD;
            }
            gfx.fill(barX, thumbY, barX + SCROLL_BAR_WIDTH, thumbY + thumbH, thumbColor);
        }

        gfx.pose().popPose(); // 恢复层级
    }

    // ================= 事件处理 =================

    @Override
    public void onClick(double mouseX, double mouseY) {
        setOpen(!isOpen);
    }

    public boolean mouseClickedList(double mouseX, double mouseY, int button) {
        if (!isOpen) return false;

        int listY = getY() + height;
        int visibleCount = Math.min(options.size(), MAX_VISIBLE_COUNT);
        int viewportH = visibleCount * ITEM_HEIGHT;

        // 检查点击是否在列表范围内
        if (mouseX < getX() || mouseX > getX() + width || mouseY < listY || mouseY > listY + viewportH) {
            return false;
        }

        int totalH = options.size() * ITEM_HEIGHT;
        boolean hasScroll = totalH > viewportH;

        // 点击滚动条区域
        if (hasScroll && mouseX >= getX() + width - SCROLL_BAR_WIDTH - 2) {
            this.isDraggingScroll = true;
            return true;
        }

        // 点击具体选项
        double relativeY = mouseY - listY + scrollAmount;
        int index = (int) (relativeY / ITEM_HEIGHT);

        if (index >= 0 && index < options.size()) {
            String val = options.get(index);
            if (val.startsWith("§7<")) return true; // 忽略占位符

            this.selected = val;
            updateMessage();
            if (onSelect != null) onSelect.accept(selected);
            setOpen(false);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return true;
    }

    public boolean mouseScrolledList(double mouseX, double mouseY, double delta) {
        if (!isOpen) return false;
        if (options.size() > MAX_VISIBLE_COUNT) {
            this.scrollAmount -= delta * 16;
            clampScroll();
            return true;
        }
        return false;
    }

    public boolean mouseDraggedList(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isOpen && isDraggingScroll) {
            int visibleH = Math.min(options.size(), MAX_VISIBLE_COUNT) * ITEM_HEIGHT;
            int totalH = options.size() * ITEM_HEIGHT;
            int trackH = visibleH - 2;

            double range = trackH - Math.max(10, (int) (trackH * ((float) visibleH / totalH)));
            if (range > 0) {
                double speed = (totalH - visibleH) / range;
                this.scrollAmount += dragY * speed;
                clampScroll();
            }
            return true;
        }
        return false;
    }

    public void mouseReleasedList(double mouseX, double mouseY, int button) {
        isDraggingScroll = false;
    }

    private void clampScroll() {
        int visibleH = Math.min(options.size(), MAX_VISIBLE_COUNT) * ITEM_HEIGHT;
        int totalH = options.size() * ITEM_HEIGHT;
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0, Math.max(0, totalH - visibleH));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
