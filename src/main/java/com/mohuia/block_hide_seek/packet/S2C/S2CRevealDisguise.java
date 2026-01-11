package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.hud.ClientGameCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CRevealDisguise {
    private final UUID targetUUID;
    private final int durationMs;

    public S2CRevealDisguise(UUID targetUUID, int durationMs) {
        this.targetUUID = targetUUID;
        this.durationMs = durationMs;
    }

    public static void encode(S2CRevealDisguise msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetUUID);
        buf.writeInt(msg.durationMs);
    }

    public static S2CRevealDisguise decode(FriendlyByteBuf buf) {
        return new S2CRevealDisguise(buf.readUUID(), buf.readInt());
    }

    public static void handle(S2CRevealDisguise msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端收到包：更新缓存
            ClientGameCache.revealDisguise(msg.targetUUID, msg.durationMs);
        });
        ctx.get().setPacketHandled(true);
    }
}
