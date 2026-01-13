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

    // 地图标签
    private final String hiderTag;
    private final String lobbyTag;

    // 道具数据
    private final int radarRange;
    private final int radarCooldown;
    private final int vanishMana;

    private final int decoyCount;
    private final int decoyCooldown;

    private final int bowCooldown;

    public S2CSyncConfig(int duration, int hits, int seekers, String hiderTag, String lobbyTag, int radarRange, int radarCooldown, int vanishMana,int decoyCount,int decoyCooldown,int bowCooldown) {
        this.duration = duration;
        this.hits = hits;
        this.seekers = seekers;
        this.hiderTag = hiderTag;
        this.lobbyTag = lobbyTag;
        this.radarRange = radarRange;
        this.radarCooldown = radarCooldown;
        this.vanishMana = vanishMana;
        this.decoyCount = decoyCount;
        this.decoyCooldown = decoyCooldown;
        this.bowCooldown = bowCooldown;
    }

    public static void encode(S2CSyncConfig msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
        buf.writeUtf(msg.hiderTag);
        buf.writeUtf(msg.lobbyTag);
        buf.writeInt(msg.radarRange);
        buf.writeInt(msg.radarCooldown);
        buf.writeInt(msg.vanishMana);
        buf.writeInt(msg.decoyCount);
        buf.writeInt(msg.decoyCooldown);
        buf.writeInt(msg.bowCooldown);
    }

    public static S2CSyncConfig decode(FriendlyByteBuf buf) {
        return new S2CSyncConfig(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                // 读取道具数据
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(S2CSyncConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // 更新客户端缓存
                    ClientConfigCache.update(
                            msg.duration,
                            msg.hits,
                            msg.seekers,
                            msg.hiderTag,
                            msg.lobbyTag,
                            msg.radarRange,
                            msg.radarCooldown,
                            msg.vanishMana,
                            msg.decoyCount,
                            msg.decoyCooldown,
                            msg.bowCooldown
                    );
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
