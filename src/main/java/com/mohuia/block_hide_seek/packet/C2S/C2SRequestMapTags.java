package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncMapTags;
import com.mohuia.block_hide_seek.world.MapExtraIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SRequestMapTags {
    // 空包，不需要字段
    public C2SRequestMapTags() {}

    public static void encode(C2SRequestMapTags msg, FriendlyByteBuf buf) {}

    public static C2SRequestMapTags decode(FriendlyByteBuf buf) {
        return new C2SRequestMapTags();
    }

    public static void handle(C2SRequestMapTags msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 获取最新数据 (触发 MapExtraIntegration 的热重载逻辑)
                MapExtraIntegration data = MapExtraIntegration.get(player.serverLevel());

                // 2. 将最新的标签列表发回给该玩家
                PacketHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CSyncMapTags(data.getAllTags())
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
