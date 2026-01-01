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
     * 服务端/通用计算方法：仅基于物理 VoxelShape
     * 保证服务端不崩，且逻辑统一
     */
    @Unique
    private double bhs$getPhysicalHeight(Player player, BlockState state) {
        try {
            Block block = state.getBlock();

            // 1. 双层方块 (门、高花)
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                    state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                return 1.95;
            }
            // 2. 床
            else if (block instanceof BedBlock) {
                return 0.56;
            }
            // 3. 通用逻辑：获取物理碰撞箱高度
            else {
                VoxelShape shape = state.getShape(player.level(), player.blockPosition(), CollisionContext.of(player));
                if (shape.isEmpty()) {
                    return 0.8;
                } else {
                    return shape.max(Direction.Axis.Y);
                }
            }
        } catch (Exception e) {
            return 0.8;
        }
    }

    /**
     * 碰撞箱修改 (getDimensions)
     * 注意：这里必须保持 Client/Server 同步，所以【不能】扫描模型
     */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 使用物理高度 (服务端安全)
                float blockHeight = (float) bhs$getPhysicalHeight(self, cap.getDisguise());

                // 高度微调：方便钻洞
                if (blockHeight >= 0.99F && blockHeight <= 1.0F) {
                    blockHeight = 0.95F;
                }

                float clampedHeight = Math.max(0.2F, Math.min(blockHeight, 2.9F));
                cir.setReturnValue(EntityDimensions.fixed(0.5F, clampedHeight));
            }
        });
    }

    /**
     * 视角高度修改 (getStandingEyeHeight)
     * 【核心优化】：这里只在客户端调用模型扫描，完美解决视角问题！
     */
    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                float visualHeight;

                // ==================================================
                // 1. 如果是客户端 -> 启用高科技模型扫描！
                // ==================================================
                if (self.level().isClientSide) {
                    // 调用 ClientModelHelper 获取【视觉】高度
                    // 这样就算物理判定是 1格，扫描出来是 2格，视角也会在 2格的位置
                    float[] size = ClientModelHelper.getOptimalSize(cap.getDisguise());
                    visualHeight = size[1]; // index 1 是高度
                }
                // ==================================================
                // 2. 如果是服务端 -> 回退到物理高度
                // ==================================================
                else {
                    visualHeight = dimensions.height; // 复用 onGetDimensions 算出的结果
                }

                // 最终计算：视角设为高度的 85%
                // 举例：售货机物理 1.0，但模型扫描出 2.0 -> 视角 = 2.0 * 0.85 = 1.7 (完美人眼高度)
                float eyeHeight = visualHeight * 0.85F;
                cir.setReturnValue(Math.max(0.2F, eyeHeight));
            }
        });
    }
}
