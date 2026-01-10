package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.components.DarkFlatButton;
import com.mohuia.block_hide_seek.components.InventoryPicker;
import com.mohuia.block_hide_seek.components.WhitelistGrid;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SToggleWhitelist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ConfigScreen extends Screen {
    // 数据模型
    private final List<BlockState> whitelist;

    // UI 组件
    private WhitelistGrid whitelistGrid;
    private InventoryPicker inventoryPicker;

    // 布局常量
    private static final int GUI_WIDTH = 242;
    private static final int GUI_HEIGHT = 236;
    private int guiLeft;
    private int guiTop;

    public ConfigScreen(List<BlockState> whitelist) {
        super(Component.literal("配置躲藏方块"));
        this.whitelist = new java.util.ArrayList<>(whitelist);
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        int listX = this.guiLeft + 12;
        int listY = this.guiTop + 30;

        // 1. 初始化白名单网格组件
        this.whitelistGrid = new WhitelistGrid(listX, listY, this.whitelist, this::toggleBlock);

        // 2. 游戏规则按钮
        int btnWidth = 120;
        int btnX = this.guiLeft + (GUI_WIDTH - btnWidth) / 2;
        int btnY = listY + (4 * 18) + 6; // list height = 4 * 18

        this.addRenderableWidget(new DarkFlatButton(btnX, btnY, btnWidth, 18,
                Component.literal("⚙ 游戏规则设置"),
                button -> Minecraft.getInstance().setScreen(new GameSettingsScreen(this))
        ));

        // 3. 初始化背包选择组件
        int invX = this.guiLeft + (GUI_WIDTH - 162) / 2;
        int invY = btnY + 18 + 18;
        this.inventoryPicker = new InventoryPicker(invX, invY, this::tryAddFromInventory);
        this.inventoryPicker.setWhitelistReference(this.whitelist);
    }

    // --- 事件代理 ---

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        // 渲染窗口背景
        gfx.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, 0xFF000000);
        gfx.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF303030);

        // 渲染文字
        gfx.drawCenteredString(this.font, "躲猫猫 - 方块配置", this.width / 2, guiTop + 8, 0xFFFFFF);
        gfx.drawString(this.font, "已添加 (" + whitelist.size() + ") - 可滚动:", guiLeft + 12, guiTop + 20, 0xA0A0A0, false);
        gfx.drawString(this.font, "你的背包 (点击添加):", guiLeft + (GUI_WIDTH - 162) / 2, guiTop + 130, 0xA0A0A0, false); // Y坐标需根据实际调整

        // 渲染组件
        whitelistGrid.render(gfx, mouseX, mouseY);
        inventoryPicker.render(gfx, mouseX, mouseY);

        super.render(gfx, mouseX, mouseY, partialTick); // 渲染按钮

        // 渲染 Tooltips (最后绘制以防遮挡)
        whitelistGrid.renderTooltips(gfx, mouseX, mouseY);
        // InventoryPicker 的 tooltip 已在内部 render 中处理，也可移出来统一管理
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (whitelistGrid.mouseClicked(mouseX, mouseY, button)) return true;
        if (inventoryPicker.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (whitelistGrid.mouseScrolled(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (whitelistGrid.mouseDragged(mouseY, whitelistGridY())) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        whitelistGrid.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- 业务逻辑 ---

    private void toggleBlock(BlockState state) {
        PacketHandler.INSTANCE.sendToServer(new C2SToggleWhitelist(state));
        playClickSound();
    }

    private void tryAddFromInventory(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            toggleBlock(blockItem.getBlock().defaultBlockState());
        }
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    // 服务端同步更新
    public void updateWhitelist(List<BlockState> newList) {
        this.whitelist.clear();
        this.whitelist.addAll(newList);
        // 通知组件数据已更新
        if (whitelistGrid != null) whitelistGrid.updateList(this.whitelist);
        // 背包组件持有的是引用，所以不需要显式更新，但为了安全也可以加个 setter
    }

    // 辅助获取Grid的Y坐标供拖拽计算
    private int whitelistGridY() { return this.guiTop + 30; }

    @Override
    public boolean isPauseScreen() { return false; }
}
