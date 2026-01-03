package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.config.ClientConfigCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：同步游戏配置
 */
public class S2CSyncConfig {
    private final int duration;
    private final int hits;
    private final int seekers;

    public S2CSyncConfig(int duration, int hits, int seekers) {
        this.duration = duration;
        this.hits = hits;
        this.seekers = seekers;
    }

    public static void encode(S2CSyncConfig msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
    }

    public static S2CSyncConfig decode(FriendlyByteBuf buf) {
        return new S2CSyncConfig(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(S2CSyncConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 安全调用客户端代码
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientConfigCache.update(msg.duration, msg.hits, msg.seekers);
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
