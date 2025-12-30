package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GameSettingsScreen extends Screen {
    private final Screen lastScreen;

    // 参数缓存
    private int duration = 300;
    private int hits = 5;
    private int seekers = 1;

    public GameSettingsScreen(Screen lastScreen) {
        super(Component.literal("游戏规则设置"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int step = 25;

        // 1. 游戏时长
        addRenderableWidget(Button.builder(Component.literal("-"), b -> duration = Math.max(60, duration - 60)).bounds(centerX - 80, startY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> duration += 60).bounds(centerX + 60, startY, 20, 20).build());

        // 2. 挨打次数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> hits = Math.max(1, hits - 1)).bounds(centerX - 80, startY + step, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> hits += 1).bounds(centerX + 60, startY + step, 20, 20).build());

        // 3. 抓捕人数
        addRenderableWidget(Button.builder(Component.literal("-"), b -> seekers = Math.max(1, seekers - 1)).bounds(centerX - 80, startY + step * 2, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> seekers += 1).bounds(centerX + 60, startY + step * 2, 20, 20).build());

        // 保存并返回
        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            // 发送包到服务器更新
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SUpdateGameSettings(duration, hits, seekers));
            this.minecraft.setScreen(lastScreen);
        }).bounds(centerX - 50, startY + step * 4, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int step = 25;

        gfx.drawCenteredString(this.font, this.title, centerX, startY - 30, 0xFFFFFF);

        // 绘制数值文本
        gfx.drawCenteredString(this.font, "游戏时长: " + (duration / 60) + " 分钟", centerX, startY + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "承受攻击: " + hits + " 次", centerX, startY + step + 6, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "初始抓捕者: " + seekers + " 人", centerX, startY + step * 2 + 6, 0xFFFFFF);

        super.render(gfx, mouseX, mouseY, partialTick);
    }
}
