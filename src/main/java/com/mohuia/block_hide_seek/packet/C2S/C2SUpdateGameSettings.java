package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SUpdateGameSettings {
    private final int duration;
    private final int hits;
    private final int seekers;

    public C2SUpdateGameSettings(int duration, int hits, int seekers) {
        this.duration = duration;
        this.hits = hits;
        this.seekers = seekers;
    }

    public static void encode( C2SUpdateGameSettings msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
    }

    public static  C2SUpdateGameSettings decode(FriendlyByteBuf buf) {
        return new  C2SUpdateGameSettings(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle( C2SUpdateGameSettings msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                ServerGameConfig config = ServerGameConfig.get(player.level());
                config.gameDurationSeconds = msg.duration;
                config.hitsToConvert = msg.hits;
                config.seekerCount = msg.seekers;
                config.setDirty();
                player.sendSystemMessage(Component.literal("✅ 游戏设置已更新！"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
