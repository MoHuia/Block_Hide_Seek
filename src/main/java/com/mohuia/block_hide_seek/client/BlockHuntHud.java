package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

public class BlockHuntHud implements IGuiOverlay {

    // --- 📐 尺寸与布局配置 ---
    private static final int AVATAR_SIZE = 20;     // 头像内胆
    private static final int BORDER = 1;           // 基础边框
    private static final int GAP = 1;              // 间距

    // 卡片宽度 (22px)
    private static final int BOX_WIDTH = AVATAR_SIZE + (BORDER * 2);
    private static final int STRIDE = BOX_WIDTH + GAP;

    private static final int TOP_MARGIN = 3;       // 顶部边距
    private static final int TIME_HEIGHT = 18;     // 时间条高度
    private static final int NAME_BOX_HEIGHT = 5;  // 名字框高度
    private static final int DISGUISE_BOX_HEIGHT = 18; // ⚡ 增高了方块底座

    private static final int CENTER_BOX_HALF_WIDTH = 25;

    // 🎨 鲜艳色板
    private static final int[] PLAYER_COLORS = {
            0xFFE74C3C, 0xFFE67E22, 0xFFF1C40F, 0xFF2ECC71, 0xFF1ABC9C,
            0xFF3498DB, 0xFF9B59B6, 0xFFE91E63, 0xFF16A085, 0xFF2C3E50
    };

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientGameCache.isGameRunning) return;

        Minecraft mc = Minecraft.getInstance();
        int centerX = screenWidth / 2;

        // --- 1. 顶部时间条 ---
        int timeYStart = TOP_MARGIN;
        int timeYEnd = timeYStart + TIME_HEIGHT;

        guiGraphics.fill(centerX - CENTER_BOX_HALF_WIDTH, timeYStart, centerX + CENTER_BOX_HALF_WIDTH, timeYEnd, 0xEE090909);
        guiGraphics.drawCenteredString(mc.font, formatTime(ClientGameCache.timeRemaining), centerX, timeYStart + 5, 0xFFFFFF);

        // --- 2. 下方人数条 ---
        int infoYStart = timeYEnd + 1;
        int infoYEnd = infoYStart + 14;
        int colorTop = 0xEE090909;
        int colorBot = 0x00000000;

        guiGraphics.fillGradient(centerX - CENTER_BOX_HALF_WIDTH, infoYStart, centerX, infoYEnd, colorTop, colorBot);
        guiGraphics.fillGradient(centerX + 1, infoYStart, centerX + CENTER_BOX_HALF_WIDTH, infoYEnd, colorTop, colorBot);

        guiGraphics.drawCenteredString(mc.font, String.valueOf(ClientGameCache.hiders.size()), centerX - 12, infoYStart + 3, 0xFF55FFFF);
        guiGraphics.drawCenteredString(mc.font, String.valueOf(ClientGameCache.seekers.size()), centerX + 12, infoYStart + 3, 0xFFFF5555);

        // --- 3. 绘制两侧队伍 ---
        drawTeam(guiGraphics, mc, ClientGameCache.hiders, centerX - CENTER_BOX_HALF_WIDTH, true);
        drawTeam(guiGraphics, mc, ClientGameCache.seekers, centerX + CENTER_BOX_HALF_WIDTH, false);
    }

    private void drawTeam(GuiGraphics g, Minecraft mc, List<ClientGameCache.PlayerInfo> players, int anchorX, boolean isLeft) {
        if (players.isEmpty()) return;

        for (int i = 0; i < players.size(); i++) {
            if (i >= 10) break;

            ClientGameCache.PlayerInfo p = players.get(i);

            // 坐标计算
            int x;
            if (isLeft) {
                x = anchorX - GAP - (i * STRIDE) - BOX_WIDTH;
            } else {
                x = anchorX + GAP + (i * STRIDE);
            }

            int nameY = TOP_MARGIN;
            int avatarY = nameY + NAME_BOX_HEIGHT;
            int disguiseY = avatarY + BOX_WIDTH;

            // 1. 名字框
            drawPlayerNameBox(g, mc, p.name, x, nameY);

            // 2. 头像边框
            int colorIndex = i % PLAYER_COLORS.length;
            int borderColor = PLAYER_COLORS[colorIndex];
            g.fill(x, avatarY, x + BOX_WIDTH, avatarY + BOX_WIDTH, borderColor);

            // 3. 头像
            drawPlayerFace(g, mc, p, x + BORDER, avatarY + BORDER);

            // 4. 伪装方块 (仅躲藏者)
            boolean hasDisguise = isLeft && !p.disguiseItem.isEmpty();
            if (hasDisguise) {
                drawDisguiseBox(g, p.disguiseItem, x, disguiseY);
            }

            // ⚡⚡⚡ 5. 自己专属高亮 (最后绘制，覆盖在最上层) ⚡⚡⚡
            if (mc.player != null && mc.player.getUUID().equals(p.uuid)) {
                // 计算包裹的总高度：名字 + 头像 + (如果有方块就加方块高度)
                int totalHeight = NAME_BOX_HEIGHT + BOX_WIDTH;
                if (hasDisguise) {
                    totalHeight += DISGUISE_BOX_HEIGHT;
                }

                // 绘制特殊边框
                drawSelfHighlight(g, x, nameY, BOX_WIDTH, totalHeight, borderColor);
            }
        }
    }

    /**
     * ⚡ 绘制玩家自己的高亮边框 (渐变 + 底部圆角)
     */
    private void drawSelfHighlight(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        // 1. 计算颜色
        // baseColor 是深色 (四周深)
        // lightColor 是浅色 (中间浅) -> 我们把 baseColor 混合白色，变得更亮
        int r = (baseColor >> 16) & 0xFF;
        int gr = (baseColor >> 8) & 0xFF;
        int b = (baseColor) & 0xFF;
        // 简单提亮算法：向 255 靠近
        int lr = Math.min(255, r + 100);
        int lg = Math.min(255, gr + 100);
        int lb = Math.min(255, b + 100);
        int lightColor = (0xFF << 24) | (lr << 16) | (lg << 8) | lb;

        // 2. 绘制左边框 (垂直渐变：深 -> 浅 -> 深)
        // 上半段
        g.fillGradient(x, y, x + 1, y + h / 2, baseColor, lightColor);
        // 下半段 (注意高度 -1，为了圆角)
        g.fillGradient(x, y + h / 2, x + 1, y + h - 1, lightColor, baseColor);

        // 3. 绘制右边框 (同上)
        g.fillGradient(x + w - 1, y, x + w, y + h / 2, baseColor, lightColor);
        g.fillGradient(x + w - 1, y + h / 2, x + w, y + h - 1, lightColor, baseColor);

        // 4. 绘制上边框 (水平渐变：深 -> 浅 -> 深)
        g.fillGradient(x, y, x + w / 2, y + 1, baseColor, lightColor);
        g.fillGradient(x + w / 2, y, x + w, y + 1, lightColor, baseColor);

        // 5. 绘制下边框 (圆角处理：左右各缩进 1px)
        // 颜色全深，或者微渐变
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, baseColor);

        // *注*：左下角(x, y+h-1) 和 右下角(x+w-1, y+h-1) 故意不画，形成 1px 的缺口，模拟圆角
    }

    private void drawDisguiseBox(GuiGraphics g, ItemStack item, int x, int y) {
        int colorTop = 0xEE090909;
        int colorBot = 0x00000000;
        g.fillGradient(x, y, x + BOX_WIDTH, y + DISGUISE_BOX_HEIGHT, colorTop, colorBot);

        g.pose().pushPose();
        float centerX = x + (BOX_WIDTH / 2.0f);
        float centerY = y + (DISGUISE_BOX_HEIGHT / 2.0f);
        float scale = 0.8f;
        g.pose().translate(centerX, centerY, 100);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(item, -8, -8);
        g.pose().popPose();
    }

    private void drawPlayerNameBox(GuiGraphics g, Minecraft mc, String name, int x, int y) {
        g.fill(x, y, x + BOX_WIDTH, y + NAME_BOX_HEIGHT, 0x66000000);

        float scale = 0.5f;
        g.pose().pushPose();
        int maxTextWidth = (int)((BOX_WIDTH / scale) - 2);
        String renderName = mc.font.plainSubstrByWidth(name, maxTextWidth);

        float centerX = x + (BOX_WIDTH / 2.0f);
        float centerY = y + (NAME_BOX_HEIGHT / 2.0f);

        g.pose().translate(centerX, centerY, 5);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(mc.font, renderName, 0, -4, 0xFFFFFFFF);
        g.pose().popPose();
    }

    private void drawPlayerFace(GuiGraphics g, Minecraft mc, ClientGameCache.PlayerInfo p, int x, int y) {
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(p.uuid);
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(p.uuid);
            if (info != null) skin = info.getSkinLocation();
        }
        RenderSystem.setShaderTexture(0, skin);
        PlayerFaceRenderer.draw(g, skin, x, y, AVATAR_SIZE);
    }

    private String formatTime(int ticks) {
        int totalSeconds = ticks / 20;
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return String.format("%02d:%02d", min, sec);
    }
}
