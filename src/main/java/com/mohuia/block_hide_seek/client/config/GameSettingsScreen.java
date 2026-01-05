package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRequestMapTags;
import com.mohuia.block_hide_seek.packet.C2S.C2SUpdateGameSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameSettingsScreen extends Screen {
    private final Screen lastScreen;

    private int duration;
    private int hits;
    private int seekers;

    private ScrollableDropdown hiderDropdown;
    private ScrollableDropdown lobbyDropdown;
    private ScrollableDropdown activeDropdown = null;

    private final List<String> availableTags;

    public GameSettingsScreen(Screen lastScreen) {
        super(Component.literal("游戏规则设置"));
        this.lastScreen = lastScreen;
        this.duration = ClientConfigCache.duration;
        this.hits = ClientConfigCache.hits;
        this.seekers = ClientConfigCache.seekers;

        this.availableTags = new ArrayList<>();
        if (ClientConfigCache.availableTags != null) {
            this.availableTags.addAll(ClientConfigCache.availableTags);
        }
    }

    @Override
    protected void init() {
        // 请求最新地图数据
        PacketHandler.INSTANCE.sendToServer(new C2SRequestMapTags());

        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;
        int step = 25;

        // --- 按钮组 ---
        // 时长
        addRenderableWidget(Button.builder(Component.literal("-"), b -> duration = Math.max(60, duration - 60))
                .bounds(centerX - 80, startY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> duration += 60)
                .bounds(centerX + 60, startY, 20, 20).build());

        // 受击次数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> hits = Math.max(1, hits - 1))
                .bounds(centerX - 80, startY + step, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> hits += 1)
                .bounds(centerX + 60, startY + step, 20, 20).build());

        // 抓捕者人数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> seekers = Math.max(1, seekers - 1))
                .bounds(centerX - 80, startY + step * 2, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> seekers += 1)
                .bounds(centerX + 60, startY + step * 2, 20, 20).build());

        // --- 下拉框 ---
        this.hiderDropdown = new ScrollableDropdown(centerX - 50, startY + step * 3, 120, 20,
                ClientConfigCache.hiderSpawnTag, availableTags, s -> {});

        this.lobbyDropdown = new ScrollableDropdown(centerX - 50, startY + step * 4, 120, 20,
                ClientConfigCache.lobbySpawnTag, availableTags, s -> {});

        addRenderableWidget(this.hiderDropdown);
        addRenderableWidget(this.lobbyDropdown);

        // --- 保存按钮 ---
        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            PacketHandler.INSTANCE.sendToServer(new C2SUpdateGameSettings(
                    duration, hits, seekers,
                    hiderDropdown.getSelected(),
                    lobbyDropdown.getSelected()
            ));
            this.minecraft.setScreen(lastScreen);
        }).bounds(centerX - 50, startY + step * 6, 100, 20).build());
    }

    /**
     * ✅ 优化：通过 setOptions 动态更新下拉框，不销毁重建 GUI
     */
    public void refreshDropdowns(List<String> newTags) {
        this.availableTags.clear();
        if (newTags != null) {
            this.availableTags.addAll(newTags);
        }

        // 核心优化点：直接更新控件数据
        if (this.hiderDropdown != null) {
            this.hiderDropdown.setOptions(this.availableTags);
        }
        if (this.lobbyDropdown != null) {
            this.lobbyDropdown.setOptions(this.availableTags);
        }
    }

    // ================= 事件传递 (下拉框焦点逻辑) =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 优先处理已打开的下拉框列表点击
        if (activeDropdown != null && activeDropdown.isOpen()) {
            if (activeDropdown.mouseClickedList(mouseX, mouseY, button)) return true;
            // 点击外部，关闭下拉框
            if (!activeDropdown.isMouseOver(mouseX, mouseY)) {
                activeDropdown.setOpen(false);
                activeDropdown = null;
            }
        }

        // 2. 判定点击是否触发了某个下拉框的开启
        if (hiderDropdown.isMouseOver(mouseX, mouseY)) {
            switchDropdown(hiderDropdown);
        } else if (lobbyDropdown.isMouseOver(mouseX, mouseY)) {
            switchDropdown(lobbyDropdown);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchDropdown(ScrollableDropdown target) {
        if (activeDropdown != null && activeDropdown != target) {
            activeDropdown.setOpen(false);
        }
        activeDropdown = target;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeDropdown != null && activeDropdown.isOpen()) {
            if (activeDropdown.mouseScrolledList(mouseX, mouseY, delta)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeDropdown != null && activeDropdown.isOpen()) {
            if (activeDropdown.mouseDraggedList(mouseX, mouseY, button, dragX, dragY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeDropdown != null) activeDropdown.mouseReleasedList(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        // 文字渲染
        int centerX = this.width / 2;
        int startY = this.height / 2 - 80;
        int step = 25;

        gfx.drawCenteredString(this.font, this.title, centerX, startY - 25, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "游戏时长: " + (duration / 60) + " 分钟", centerX, startY + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "承受攻击: " + hits + " 次", centerX, startY + step + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "初始抓捕者: " + seekers + " 人", centerX, startY + step * 2 + 6, 0xFFFFFF);

        gfx.drawString(this.font, "游戏地图:", centerX - 100, startY + step * 3 + 6, 0xAAAAAA, true);
        gfx.drawString(this.font, "返回大厅:", centerX - 100, startY + step * 4 + 6, 0xAAAAAA, true);

        // 最后渲染下拉框的列表层 (确保覆盖在其他按钮之上)
        if (activeDropdown != null && activeDropdown.isOpen()) {
            activeDropdown.renderDropdownList(gfx, mouseX, mouseY);
        }
    }
}
