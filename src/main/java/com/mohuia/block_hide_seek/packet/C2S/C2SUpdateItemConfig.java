package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.item.Vanish;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncConfig;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record C2SUpdateItemConfig(int radarRange, int radarCooldown,int vanishMana,int decoyCount,int decoyCooldown,int bowCooldown) {

    public static void encode(C2SUpdateItemConfig msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.radarRange);
        buf.writeInt(msg.radarCooldown);
        buf.writeInt(msg.vanishMana);
        buf.writeInt(msg.decoyCount);
        buf.writeInt(msg.decoyCooldown);
        buf.writeInt(msg.bowCooldown);
    }

    public static C2SUpdateItemConfig decode(FriendlyByteBuf buf) {
        return new C2SUpdateItemConfig(buf.readInt(), buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt());
    }

    public static void handle(C2SUpdateItemConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {

                // 1. 存入地图存档
                ServerGameConfig config = ServerGameConfig.get(player.level());
                config.radarRange = msg.radarRange();
                config.radarCooldown = msg.radarCooldown();
                config.vanishMana = msg.vanishMana();
                config.decoyCount = msg.decoyCount();
                config.decoyCooldown = msg.decoyCooldown();
                config.bowCooldown = msg.bowCooldown();
                config.setDirty(); // 💾 保存！

                // 额外动作：更新服务端 Vanish 类的静态变量
                // 确保服务端逻辑（如物品耐久检测）也能即时生效
                Vanish.MAX_MANA = config.vanishMana;

                player.sendSystemMessage(Component.literal("道具配置已保存！"));

                // 2. 广播同步给所有客户端 (确保大家拿到最新数据)
                // 注意：这里需要你修改一下 S2CSyncConfig 包，把雷达数据也带上
                // 这里我假设你已经改好了 S2CSyncConfig 的构造函数
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new S2CSyncConfig(
                                config.gameDurationSeconds,
                                config.hitsToConvert,
                                config.seekerCount,
                                config.gameMapTag,
                                config.lobbyTag,
                                config.radarRange,
                                config.radarCooldown,
                                config.vanishMana,
                                config.decoyCount,
                                config.decoyCooldown,
                                config.bowCooldown,
                                config.hidingTimeSeconds
                        )
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
