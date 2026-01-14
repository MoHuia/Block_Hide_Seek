package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncConfig;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;


public record C2SUpdateGameSettings(int duration, int hits, int seekers, String hiderTag, String lobbyTag) {

    // 编码：写入缓冲区
    public static void encode(C2SUpdateGameSettings msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
        buf.writeUtf(msg.hiderTag);
        buf.writeUtf(msg.lobbyTag);
    }

    // 解码：从缓冲区读取
    public static C2SUpdateGameSettings decode(FriendlyByteBuf buf) {
        return new C2SUpdateGameSettings(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    // 处理逻辑
    public static void handle(C2SUpdateGameSettings msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            // 权限检查：只有OP/管理员(权限等级2及以上)可以执行
            if (player != null && player.hasPermissions(2)) {

                // 1. 获取并更新服务端配置
                ServerGameConfig config = ServerGameConfig.get(player.level());

                // record 的字段访问方式是 msg.fieldName()
                config.gameDurationSeconds = msg.duration();
                config.hitsToConvert = msg.hits();
                config.seekerCount = msg.seekers();
                config.gameMapTag = msg.hiderTag();
                config.lobbyTag = msg.lobbyTag();

                // 必须标记为脏数据，否则服务器重启后设置会回滚
                config.setDirty();

                player.sendSystemMessage(Component.literal("✅ 游戏设置已更新！"));

                // 2. 广播给所有玩家，同步他们的客户端缓存
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new S2CSyncConfig(
                                msg.duration(),
                                msg.hits(),
                                msg.seekers(),
                                msg.hiderTag(),
                                msg.lobbyTag()
                        )
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
