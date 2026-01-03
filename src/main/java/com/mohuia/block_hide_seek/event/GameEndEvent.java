package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.game.WinnerType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class GameEndEvent extends Event {
    private final ServerLevel level;
    private final WinnerType winner;
    private final Component reason;
    // 你还可以加更多字段，比如剩余时间、存活人数等

    public GameEndEvent(ServerLevel level, WinnerType winner, Component reason) {
        this.level = level;
        this.winner = winner;
        this.reason = reason;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public WinnerType getWinner() {
        return winner;
    }

    public Component getReason() {
        return reason;
    }

    public String getWinnerName() {
        return winner.name();
    }
}
