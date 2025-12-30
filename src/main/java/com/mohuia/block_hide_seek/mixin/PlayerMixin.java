package com.mohuia.block_hide_seek.mixin;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 告诉 Mixin：我要修改 Player 这个类
@Mixin(Player.class)
public class PlayerMixin {

    // 拦截 getDimensions 方法
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        // (Player) (Object) this 是获取当前玩家实体的固定写法
        Player self = (Player) (Object) this;

        // 获取 Capability
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 如果不是抓捕者 且 有伪装
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                // 【关键修改】
                // 宽度 0.7F (原版方块是1.0，留出0.3的空隙，防卡墙)
                // 高度 0.8F (原版方块是1.0，留出0.2的空隙，方便钻洞)
                cir.setReturnValue(EntityDimensions.fixed(0.7F, 0.8F));
            }
        });
    }

    // 拦截视线高度 (防止视角卡在地里或天花板)
    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                // 【关键修改】
                // 视线高度设为 0.7F (比碰撞箱略矮一点点)
                // 这样在 1格高的隧道里，视角也能看清，不会穿模到天花板上
                cir.setReturnValue(0.7F);
            }
        });
    }
}
