package com.mohuia.block_hide_seek.packet.C2S;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SSelectBlock {
        private final BlockState selection;
        private final float width;   // modelW
        private final float height;  // modelH

        // 新增：OBB真实尺寸
        private final float obbX;
        private final float obbY;
        private final float obbZ;

        // 新构造：带 OBB
        public C2SSelectBlock(BlockState s, float width, float height, float obbX, float obbY, float obbZ) {
            this.selection = s;
            this.width = width;
            this.height = height;
            this.obbX = obbX;
            this.obbY = obbY;
            this.obbZ = obbZ;
        }

        // 旧构造保留兼容：没传 OBB 时，用宽高推一个默认（比如 x=z=width, y=height）
        public C2SSelectBlock(BlockState s, float width, float height) {
            this(s, width, height, width, height, width);
        }

        public static void encode( C2SSelectBlock msg, FriendlyByteBuf buf) {
            buf.writeInt(Block.getId(msg.selection));
            buf.writeFloat(msg.width);
            buf.writeFloat(msg.height);

            // 追加写入 OBB
            buf.writeFloat(msg.obbX);
            buf.writeFloat(msg.obbY);
            buf.writeFloat(msg.obbZ);
        }

        public static  C2SSelectBlock decode(FriendlyByteBuf buf) {
            BlockState s = Block.stateById(buf.readInt());
            float w = buf.readFloat();
            float h = buf.readFloat();

            // 兼容：如果未来你怕老客户端/老包，会需要判断剩余字节。
            // 但你现在是同一mod版本一起更新，直接读即可：
            float ox = buf.readFloat();
            float oy = buf.readFloat();
            float oz = buf.readFloat();

            return new  C2SSelectBlock(s, w, h, ox, oy, oz);
        }

        public static void handle( C2SSelectBlock msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                        cap.setDisguise(msg.selection);

                        // 玩家真实碰撞尺寸
                        cap.setModelSize(msg.width, msg.height);

                        // ✅ 虚拟 OBB 尺寸（真实尺寸）
                        cap.setAABBSize(msg.obbX, msg.obbY, msg.obbZ);

                        // 同步给所有人：除了原来的 modelW/H，也同步 OBB
                        PacketHandler.INSTANCE.send(
                                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new S2CSyncGameData(
                                        player.getId(),
                                        cap.isSeeker(),
                                        msg.selection,
                                        msg.width,
                                        msg.height,
                                        msg.obbX,
                                        msg.obbY,
                                        msg.obbZ
                                )
                        );

                        player.refreshDimensions();
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }

}
