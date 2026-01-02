package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SModelSizeResponse {
    private final float width;
    private final float height;
    private final String blockName;
    private final String debugLog; // 【新增】调试日志

    public C2SModelSizeResponse(float width, float height, String blockName, String debugLog) {
        this.width = width;
        this.height = height;
        this.blockName = blockName;
        this.debugLog = debugLog;
    }

    public static void encode(C2SModelSizeResponse msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.width);
        buf.writeFloat(msg.height);
        buf.writeUtf(msg.blockName);
        buf.writeUtf(msg.debugLog); // 【新增】
    }

    public static C2SModelSizeResponse decode(FriendlyByteBuf buf) {
        return new C2SModelSizeResponse(buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SModelSizeResponse msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 1. 打印详细调试日志
                player.sendSystemMessage(Component.literal("§e=== 模型分析报告 ==="));
                player.sendSystemMessage(Component.literal("§7方块: " + msg.blockName));

                // 将日志按行打印
                String[] logs = msg.debugLog.split("\n");
                for (String log : logs) {
                    player.sendSystemMessage(Component.literal("§8" + log));
                }

                player.sendSystemMessage(Component.literal(String.format("§b[最终结果] 宽: %.2f | 高: %.2f", msg.width, msg.height)));
                player.sendSystemMessage(Component.literal("§e======================"));

                // 2. 生成实体建议
                player.sendSystemMessage(Component.literal("📋 建议代码: EntityDimensions.fixed(" + msg.width + "F, " + msg.height + "F)"));
            }
        });
        ctx.get().setPacketHandled(true);

    }
}
