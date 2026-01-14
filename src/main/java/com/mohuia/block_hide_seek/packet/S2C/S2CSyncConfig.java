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

    private final String hiderTag;
    private final String lobbyTag;

    // ✅ 修正：构造函数必须接收所有 5 个参数
    public S2CSyncConfig(int duration, int hits, int seekers, String hiderTag, String lobbyTag) {
        this.duration = duration;
        this.hits = hits;
        this.seekers = seekers;
        this.hiderTag = hiderTag;
        this.lobbyTag = lobbyTag;
    }

    public static void encode(S2CSyncConfig msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
        // 写入字符串
        buf.writeUtf(msg.hiderTag);
        buf.writeUtf(msg.lobbyTag);
    }

    public static S2CSyncConfig decode(FriendlyByteBuf buf) {
        // ✅ 修正：这里调用构造函数时，参数数量和类型必须和上面定义的一致
        return new S2CSyncConfig(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    public static void handle(S2CSyncConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 安全调用客户端代码
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // 确保 ClientConfigCache.update 也接受了这 5 个参数
                    ClientConfigCache.update(msg.duration, msg.hits, msg.seekers, msg.hiderTag, msg.lobbyTag);
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
