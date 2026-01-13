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

import static com.mohuia.block_hide_seek.client.config.ClientConfigCache.bowCooldown;

public class ItemConfigScreen extends Screen {
    private final Screen lastScreen;

    private int radarRange;
    private int radarCooldown;
    private int vanishMana;
    private int decoyCount;
    private int decoyCooldown;
    private int bowCooldown;

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
        this.bowCooldown = ClientConfigCache.bowCooldown;
    }

    @Override
    protected void init() {
        this.sections.clear();

        // ==========================================
        // 1. 布局计算 (动态高度)
        // ==========================================
        int colWidth = (int) (this.width * 0.45);      // 列宽 45%
        int leftX = (int) (this.width * 0.05);         // 左列 X
        int rightX = (int) (this.width * 0.52);        // 右列 X

        int startY = 35;                               // 第一行起始 Y

        // 设定两种高度：大板块(2行配置) 和 小板块(1行配置)
        int HEIGHT_LARGE = 85;
        int HEIGHT_SMALL = 60;
        int GAP = 10;

        // 第二行的 Y 坐标 = 第一行 Y + 大板块高度 + 间距
        int row2_Y = startY + HEIGHT_LARGE + GAP;

        // ==========================================
        // 2. 左列内容 (Radar -> Vanish)
        // ==========================================

        // [左上] 觅影 (Radar) - 2个参数 -> 用大高度
        ConfigSection radarSection = new ConfigSection(
                "觅影 (Radar)", "探测最近的躲藏者",
                () -> "(" + radarCooldown + " ticks)",
                0xFFFFD700,
                leftX, startY, colWidth, HEIGHT_LARGE
        );
        radarSection.addNumberRow(this::addRenderableWidget, "范围:",
                () -> radarRange, v -> radarRange = v, 10, 500, 10, false);
        radarSection.addNumberRow(this::addRenderableWidget, "冷却:",
                () -> radarCooldown, v -> radarCooldown = v, 0, 12000, 10, true);
        this.sections.add(radarSection);

        // [左下] 云翳 (Vanish) - 1个参数 -> 用小高度
        ConfigSection vanishSection = new ConfigSection(
                "云翳 (Vanish)", "使用总时长",
                () -> "(" + vanishMana + " ticks)",
                0xFF00FFFF,
                leftX, row2_Y, colWidth, HEIGHT_SMALL
        );
        vanishSection.addNumberRow(this::addRenderableWidget, "能量:",
                () -> vanishMana, v -> vanishMana = v, 20, 12000, 20, true);
        this.sections.add(vanishSection);


        // ==========================================
        // 3. 右列内容 (Decoy -> Bow)
        // ==========================================

        // [右上] 幻象 (Decoy) - 2个参数 -> 用大高度
        ConfigSection decoySection = new ConfigSection(
                "幻象 (Decoy)", "制造假身",
                () -> "(" + decoyCooldown + " ticks)",
                0xFF00FF00,
                rightX, startY, colWidth, HEIGHT_LARGE
        );
        decoySection.addNumberRow(this::addRenderableWidget, "数量:",
                () -> decoyCount, v -> decoyCount = v, 1, 10, 1, false);
        decoySection.addNumberRow(this::addRenderableWidget, "冷却:",
                () -> decoyCooldown, v -> decoyCooldown = v, 0, 12000, 10, true);
        this.sections.add(decoySection);

        // [右下] 猎弓 (Bow) - 1个参数 -> 用小高度
        // 它的 Y 坐标和左边的 Vanish 对齐，非常整齐
        ConfigSection bowSection = new ConfigSection(
                "猎弓 (Bow)", "抓捕者的武器",
                () -> "(" + bowCooldown + " ticks)",
                0xFFFF0000,
                rightX, row2_Y, colWidth, HEIGHT_SMALL
        );
        bowSection.addNumberRow(this::addRenderableWidget, "冷却:",
                () -> bowCooldown, v -> bowCooldown = v, 0, 12000, 10, true);
        this.sections.add(bowSection);


        // ==========================================
        // 4. 底部按钮
        // ==========================================
        int bottomY = row2_Y + HEIGHT_SMALL + 15;

        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            saveAndClose();
        }).bounds(this.width / 2 - 50, bottomY, 100, 20).build());
    }

    private void saveAndClose() {
        sections.forEach(ConfigSection::applyPendingEdits);
        PacketHandler.INSTANCE.sendToServer(new C2SUpdateItemConfig(radarRange, radarCooldown, vanishMana, decoyCount, decoyCooldown,bowCooldown));
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
