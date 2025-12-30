package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.game.GameLoopManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class GameTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 1. 只在 tick 结束阶段运行，防止一帧运行两次
        if (event.phase != TickEvent.Phase.END) return;

        // 2. 获取服务器实例
        var server = event.getServer();
        if (server == null) return;

        // 3. 调用我们的游戏主循环
        // 我们通常传入 Overworld (主世界) 作为标准 level
        GameLoopManager.tick(server.overworld());
    }
}
