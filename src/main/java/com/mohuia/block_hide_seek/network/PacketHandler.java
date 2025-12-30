package com.mohuia.block_hide_seek.network;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.client.ClientHooks;
import com.mohuia.block_hide_seek.world.BlockWhitelistData; // 确保引用了之前创建的数据类
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        //原有包
        INSTANCE.registerMessage(id++, S2COpenSelectScreen.class, S2COpenSelectScreen::encode, S2COpenSelectScreen::decode, S2COpenSelectScreen::handle);
        INSTANCE.registerMessage(id++, C2SSelectBlock.class, C2SSelectBlock::encode, C2SSelectBlock::decode, C2SSelectBlock::handle);
        INSTANCE.registerMessage(id++, S2CSyncGameData.class, S2CSyncGameData::encode, S2CSyncGameData::decode, S2CSyncGameData::handle);

        // --- 新增：配置界面相关包 ---
        INSTANCE.registerMessage(id++, C2SToggleWhitelist.class, C2SToggleWhitelist::encode, C2SToggleWhitelist::decode, C2SToggleWhitelist::handle);
        INSTANCE.registerMessage(id++, C2SRequestConfig.class, C2SRequestConfig::encode, C2SRequestConfig::decode, C2SRequestConfig::handle);
        INSTANCE.registerMessage(id++, S2COpenConfigScreen.class, S2COpenConfigScreen::encode, S2COpenConfigScreen::decode, S2COpenConfigScreen::handle);
    }

    // ==========================================
    //            原有逻辑 (游戏流程)
    // ==========================================

    public static class S2COpenSelectScreen {
        private final List<BlockState> options;
        public S2COpenSelectScreen(List<BlockState> options) { this.options = options; }
        public static void encode(S2COpenSelectScreen msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.options.size());
            for (BlockState s : msg.options) buf.writeInt(Block.getId(s));
        }
        public static S2COpenSelectScreen decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<BlockState> list = new ArrayList<>();
            for (int i = 0; i < size; i++) list.add(Block.stateById(buf.readInt()));
            return new S2COpenSelectScreen(list);
        }
        public static void handle(S2COpenSelectScreen msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGui(msg.options))
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2SSelectBlock {
        private final BlockState selection;
        public C2SSelectBlock(BlockState s) { this.selection = s; }
        public static void encode(C2SSelectBlock msg, FriendlyByteBuf buf) {
            buf.writeInt(Block.getId(msg.selection));
        }
        public static C2SSelectBlock decode(FriendlyByteBuf buf) {
            return new C2SSelectBlock(Block.stateById(buf.readInt()));
        }
        public static void handle(C2SSelectBlock msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                        cap.setDisguise(msg.selection);
                        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new S2CSyncGameData(player.getId(), cap.isSeeker(), msg.selection));
                        player.refreshDimensions();
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class S2CSyncGameData {
        private final int entityId;
        private final boolean isSeeker;
        private final BlockState block;
        public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block) {
            this.entityId = entityId;
            this.isSeeker = isSeeker;
            this.block = block;
        }
        public static void encode(S2CSyncGameData msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.isSeeker);
            buf.writeInt(msg.block == null ? -1 : Block.getId(msg.block));
        }
        public static S2CSyncGameData decode(FriendlyByteBuf buf) {
            int id = buf.readInt();
            boolean seeker = buf.readBoolean();
            int blockId = buf.readInt();
            BlockState state = blockId == -1 ? null : Block.stateById(blockId);
            return new S2CSyncGameData(id, seeker, state);
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
                                    entity.refreshDimensions();
                                });
                            }
                        }
                    })
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // ==========================================
    //            新增：配置白名单逻辑
    // ==========================================

    // 1. 客户端请求添加/删除方块 (C -> S)
    public static class C2SToggleWhitelist {
        private final BlockState state;
        public C2SToggleWhitelist(BlockState state) { this.state = state; }
        public static void encode(C2SToggleWhitelist msg, FriendlyByteBuf buf) { buf.writeInt(Block.getId(msg.state)); }
        public static C2SToggleWhitelist decode(FriendlyByteBuf buf) { return new C2SToggleWhitelist(Block.stateById(buf.readInt())); }
        public static void handle(C2SToggleWhitelist msg, Supplier<NetworkEvent.Context> ctx) {
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
                    // 操作完后，刷新客户端的UI
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 2. 客户端按下P键，请求打开UI (C -> S)
    public static class C2SRequestConfig {
        public C2SRequestConfig() {}
        public static void encode(C2SRequestConfig msg, FriendlyByteBuf buf) {}
        public static C2SRequestConfig decode(FriendlyByteBuf buf) { return new C2SRequestConfig(); }
        public static void handle(C2SRequestConfig msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    BlockWhitelistData data = BlockWhitelistData.get(player.level());
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 3. 服务端发给客户端：这是最新的白名单，请打开/刷新 UI (S -> C)
    public static class S2COpenConfigScreen {
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
                            ClientHooks.openConfigGui(msg.list))
            );
            ctx.get().setPacketHandled(true);
        }
    }
}
