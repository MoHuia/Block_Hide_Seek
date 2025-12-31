package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientHudHandler {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Pre event) {
        // 1. 获取客户端玩家
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        // 2. 获取 Capability 数据
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {

            // 核心逻辑修改：
            // 如果是抓捕者 (isSeeker)  -> 隐藏
            // 或者
            // 如果有伪装 (disguise != null) -> 隐藏 (说明是躲藏者且已变身)
            boolean isPlaying = cap.isSeeker() || cap.getDisguise() != null;

            if (isPlaying) {
                // --- 隐藏血条 ---
                if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
                    event.setCanceled(true);
                }

                // --- 隐藏饥饿值 (躲猫猫不需要吃饭) ---
                if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                    event.setCanceled(true);
                }

                // --- 隐藏护甲值 (看起来更像纯净的观察者模式) ---
                if (event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
                    event.setCanceled(true);
                }

            }
        });
    }
}
