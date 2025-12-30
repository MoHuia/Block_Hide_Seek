package com.mohuia.block_hide_seek.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * 游戏开始时触发
 * KubeJS 监听到这个事件后，负责把人传送到竞技场
 */
public class GameStartEvent extends Event {
    private final ServerLevel level;

    public GameStartEvent(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
