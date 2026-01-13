package com.mohuia.block_hide_seek.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget; // 必须导入

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigSection {
    private final String title;
    private final String desc;
    private final Supplier<String> dynamicInfo;
    private final int barColor;
    private final int x, y, width, height;

    private final List<NumberInputRow> rows = new ArrayList<>();
    private int currentRowYOffset = 10;

    public ConfigSection(String title, String desc, Supplier<String> dynamicInfo, int barColor, int x, int y, int width, int height) {
        this.title = title;
        this.desc = desc;
        this.dynamicInfo = dynamicInfo;
        this.barColor = barColor;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 修改：使用 Consumer<AbstractWidget> widgetAdder 代替 Screen screen
     */
    public void addNumberRow(Consumer<AbstractWidget> widgetAdder, String label, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max, int step, boolean asSeconds) {
        int rightAlignX = x + width - 10;
        int rowY = y + currentRowYOffset;

        NumberInputRow row = new NumberInputRow(widgetAdder, label, rowY, rightAlignX, getter, setter, min, max, step, asSeconds);
        rows.add(row);

        currentRowYOffset += 26;
    }

    public void applyPendingEdits() {
        rows.forEach(NumberInputRow::parseEditBox);
    }

    public void render(GuiGraphics gfx, Font font) {
        gfx.fill(x, y, x + width, y + height, 0x80000000);
        gfx.fill(x, y, x + 2, y + height, barColor);

        gfx.drawString(font, title, x + 10, y + 10, 0xFFFFFF, true);
        gfx.drawString(font, desc, x + 10, y + 22, 0xAAAAAA, false);
        gfx.drawString(font, dynamicInfo.get(), x + 10, y + 36, 0x555555, false);

        for (NumberInputRow row : rows) {
            row.renderLabel(gfx, font);
        }
    }
}
