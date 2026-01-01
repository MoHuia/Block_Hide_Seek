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
import net.minecraft.util.Mth;
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

        // 【新增】模型尺寸请求与响应 (用于 /bhs block 调试)
        INSTANCE.registerMessage(id++, S2CRequestModelData.class, S2CRequestModelData::encode, S2CRequestModelData::decode, S2CRequestModelData::handle);
        INSTANCE.registerMessage(id++, C2SModelSizeResponse.class, C2SModelSizeResponse::encode, C2SModelSizeResponse::decode, C2SModelSizeResponse::handle);
        //左键检查

        INSTANCE.registerMessage(id++, C2SAttackRaycast.class, C2SAttackRaycast::encode, C2SAttackRaycast::decode, C2SAttackRaycast::handle);
        //OBB
        // 【新增】Caps 锁定朝向：客户端->服务端
        INSTANCE.registerMessage(id++, C2SSetYawLock.class, C2SSetYawLock::encode, C2SSetYawLock::decode, C2SSetYawLock::handle);
        // 【新增】Caps 锁定朝向：服务端->客户端（广播同步）
        INSTANCE.registerMessage(id++, S2CSyncYawLock.class, S2CSyncYawLock::encode, S2CSyncYawLock::decode, S2CSyncYawLock::handle);
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

        public static void encode(C2SSelectBlock msg, FriendlyByteBuf buf) {
            buf.writeInt(Block.getId(msg.selection));
            buf.writeFloat(msg.width);
            buf.writeFloat(msg.height);

            // 追加写入 OBB
            buf.writeFloat(msg.obbX);
            buf.writeFloat(msg.obbY);
            buf.writeFloat(msg.obbZ);
        }

        public static C2SSelectBlock decode(FriendlyByteBuf buf) {
            BlockState s = Block.stateById(buf.readInt());
            float w = buf.readFloat();
            float h = buf.readFloat();

            // 兼容：如果未来你怕老客户端/老包，会需要判断剩余字节。
            // 但你现在是同一mod版本一起更新，直接读即可：
            float ox = buf.readFloat();
            float oy = buf.readFloat();
            float oz = buf.readFloat();

            return new C2SSelectBlock(s, w, h, ox, oy, oz);
        }

        public static void handle(C2SSelectBlock msg, Supplier<NetworkEvent.Context> ctx) {
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

    public static class S2CSyncGameData {
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

        public static void encode(S2CSyncGameData msg, FriendlyByteBuf buf) {
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

            return new S2CSyncGameData(id, seeker, state, w, h, ox, oy, oz);
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

    // ==========================================
//        ✅ 新增：左键触发服务端射线检测
// ==========================================
    public static class C2SAttackRaycast {

        /** debug 粒子开关：你也可以改成读取服务端 config */
        private final boolean debugParticles;

        public C2SAttackRaycast(boolean debugParticles) {
            this.debugParticles = debugParticles;
        }

        public static void encode(C2SAttackRaycast msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.debugParticles);
        }

        public static C2SAttackRaycast decode(FriendlyByteBuf buf) {
            return new C2SAttackRaycast(buf.readBoolean());
        }

        public static void handle(C2SAttackRaycast msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                // ✅ 服务端判断：游戏进行中才处理
                com.mohuia.block_hide_seek.game.GameLoopManager.onSeekerLeftClickRaycast(player, msg.debugParticles);
            });
            ctx.get().setPacketHandled(true);
        }
    }
    public static class C2SSetYawLock {
        private final boolean locked;
        private final float yawDeg; // 锁定角度（度）

        public C2SSetYawLock(boolean locked, float yawDeg) {
            this.locked = locked;
            this.yawDeg = yawDeg;
        }

        public static void encode(C2SSetYawLock msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.locked);
            buf.writeFloat(msg.yawDeg);
        }

        public static C2SSetYawLock decode(FriendlyByteBuf buf) {
            boolean locked = buf.readBoolean();
            float yaw = buf.readFloat();
            return new C2SSetYawLock(locked, yaw);
        }

        public static void handle(C2SSetYawLock msg, Supplier<NetworkEvent.Context> ctx) {
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
    public static class S2CSyncYawLock {
        private final int entityId;
        private final boolean locked;
        private final float lockedYawDeg;

        public S2CSyncYawLock(int entityId, boolean locked, float lockedYawDeg) {
            this.entityId = entityId;
            this.locked = locked;
            this.lockedYawDeg = lockedYawDeg;
        }

        public static void encode(S2CSyncYawLock msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.locked);
            buf.writeFloat(msg.lockedYawDeg);
        }

        public static S2CSyncYawLock decode(FriendlyByteBuf buf) {
            int id = buf.readInt();
            boolean locked = buf.readBoolean();
            float yaw = buf.readFloat();
            return new S2CSyncYawLock(id, locked, yaw);
        }

        public static void handle(S2CSyncYawLock msg, Supplier<NetworkEvent.Context> ctx) {
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

}
