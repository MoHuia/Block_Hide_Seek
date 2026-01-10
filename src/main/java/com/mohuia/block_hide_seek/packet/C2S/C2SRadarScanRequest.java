package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.packet.S2C.S2CRadarScanService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRadarScanRequest {
    public C2SRadarScanRequest() {}

    public static void encode(C2SRadarScanRequest msg, FriendlyByteBuf buf) {
        // 空包，无需写入
    }

    public static C2SRadarScanRequest decode(FriendlyByteBuf buf) {
        return new C2SRadarScanRequest();
    }

    public static void handle(C2SRadarScanRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // ✅ 收到请求后，调用服务端 Service 广播扫描结果
            S2CRadarScanService.broadcastScan(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
