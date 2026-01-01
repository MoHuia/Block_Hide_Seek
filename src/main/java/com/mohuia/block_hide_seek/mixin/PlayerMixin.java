package com.mohuia.block_hide_seek.mixin;

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

    @Unique
    private double bhs$getDisguiseHeight(Player player, BlockState state) {
        try {
            Block block = state.getBlock();
            double height;

            // 1. 双层方块处理 (门、高花)
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                    state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                height = 1.95; // 稍微小于 2.0，防止卡门头
            }
            // 2. 床
            else if (block instanceof BedBlock) {
                height = 0.56;
            }
            // 3. 普通方块
            else {
                VoxelShape shape = state.getShape(player.level(), player.blockPosition(), CollisionContext.of(player));
                if (shape.isEmpty()) {
                    height = 0.8;
                } else {
                    height = shape.max(Direction.Axis.Y);
                }
            }
            return height;
        } catch (Exception e) {
            return 0.8;
        }
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 获取方块物理高度
                float blockHeight = (float) bhs$getDisguiseHeight(self, cap.getDisguise());

                // 【核心修复 1】高度微调
                // 如果高度正好是 1.0 (比如石头)，我们把它设为 0.95
                // 这样能保证绝对顺滑地进入 1格高 的洞，不会摩擦头皮
                if (blockHeight >= 0.99F && blockHeight <= 1.0F) {
                    blockHeight = 0.95F;
                }

                // 限制最大最小高度
                float clampedHeight = Math.max(0.2F, Math.min(blockHeight, 2.9F));

                // 【核心修复 2】宽度设为 0.5F
                // 原版玩家 0.6F，之前你设的 0.7F。
                // 0.5F (半格宽) 是躲猫猫黄金尺寸，既能钻洞又不会觉得判定太小
                cir.setReturnValue(EntityDimensions.fixed(0.5F, clampedHeight));
            }
        });
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                float height = dimensions.height;
                // 视线高度设为总高度的 85%，保证不卡视野
                float eyeHeight = height * 0.85F;
                cir.setReturnValue(Math.max(0.2F, eyeHeight));
            }
        });
    }
}
