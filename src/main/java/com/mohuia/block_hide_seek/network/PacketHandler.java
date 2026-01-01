package com.mohuia.block_hide_seek.network;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.ClientHooks;
import com.mohuia.block_hide_seek.client.ClientModelHelper;
import com.mohuia.block_hide_seek.client.ConfigScreen;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
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
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, S2COpenSelectScreen.class, S2COpenSelectScreen::encode, S2COpenSelectScreen::decode, S2COpenSelectScreen::handle);
        INSTANCE.registerMessage(id++, C2SSelectBlock.class, C2SSelectBlock::encode, C2SSelectBlock::decode, C2SSelectBlock::handle);
        INSTANCE.registerMessage(id++, S2CSyncGameData.class, S2CSyncGameData::encode, S2CSyncGameData::decode, S2CSyncGameData::handle);

        // 配置相关
        INSTANCE.registerMessage(id++, C2SToggleWhitelist.class, C2SToggleWhitelist::encode, C2SToggleWhitelist::decode, C2SToggleWhitelist::handle);
        INSTANCE.registerMessage(id++, C2SRequestConfig.class, C2SRequestConfig::encode, C2SRequestConfig::decode, C2SRequestConfig::handle);
        INSTANCE.registerMessage(id++, S2COpenConfigScreen.class, S2COpenConfigScreen::encode, S2COpenConfigScreen::decode, S2COpenConfigScreen::handle);
        INSTANCE.registerMessage(id++, C2SUpdateGameSettings.class, C2SUpdateGameSettings::encode, C2SUpdateGameSettings::decode, C2SUpdateGameSettings::handle);

        // 【新增】静默更新广播
        INSTANCE.registerMessage(id++, S2CUpdateConfigGui.class, S2CUpdateConfigGui::encode, S2CUpdateConfigGui::decode, S2CUpdateConfigGui::handle);

        // 【新增】静默更新广播
        INSTANCE.registerMessage(id++, S2CUpdateConfigGui.class, S2CUpdateConfigGui::encode, S2CUpdateConfigGui::decode, S2CUpdateConfigGui::handle);

        // 【新增】模型尺寸请求与响应 (用于 /bhs block 调试)
        INSTANCE.registerMessage(id++, S2CRequestModelData.class, S2CRequestModelData::encode, S2CRequestModelData::decode, S2CRequestModelData::handle);
        INSTANCE.registerMessage(id++, C2SModelSizeResponse.class, C2SModelSizeResponse::encode, C2SModelSizeResponse::decode, C2SModelSizeResponse::handle);
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

    // 1. 客户端选方块 -> 服务端 (带尺寸)
    public static class C2SSelectBlock {
        private final BlockState selection;
        private final float width;
        private final float height;

        // 构造函数
        public C2SSelectBlock(BlockState s, float width, float height) {
            this.selection = s;
            this.width = width;
            this.height = height;
        }

        // 编码
        public static void encode(C2SSelectBlock msg, FriendlyByteBuf buf) {
            buf.writeInt(Block.getId(msg.selection));
            buf.writeFloat(msg.width);
            buf.writeFloat(msg.height);
        }

        // 解码
        public static C2SSelectBlock decode(FriendlyByteBuf buf) {
            return new C2SSelectBlock(Block.stateById(buf.readInt()), buf.readFloat(), buf.readFloat());
        }

        // 处理
        public static void handle(C2SSelectBlock msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                        // 服务端保存客户端传来的尺寸
                        cap.setDisguise(msg.selection);
                        cap.setModelSize(msg.width, msg.height);

                        // 同步给所有人 (包含尺寸)
                        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new S2CSyncGameData(player.getId(), cap.isSeeker(), msg.selection, msg.width, msg.height));

                        player.refreshDimensions(); // 立即刷新碰撞箱
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 2. 服务端 -> 所有客户端 (同步尺寸)
    public static class S2CSyncGameData {
        private final int entityId;
        private final boolean isSeeker;
        private final BlockState block;
        private final float width;  // 移除 = 0.5f
        private final float height; // 移除 = 1.0f

        // 构造函数
        public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block, float width, float height) {
            this.entityId = entityId;
            this.isSeeker = isSeeker;
            this.block = block;
            this.width = width;
            this.height = height;
        }

        // 旧的构造函数重载 (保持兼容性可选，或者直接删掉只用新的)
        // 为了避免报错，建议把所有调用旧构造函数的地方都改掉，或者保留一个默认值的重载
        public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block) {
            this(entityId, isSeeker, block, 0.5f, 1.0f);
        }

        public static void encode(S2CSyncGameData msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.isSeeker);
            buf.writeInt(msg.block == null ? -1 : Block.getId(msg.block));
            buf.writeFloat(msg.width);
            buf.writeFloat(msg.height);
        }

        public static S2CSyncGameData decode(FriendlyByteBuf buf) {
            int id = buf.readInt();
            boolean seeker = buf.readBoolean();
            int blockId = buf.readInt();
            BlockState state = blockId == -1 ? null : Block.stateById(blockId);
            float w = buf.readFloat();
            float h = buf.readFloat();
            return new S2CSyncGameData(id, seeker, state, w, h);
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
                                    cap.setModelSize(msg.width, msg.height); // 客户端同步数据
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
    //            配置白名单逻辑
    // ==========================================

    // 1. 客户端请求添加/删除方块 (修改后：广播更新)
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

                    // 【关键修改】发送 S2CUpdateConfigGui 给所有玩家 (静默刷新)
                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CUpdateConfigGui(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 2. 客户端 P 键请求打开 UI
    public static class C2SRequestConfig {
        public C2SRequestConfig() {}
        public static void encode(C2SRequestConfig msg, FriendlyByteBuf buf) {}
        public static C2SRequestConfig decode(FriendlyByteBuf buf) { return new C2SRequestConfig(); }
        public static void handle(C2SRequestConfig msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    BlockWhitelistData data = BlockWhitelistData.get(player.level());
                    // 只有请求者打开窗口
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 3. 服务端 -> 客户端：强制打开窗口
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
                            ClientHooks.openConfigGui(msg.list)) // 强制打开
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // 4. 【新增】服务端 -> 客户端：静默刷新数据 (如果窗口开着)
    public static class S2CUpdateConfigGui {
        private final List<BlockState> list;
        public S2CUpdateConfigGui(List<BlockState> list) { this.list = list; }
        public static void encode(S2CUpdateConfigGui msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.list.size());
            for(BlockState s : msg.list) buf.writeInt(Block.getId(s));
        }
        public static S2CUpdateConfigGui decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<BlockState> l = new ArrayList<>();
            for(int i=0; i<size; i++) l.add(Block.stateById(buf.readInt()));
            return new S2CUpdateConfigGui(l);
        }
        public static void handle(S2CUpdateConfigGui msg, Supplier<NetworkEvent.Context> ctx) {
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

    // 5. 游戏规则更新
    public static class C2SUpdateGameSettings {
        private final int duration;
        private final int hits;
        private final int seekers;

        public C2SUpdateGameSettings(int duration, int hits, int seekers) {
            this.duration = duration;
            this.hits = hits;
            this.seekers = seekers;
        }

        public static void encode(C2SUpdateGameSettings msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.duration);
            buf.writeInt(msg.hits);
            buf.writeInt(msg.seekers);
        }

        public static C2SUpdateGameSettings decode(FriendlyByteBuf buf) {
            return new C2SUpdateGameSettings(buf.readInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(C2SUpdateGameSettings msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    ServerGameConfig config = ServerGameConfig.get(player.level());
                    config.gameDurationSeconds = msg.duration;
                    config.hitsToConvert = msg.hits;
                    config.seekerCount = msg.seekers;
                    config.setDirty();
                    player.sendSystemMessage(Component.literal("✅ 游戏设置已更新！"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    // ==========================================
    //            模型调试逻辑 (调试用)
    // ==========================================

    // 1. 服务端 -> 客户端：请求计算当前手持物品的模型尺寸
    public static class S2CRequestModelData {
        public S2CRequestModelData() {}
        public static void encode(S2CRequestModelData msg, FriendlyByteBuf buf) {}
        public static S2CRequestModelData decode(FriendlyByteBuf buf) { return new S2CRequestModelData(); }
        public static void handle(S2CRequestModelData msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    // 安全地调用客户端代码，避免服务端崩溃
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientModelHelper.handleRequest())
            );
            ctx.get().setPacketHandled(true);
        }
    }

    // 2. 客户端 -> 服务端：返回计算好的尺寸 + 调试日志
    public static class C2SModelSizeResponse {
        private final float width;
        private final float height;
        private final String blockName;
        private final String debugLog; // 【新增】调试日志

        public C2SModelSizeResponse(float width, float height, String blockName, String debugLog) {
            this.width = width;
            this.height = height;
            this.blockName = blockName;
            this.debugLog = debugLog;
        }

        public static void encode(C2SModelSizeResponse msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.width);
            buf.writeFloat(msg.height);
            buf.writeUtf(msg.blockName);
            buf.writeUtf(msg.debugLog); // 【新增】
        }

        public static C2SModelSizeResponse decode(FriendlyByteBuf buf) {
            return new C2SModelSizeResponse(buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(C2SModelSizeResponse msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    // 1. 打印详细调试日志
                    player.sendSystemMessage(Component.literal("§e=== 模型分析报告 ==="));
                    player.sendSystemMessage(Component.literal("§7方块: " + msg.blockName));

                    // 将日志按行打印
                    String[] logs = msg.debugLog.split("\n");
                    for (String log : logs) {
                        player.sendSystemMessage(Component.literal("§8" + log));
                    }

                    player.sendSystemMessage(Component.literal(String.format("§b[最终结果] 宽: %.2f | 高: %.2f", msg.width, msg.height)));
                    player.sendSystemMessage(Component.literal("§e======================"));

                    // 2. 生成实体建议
                    player.sendSystemMessage(Component.literal("📋 建议代码: EntityDimensions.fixed(" + msg.width + "F, " + msg.height + "F)"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    

    
}
