package com.mohuia.block_hide_seek.event;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT)
public class ClientEvents {

    // 状态控制变量
    private static Float lockedBodyYaw = null;      // 锁定的身体朝向 (null 表示未锁定)
    private static boolean isAlignActive = false;   // 是否开启位置对齐
    private static boolean isRotationLocked = false;// 是否开启旋转锁定

    private static final Map<BlockState, float[]> MODEL_OFFSET_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker()) return;

            BlockState state = cap.getDisguise();
            if (state != null && state.getRenderShape() != RenderShape.INVISIBLE) {
                event.setCanceled(true);
                renderDisguise(event, state);
            }
        });
    }

    private static void renderDisguise(RenderPlayerEvent.Pre event, BlockState state) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Player player = event.getEntity();
        float renderYaw;

        // 如果开启了旋转锁定，并且是当前玩家自己，强制使用锁定角度
        if (lockedBodyYaw != null && player == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            // 否则使用平滑插值
            float partialTick = event.getPartialTick();
            renderYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        }

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        Level level = player.level();

        // 1. 实体方块
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            renderEntityBlockAsItem(event, poseStack, state);
        }
        // 2. 3D 物品
        else if (shouldRenderAsItem(state, level)) {
            ItemStack stack = new ItemStack(state.getBlock());
            BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
            renderItemWithAutoCenter(event, poseStack, state, stack, itemModel);
        }
        // 3. 普通方块
        else {
            renderBlockManually(event, poseStack, state);
        }

        poseStack.popPose();
    }

    // ... [中间的渲染辅助方法 renderItemWithAutoCenter, renderBlockManually, renderEntityBlockAsItem, renderSingleBlockModel, calculateModelOffsets, shouldRenderAsItem 保持不变，为了篇幅省略，请直接复用之前的代码] ...
    // ... [onRenderNameTag 也保持不变] ...

    // 为了完整性，这里我把必须的辅助方法占位写一下，实际使用请保留你之前的代码
    private static boolean shouldRenderAsItem(BlockState state, Level level) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return false;
        BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        return itemModel.isGui3d();
    }
    private static void renderItemWithAutoCenter(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state, ItemStack stack, BakedModel model) {
        // ... 复用之前的代码 ...
        // 如果你需要我再次发送这段渲染代码，请告诉我
        // 这里假设上面的渲染逻辑你已经有了，重点看下面的 Tick 逻辑
        poseStack.pushPose();
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        float transX = 0.5f - offsets[0];
        float transY = 0.5f - offsets[1];
        float transZ = 0.5f - offsets[2];
        poseStack.translate(transX, transY, transZ);
        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, event.getMultiBufferSource(), event.getEntity().level(), 0);
        poseStack.popPose();
    }
    private static void renderBlockManually(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        // ... 复用之前的代码 ...
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);
        Level level = event.getEntity().level();
        BlockPos pos = event.getEntity().blockPosition();
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        poseStack.pushPose();
        poseStack.translate(-offsets[0], -offsets[1], -offsets[2]);
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(renderType);
        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(level, lightPos);
        long fixedSeed = state.getSeed(BlockPos.ZERO);
        renderSingleBlockModel(poseStack, buffer, model, state, light, fixedSeed, level, pos);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            BakedModel upperModel = blockRenderer.getBlockModel(upperState);
            poseStack.pushPose();
            poseStack.translate(0.0, 1.0, 0.0);
            renderSingleBlockModel(poseStack, buffer, upperModel, upperState, light, fixedSeed, level, pos);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
    private static void renderEntityBlockAsItem(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        // ... 复用之前的代码 ...
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);
        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, event.getMultiBufferSource(), event.getEntity().level(), 0);
        poseStack.popPose();
    }
    private static void renderSingleBlockModel(PoseStack poseStack, VertexConsumer buffer, BakedModel model, BlockState state, int light, long seed, Level level, BlockPos pos) {
        // ... 复用之前的代码 ...
        RandomSource rand = RandomSource.create();
        for (Direction dir : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            rand.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, dir, rand);
            for (BakedQuad quad : quads) {
                float r = 1f, g = 1f, b = 1f;
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
    private static float[] calculateModelOffsets(BakedModel model) {
        // ... 复用之前的代码 ...
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
        if (!found) return new float[]{0.5f, 0.0f, 0.5f};
        float midX = (minX + maxX) / 2.0f;
        float midZ = (minZ + maxZ) / 2.0f;
        return new float[]{midX, minY, midZ};
    }
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (!cap.isSeeker() && cap.getDisguise() != null) {
                    event.setResult(Event.Result.DENY);
                }
            });
        }
    }


    // ==========================================
    //           核心控制逻辑 (已修改)
    // ==========================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 定期清理缓存
        if (player.tickCount % 1200 == 0) {
            MODEL_OFFSET_CACHE.clear();
        }

        // 物理高度修正
        float expectedHeight = player.getDimensions(player.getPose()).height;
        float actualHeight = player.getBbHeight();
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }

        // 处理菜单按键
        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SRequestConfig());
        }

        // 处理移动、对齐与锁定逻辑
        handleControlLogic(player);
    }

    private static void handleControlLogic(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 如果不是伪装状态，重置所有逻辑
            if (cap.isSeeker() || cap.getDisguise() == null) {
                resetStates();
                return;
            }

            // 1. 检测玩家是否正在移动 (输入 或 速度)
            // forwardImpulse 和 leftImpulse 代表 WASD 输入
            boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;
            boolean isJumping = player.input.jumping;
            // deltaMovement 代表实际位移速度
            boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.005;

            // 2. 如果检测到移动输入 -> 强制取消所有锁定
            if (hasInput || isJumping || (!player.onGround() && isMoving)) {
                if (isAlignActive || isRotationLocked) {
                    resetStates();
                    player.displayClientMessage(Component.literal("§c已解除锁定"), true);
                }
            }
            // 3. 静止状态 -> 允许切换功能
            else {
                // 切换位置对齐 (Left Alt)
                if (KeyInit.TOGGLE_ALIGN.consumeClick()) {
                    isAlignActive = !isAlignActive;
                    player.displayClientMessage(Component.literal(isAlignActive ? "§a自动对齐: 开启" : "§c自动对齐: 关闭"), true);
                }

                // 切换视角锁定 (Caps Lock)
                if (KeyInit.LOCK_ROTATION.consumeClick()) {
                    isRotationLocked = !isRotationLocked;

                    if (isRotationLocked) {
                        // 锁定当前朝向到最近的 90 度
                        float currentBodyYaw = player.yBodyRot;
                        lockedBodyYaw = (float) (Math.round(currentBodyYaw / 90.0f) * 90.0f);
                        player.displayClientMessage(Component.literal("§a方向锁定: 开启 (自由视角)"), true);
                    } else {
                        lockedBodyYaw = null;
                        player.displayClientMessage(Component.literal("§c方向锁定: 关闭"), true);
                    }
                }

                // 执行对齐逻辑
                if (isAlignActive) {
                    performPositionSnap(player);
                }

                // 维护 lockedBodyYaw 状态
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

    private static void performPositionSnap(LocalPlayer player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();

        // 平滑吸附
        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;

        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;

        // 仅修改位置，不修改旋转
        player.setPos(newX, player.getY(), newZ);
    }

    @Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.OPEN_CONFIG);
            // 注册新按键
            event.register(KeyInit.TOGGLE_ALIGN);
            event.register(KeyInit.LOCK_ROTATION);
        }
    }
    private static long lastSendMs = 0;

    @SubscribeEvent
    public static void onLeftClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return; // 左键攻击键

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return; // 打开 GUI 时不发（按需）

        // 防抖：避免按住左键每 tick 发包
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 20) return;
        lastSendMs = now;

        // debug 时先开粒子线：true；平时关掉就 false
        boolean debugParticles = false;
        System.out.println("客户端发现你点了一次左键");
        PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SAttackRaycast(debugParticles));
    }

}
