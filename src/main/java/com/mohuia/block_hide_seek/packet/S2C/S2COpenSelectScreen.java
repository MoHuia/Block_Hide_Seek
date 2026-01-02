package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.ClientHooks;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2COpenSelectScreen {
    private final List<BlockState> options;
    public S2COpenSelectScreen(List<BlockState> options) { this.options = options; }
    public static void encode( S2COpenSelectScreen msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.options.size());
        for (BlockState s : msg.options) buf.writeInt(Block.getId(s));
    }
    public static  S2COpenSelectScreen decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockState> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(Block.stateById(buf.readInt()));
        return new  S2COpenSelectScreen(list);
    }
    public static void handle( S2COpenSelectScreen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGui(msg.options))
        );
        ctx.get().setPacketHandled(true);
    }
}
