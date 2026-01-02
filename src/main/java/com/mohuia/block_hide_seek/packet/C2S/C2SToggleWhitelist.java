package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.client.ClientHooks;
import com.mohuia.block_hide_seek.client.ConfigScreen;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CUpdateConfigGui;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SToggleWhitelist {
    private final BlockState state;
    public C2SToggleWhitelist(BlockState state) { this.state = state; }
    public static void encode( C2SToggleWhitelist msg, FriendlyByteBuf buf) { buf.writeInt(Block.getId(msg.state)); }
    public static  C2SToggleWhitelist decode(FriendlyByteBuf buf) { return new  C2SToggleWhitelist(Block.stateById(buf.readInt())); }

    public static void handle( C2SToggleWhitelist msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockWhitelistData data = BlockWhitelistData.get(player.level());
                List<BlockState> current = data.getAllowedStates();
                boolean exists = current.stream().anyMatch(s -> s.getBlock() == msg.state.getBlock());

                if (exists) {
                    data.removeBlock(msg.state);
                    player.sendSystemMessage(Component.literal("❌ 已移除: " + msg.state.getBlock().getName().getString()));
                } else {
                    data.addBlock(msg.state);
                    player.sendSystemMessage(Component.literal("✅ 已添加: " + msg.state.getBlock().getName().getString()));
                }

                // 【关键修改】发送 S2CUpdateConfigGui 给所有玩家 (静默刷新)
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CUpdateConfigGui(data.getAllowedStates()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

