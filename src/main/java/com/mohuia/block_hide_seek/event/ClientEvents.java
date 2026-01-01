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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

    // 缓存模型的偏移量
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

        float renderYaw;
        if (lockedBodyYaw != null && event.getEntity() == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            renderYaw = event.getEntity().yBodyRot;
        }
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        // =========================================================================
        // 【通用判定逻辑】
        // 不需要硬编码名字了，我们通过几何形状来判断！
        // =========================================================================

        Level level = event.getEntity().level();
        BlockPos pos = event.getEntity().blockPosition();

        // 判定1：是否是标准实心方块 (例如石头、泥土)
        // isSolidRender 通常意味着它是一个不透明的完整方块
        boolean isSolid = state.isSolidRender(level, pos);

        // 判定2：碰撞箱是否是完整立方体
        // isCollisionShapeFullBlock 意味着它的物理体积也是满的
        boolean isFullCube = state.isCollisionShapeFullBlock(level, pos);

        // 核心逻辑：
        // 只要它不是一个"既实心又完整"的方块，它就是一个"复杂方块" (电瓶车、椅子、花、台阶等)
        // 复杂方块强制走 Item 渲染，这样会自动应用你的"自动居中算法"
        boolean forceItemRender = !(isSolid && isFullCube);

        // 额外兼容：如果 RenderShape 本身不是 MODEL (比如特殊的实体渲染)，也强制走 Item
        if (state.getRenderShape() != RenderShape.MODEL) {
            forceItemRender = true;
        }

        if (forceItemRender) {
            renderComplexBlockAsItem(event, poseStack, state);
        } else {
            renderRealBlock(event, poseStack, state);
        }

        poseStack.popPose();
    }

    /**
     * 普通方块：真实光影渲染 (已包含AO)
     */
    private static void renderRealBlock(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        poseStack.pushPose();
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        var minecraft = Minecraft.getInstance();
        var blockRenderer = minecraft.getBlockRenderer();
        var modelRenderer = blockRenderer.getModelRenderer();
        var level = event.getEntity().level();
        var pos = event.getEntity().blockPosition();

        RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(state);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(renderType);
        BakedModel model = blockRenderer.getBlockModel(state);
        long seed = state.getSeed(pos);

        modelRenderer.tesselateBlock(level, model, state, pos, poseStack, buffer, false, RandomSource.create(), seed, OverlayTexture.NO_OVERLAY);

        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            BakedModel upperModel = blockRenderer.getBlockModel(upperState);
            poseStack.pushPose();
            poseStack.translate(0.0D, 1.0D, 0.0D);
            modelRenderer.tesselateBlock(level, upperModel, upperState, pos.above(), poseStack, buffer, false, RandomSource.create(), seed, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * 复杂方块：模型直出渲染 (手动光照修正版)
     * 保留了你的自动对齐算法，只修复了光照太亮/太平的问题
     */
    private static void renderComplexBlockAsItem(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;

        poseStack.pushPose();

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        var model = itemRenderer.getModel(stack, event.getEntity().level(), null, 0);

        // 1. 你的核心算法 (保持不变)
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        float midX = offsets[0];
        float minY = offsets[1];
        float midZ = offsets[2];

        // 2. 你的位移逻辑 (保持不变)
        poseStack.translate(-midX, -minY, -midZ);

        // 3. 准备渲染
        var renderType = RenderType.cutout(); // 或者使用 ItemBlockRenderTypes.getRenderType(stack, true)
        var buffer = event.getMultiBufferSource().getBuffer(renderType);

        BlockPos lightPos = BlockPos.containing(event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ());
        int light = LevelRenderer.getLightColor(event.getEntity().level(), lightPos);

        // 4. 【核心修复】：手动遍历面并应用阴影
        // 不再调用 blockRenderer.renderModel，而是手动画，这样才能控制光影
        RandomSource rand = RandomSource.create();
        long seed = state.getSeed(event.getEntity().blockPosition());

        for (Direction dir : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            rand.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, dir, rand);

            for (BakedQuad quad : quads) {
                float shade = 1.0F;

                // 如果这个面需要阴影 (大部分模型都需要)
                if (quad.isShade()) {
                    // 模拟原版物品/方块的漫反射光照
                    // 上面亮，下面暗，侧面有深浅
                    Direction faceDir = quad.getDirection();
                    if (faceDir != null) {
                        switch (faceDir) {
                            case DOWN -> shade = 0.5F;
                            case UP -> shade = 1.0F;
                            case NORTH, SOUTH -> shade = 0.8F;
                            case WEST, EAST -> shade = 0.6F;
                            default -> shade = 1.0F;
                        }
                    }
                }

                // 使用 putBulkData 绘制，同时乘上 shade 系数
                buffer.putBulkData(
                        poseStack.last(),
                        quad,
                        shade, shade, shade, // RGB 颜色乘上阴影系数
                        light,
                        OverlayTexture.NO_OVERLAY
                );
            }
        }

        poseStack.popPose();
    }

    /**
     * 【算法核心】扫描模型顶点
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
