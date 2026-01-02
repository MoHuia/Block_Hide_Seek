package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncGameData {
    private final int entityId;
    private final boolean isSeeker;
    private final BlockState block;

    private final float width;   // modelW
    private final float height;  // modelH

    // 新增：OBB真实尺寸
    private final float obbX;
    private final float obbY;
    private final float obbZ;

    // 新构造
    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block,
                           float width, float height,
                           float obbX, float obbY, float obbZ) {
        this.entityId = entityId;
        this.isSeeker = isSeeker;
        this.block = block;
        this.width = width;
        this.height = height;
        this.obbX = obbX;
        this.obbY = obbY;
        this.obbZ = obbZ;
    }

    // 旧构造兼容（如果旧地方还在用）
    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block, float width, float height) {
        this(entityId, isSeeker, block, width, height, width, height, width);
    }

    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block) {
        this(entityId, isSeeker, block, 0.5f, 1.0f, 0.5f, 1.0f, 0.5f);
    }

    public static void encode( S2CSyncGameData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.isSeeker);
        buf.writeInt(msg.block == null ? -1 : Block.getId(msg.block));

        buf.writeFloat(msg.width);
        buf.writeFloat(msg.height);

        // 新增
        buf.writeFloat(msg.obbX);
        buf.writeFloat(msg.obbY);
        buf.writeFloat(msg.obbZ);
    }

    public static  S2CSyncGameData decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean seeker = buf.readBoolean();
        int blockId = buf.readInt();
        BlockState state = blockId == -1 ? null : Block.stateById(blockId);

        float w = buf.readFloat();
        float h = buf.readFloat();

        float ox = buf.readFloat();
        float oy = buf.readFloat();
        float oz = buf.readFloat();

        return new  S2CSyncGameData(id, seeker, state, w, h, ox, oy, oz);
    }

    public static void handle( S2CSyncGameData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (Minecraft.getInstance().level != null) {
                        Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
                        if (entity != null) {
                            entity.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                                cap.setSeeker(msg.isSeeker);
                                cap.setDisguise(msg.block);

                                // 玩家真实碰撞尺寸
                                cap.setModelSize(msg.width, msg.height);
                                entity.refreshDimensions();

                                // ✅ 虚拟 OBB 尺寸（不需要 refreshDimensions）
                                cap.setAABBSize(msg.obbX, msg.obbY, msg.obbZ);
                                //cap.setYawLocked(msg.locked);//我添加的
                            });
                        }
                    }
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
