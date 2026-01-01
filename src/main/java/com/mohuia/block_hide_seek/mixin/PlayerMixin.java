package com.mohuia.block_hide_seek.mixin;

import com.mohuia.block_hide_seek.client.ClientModelHelper;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    /**
     * 辅助方法：仅作为兜底 (Fallback) 使用
     * 当 Capability 数据尚未同步或异常时，才读取方块原始物理高度
     */
    @Unique
    private double bhs$getFallbackPhysicalHeight(Player player, BlockState state) {
        try {
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                    state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                return 1.95;
            }
            if (state.getBlock() instanceof BedBlock) {
                return 0.56;
            }
            VoxelShape shape = state.getShape(player.level(), player.blockPosition(), CollisionContext.of(player));
            return shape.isEmpty() ? 0.8 : shape.max(Direction.Axis.Y);
        } catch (Exception e) {
            return 0.8;
        }
    }

    /**
     * 【核心修复】：让物理碰撞箱完全同步 OBB (视觉模型) 的高度
     */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 1. 优先读取 Capability 里的模型高度 (这是 ClientModelHelper 扫描出来的真实高度)
                float visualHeight = cap.getModelHeight();

                // 2. 如果数据异常 (比如刚初始化是 0)，才回退到方块物理属性
                if (visualHeight <= 0.05F) {
                    visualHeight = (float) bhs$getFallbackPhysicalHeight(self, cap.getDisguise());
                }

                // 3. 高度微调策略
                float collisionHeight = visualHeight;

                // 优化：如果是普通的 1格高方块 (0.99 ~ 1.0)，稍微压扁一点点到 0.95
                // 这样玩家可以轻松走进 1格高的洞，而不会被浮点数误差卡住
                if (collisionHeight >= 0.99F && collisionHeight <= 1.0F) {
                    collisionHeight = 0.95F;
                }

                // 4. 安全限制
                // 最小 0.2 (防止太扁)
                // 最大 4.0 (防止巨型方块导致物理引擎崩溃，之前是 2.9 可能卡住3格高的方块)
                float clampedHeight = Math.max(0.2F, Math.min(collisionHeight, 4.0F));

                // 5. 应用尺寸
                // 宽度固定 0.5F (保持身法灵活)，高度完全同步模型
                cir.setReturnValue(EntityDimensions.fixed(0.5F, clampedHeight));
            }
        });
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                // 这里的逻辑不用动：
                // 客户端用扫描数据，服务端用 dimensions.height (即上面 onGetDimensions 算出的结果)
                float h = self.level().isClientSide ? ClientModelHelper.getOptimalSize(cap.getDisguise())[1] : dimensions.height;
                cir.setReturnValue(Math.max(0.2F, h * 0.85F));
            }
        });
    }
}
