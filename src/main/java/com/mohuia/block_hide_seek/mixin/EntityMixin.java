package com.mohuia.block_hide_seek.mixin;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    // 1. 拦截疾跑粒子 (隐身时不冒烟)
    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void cancelSprintParticle(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (cap.isInvisible()) {
                    ci.cancel();
                }
            });
        }
    }

    /**
     * 2. 拦截脚步声 (走路、跑步、轻微落地)
     * 注意：这【不会】拦截高空坠落造成的 Fall Damage 声音 (GENERIC_BIG_FALL / GENERIC_SMALL_FALL)
     * 那些声音由 checkFallDamage -> playBlockFallSound 控制，我们没有拦截它。
     */
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void cancelStepSound(BlockPos pPos, BlockState pState, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            boolean isShifting = player.isShiftKeyDown();

            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 只要满足：1. 隐身道具生效中  或者  2. 按住Shift潜行
                // 就取消脚步声
                if (cap.isInvisible() || isShifting) {
                    ci.cancel();
                }
            });
        }
    }
}
