package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.data.IGameData;
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

    // OBB真实尺寸
    private final float obbX;
    private final float obbY;
    private final float obbZ;

    // ✅ 新增：隐身状态
    private final boolean isInvisible;

    // --- 推荐构造函数 ---

    /**
     * ✅ 便捷构造函数：直接从 Capability 对象读取所有数据
     */
    public S2CSyncGameData(int entityId, IGameData cap) {
        this(
                entityId,
                cap.isSeeker(),
                cap.getDisguise(),
                cap.getModelWidth(), cap.getModelHeight(),
                cap.getAABBX(), cap.getAABBY(), cap.getAABBZ(),
                cap.isInvisible() // ✅ 读取隐身状态
        );
    }

    // 全参构造
    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block,
                           float width, float height,
                           float obbX, float obbY, float obbZ,
                           boolean isInvisible) {
        this.entityId = entityId;
        this.isSeeker = isSeeker;
        this.block = block;
        this.width = width;
        this.height = height;
        this.obbX = obbX;
        this.obbY = obbY;
        this.obbZ = obbZ;
        this.isInvisible = isInvisible;
    }

    // --- 旧构造兼容 (保留以防其他地方报错，但建议逐步替换) ---
    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block, float width, float height, float obbX, float obbY, float obbZ) {
        this(entityId, isSeeker, block, width, height, obbX, obbY, obbZ, false);
    }

    public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block) {
        this(entityId, isSeeker, block, 0.6f, 1.8f, 0.6f, 1.8f, 0.6f, false);
    }


    public static void encode(S2CSyncGameData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.isSeeker);
        buf.writeInt(msg.block == null ? -1 : Block.getId(msg.block));

        buf.writeFloat(msg.width);
        buf.writeFloat(msg.height);

        buf.writeFloat(msg.obbX);
        buf.writeFloat(msg.obbY);
        buf.writeFloat(msg.obbZ);

        // ✅ 写入隐身状态
        buf.writeBoolean(msg.isInvisible);
    }

    public static S2CSyncGameData decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        boolean seeker = buf.readBoolean();
        int blockId = buf.readInt();
        BlockState state = blockId == -1 ? null : Block.stateById(blockId);

        float w = buf.readFloat();
        float h = buf.readFloat();

        float ox = buf.readFloat();
        float oy = buf.readFloat();
        float oz = buf.readFloat();

        // ✅ 读取隐身状态
        boolean invis = buf.readBoolean();

        return new S2CSyncGameData(id, seeker, state, w, h, ox, oy, oz, invis);
    }

    public static void handle(S2CSyncGameData msg, Supplier<NetworkEvent.Context> ctx) {
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

                                // ✅ 只有当尺寸真的变化时才刷新，节省性能
                                // (或者直接无脑刷，反正是在 handle 里执行一次也不频繁)
                                entity.refreshDimensions();

                                // 虚拟 OBB 尺寸
                                cap.setAABBSize(msg.obbX, msg.obbY, msg.obbZ);

                                // ✅ 更新客户端的隐身状态
                                cap.setInvisible(msg.isInvisible);
                            });
                        }
                    }
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
