package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.components.ConfigSection;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SUpdateItemConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ItemConfigScreen extends Screen {
    private final Screen lastScreen;

    private int radarRange;
    private int radarCooldown;
    private int vanishMana;
    private int decoyCount;
    private int decoyCooldown;

    private final List<ConfigSection> sections = new ArrayList<>();

    private static final int ITEM_HEIGHT = 65;
    private static final int GAP = 10;

    public ItemConfigScreen(Screen lastScreen) {
        super(Component.literal("道具配置"));
        this.lastScreen = lastScreen;
        this.radarRange = ClientConfigCache.radarRange;
        this.radarCooldown = ClientConfigCache.radarCooldown;
        this.vanishMana = ClientConfigCache.vanishMana;
        this.decoyCount = ClientConfigCache.decoyCount;
        this.decoyCooldown = ClientConfigCache.decoyCooldown;
    }

    @Override
    protected void init() {
        this.sections.clear();

        int listWidth = (int) (this.width * 0.85);
        int startX = (this.width - listWidth) / 2;
        int currentY = 40;

        // 区域 1
        ConfigSection radarSection = new ConfigSection(
                "觅影 (Radar)",
                "探测最近的躲藏者",
                () -> "(实际冷却: " + radarCooldown + " ticks)",
                0xFFFFD700,
                startX, currentY, listWidth, ITEM_HEIGHT
        );

        // -> 关键修改：传递 this::addRenderableWidget
        radarSection.addNumberRow(this::addRenderableWidget, "范围 (格):",
                () -> radarRange,
                val -> radarRange = val,
                10, 500, 10,
                false);

        radarSection.addNumberRow(this::addRenderableWidget, "冷却 (秒):",
                () -> radarCooldown,
                val -> radarCooldown = val,
                0, 12000, 10,
                true);

        this.sections.add(radarSection);
        currentY += ITEM_HEIGHT + GAP;

        // 区域 2
        ConfigSection vanishSection = new ConfigSection(
                "云翳 (Vanish)",
                "使用该道具的总时长",
                () -> "(实际容量: " + vanishMana + " ticks)",
                0xFF00FFFF,
                startX, currentY, listWidth, ITEM_HEIGHT
        );

        vanishSection.addNumberRow(this::addRenderableWidget, "总能量 (秒):",
                () -> vanishMana,
                val -> vanishMana = val,
                20, 12000, 20,
                true);
        this.sections.add(vanishSection);

        currentY += ITEM_HEIGHT + GAP;

        // 区域 3
        ConfigSection decoySection = new ConfigSection(
                "幻象 (Decoy)",
                "制造假身迷惑敌人",
                () -> "(实际冷却: " + decoyCooldown + " ticks)",
                0xFF00FF00, // 绿色
                startX, currentY, listWidth, ITEM_HEIGHT
        );

        // 数量 (整数)
        decoySection.addNumberRow(this::addRenderableWidget, "最大数量:",
                () -> decoyCount,
                val -> decoyCount = val,
                1, 10, 1, // 最小1个，最大10个，每次加1
                false);

        // 冷却 (时间)
        decoySection.addNumberRow(this::addRenderableWidget, "冷却 (秒):",
                () -> decoyCooldown,
                val -> decoyCooldown = val,
                0, 12000, 10,
                true);
        this.sections.add(decoySection);

        // 底部按钮
        int bottomY = Math.max(this.height - 30, currentY + ITEM_HEIGHT + 10);

        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            saveAndClose();
        }).bounds(this.width / 2 - 50, bottomY, 100, 20).build());
    }

    private void saveAndClose() {
        sections.forEach(ConfigSection::applyPendingEdits);
        PacketHandler.INSTANCE.sendToServer(new C2SUpdateItemConfig(radarRange, radarCooldown, vanishMana, decoyCount, decoyCooldown));
        this.minecraft.setScreen(lastScreen);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        gfx.drawCenteredString(this.font, "道具详细参数配置", this.width / 2, 15, 0xFFFFFF);

        for (ConfigSection section : sections) {
            section.render(gfx, this.font);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(lastScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
