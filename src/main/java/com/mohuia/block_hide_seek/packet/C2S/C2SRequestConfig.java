package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2COpenConfigScreen;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SRequestConfig {
    public C2SRequestConfig() {}
    public static void encode(C2SRequestConfig msg, FriendlyByteBuf buf) {}
    public static C2SRequestConfig decode(FriendlyByteBuf buf) { return new C2SRequestConfig(); }
    public static void handle(C2SRequestConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockWhitelistData data = BlockWhitelistData.get(player.level());
                // 只有请求者打开窗口
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
