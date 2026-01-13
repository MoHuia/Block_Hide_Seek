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

    // 编码：只写入这 5 个游戏规则字段
    public static void encode(C2SUpdateGameSettings msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeInt(msg.hits);
        buf.writeInt(msg.seekers);
        buf.writeUtf(msg.hiderTag);
        buf.writeUtf(msg.lobbyTag);
        // ❌ 删除那行 buf.writeInt(msg.)，这个包不传雷达数据
    }

    // 解码
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
            if (player != null && player.hasPermissions(2)) {

                // 1. 获取服务端当前配置
                ServerGameConfig config = ServerGameConfig.get(player.level());

                // 2. 只更新游戏规则 (雷达数据保持不变)
                config.gameDurationSeconds = msg.duration();
                config.hitsToConvert = msg.hits();
                config.seekerCount = msg.seekers();
                config.gameMapTag = msg.hiderTag();
                config.lobbyTag = msg.lobbyTag();

                config.setDirty(); // 保存

                player.sendSystemMessage(Component.literal("✅ 游戏设置已更新！"));

                // 3. 广播同步 (关键点)
                // S2CSyncConfig 需要7个参数。
                // 前5个用新的，后2个(雷达)用服务端现有的 config.radarRange
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new S2CSyncConfig(
                                config.gameDurationSeconds, // 新值
                                config.hitsToConvert,       // 新值
                                config.seekerCount,         // 新值
                                config.gameMapTag,          // 新值
                                config.lobbyTag,            // 新值
                                config.radarRange,
                                config.radarCooldown,
                                config.vanishMana,
                                config.decoyCount,
                                config.decoyCooldown
                        )
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
