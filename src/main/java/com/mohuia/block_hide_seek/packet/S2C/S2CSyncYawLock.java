package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncYawLock {
    private final int entityId;
    private final boolean locked;
    private final float lockedYawDeg;

    public S2CSyncYawLock(int entityId, boolean locked, float lockedYawDeg) {
        this.entityId = entityId;
        this.locked = locked;
        this.lockedYawDeg = lockedYawDeg;
    }

    public static void encode( S2CSyncYawLock msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.locked);
        buf.writeFloat(msg.lockedYawDeg);
    }

    public static  S2CSyncYawLock decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean locked = buf.readBoolean();
        float yaw = buf.readFloat();
        return new  S2CSyncYawLock(id, locked, yaw);
    }

    public static void handle( S2CSyncYawLock msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (Minecraft.getInstance().level == null) return;

                    Entity e = Minecraft.getInstance().level.getEntity(msg.entityId);
                    if (e == null) return;

                    e.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                        cap.setYawLocked(msg.locked);
                        cap.setLockedYaw(Mth.wrapDegrees(msg.lockedYawDeg));
                    });
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
