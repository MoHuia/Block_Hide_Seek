package com.mohuia.block_hide_seek.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget; // 必须导入这个
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class NumberInputRow {
    private final String label;
    private final EditBox editBox;
    private final Supplier<Integer> getter;
    private final Consumer<Integer> setter;
    private final int min, max, step;
    private final boolean asSeconds;

    // 修改：第一个参数改为 Consumer<AbstractWidget> widgetAdder
    public NumberInputRow(Consumer<AbstractWidget> widgetAdder, String label, int anchorY, int rightAlignX,
                          Supplier<Integer> getter, Consumer<Integer> setter,
                          int min, int max, int step, boolean asSeconds) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.asSeconds = asSeconds;

        int btnSize = 20;
        int boxWidth = 50;
        int spacing = 5;

        // 1. [+] 按钮 - 使用 widgetAdder.accept 代替 screen.addRenderableWidget
        widgetAdder.accept(Button.builder(Component.literal("+"), b -> {
            updateValue(this.getter.get() + this.step);
        }).bounds(rightAlignX - btnSize, anchorY, btnSize, btnSize).build());

        // 2. [输入框]
        int boxX = rightAlignX - btnSize - spacing - boxWidth;
        this.editBox = new EditBox(Minecraft.getInstance().font, boxX, anchorY, boxWidth, btnSize, Component.literal(label));
        updateDisplay();

        if (asSeconds) {
            this.editBox.setFilter(s -> s.matches("[0-9.]*"));
        } else {
            this.editBox.setFilter(s -> s.matches("\\d*"));
        }

        this.editBox.setResponder(s -> {
            try {
                if (s.isEmpty()) return;
                int newVal;
                if (asSeconds) {
                    newVal = (int) (Float.parseFloat(s) * 20);
                } else {
                    newVal = Integer.parseInt(s);
                }
                newVal = Math.max(min, Math.min(max, newVal));
                this.setter.accept(newVal);
            } catch (Exception ignored) {}
        });

        // 添加输入框
        widgetAdder.accept(this.editBox);

        // 3. [-] 按钮
        int minusX = boxX - spacing - btnSize;
        widgetAdder.accept(Button.builder(Component.literal("-"), b -> {
            updateValue(this.getter.get() - this.step);
        }).bounds(minusX, anchorY, btnSize, btnSize).build());
    }

    private void updateValue(int rawVal) {
        int clamped = Math.max(min, Math.min(max, rawVal));
        setter.accept(clamped);
        updateDisplay();
    }

    public void parseEditBox() {
        String s = editBox.getValue();
        try {
            if (!s.isEmpty()) {
                int val;
                if (asSeconds) val = (int) (Float.parseFloat(s) * 20);
                else val = Integer.parseInt(s);
                setter.accept(Math.max(min, Math.min(max, val)));
            }
        } catch (Exception ignored) {}
    }

    private void updateDisplay() {
        String text;
        if (asSeconds) {
            text = String.format("%.1f", getter.get() / 20.0f);
        } else {
            text = String.valueOf(getter.get());
        }

        if (!editBox.getValue().equals(text) && !editBox.isFocused()) {
            editBox.setValue(text);
        } else if (!editBox.isFocused()) {
            editBox.setValue(text);
        }
    }

    public void renderLabel(GuiGraphics gfx, Font font) {
        int btnSize = 20;
        int spacing = 5;
        int minusBtnLeft = editBox.getX() - spacing - btnSize;
        int labelWidth = font.width(label);
        gfx.drawString(font, label, minusBtnLeft - labelWidth - 5, editBox.getY() + 6, 0xDDDDDD, false);
    }
}
