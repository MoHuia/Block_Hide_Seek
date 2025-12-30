package com.mohuia.block_hide_seek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 拦截实体渲染调度器
@Mixin(EntityRenderDispatcher.class)
public class EntityDispatcherMixin {

    // 拦截 renderShadow (渲染阴影) 方法
    // 注意：在 1.20.1 中这是一个静态方法，Mixin 支持注入静态方法
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void onRenderShadow(PoseStack pPoseStack, MultiBufferSource pBufferSource, Entity pEntity, float pWeight, float pPartialTick, LevelReader pLevel, float pRadius, CallbackInfo ci) {
        // 只检查玩家
        if (pEntity instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 如果是【躲藏者】且【有伪装】
                if (!cap.isSeeker() && cap.getDisguise() != null) {
                    // 直接取消阴影渲染，ci.cancel() 会阻止原版代码执行
                    ci.cancel();
                }
            });
        }
    }
}
