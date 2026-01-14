package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.components.ScrollableDropdown;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRequestMapTags;
import com.mohuia.block_hide_seek.packet.C2S.C2SUpdateGameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameSettingsScreen extends Screen {
    private final Screen lastScreen;

    // 数据缓存
    private int duration;
    private int hits;
    private int seekers;
    private int hidingTime;

    // 下拉框组件
    private ScrollableDropdown hiderDropdown;
    private ScrollableDropdown lobbyDropdown;
    private ScrollableDropdown activeDropdown = null;

    private final List<String> availableTags;

    // 布局常量
    private int panelX, panelY, panelWidth, panelHeight;

    public GameSettingsScreen(Screen lastScreen) {
        super(Component.literal("游戏设置"));
        this.lastScreen = lastScreen;

        // 加载数据
        this.duration = ClientConfigCache.duration;
        this.hits = ClientConfigCache.hits;
        this.seekers = ClientConfigCache.seekers;
        this.hidingTime = ClientConfigCache.hidingTime > 0 ? ClientConfigCache.hidingTime : 30;

        this.availableTags = new ArrayList<>();
        if (ClientConfigCache.availableTags != null) {
            this.availableTags.addAll(ClientConfigCache.availableTags);
        }
    }

    @Override
    protected void init() {
        PacketHandler.INSTANCE.sendToServer(new C2SRequestMapTags());

        // --- 1. 计算主面板尺寸 ---
        // 宽度 320，高度 210，居中显示
        this.panelWidth = 320;
        this.panelHeight = 210;
        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;

        int contentStartY = panelY + 45; // 标题栏下方开始
        int leftCenterX = panelX + panelWidth / 4;      // 左分栏中心
        int rightCenterX = panelX + (panelWidth / 4) * 3; // 右分栏中心
        int rowHeight = 28; // 行高

        // --- 2. 左侧：规则设置区 ---

        // (1) 游戏时长
        addBtn("-", leftCenterX - 55, contentStartY, b -> duration = Math.max(60, duration - 60));
        addBtn("+", leftCenterX + 35, contentStartY, b -> duration += 60);

        // (2) 躲藏时间
        addBtn("-", leftCenterX - 55, contentStartY + rowHeight, b -> hidingTime = Math.max(0, hidingTime - 5));
        addBtn("+", leftCenterX + 35, contentStartY + rowHeight, b -> hidingTime += 5);

        // (3) 初始抓捕者
        addBtn("-", leftCenterX - 55, contentStartY + rowHeight * 2, b -> seekers = Math.max(1, seekers - 1));
        addBtn("+", leftCenterX + 35, contentStartY + rowHeight * 2, b -> seekers += 1);

        // (4) 承受攻击
        addBtn("-", leftCenterX - 55, contentStartY + rowHeight * 3, b -> hits = Math.max(1, hits - 1));
        addBtn("+", leftCenterX + 35, contentStartY + rowHeight * 3, b -> hits += 1);


        // --- 3. 右侧：地图设置区 ---

        int dropdownWidth = 120;
        int mapSectionY = contentStartY + 10;

        // 游戏地图选择
        this.hiderDropdown = new ScrollableDropdown(rightCenterX - dropdownWidth/2, mapSectionY, dropdownWidth, 20,
                ClientConfigCache.hiderSpawnTag, availableTags, s -> {});

        // 大厅选择
        this.lobbyDropdown = new ScrollableDropdown(rightCenterX - dropdownWidth/2, mapSectionY + 50, dropdownWidth, 20,
                ClientConfigCache.lobbySpawnTag, availableTags, s -> {});

        addRenderableWidget(this.hiderDropdown);
        addRenderableWidget(this.lobbyDropdown);


        // --- 4. 底部：操作按钮 ---

        int bottomY = panelY + panelHeight - 35;

        // 道具配置 (左下角)
        addRenderableWidget(Button.builder(Component.literal("🔧 道具配置"), b -> {
            this.minecraft.setScreen(new ItemConfigScreen(this));
        }).bounds(panelX + 20, bottomY, 100, 20).build());

        // 保存 (右下角，绿色高亮文字)
        addRenderableWidget(Button.builder(Component.literal("✅ 保存设置"), b -> {
            saveAndExit();
        }).bounds(panelX + panelWidth - 120, bottomY, 100, 20).build());
    }

    // 辅助方法：快速添加小按钮
    private void addBtn(String text, int x, int y, Button.OnPress press) {
        addRenderableWidget(Button.builder(Component.literal(text), press)
                .bounds(x, y, 20, 20).build());
    }

    private void saveAndExit() {
        PacketHandler.INSTANCE.sendToServer(new C2SUpdateGameSettings(
                duration, hits, seekers,
                hiderDropdown.getSelected(),
                lobbyDropdown.getSelected(),
                hidingTime
        ));
        this.minecraft.setScreen(lastScreen);
    }

    public void refreshDropdowns(List<String> newTags) {
        this.availableTags.clear();
        if (newTags != null) this.availableTags.addAll(newTags);
        if (hiderDropdown != null) hiderDropdown.setOptions(this.availableTags);
        if (lobbyDropdown != null) lobbyDropdown.setOptions(this.availableTags);
    }

    // ================= 渲染逻辑 (关键) =================

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx); // 默认黑色半透明背景

        // 1. 绘制主面板背景 (深灰色圆角矩形风格)
        int bgColor = 0xFF212121; // 深灰背景
        int borderColor = 0xFF555555; // 边框
        gfx.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, bgColor);
        gfx.renderOutline(panelX, panelY, panelWidth, panelHeight, borderColor);

        // 2. 标题栏
        gfx.drawCenteredString(this.font, Component.literal("📐 游戏规则设置").withStyle(net.minecraft.ChatFormatting.BOLD),
                panelX + panelWidth / 2, panelY + 12, 0xFFFFFFFF);

        // 标题分割线
        gfx.fill(panelX + 10, panelY + 30, panelX + panelWidth - 10, panelY + 31, 0xFF444444);

        // 3. 中间垂直分割线 (区分左右区域)
        gfx.fill(panelX + panelWidth / 2, panelY + 40, panelX + panelWidth / 2 + 1, panelY + panelHeight - 50, 0xFF333333);


        // --- 左侧内容渲染 ---
        int leftCenterX = panelX + panelWidth / 4;
        int contentStartY = panelY + 45;
        int rowHeight = 28;
        int labelColor = 0xFFAAAAAA;
        int valueColor = 0xFFFFFFFF;

        // (1) 时长
        drawLabelValue(gfx, "单局时长", (duration / 60) + " 分钟", leftCenterX, contentStartY, labelColor, valueColor);
        // (2) 躲藏时间
        drawLabelValue(gfx, "躲藏时间", hidingTime + " 秒", leftCenterX, contentStartY + rowHeight, labelColor, valueColor);
        // (3) 抓捕者
        drawLabelValue(gfx, "抓捕者", seekers + " 人", leftCenterX, contentStartY + rowHeight * 2, labelColor, valueColor);
        // (4) 承受攻击
        drawLabelValue(gfx, "受击上限", hits + " 次", leftCenterX, contentStartY + rowHeight * 3, labelColor, valueColor);


        // --- 右侧内容渲染 ---
        int rightCenterX = panelX + (panelWidth / 4) * 3;
        int mapSectionY = contentStartY + 10;

        // 标签文字 (下拉框已经在 init 里添加了，这里只画标签)
        gfx.drawCenteredString(this.font, "🗺️ 游戏地图", rightCenterX, mapSectionY - 12, 0xFFEEEEEE);
        gfx.drawCenteredString(this.font, "🏠 结束大厅", rightCenterX, mapSectionY + 50 - 12, 0xFFEEEEEE);


        // 4. 渲染子组件 (按钮、下拉框)
        super.render(gfx, mouseX, mouseY, partialTick);

        // 5. 特殊处理：下拉框展开列表必须最后画，在最顶层
        if (activeDropdown != null && activeDropdown.isOpen()) {
            // 需要变换坐标系，因为 dropdown 内部渲染是基于屏幕绝对坐标的，
            // 但如果这里有 PoseStack 变换可能会乱。
            // ScrollableDropdown 的 renderList 是绝对坐标，直接调用即可。
            activeDropdown.renderDropdownList(gfx, mouseX, mouseY);
        }
    }

    // 辅助绘制： [Label]  <Value>
    private void drawLabelValue(GuiGraphics gfx, String label, String value, int centerX, int y, int labelColor, int valColor) {
        // 标签在按钮左边上方一点，或者居中？
        // 采用布局： 按钮[-]  数值  按钮[+]
        // 标签写在数值正上方比较挤，不如写在左侧？
        // 这里采用： 标签(小字)在上方，数值在中间

        // 调整：标签在上方 (小字体)
        gfx.pose().pushPose();
        float scale = 0.8f;
        gfx.pose().scale(scale, scale, 1.0f);
        // 缩放后坐标需反向放大
        int scaledX = (int) (centerX / scale);
        int scaledY = (int) ((y - 6) / scale);
        gfx.drawCenteredString(this.font, label, scaledX, scaledY, labelColor);
        gfx.pose().popPose();

        // 数值
        gfx.drawCenteredString(this.font, value, centerX, y + 6, valColor);
    }

    // ================= 事件传递 (保持逻辑不变) =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 优先处理下拉框展开
        if (activeDropdown != null && activeDropdown.isOpen()) {
            if (activeDropdown.mouseClickedList(mouseX, mouseY, button)) return true;
            if (!activeDropdown.isMouseOver(mouseX, mouseY)) {
                activeDropdown.setOpen(false);
                activeDropdown = null;
            }
        }

        // 检查点击新的下拉框
        if (hiderDropdown.isMouseOver(mouseX, mouseY)) switchDropdown(hiderDropdown);
        else if (lobbyDropdown.isMouseOver(mouseX, mouseY)) switchDropdown(lobbyDropdown);

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchDropdown(ScrollableDropdown target) {
        if (activeDropdown != null && activeDropdown != target) activeDropdown.setOpen(false);
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
}
