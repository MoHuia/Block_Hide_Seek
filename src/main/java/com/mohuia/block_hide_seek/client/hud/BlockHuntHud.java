package com.mohuia.block_hide_seek.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockHuntHud implements IGuiOverlay {

    // --- 📐 尺寸与布局配置 ---
    private static final int AVATAR_SIZE = 20;
    private static final int BORDER = 1;
    private static final int GAP = 1;
    private static final int BOX_WIDTH = AVATAR_SIZE + (BORDER * 2);
    private static final int STRIDE = BOX_WIDTH + GAP;
    private static final int TOP_MARGIN = 3;
    private static final int TIME_HEIGHT = 18;
    private static final int NAME_BOX_HEIGHT = 5;
    private static final int DISGUISE_BOX_HEIGHT = 18;
    private static final int CENTER_BOX_HALF_WIDTH = 25;

    // 🎨 鲜艳色板
    private static final int[] PLAYER_COLORS = {
            0xFFE74C3C, 0xFFE67E22, 0xFFF1C40F, 0xFF2ECC71, 0xFF1ABC9C,
            0xFF3498DB, 0xFF9B59B6, 0xFFE91E63, 0xFF16A085, 0xFF2C3E50
    };
    private static final int OFFLINE_BORDER_COLOR = 0xFF444444;
    private static final int OFFLINE_OVERLAY_COLOR = 0xB0111111;

    // 🔥 独立的状态类，确保存储动画和闪光状态
    private static class HudState {
        float anim = 0.0f;      // 0.0 ~ 1.0 (滑出进度)
        float flash = 0.0f;     // 0.0 ~ 1.0 (闪光强度)
        boolean lastShow = false; // 上一帧是否显示
    }

    // 缓存状态 Map
    private static final Map<UUID, HudState> STATES = new HashMap<>();

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientGameCache.isGameRunning) return;

        Minecraft mc = Minecraft.getInstance();
        int centerX = screenWidth / 2;

        // 1. 顶部时间条
        int timeYStart = TOP_MARGIN;
        int timeYEnd = timeYStart + TIME_HEIGHT;
        guiGraphics.fill(centerX - CENTER_BOX_HALF_WIDTH, timeYStart, centerX + CENTER_BOX_HALF_WIDTH, timeYEnd, 0xEE090909);
        guiGraphics.drawCenteredString(mc.font, formatTime(ClientGameCache.timeRemaining), centerX, timeYStart + 5, 0xFFFFFF);

        // 2. 下方人数条
        int infoYStart = timeYEnd + 1;
        int infoYEnd = infoYStart + 14;
        guiGraphics.fillGradient(centerX - CENTER_BOX_HALF_WIDTH, infoYStart, centerX, infoYEnd, 0xEE090909, 0x00000000);
        guiGraphics.fillGradient(centerX + 1, infoYStart, centerX + CENTER_BOX_HALF_WIDTH, infoYEnd, 0xEE090909, 0x00000000);

        guiGraphics.drawCenteredString(mc.font, String.valueOf(ClientGameCache.hiders.size()), centerX - 12, infoYStart + 3, 0xFF55FFFF);
        guiGraphics.drawCenteredString(mc.font, String.valueOf(ClientGameCache.seekers.size()), centerX + 12, infoYStart + 3, 0xFFFF5555);

        // 3. 绘制队伍
        drawTeam(guiGraphics, mc, ClientGameCache.hiders, centerX - CENTER_BOX_HALF_WIDTH, true);
        drawTeam(guiGraphics, mc, ClientGameCache.seekers, centerX + CENTER_BOX_HALF_WIDTH, false);
    }

    private void drawTeam(GuiGraphics g, Minecraft mc, List<ClientGameCache.PlayerInfo> players, int anchorX, boolean isLeft) {
        if (players.isEmpty()) return;

        for (int i = 0; i < players.size(); i++) {
            if (i >= 10) break;
            ClientGameCache.PlayerInfo p = players.get(i);
            boolean isOnline = isPlayerOnline(mc, p);

            int x = isLeft ? (anchorX - GAP - (i * STRIDE) - BOX_WIDTH) : (anchorX + GAP + (i * STRIDE));
            int nameY = TOP_MARGIN;
            int avatarY = nameY + NAME_BOX_HEIGHT;
            int disguiseYBase = avatarY + BOX_WIDTH;

            // --- 状态更新逻辑 ---
            HudState state = STATES.computeIfAbsent(p.uuid, k -> new HudState());

            boolean hasDisguiseItem = isLeft && !p.disguiseItem.isEmpty();
            boolean isSelf = mc.player != null && mc.player.getUUID().equals(p.uuid);
            boolean isRevealed = System.currentTimeMillis() < p.revealDeadline;
            boolean showDisguise = hasDisguiseItem && (isSelf || isRevealed);

            // ⚡ 触发闪光：如果这一帧要显示，但上一帧是隐藏的，就“闪”一下
            if (showDisguise && !state.lastShow) {
                state.flash = 1.0f; // 满闪光
            }
            state.lastShow = showDisguise;

            // ⚡ 动画插值：极慢速度 0.02f
            float targetAnim = showDisguise ? 1.0f : 0.0f;
            state.anim = Mth.lerp(0.04f, state.anim, targetAnim); // 0.04f 稍微顺滑一点，之前0.2太快

            // ⚡ 闪光衰减
            if (state.flash > 0) {
                state.flash *= 0.9f; // 每帧减弱
                if (state.flash < 0.01f) state.flash = 0f;
            }

            // --- 绘制开始 ---

            // 1. 名字 (最底层)
            drawPlayerNameBox(g, mc, p.name, x, nameY);

            // 2. 伪装方块 (中层)
            // 即使是滑进去，只要 anim > 0 就要画，不然看不见滑动的过程
            if (state.anim > 0.001f) {
                float yOffset = -DISGUISE_BOX_HEIGHT * (1.0f - state.anim);

                // 透明度随动画
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, state.anim);

                g.pose().pushPose();
                // ⚠️ Z = 0: 放在底层
                g.pose().translate(0, yOffset, 0);
                drawDisguiseBox(g, p.disguiseItem, x, disguiseYBase, state.flash);
                g.pose().popPose();

                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // 3. 头像 (⚠️ 绝对顶层)
            // ⚠️ 关键修改：将头像 Z 轴设为 200，确保覆盖掉方块里的 Item (Item默认Z约150)
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);

            int borderColor = isOnline ? PLAYER_COLORS[i % PLAYER_COLORS.length] : OFFLINE_BORDER_COLOR;
            // 画背景填满（防止透过头像看到滑动的方块）
            g.fill(x, avatarY, x + BOX_WIDTH, avatarY + BOX_WIDTH, borderColor);
            drawPlayerFace(g, mc, p, x + BORDER, avatarY + BORDER);
            g.pose().popPose();

            // 4. 高亮框 (更顶层 Z=210)
            if (isOnline && isSelf) {
                int baseH = NAME_BOX_HEIGHT + BOX_WIDTH;
                int extraH = (int)(DISGUISE_BOX_HEIGHT * state.anim);

                g.pose().pushPose();
                g.pose().translate(0, 0, 210);
                drawSelfHighlight(g, x, nameY, BOX_WIDTH, baseH + extraH, borderColor);
                g.pose().popPose();
            }

            // 5. 离线遮罩 (最顶层 Z=400)
            if (!isOnline) {
                int baseH = NAME_BOX_HEIGHT + BOX_WIDTH;
                int extraH = (int)(DISGUISE_BOX_HEIGHT * state.anim);
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                g.fill(x, nameY, x + BOX_WIDTH, nameY + baseH + extraH, OFFLINE_OVERLAY_COLOR);
                g.pose().popPose();
            }
        }
    }

    private void drawDisguiseBox(GuiGraphics g, ItemStack item, int x, int y, float flashAlpha) {
        int colorTop = 0xEE090909;
        int colorBot = 0x00000000;
        g.fillGradient(x, y, x + BOX_WIDTH, y + DISGUISE_BOX_HEIGHT, colorTop, colorBot);

        // 渲染物品
        g.pose().pushPose();
        float centerX = x + (BOX_WIDTH / 2.0f);
        float centerY = y + (DISGUISE_BOX_HEIGHT / 2.0f);
        float scale = 0.8f;
        g.pose().translate(centerX, centerY, 5);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(item, -8, -8);
        g.pose().popPose();

        // ⚡ 渲染闪光覆盖层 (白色)
        if (flashAlpha > 0.01f) {
            int alpha = (int)(flashAlpha * 180); // 最大不透明度 180 (不是全白，稍微透一点)
            int whiteColor = (alpha << 24) | 0xFFFFFF;
            // 覆盖在方块上
            g.fill(x, y, x + BOX_WIDTH, y + DISGUISE_BOX_HEIGHT, whiteColor);
        }
    }

    private boolean isPlayerOnline(Minecraft mc, ClientGameCache.PlayerInfo p) {
        if (p.forceOffline) return false;
        if (mc.isSingleplayer() && mc.getConnection() == null) return true;
        if (mc.getConnection() != null) {
            if (mc.isSingleplayer()) return true;
            return mc.getConnection().getPlayerInfo(p.uuid) != null;
        }
        return false;
    }

    private void drawSelfHighlight(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        int r = (baseColor >> 16) & 0xFF;
        int gr = (baseColor >> 8) & 0xFF;
        int b = (baseColor) & 0xFF;
        int lr = Math.min(255, r + 100);
        int lg = Math.min(255, gr + 100);
        int lb = Math.min(255, b + 100);
        int lightColor = (0xFF << 24) | (lr << 16) | (lg << 8) | lb;

        g.fillGradient(x, y, x + 1, y + h / 2, baseColor, lightColor);
        g.fillGradient(x, y + h / 2, x + 1, y + h - 1, lightColor, baseColor);
        g.fillGradient(x + w - 1, y, x + w, y + h / 2, baseColor, lightColor);
        g.fillGradient(x + w - 1, y + h / 2, x + w, y + h - 1, lightColor, baseColor);
        g.fillGradient(x, y, x + w / 2, y + 1, baseColor, lightColor);
        g.fillGradient(x + w / 2, y, x + w, y + 1, lightColor, baseColor);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, baseColor);
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
