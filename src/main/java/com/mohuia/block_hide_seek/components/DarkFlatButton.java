package com.mohuia.block_hide_seek.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class DarkFlatButton extends Button {
    public DarkFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int bgColor = this.isHoveredOrFocused() ? 0xFF505050 : 0xFF303030;
        gfx.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        gfx.renderOutline(getX(), getY(), width, height, 0xFF000000);

        int textColor = this.active ? 0xFFFFFF : 0xA0A0A0;
        gfx.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
    }
}
