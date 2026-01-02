package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SAttackRaycast {
    /** debug 粒子开关：你也可以改成读取服务端 config */
    private final boolean debugParticles;

    public C2SAttackRaycast(boolean debugParticles) {this.debugParticles = debugParticles;}

    public static void encode(C2SAttackRaycast msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.debugParticles);}

    public static C2SAttackRaycast decode(FriendlyByteBuf buf) {return new C2SAttackRaycast(buf.readBoolean());}

    public static void handle(C2SAttackRaycast msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ✅ 服务端判断：游戏进行中才处理
            com.mohuia.block_hide_seek.game.GameLoopManager.onSeekerLeftClickRaycast(player, msg.debugParticles);
        });
        ctx.get().setPacketHandled(true);
    }
}
