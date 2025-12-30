package com.mohuia.block_hide_seek.network;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.client.ClientHooks;
import com.mohuia.block_hide_seek.world.BlockWhitelistData; // 确保引用了之前创建的数据类
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
    //版本协议号，防止数据错误（必须）
    private static final String PROTOCOL = "1";
    //SimpleChannel专属通道，服务端和客户端都通过这个专属通道进行数据传输
    //NetworkRegistry.newSimpleChannel创建一条新的路线
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            //给通道起名，“模组名+main”，防止和其他模组的网络通道冲突（重要）
            ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "main"),
            //当前端的协议版本
            () -> PROTOCOL,
            //判断对方的协议版本是否和自己一致
            //第一个判断服务端是否兼容客户端
            PROTOCOL::equals,
            //第二个判断客户端是否兼容服务端
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        //给网络通道注册所有需要的数据包类型
        //C2S:Client to Server;S2C:Server to Client
        //编码（encode）:把 Java 对象（数据包内容）转换成「字节流」;
        //解码（decode）:把收到的「字节流」转换回 Java 对象，方便程序处理;
        //处理（handle）:对接收到的数据包进行业务逻辑处理

        INSTANCE.registerMessage(id++, S2COpenSelectScreen.class, S2COpenSelectScreen::encode, S2COpenSelectScreen::decode, S2COpenSelectScreen::handle);
        INSTANCE.registerMessage(id++, C2SSelectBlock.class, C2SSelectBlock::encode, C2SSelectBlock::decode, C2SSelectBlock::handle);
        INSTANCE.registerMessage(id++, S2CSyncGameData.class, S2CSyncGameData::encode, S2CSyncGameData::decode, S2CSyncGameData::handle);
        INSTANCE.registerMessage(id++, C2SToggleWhitelist.class, C2SToggleWhitelist::encode, C2SToggleWhitelist::decode, C2SToggleWhitelist::handle);
        INSTANCE.registerMessage(id++, C2SRequestConfig.class, C2SRequestConfig::encode, C2SRequestConfig::decode, C2SRequestConfig::handle);
        INSTANCE.registerMessage(id++, S2COpenConfigScreen.class, S2COpenConfigScreen::encode, S2COpenConfigScreen::decode, S2COpenConfigScreen::handle);
        INSTANCE.registerMessage(id++, C2SUpdateGameSettings.class, C2SUpdateGameSettings::encode, C2SUpdateGameSettings::decode, C2SUpdateGameSettings::handle);
    }

    // ==========================================
    //            原有逻辑 (游戏流程)
    // ==========================================

    //服务端通知客户端「打开方块选择界面」
    public static class S2COpenSelectScreen {
        // 1. 数据包携带的数据：方块状态列表（要显示的可选方块）
        private final List<BlockState> options;
        // 2. 编码：把方块列表转换成字节流
        public S2COpenSelectScreen(List<BlockState> options) { this.options = options; }
        public static void encode(S2COpenSelectScreen msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.options.size());// 先写列表大小，方便接收端读取
            for (BlockState s : msg.options) buf.writeInt(Block.getId(s));//逐个写入方块的ID
        }
        // 3. 解码：把字节流转换回方块列表
        public static S2COpenSelectScreen decode(FriendlyByteBuf buf) {
            int size = buf.readInt();// 先读取列表大小
            List<BlockState> list = new ArrayList<>();//存入集合
            for (int i = 0; i < size; i++) list.add(Block.stateById(buf.readInt()));// 逐个读取方块ID，转换为方块状态
            return new S2COpenSelectScreen(list);
        }
        // 4. 处理：客户端收到消息后，打开方块选择界面
        public static void handle(S2COpenSelectScreen msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    // 只在客户端执行（打开界面是客户端的UI操作）
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGui(msg.options))
            );
            ctx.get().setPacketHandled(true);// 标记消息已处理，避免内存泄漏
        }
    }

    //客户端发给服务端「我选择了这个方块」
    public static class C2SSelectBlock {
        //携带的数据：玩家选择的方块状态
        private final BlockState selection;
        public C2SSelectBlock(BlockState s) { this.selection = s; }
        //编码：把选择的方块转换成整数ID，写入字节流
        public static void encode(C2SSelectBlock msg, FriendlyByteBuf buf) {
            buf.writeInt(Block.getId(msg.selection));
        }
        // 解码：读取整数ID，转换回方块状态
        public static C2SSelectBlock decode(FriendlyByteBuf buf) {
            return new C2SSelectBlock(Block.stateById(buf.readInt()));
        }
        //处理：服务端收到后，保存玩家的方块选择，并同步给其他客户端
        public static void handle(C2SSelectBlock msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();//获取发送这个数据包的玩家对象
                if (player != null) {
                    // 获取玩家的游戏数据，保存选择的方块（伪装形态）
                    player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                        cap.setDisguise(msg.selection);
                        // 同步数据给所有跟踪该玩家的客户端（让其他玩家看到该玩家的伪装形态）
                        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                                new S2CSyncGameData(player.getId(), cap.isSeeker(), msg.selection));
                        player.refreshDimensions();
                    });
                }
            });
            ctx.get().setPacketHandled(true);// 标记消息已处理，避免内存泄漏
        }
    }

    public static class S2CSyncGameData {
        private final int entityId;//玩家实体ID
        private final boolean isSeeker;//是否为抓捕者
        private final BlockState block;//伪装的方块状态

        //构造方法，初始化携带的数据
        //编码：依次写入实体ID、布尔值、方块ID（注意方块可能为null，用-1标记）
        public S2CSyncGameData(int entityId, boolean isSeeker, BlockState block) {
            this.entityId = entityId;
            this.isSeeker = isSeeker;
            this.block = block;
        }
        //解码：依次读取数据，转换回Java对象
        public static void encode(S2CSyncGameData msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.isSeeker);
            buf.writeInt(msg.block == null ? -1 : Block.getId(msg.block));
        }
        //处理：客户端收到后，更新对应玩家的游戏数据
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
                        // 获取当前客户端的世界
                        if (Minecraft.getInstance().level != null) {
                            // 通过实体ID找到对应的玩家
                            Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
                            if (entity != null) {
                                // 更新玩家的游戏数据（是否是寻找者、伪装方块）
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
        private final BlockState state;// 要添加/删除的方块状态
        //构造方法
        // 编码：方块转ID写入字节流
        public C2SToggleWhitelist(BlockState state) { this.state = state; }
        // 解码：读取ID转回方块
        public static void encode(C2SToggleWhitelist msg, FriendlyByteBuf buf) { buf.writeInt(Block.getId(msg.state)); }
        public static C2SToggleWhitelist decode(FriendlyByteBuf buf) { return new C2SToggleWhitelist(Block.stateById(buf.readInt())); }
        public static void handle(C2SToggleWhitelist msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    // 1. 获取服务端的白名单数据（之前写的BlockWhitelistData）
                    BlockWhitelistData data = BlockWhitelistData.get(player.level());
                    // 2. 判断方块是否已在白名单中
                    List<BlockState> current = data.getAllowedStates();
                    boolean exists = current.stream().anyMatch(s -> s.getBlock() == msg.state.getBlock());

                    // 3. 存在则删除，不存在则添加，并给玩家发送提示消息
                    if (exists) {
                        data.removeBlock(msg.state);
                        player.sendSystemMessage(Component.literal("❌ 已移除: " + msg.state.getBlock().getName().getString()));
                    } else {
                        data.addBlock(msg.state);
                        player.sendSystemMessage(Component.literal("✅ 已添加: " + msg.state.getBlock().getName().getString()));
                    }
                    // 4. 操作完成后，给该玩家发送数据包，刷新客户端的配置界面
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 2. 客户端按下P键，请求打开UI (C -> S)
    public static class C2SRequestConfig {
        // 无参数构造方法（这个数据包不需要携带额外数据，只做请求）
        public C2SRequestConfig() {}

        // 无数据需要编码，方法为空
        public static void encode(C2SRequestConfig msg, FriendlyByteBuf buf) {}
        // 无数据需要解码，直接新建对象
        public static C2SRequestConfig decode(FriendlyByteBuf buf) { return new C2SRequestConfig(); }

        public static void handle(C2SRequestConfig msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                // 1. 获取服务端最新的白名单数据
                if (player != null) {
                    BlockWhitelistData data = BlockWhitelistData.get(player.level());
                    // 2. 给客户端发送数据包，让客户端打开配置界面并传入最新白名单
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenConfigScreen(data.getAllowedStates()));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // 3. 服务端发给客户端：这是最新的白名单，请打开/刷新 UI (S -> C)
    public static class S2COpenConfigScreen {
        // 最新的白名单方块列表
        private final List<BlockState> list;
        public S2COpenConfigScreen(List<BlockState> list) { this.list = list; }

        // 编码：先写列表大小，再逐个写入方块ID
        public static void encode(S2COpenConfigScreen msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.list.size());
            for(BlockState s : msg.list) buf.writeInt(Block.getId(s));
        }
        // 解码：读取列表大小，再逐个读取方块ID，转换回方块列表
        public static S2COpenConfigScreen decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<BlockState> l = new ArrayList<>();
            for(int i=0; i<size; i++) l.add(Block.stateById(buf.readInt()));
            return new S2COpenConfigScreen(l);
        }
        // 处理：客户端收到后，打开/刷新配置界面
        public static void handle(S2COpenConfigScreen msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientHooks.openConfigGui(msg.list))// 调用客户端方法，打开配置界面
            );
            ctx.get().setPacketHandled(true);
        }
    }

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
                if (player != null && player.hasPermissions(2)) { // 只有管理员能改
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
}
