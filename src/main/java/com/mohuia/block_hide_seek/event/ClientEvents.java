package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.packet.C2S.C2SAttackRaycast;
import com.mohuia.block_hide_seek.packet.C2S.C2SRequestConfig;
import com.mohuia.block_hide_seek.packet.C2S.C2SSetYawLock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.KeyInit;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端核心事件处理类
 * 负责：渲染伪装方块、处理按键逻辑、发送攻击请求
 */
@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT)
public class ClientEvents {

    // ==========================================
    // 状态控制变量 (State Variables)
    // ==========================================

    // 锁定的朝向角度 (null 表示未锁定，非 null 表示锁定在特定角度)
    private static Float lockedBodyYaw = null;

    //======吸附=======
    // 是否激活了位置吸附对齐，这个的目的是为了平滑吸附而不是瞬间位移
    private static boolean isAlignActive = false;
    // 角度修正的起点/终点（单位：度）
    private static float alignYawStart = 0.0f;
    private static float alignYawTarget = 0.0f;
    // 进度 0~1（每 帧 增长）
    private static float alignYawT = 1.0f;
    // 本次吸附的持续时间（秒）——你可以调：0.12~0.25 都常见
    private static final float ALIGN_YAW_DURATION_SEC = 0.28f;

    //======锁定=======
    // 是否激活了旋转锁定模式
    private static boolean isRotationLocked = false;
    // 上次发送攻击包的时间 (用于防抖)
    private static long lastSendMs = 0;

    // 模型中心点偏移缓存 (避免每帧重复计算模型包围盒)
    private static final Map<BlockState, float[]> MODEL_OFFSET_CACHE = new HashMap<>();

    // ==========================================
    // 渲染逻辑 (Rendering)
    // ==========================================

    /**
     * 在玩家渲染之前触发。
     * 如果玩家是伪装者，取消原版玩家渲染，改为渲染方块。
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        // 获取 Capability 数据
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {

            // ✅ 新增：优先处理隐身逻辑
            if (cap.isInvisible()) {
                // 彻底取消渲染：既不画人，也不画方块
                event.setCanceled(true);
                return;
            }

            // 如果是抓捕者(Seeker)，不做任何处理，直接返回
            if (cap.isSeeker()) return;

            BlockState state = cap.getDisguise();
            // 如果有伪装数据，且不是隐形方块
            if (state != null && state.getRenderShape() != RenderShape.INVISIBLE) {
                // 1. 取消原版玩家渲染 (不画史蒂夫/艾利克斯)
                event.setCanceled(true);
                // 2. 执行自定义方块渲染
                renderDisguise(event, state);
            }
        });
    }

    /**
     * 核心渲染方法：决定如何画出伪装的方块/物品
     */
    private static void renderDisguise(RenderPlayerEvent.Pre event, BlockState state) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Player player = event.getEntity();
        float renderYaw;

        // --- [修改点] 角度计算逻辑 ---

        // 1. 如果开启了【方向锁定】(Caps Lock)，且渲染的是当前玩家自己
        //    则强制使用锁定时的角度，无视当前鼠标朝向，实现“自由观察”
        if (lockedBodyYaw != null && player == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        }
        // 2. 否则，完全跟随【玩家准星/视线】(View Yaw)
        //    这样操作更灵敏，指哪打哪，不再有身体转动的延迟
        else {
            float partialTick = event.getPartialTick();
            renderYaw = player.getViewYRot(partialTick);
        }

        // 应用旋转 (注意：Minecraft 渲染旋转通常取反)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        Level level = player.level();

        // 根据方块类型分发渲染任务：

        // A. 实体方块 (如：箱子、告示牌，通常用 Item 渲染更稳)
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            renderEntityBlockAsItem(event, poseStack, state);
        }
        // B. 3D 物品模型 (如：梯子、火把、栅栏等，作为物品渲染位置更正)
        else if (shouldRenderAsItem(state, level)) {
            ItemStack stack = new ItemStack(state.getBlock());
            BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
            renderItemWithAutoCenter(event, poseStack, state, stack, itemModel);
        }
        // C. 普通方块 (如：石头、泥土，直接渲染 BlockModel)
        else {
            renderBlockManually(event, poseStack, state);
        }

        poseStack.popPose();
    }

    /**
     * 隐藏玩家头顶的名字标签
     * 躲猫猫模式下，伪装者不应该显示名字
     */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 如果不是抓捕者(即伪装者)，且有伪装状态 -> 隐藏名字
                if (!cap.isSeeker() && cap.getDisguise() != null) {
                    event.setResult(Event.Result.DENY);
                }
            });
        }
    }

    // ==========================================
    // 渲染辅助方法 (Helper Methods)
    // ==========================================

    /**
     * 判断是否应该作为物品渲染 (比如火把、梯子这些非全立方体)
     */
    private static boolean shouldRenderAsItem(BlockState state, Level level) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return false;
        // 检查物品模型是否是 GUI 3D 属性 (通常意味着它在手中也是立体的)
        BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        return itemModel.isGui3d();
    }

    /**
     * 渲染物品模式，并自动居中
     */
    private static void renderItemWithAutoCenter(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state, ItemStack stack, BakedModel model) {
        poseStack.pushPose();

        // 计算偏移量，确保视觉中心在脚下
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        float transX = 0.5f - offsets[0];
        float transY = 0.5f - offsets[1];
        float transZ = 0.5f - offsets[2];

        poseStack.translate(transX, transY, transZ);

        // 获取光照
        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);

        // 调用原版 ItemRenderer
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, event.getMultiBufferSource(), event.getEntity().level(), 0);

        poseStack.popPose();
    }

    /**
     * 手动渲染普通方块 (BlockModel)
     * 支持多层纹理和半透明
     */
    private static void renderBlockManually(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);
        Level level = event.getEntity().level();
        BlockPos pos = event.getEntity().blockPosition();

        // 偏移修正
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));

        poseStack.pushPose();
        // 反向偏移，把模型拉回到原点中心
        poseStack.translate(-offsets[0], -offsets[1], -offsets[2]);

        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(renderType);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(level, lightPos);
        long fixedSeed = state.getSeed(BlockPos.ZERO);

        // 渲染下半部分
        renderSingleBlockModel(poseStack, buffer, model, state, light, fixedSeed, level, pos);

        // 特殊处理：如果是双层方块(如高草/门)的下半截，尝试自动渲染上半截
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            BakedModel upperModel = blockRenderer.getBlockModel(upperState);

            poseStack.pushPose();
            poseStack.translate(0.0, 1.0, 0.0); // 向上移动一格
            renderSingleBlockModel(poseStack, buffer, upperModel, upperState, light, fixedSeed, level, pos);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * 渲染实体方块 (EntityBlock) 代理为物品渲染
     */
    private static void renderEntityBlockAsItem(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        // 大多数实体方块物品模型中心在底部，往上提 0.5 看起来更自然
        poseStack.translate(0.0, 0.5, 0.0);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);

        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, event.getMultiBufferSource(), event.getEntity().level(), 0);

        poseStack.popPose();
    }

    /**
     * 底层方法：遍历 Quad 进行绘制
     */
    private static void renderSingleBlockModel(PoseStack poseStack, VertexConsumer buffer, BakedModel model, BlockState state, int light, long seed, Level level, BlockPos pos) {
        RandomSource rand = RandomSource.create();
        // 遍历所有方向 + null 方向
        for (Direction dir : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            rand.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, dir, rand);
            for (BakedQuad quad : quads) {
                float r = 1f, g = 1f, b = 1f;
                // 处理硬编码颜色 (如草地颜色)
                if (quad.isTinted()) {
                    int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, quad.getTintIndex());
                    r = (float)(color >> 16 & 255) / 255.0F;
                    g = (float)(color >> 8 & 255) / 255.0F;
                    b = (float)(color & 255) / 255.0F;
                }
                buffer.putBulkData(poseStack.last(), quad, r, g, b, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    /**
     * 计算模型的几何中心点 (用于居中校正)
     */
    private static float[] calculateModelOffsets(BakedModel model) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean found = false;

        RandomSource rand = RandomSource.create();
        for (Direction dir : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            List<BakedQuad> quads = model.getQuads(null, dir, rand);
            for (BakedQuad quad : quads) {
                int[] vertices = quad.getVertices();
                for (int i = 0; i < 4; i++) {
                    int offset = i * 8;
                    float x = Float.intBitsToFloat(vertices[offset]);
                    float y = Float.intBitsToFloat(vertices[offset + 1]);
                    float z = Float.intBitsToFloat(vertices[offset + 2]);

                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                    found = true;
                }
            }
        }

        // 如果模型是空的，默认居中
        if (!found) return new float[]{0.5f, 0.0f, 0.5f};

        // X 和 Z 取中点，Y 取最低点 (保证方块站在地上)
        float midX = (minX + maxX) / 2.0f;
        float midZ = (minZ + maxZ) / 2.0f;
        return new float[]{midX, minY, midZ};
    }


    // ==========================================
    // 逻辑控制与按键 (Logic & Input)
    // ==========================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 定期清理缓存 (防止内存泄漏)
        if (player.tickCount % 1200 == 0) {
            MODEL_OFFSET_CACHE.clear();
        }

        // --- 物理高度修正 ---
        // 确保客户端的碰撞箱高度和伪装数据一致
        float expectedHeight = player.getDimensions(player.getPose()).height;
        float actualHeight = player.getBbHeight();
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }

        // --- 打开菜单 (按键: O) ---
        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new C2SRequestConfig());
        }

        // --- 移动、对齐与锁定逻辑 ---
        handleControlLogic(player);
    }

    private static void handleControlLogic(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 如果不是伪装状态，重置所有逻辑
            if (cap.isSeeker() || cap.getDisguise() == null) {
                resetStates();
                return;
            }

            // 1. 检测玩家是否正在移动
            boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;
            boolean isJumping = player.input.jumping;
            boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.005;

            // 2. 移动保护：如果有移动输入，强制取消所有锁定状态
            //    (避免玩家忘记解锁导致操作困难)
            if (hasInput || isJumping || (!player.onGround() && isMoving)) {
                if (isAlignActive || isRotationLocked) {
                    resetStates();
                    player.displayClientMessage(Component.literal("§c已解除锁定"), true);
                    PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(false, 0.0f));
                }
            }
            // 3. 静止状态：允许功能切换
            else {
                // [Alt] 切换位置对齐
                if (KeyInit.TOGGLE_ALIGN.consumeClick()) {
                    // 即便是修正过程中按按钮，也不影响正常修正
                    if (!isAlignActive) {
                        isAlignActive = true;

                        // 起点：当前身体 yaw
                        alignYawStart = player.getYRot();

                        // 终点：以视角 yaw 吸附到最近 90°
                        float viewYaw = player.getViewYRot(1.0f);
                        alignYawTarget = (float) (Math.round(viewYaw / 90.0f) * 90.0f);

                        // 进度归零——之后由“按帧事件”推进
                        alignYawT = 0.0f;
                    }
                }

                // [Caps Lock] 切换方向锁定
                if (KeyInit.LOCK_ROTATION.consumeClick()) {
                    isRotationLocked = !isRotationLocked;

                    if (isRotationLocked) {
                        lockedBodyYaw = player.getViewYRot(1.0f);
                        player.displayClientMessage(Component.literal("§a方向锁定: 开启 (自由视角)"), true);
                        // ✅ 发包通知服务端（锁定 + yaw）
                        PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(true, lockedBodyYaw));
                    } else {
                        lockedBodyYaw = null;
                        player.displayClientMessage(Component.literal("§c方向锁定: 关闭"), true);
                        // ✅ 发包通知服务端（解除锁定）
                        PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(false, 0.0f));
                    }
                }
                // 执行对齐逻辑 (吸附到方块中心)
                if (isAlignActive) {//每帧进行
                    performPositionSnap(player);
                }
                // 维护状态同步
                if (!isRotationLocked) {
                    lockedBodyYaw = null;
                }
            }
        });
    }

    private static void resetStates() {
        isAlignActive = false;
        isRotationLocked = false;
        lockedBodyYaw = null;
    }

    /**
     * 位置吸附逻辑：平滑地将玩家拉向方块中心 (x.5, z.5)
     */
    private static void performPositionSnap(LocalPlayer player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();

        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;

        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;

        player.setPos(newX, player.getY(), newZ);
    }

    /**
     * 注册额外的客户端按键
     */
    @Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.OPEN_CONFIG); // 菜单
            event.register(KeyInit.TOGGLE_ALIGN); // 对齐 (Alt)
            event.register(KeyInit.LOCK_ROTATION); // 锁定 (Caps Lock)
        }
    }

    /**
     * 攻击判定入口
     * 监听左键，发送射线检测包
     */
    @SubscribeEvent
    public static void onLeftClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return; // 必须是左键攻击

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return; // 打开 GUI 时不触发

        // 防抖：避免按住左键时每 Tick 都发包，导致服务器过载
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 50) return; // 50ms 冷却
        lastSendMs = now;

        // 发送攻击请求到服务端
        boolean debugParticles = false; // 调试开关
        PacketHandler.INSTANCE.sendToServer(new C2SAttackRaycast(debugParticles));
    }
    //================按帧滑动屏幕=================
    //先注册
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!isAlignActive) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 这里的 frameTime 在 1.20 是 “本帧的 partial tick”（0~1）
        // 把它换算成秒：每 tick = 1/20 秒
        float dtSec = mc.getFrameTime() / 20.0f;

        performYawSnapPerFrame(player, dtSec);
    }
    private static void performYawSnapPerFrame(LocalPlayer player, float dtSec) {
        if (alignYawT >= 1.0f) return;

        // 推进进度：dt / duration
        float step = dtSec / ALIGN_YAW_DURATION_SEC;
        alignYawT = Math.min(1.0f, alignYawT + step);

        // 缓动：0~1 -> 0~1（更丝滑）
        float eased = easeInOutCubic(alignYawT);

        // 最短角度差，避免转大圈
        float delta = Mth.wrapDegrees(alignYawTarget - alignYawStart);
        float yawNow = alignYawStart + delta * eased;

        // ✅ 作用于玩家本身（第一人称画面会跟着变）
        player.setYRot(yawNow);
        player.setYHeadRot(yawNow);
        player.setYBodyRot(yawNow);

        // 避免插值回弹
        player.yRotO = yawNow;
        player.yBodyRotO = yawNow;

        // 到位后可选择结束吸附（如果你希望 Alt 只是“一次工具”）
        if (alignYawT >= 1.0f) {
            // 如果你希望“位置仍然继续吸到中心”，但角度已完成，可以只停角度，不停位置
            // 这里按你当前逻辑：吸附整体结束
            isAlignActive = false;
        }
    }
    private static float easeInOutCubic(float t) {
        // t: 0~1
        return (t < 0.5f)
                ? 4.0f * t * t * t
                : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0f) / 2.0f;
    }
}
