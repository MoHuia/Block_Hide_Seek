package com.mohuia.block_hide_seek.mixin;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    public void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;

        // 这是一个高频调用的方法，我们只做轻量级操作
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 1. 直接读取 Capability 缓存的尺寸 (这是客户端发过来的)
                float width = cap.getModelWidth();
                float height = cap.getModelHeight();

                // 2. 高度微调 (避免头皮摩擦)
                // 如果高度 > 1.0 (比如 2.0 的门)，减去 0.05 -> 1.95，防止卡门框
                // 如果高度 <= 1.0，减去 0.02 -> 防止卡 1格高的洞
                float heightOffset = (height > 1.0f) ? 0.05f : 0.02f;
                float finalHeight = Math.max(0.1f, height - heightOffset);

                // 3. 宽度微调
                // 确保宽度是合理的，虽然客户端算过了，服务端再校验一次
                // 宽度通常不做减少，否则会显得实体和影子不匹配
                float finalWidth = Math.max(0.2f, width);

                // 4. 返回固定尺寸
                cir.setReturnValue(EntityDimensions.fixed(finalWidth, finalHeight));
            }
        });
    }

    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetEyeHeight(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        self.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                // 视线高度：总高度的 85%
                // 确保至少有 0.2 的视线高度，不然就在地里了
                cir.setReturnValue(Math.max(0.2F, dimensions.height * 0.85F));
            }
        });
    }
}
