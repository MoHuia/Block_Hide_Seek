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

    private static Float lockedBodyYaw = null;
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

        // 【关键修复】使用插值 (Interpolation) 让旋转丝滑
        // Mth.rotLerp 会自动处理 360度 -> 0度 的边界问题，防止转圈鬼畜
        if (lockedBodyYaw != null && player == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            float partialTick = event.getPartialTick();
            renderYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        }

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        Level level = player.level();
        // =============================================================
        // 分类渲染逻辑
        // =============================================================

        // 1. 【实体方块】(床、箱子) -> 强制 Item 渲染 (带实体抬高修复)
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            renderEntityBlockAsItem(event, poseStack, state);
        }
        // 2. 【复杂 3D 物品】(家具、车、栏杆) -> 走 Item 渲染 (兼容性修复 + 居中修正)
        else if (shouldRenderAsItem(state, level)) {
            ItemStack stack = new ItemStack(state.getBlock());
            BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
            renderItemWithAutoCenter(event, poseStack, state, stack, itemModel);
        }
        // 3. 【所有其他方块】(原版方块、门、花、草) -> 走手动 Quad 渲染 (解决闪烁 + 支持双层)
        else {
            renderBlockManually(event, poseStack, state);
        }

        poseStack.popPose();
    }

    // 辅助判断：是否应该作为物品渲染 (GUI里是3D的物品)
    private static boolean shouldRenderAsItem(BlockState state, Level level) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return false;
        BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        return itemModel.isGui3d();
    }

    /**
     * 策略 A: 3D 物品渲染 (Yuushya、家具、载具)
     * 应用 0.5 - offset 反向补偿，解决陷地和偏心
     */
    private static void renderItemWithAutoCenter(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state, ItemStack stack, BakedModel model) {
        poseStack.pushPose();

        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        float midX = offsets[0];
        float minY = offsets[1];
        float midZ = offsets[2];

        // 反向补偿：将模型的“脚底”对齐到玩家的“脚底”
        float transX = 0.5f - midX;
        float transY = 0.5f - minY;
        float transZ = 0.5f - midZ;

        poseStack.translate(transX, transY, transZ);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                net.minecraft.world.item.ItemDisplayContext.NONE,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                event.getMultiBufferSource(),
                event.getEntity().level(),
                0
        );

        poseStack.popPose();
    }

    /**
     * 策略 B: 手动方块渲染 (原版方块、门、花)
     * 1. 彻底解决跳跃闪烁 (不计算动态 AO)
     * 2. 支持生物群系颜色
     * 3. 支持 Seed 锁定
     * 4. 支持双层结构
     */
    private static void renderBlockManually(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);
        Level level = event.getEntity().level();
        BlockPos pos = event.getEntity().blockPosition();

        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));

        poseStack.pushPose();
        // BlockRenderer 坐标系是 0~1，直接减去中心点居中
        poseStack.translate(-offsets[0], -offsets[1], -offsets[2]);

        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(renderType);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(level, lightPos);

        long fixedSeed = state.getSeed(BlockPos.ZERO);

        // 渲染主体
        renderSingleBlockModel(poseStack, buffer, model, state, light, fixedSeed, level, pos);

        // 双层补全 (门、高花)
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {

            BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            BakedModel upperModel = blockRenderer.getBlockModel(upperState);

            poseStack.pushPose();
            poseStack.translate(0.0, 1.0, 0.0);
            renderSingleBlockModel(poseStack, buffer, upperModel, upperState, light, fixedSeed, level, pos);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * 策略 C: 实体方块 (床、箱子)
     * 简单抬高
     */
    private static void renderEntityBlockAsItem(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getEyeY(), event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);

        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, event.getMultiBufferSource(), event.getEntity().level(), 0);
        poseStack.popPose();
    }

    // 核心绘制方法：支持颜色混合 (Tinting)
    private static void renderSingleBlockModel(PoseStack poseStack, VertexConsumer buffer, BakedModel model, BlockState state, int light, long seed, Level level, BlockPos pos) {
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

        if (!found) {
            return new float[]{0.5f, 0.0f, 0.5f};
        }

        float midX = (minX + maxX) / 2.0f;
        float midZ = (minZ + maxZ) / 2.0f;
        float bottomY = minY;

        return new float[]{midX, bottomY, midZ};
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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.tickCount % 1200 == 0) {
            MODEL_OFFSET_CACHE.clear();
        }

        float expectedHeight = player.getDimensions(player.getPose()).height;
        float actualHeight = player.getBbHeight();
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }

        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SRequestConfig());
        }
        handleAutoAlign(player);
    }

    private static void handleAutoAlign(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;
                if (hasInput || !player.onGround()) {
                    lockedBodyYaw = null;
                } else {
                    performSnap(player);
                }
            } else {
                lockedBodyYaw = null;
            }
        });
    }

    private static void performSnap(LocalPlayer player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();
        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;
        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;
        player.setPos(newX, player.getY(), newZ);
        if (lockedBodyYaw == null) {
            float currentBodyYaw = player.yBodyRot;
            lockedBodyYaw = (float) (Math.round(currentBodyYaw / 90.0f) * 90.0f);
        }
        player.setYBodyRot(lockedBodyYaw);
    }

    @Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.OPEN_CONFIG);
        }
    }
}
