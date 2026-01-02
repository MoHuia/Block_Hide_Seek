package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CUpdateConfigGui {
    private final List<BlockState> list;
    public S2CUpdateConfigGui(List<BlockState> list) { this.list = list; }
    public static void encode( S2CUpdateConfigGui msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.list.size());
        for(BlockState s : msg.list) buf.writeInt(Block.getId(s));
    }
    public static  S2CUpdateConfigGui decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockState> l = new ArrayList<>();
        for(int i=0; i<size; i++) l.add(Block.stateById(buf.readInt()));
        return new  S2CUpdateConfigGui(l);
    }
    public static void handle( S2CUpdateConfigGui msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // 检查当前界面是否是配置界面
                    if (Minecraft.getInstance().screen instanceof ConfigScreen screen) {
                        screen.updateWhitelist(msg.list); // 只更新数据，不重置界面
                    }
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
