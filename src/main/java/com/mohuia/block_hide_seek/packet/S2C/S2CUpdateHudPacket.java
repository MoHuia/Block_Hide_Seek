package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.hud.ClientGameCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CUpdateHudPacket {
    private final boolean isRunning;
    private final int timeRemaining;
    private final List<ClientGameCache.PlayerInfo> players;

    public S2CUpdateHudPacket(boolean isRunning, int timeRemaining, List<ClientGameCache.PlayerInfo> players) {
        this.isRunning = isRunning;
        this.timeRemaining = timeRemaining;
        this.players = players;
    }

    public static void encode(S2CUpdateHudPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isRunning);
        buf.writeInt(msg.timeRemaining);
        buf.writeInt(msg.players.size());
        for (ClientGameCache.PlayerInfo p : msg.players) {
            buf.writeUUID(p.uuid);
            buf.writeUtf(p.name);
            buf.writeBoolean(p.isSeeker);
            buf.writeItem(p.disguiseItem);
        }
    }

    public static S2CUpdateHudPacket decode(FriendlyByteBuf buf) {
        boolean running = buf.readBoolean();
        int time = buf.readInt();
        int size = buf.readInt();
        List<ClientGameCache.PlayerInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new ClientGameCache.PlayerInfo(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readItem()
            ));
        }
        return new S2CUpdateHudPacket(running, time, list);
    }

    public static void handle(S2CUpdateHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientGameCache.update(msg.isRunning, msg.timeRemaining, msg.players);
        }));
        ctx.get().setPacketHandled(true);
    }
}
