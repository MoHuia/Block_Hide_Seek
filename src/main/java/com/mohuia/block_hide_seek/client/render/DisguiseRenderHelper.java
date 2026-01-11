package com.mohuia.block_hide_seek.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染核心提取 - 逻辑与原 ClientEvents 完全一致
 */
@OnlyIn(Dist.CLIENT)
public class DisguiseRenderHelper {

    private static final Map<BlockState, float[]> MODEL_OFFSET_CACHE = new HashMap<>();

    public static void clearCache() {
        MODEL_OFFSET_CACHE.clear();
    }

    /**
     * 统一入口：根据方块类型分发渲染任务
     */
    public static void renderDisguiseBlock(BlockState state, PoseStack poseStack, MultiBufferSource buffer, Level level, BlockPos pos, int light) {
        if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return;

        // A. 实体方块 (如：箱子、告示牌，通常用 Item 渲染更稳)
        if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            renderEntityBlockAsItem(poseStack, buffer, state, level, light);
        }
        // B. 3D 物品模型 (如：梯子、火把、栅栏等，作为物品渲染位置更正)
        else if (shouldRenderAsItem(state, level)) {
            ItemStack stack = new ItemStack(state.getBlock());
            BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
            renderItemWithAutoCenter(poseStack, buffer, state, stack, itemModel, level, light);
        }
        // C. 普通方块 (如：石头、泥土，直接渲染 BlockModel)
        else {
            renderBlockManually(poseStack, buffer, state, level, pos, light);
        }
    }

    // ==========================================
    // 下面的逻辑完全照抄原来的 ClientEvents
    // ==========================================

    private static boolean shouldRenderAsItem(BlockState state, Level level) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return false;
        BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        return itemModel.isGui3d();
    }

    private static void renderItemWithAutoCenter(PoseStack poseStack, MultiBufferSource buffer, BlockState state, ItemStack stack, BakedModel model, Level level, int light) {
        poseStack.pushPose();

        // 计算偏移量，确保视觉中心在脚下
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));
        float transX = 0.5f - offsets[0];
        float transY = 0.5f - offsets[1];
        float transZ = 0.5f - offsets[2];

        poseStack.translate(transX, transY, transZ);

        // 调用原版 ItemRenderer
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);

        poseStack.popPose();
    }

    private static void renderEntityBlockAsItem(PoseStack poseStack, MultiBufferSource buffer, BlockState state, Level level, int light) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        // 大多数实体方块物品模型中心在底部，往上提 0.5 看起来更自然
        poseStack.translate(0.0, 0.5, 0.0);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
        poseStack.popPose();
    }

    private static void renderBlockManually(PoseStack poseStack, MultiBufferSource buffer, BlockState state, Level level, BlockPos pos, int light) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blockRenderer.getBlockModel(state);

        // 偏移修正
        float[] offsets = MODEL_OFFSET_CACHE.computeIfAbsent(state, s -> calculateModelOffsets(model));

        poseStack.pushPose();
        // 反向偏移，把模型拉回到原点中心
        poseStack.translate(-offsets[0], -offsets[1], -offsets[2]);

        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer consumer = buffer.getBuffer(renderType);
        long fixedSeed = state.getSeed(BlockPos.ZERO);

        // 渲染下半部分
        renderSingleBlockModel(poseStack, consumer, model, state, light, fixedSeed, level, pos);

        // 特殊处理：如果是双层方块
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockState upperState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            BakedModel upperModel = blockRenderer.getBlockModel(upperState);

            poseStack.pushPose();
            poseStack.translate(0.0, 1.0, 0.0); // 向上移动一格
            renderSingleBlockModel(poseStack, consumer, upperModel, upperState, light, fixedSeed, level, pos);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

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
        if (!found) return new float[]{0.5f, 0.0f, 0.5f};
        float midX = (minX + maxX) / 2.0f;
        float midZ = (minZ + maxZ) / 2.0f;
        return new float[]{midX, minY, midZ};
    }
}
