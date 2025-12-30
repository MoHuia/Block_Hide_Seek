package com.mohuia.block_hide_seek.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 标记为仅客户端运行 (Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientHudHandler {

    // 定义我们用来判断的标签名
    public static final String HIDE_HEALTH_TAG = "bhs_hide_health";

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Pre event) {
        // 1. 获取当前客户端玩家
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        // 2. 检查玩家是否有隐藏血条的标签
        if (player.getTags().contains(HIDE_HEALTH_TAG)) {

            // 3. 如果正在渲染血条，取消它
            if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
                event.setCanceled(true);
            }

            // (可选) 如果你也想隐藏饥饿值，把下面这行注释取消掉
            /*
            if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                event.setCanceled(true);
            }
            */
        }
    }
}
