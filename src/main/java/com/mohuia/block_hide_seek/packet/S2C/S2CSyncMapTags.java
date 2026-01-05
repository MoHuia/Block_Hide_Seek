package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.config.ClientConfigCache;
import com.mohuia.block_hide_seek.client.config.GameSettingsScreen; // 引入你的 Screen 类
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSyncMapTags {
    private final List<String> tags;

    public S2CSyncMapTags(List<String> tags) {
        this.tags = tags;
    }

    public static void encode(S2CSyncMapTags msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.tags.size());
        for (String tag : msg.tags) {
            buf.writeUtf(tag);
        }
    }

    public static S2CSyncMapTags decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return new S2CSyncMapTags(list);
    }

    public static void handle(S2CSyncMapTags msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 1. 更新缓存
            ClientConfigCache.availableTags = msg.tags;
            System.out.println("[Client] 收到地图标签更新: " + msg.tags);

            // 2. ✅ 核心修改：如果当前屏幕是 GameSettingsScreen，强制刷新它
            if (Minecraft.getInstance().screen instanceof GameSettingsScreen screen) {
                screen.refreshDropdowns(msg.tags);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
