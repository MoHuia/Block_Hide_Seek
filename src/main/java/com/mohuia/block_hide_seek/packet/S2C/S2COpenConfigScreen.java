package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2COpenConfigScreen {
    private final List<BlockState> list;
    public S2COpenConfigScreen(List<BlockState> list) { this.list = list; }
    public static void encode(S2COpenConfigScreen msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.list.size());
        for(BlockState s : msg.list) buf.writeInt(Block.getId(s));
    }
    public static S2COpenConfigScreen decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockState> l = new ArrayList<>();
        for(int i=0; i<size; i++) l.add(Block.stateById(buf.readInt()));
        return new S2COpenConfigScreen(l);
    }
    public static void handle(S2COpenConfigScreen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHooks.openConfigGui(msg.list)) // 强制打开
        );
        ctx.get().setPacketHandled(true);
    }
}
