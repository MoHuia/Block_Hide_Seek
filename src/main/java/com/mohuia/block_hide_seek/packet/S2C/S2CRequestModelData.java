package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.ClientModelHelper;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRequestModelData {
    public S2CRequestModelData() {}
    public static void encode( S2CRequestModelData msg, FriendlyByteBuf buf) {}
    public static  S2CRequestModelData decode(FriendlyByteBuf buf) { return new  S2CRequestModelData(); }
    public static void handle( S2CRequestModelData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 安全地调用客户端代码，避免服务端崩溃
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientModelHelper.handleRequest())
        );
        ctx.get().setPacketHandled(true);
    }
}
