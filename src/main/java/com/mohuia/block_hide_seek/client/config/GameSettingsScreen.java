package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.components.ScrollableDropdown;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRequestMapTags;
import com.mohuia.block_hide_seek.packet.C2S.C2SUpdateGameSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameSettingsScreen extends Screen {
    private final Screen lastScreen;

    private int duration;
    private int hits;
    private int seekers;
    private int vanishMana;

    private ScrollableDropdown hiderDropdown;
    private ScrollableDropdown lobbyDropdown;
    private ScrollableDropdown activeDropdown = null;
    private EditBox vanishManaField;

    private final List<String> availableTags;

    public GameSettingsScreen(Screen lastScreen) {
        super(Component.literal("游戏规则设置"));
        this.lastScreen = lastScreen;
        this.duration = ClientConfigCache.duration;
        this.hits = ClientConfigCache.hits;
        this.seekers = ClientConfigCache.seekers;
        this.vanishMana = ClientConfigCache.vanishMana;

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
        // 整体上移，为下方按钮留出充足空间
        int startY = this.height / 2 - 90;
        int step = 24;

        // --- 1. 数值调整区 (3行) ---

        // 时长
        addRenderableWidget(Button.builder(Component.literal("-"), b -> duration = Math.max(60, duration - 60))
                .bounds(centerX - 90, startY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> duration += 60)
                .bounds(centerX + 70, startY, 20, 20).build());

        // 受击次数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> hits = Math.max(1, hits - 1))
                .bounds(centerX - 90, startY + step, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> hits += 1)
                .bounds(centerX + 70, startY + step, 20, 20).build());

        // 抓捕者人数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> seekers = Math.max(1, seekers - 1))
                .bounds(centerX - 90, startY + step * 2, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> seekers += 1)
                .bounds(centerX + 70, startY + step * 2, 20, 20).build());

        // --- 2. 地图选择区 (2行) ---

        int mapY = startY + step * 3 + 5;

        this.hiderDropdown = new ScrollableDropdown(centerX - 60, mapY, 130, 20,
                ClientConfigCache.hiderSpawnTag, availableTags, s -> {});

        this.lobbyDropdown = new ScrollableDropdown(centerX - 60, mapY + step + 5, 130, 20,
                ClientConfigCache.lobbySpawnTag, availableTags, s -> {});

        addRenderableWidget(this.hiderDropdown);
        addRenderableWidget(this.lobbyDropdown);

        // --- 3. 底部操作区 ---

        int bottomY = mapY + step * 2 + 15;

        // 🔥 跳转道具配置页面 (宽按钮)
        addRenderableWidget(Button.builder(Component.literal("🔧 自定义道具配置 >"), b -> {
            this.minecraft.setScreen(new ItemConfigScreen(this));
        }).bounds(centerX - 80, bottomY, 160, 20).build());

        // ✅ 保存按钮 (最底部)
        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            PacketHandler.INSTANCE.sendToServer(new C2SUpdateGameSettings(
                    duration, hits, seekers,
                    hiderDropdown.getSelected(),
                    lobbyDropdown.getSelected()
            ));
            this.minecraft.setScreen(lastScreen);
        }).bounds(centerX - 60, bottomY + 25, 120, 20).build());
    }

    /**
     * 动态刷新下拉框数据
     */
    public void refreshDropdowns(List<String> newTags) {
        this.availableTags.clear();
        if (newTags != null) {
            this.availableTags.addAll(newTags);
        }
        if (this.hiderDropdown != null) {
            this.hiderDropdown.setOptions(this.availableTags);
        }
        if (this.lobbyDropdown != null) {
            this.lobbyDropdown.setOptions(this.availableTags);
        }
    }

    // ================= 事件传递 =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 优先处理已打开的下拉框
        if (activeDropdown != null && activeDropdown.isOpen()) {
            if (activeDropdown.mouseClickedList(mouseX, mouseY, button)) return true;
            // 点击外部关闭
            if (!activeDropdown.isMouseOver(mouseX, mouseY)) {
                activeDropdown.setOpen(false);
                activeDropdown = null;
            }
        }

        // 2. 判定是否点击打开下拉框
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

    // ================= 渲染逻辑 =================

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 90;
        int step = 24;

        // 标题
        gfx.drawCenteredString(this.font, this.title, centerX, startY - 25, 0xFFFFFF);

        // 数值显示
        gfx.drawCenteredString(this.font, "游戏时长: " + (duration / 60) + " 分钟", centerX, startY + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "承受攻击: " + hits + " 次", centerX, startY + step + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "初始抓捕者: " + seekers + " 人", centerX, startY + step * 2 + 6, 0xFFFFFF);

        // 地图标签 (向左移动，防止被下拉框遮挡)
        int mapY = startY + step * 3 + 5;
        gfx.drawString(this.font, "游戏地图:", centerX - 115, mapY + 6, 0xAAAAAA, true);
        gfx.drawString(this.font, "返回大厅:", centerX - 115, mapY + step + 5 + 6, 0xAAAAAA, true);

        // 最后渲染下拉框列表，确保在顶层
        if (activeDropdown != null && activeDropdown.isOpen()) {
            activeDropdown.renderDropdownList(gfx, mouseX, mouseY);
        }
    }
}
