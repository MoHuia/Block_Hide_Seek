package com.mohuia.block_hide_seek.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;


public class GameStartEvent extends Event {
    private final ServerLevel level;

    public GameStartEvent(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
