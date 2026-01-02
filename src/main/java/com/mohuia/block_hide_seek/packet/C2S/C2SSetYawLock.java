package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncYawLock;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SSetYawLock {
    private final boolean locked;
    private final float yawDeg; // 锁定角度（度）

    public C2SSetYawLock(boolean locked, float yawDeg) {
        this.locked = locked;
        this.yawDeg = yawDeg;
    }

    public static void encode( C2SSetYawLock msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.locked);
        buf.writeFloat(msg.yawDeg);
    }

    public static  C2SSetYawLock decode(FriendlyByteBuf buf) {
        boolean locked = buf.readBoolean();
        float yaw = buf.readFloat();
        return new  C2SSetYawLock(locked, yaw);
    }

    public static void handle( C2SSetYawLock msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ✅ 安全：wrap 到 [-180, 180)
            float yaw = Mth.wrapDegrees(msg.yawDeg);

            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                cap.setYawLocked(msg.locked);
                if (msg.locked) {
                    cap.setLockedYaw(yaw);
                }

                // ✅ 广播给追踪者 + 自己：让别人也能渲染到正确朝向
                PacketHandler.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new S2CSyncYawLock(player.getId(), cap.isYawLocked(), cap.getLockedYaw())
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
