package com.mohuia.block_hide_seek.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

public class InventoryPicker {
    private final int x;
    private final int y;
    private final Consumer<ItemStack> onItemClicked;
    private List<BlockState> currentWhitelist; // 用于高亮显示已存在的物品

    public InventoryPicker(int x, int y, Consumer<ItemStack> onItemClicked) {
        this.x = x;
        this.y = y;
        this.onItemClicked = onItemClicked;
    }

    public void setWhitelistReference(List<BlockState> list) {
        this.currentWhitelist = list;
    }

    public void render(GuiGraphics gfx, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // 主背包 (9-35)
        for (int i = 9; i < 36; i++) {
            int col = (i % 9);
            int row = (i / 9) - 1;
            renderInvSlot(gfx, x + col * 18, y + row * 18, player.getInventory().getItem(i), mouseX, mouseY);
        }

        // 快捷栏 (0-8)
        int hotbarY = y + 3 * 18 + 4;
        for (int i = 0; i < 9; i++) {
            renderInvSlot(gfx, x + i * 18, hotbarY, player.getInventory().getItem(i), mouseX, mouseY);
        }
    }

    private void renderInvSlot(GuiGraphics gfx, int slotX, int slotY, ItemStack stack, int mouseX, int mouseY) {
        // 背景槽
        gfx.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF252525);
        gfx.fill(slotX, slotY, slotX + 18, slotY + 1, 0xFF101010);
        gfx.fill(slotX, slotY, slotX + 1, slotY + 18, 0xFF101010);
        gfx.fill(slotX, slotY + 17, slotX + 18, slotY + 18, 0xFF404040);
        gfx.fill(slotX + 17, slotY, slotX + 18, slotY + 18, 0xFF404040);

        // 高亮已存在物品
        if (currentWhitelist != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            if (currentWhitelist.contains(blockItem.getBlock().defaultBlockState())) {
                gfx.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0x6000AA00);
            }
        }

        if (!stack.isEmpty()) {
            gfx.renderItem(stack, slotX + 1, slotY + 1);
            // Hover 高亮
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                gfx.fill(slotX, slotY, slotX + 18, slotY + 18, 0x80FFFFFF);
                gfx.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        // 主背包
        for (int i = 9; i < 36; i++) {
            int col = (i % 9);
            int row = (i / 9) - 1;
            int slotX = x + col * 18;
            int slotY = y + row * 18;
            if (checkClick(slotX, slotY, mouseX, mouseY, player.getInventory().getItem(i))) return true;
        }

        // 快捷栏
        int hotbarY = y + 3 * 18 + 4;
        for (int i = 0; i < 9; i++) {
            int slotX = x + i * 18;
            if (checkClick(slotX, hotbarY, mouseX, mouseY, player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private boolean checkClick(int x, int y, double mouseX, double mouseY, ItemStack stack) {
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
            if (!stack.isEmpty()) {
                onItemClicked.accept(stack);
                return true;
            }
        }
        return false;
    }
}
