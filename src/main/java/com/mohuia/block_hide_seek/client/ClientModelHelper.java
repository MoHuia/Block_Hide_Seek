package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ClientModelHelper {
    public static class SizeResult {
        public final float obbX, obbY, obbZ;     // 真实尺寸，用于 OBB
        public final float modelW, modelH;       // 策略尺寸，用于玩家碰撞

        public SizeResult(float obbX, float obbY, float obbZ, float modelW, float modelH) {
            this.obbX = obbX;
            this.obbY = obbY;
            this.obbZ = obbZ;
            this.modelW = modelW;
            this.modelH = modelH;
        }
    }

    // ==========================================
    // 1. 调试/指令逻辑 (用于 /bhs block)
    // 目标：获取方块的“视觉最大尺寸”，用于生成测试实体观察
    // ==========================================
    public static void handleRequest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        StringBuilder log = new StringBuilder();

        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            PacketHandler.INSTANCE.sendToServer(
                    new PacketHandler.C2SModelSizeResponse(1f, 1f, "非方块", "手持物品不是 BlockItem")
            );
            return;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        String name = state.getBlock().getName().getString();

        log.append("方块: ").append(name).append("\n");

        // 1. 扫描原始尺寸 (Raw Dimensions)
        float[] rawDims = scanRawDimensions(state, stack, log);

        // 2. 调试模式策略：取最大宽度 (为了视觉准确性)
        float widthX = rawDims[0];
        float heightY = rawDims[1];
        float widthZ = rawDims[2];

        float finalWidth = Math.max(widthX, widthZ); // <--- 调试用最大值
        float finalHeight = heightY;

        // 3. 应用特例 (调试模式也应用，以便查看修正结果)
        float[] fixedSize = applySpecialCases(state, finalWidth, finalHeight, log);

        // 4. 回包
        PacketHandler.INSTANCE.sendToServer(
                new PacketHandler.C2SModelSizeResponse(fixedSize[0], fixedSize[1], name, log.toString())
        );
    }

    // ==========================================
    // 2. 游戏逻辑 (用于 SelectScreen / 玩家变形)
    // 目标：获取方块的“物理最小尺寸”，方便钻洞
    // ==========================================
    public static float[] getOptimalSize(BlockState state) {
        SizeResult r = getSizeResult(state);
        return new float[]{r.modelW, r.modelH};
    }
    public static SizeResult getSizeResult(BlockState state) {
        // 1) 真实尺寸：扫描模型得到 x,y,z
        float[] raw = scanRawDimensions(state, new ItemStack(state.getBlock()), null);
        float rawX = raw[0];
        float rawY = raw[1];
        float rawZ = raw[2];

        // 2) 玩家碰撞策略：从真实尺寸导出 modelW/modelH（保持你现有策略）
        float modelW = computeModelWidthStrategy(rawX, rawZ);
        float modelH = rawY;

        float[] fixed = applySpecialCases(state, modelW, modelH, null);
        // 假设 applySpecialCases 返回的是 [width,height]（你原来就这么用）
        float finalW = fixed[0];
        float finalH = fixed[1];

        return new SizeResult(rawX, finalH, rawZ, finalW, finalH);
    }

    private static float computeModelWidthStrategy(float widthX, float widthZ) {
        float finalWidth = Math.min(widthX, widthZ);
        if (finalWidth < 0.2f) finalWidth = 0.2f;
        return finalWidth;
    }

    // ==========================================
    // 3. 核心私有方法 (Core Logic)
    // ==========================================

    /**
     * 核心扫描方法：返回 [WidthX, HeightY, WidthZ]
     * 如果扫描失败，返回默认 [1.0, 1.0, 1.0]
     */
    public static float[] scanRawDimensions(BlockState state, ItemStack stack, StringBuilder log) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        BakedModel missing = mc.getModelManager().getMissingModel();

        // 步骤 A: 尝试扫描 BlockModel
        if (model != null && model != missing) {
            if (log != null) log.append(">> 扫描 BlockModel...\n");
            float[] result = scanBakedModel(model, state);
            if (result != null) return result;
        }

        // 步骤 B: 回退扫描 ItemModel
        if (log != null) log.append(">> BlockModel 无效，尝试 ItemModel...\n");
        BakedModel itemModel = mc.getItemRenderer().getModel(stack, null, null, 0);
        if (itemModel != null && itemModel != missing) {
            float[] result = scanBakedModel(itemModel, null); // ItemModel 通常传入 null state
            if (result != null) {
                if (log != null) log.append("ℹ 使用了物品模型数据\n");
                return result;
            }
        }

        // 步骤 C: 彻底失败，返回默认全尺寸
        if (log != null) log.append("❌ 扫描失败，使用默认 1x1x1\n");
        return new float[]{1.0f, 1.0f, 1.0f};
    }

    /**
     * 遍历模型的所有 Quad，计算 X/Y/Z 跨度
     * 返回: float[]{ spanX, spanY, spanZ } 或 null
     */
    private static float[] scanBakedModel(BakedModel model, BlockState stateOrNull) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        boolean foundQuad = false;
        RandomSource rand = RandomSource.create();
        Direction[] dirs = new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

        for (Direction dir : dirs) {
            List<BakedQuad> quads;
            try {
                quads = model.getQuads(stateOrNull, dir, rand);
            } catch (Exception e) { quads = List.of(); }

            // 针对某些 ItemModel 的特殊处理
            if (quads.isEmpty() && stateOrNull == null && dir == null) {
                try { quads = model.getQuads(null, null, rand); } catch (Exception ignored) {}
            }

            for (BakedQuad quad : quads) {
                foundQuad = true;
                int[] vertices = quad.getVertices();
                // 遍历 4 个顶点
                for (int i = 0; i < 4; i++) {
                    int offset = i * 8;
                    if (vertices.length < offset + 3) continue;

                    float x = Float.intBitsToFloat(vertices[offset]);
                    float y = Float.intBitsToFloat(vertices[offset + 1]);
                    float z = Float.intBitsToFloat(vertices[offset + 2]);

                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
            }
        }

        if (!foundQuad) return null;

        float wX = roundIfClose(Math.abs(maxX - minX));
        float hY = roundIfClose(Math.abs(maxY - minY));
        float wZ = roundIfClose(Math.abs(maxZ - minZ));

        return new float[]{wX, hY, wZ};
    }

    private static float roundIfClose(float val) {
        float r = Math.round(val);
        // 浮点误差修正：如果是 0.99999 -> 1.0
        return Math.abs(val - r) < 0.05f ? r : val;
    }

    private static float[] applySpecialCases(BlockState state, float width, float height, StringBuilder log) {
        if (state.is(BlockTags.BEDS) || state.getBlock() instanceof BedBlock) {
            if (log != null) log.append("ℹ 特例: 床 -> 2x0.56\n");
            return new float[]{1.0f, 0.5625f};
        }
        if (state.is(BlockTags.DOORS) || state.getBlock() instanceof DoorBlock) {
            if (log != null) log.append("ℹ 特例: 门 -> 1x2\n");
            // 门在游戏中比较特殊，为了能过门，宽度给 0.5 比较合适，但视觉上是 1.0
            // 这里我们保持 SelectScreen 的逻辑，门虽然视觉宽，但物理碰撞箱建议给小一点(如果你想让门能钻洞)
            // 但标准做法是:
            return new float[]{0.2f, 2.0f};
        }
        if (state.is(BlockTags.TALL_FLOWERS) || state.getBlock() instanceof DoublePlantBlock) {
            if (log != null) log.append("ℹ 特例: 双格植物 -> h=2.0\n");
            return new float[]{width, 2.0f};
        }
        return new float[]{width, height};
    }
}
