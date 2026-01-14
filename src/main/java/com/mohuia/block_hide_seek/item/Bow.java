package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.entity.ArrowEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;

public class Bow extends BowItem {
    // 冷却时间 (Ticks)
    public static int COOLDOWN = 100;

    public Bow(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);

        // 冷却中不可使用
        if (pPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemstack);
        }

        pPlayer.startUsingItem(pHand);
        return InteractionResultHolder.consume(itemstack);
    }

    @Override
    public void releaseUsing(ItemStack pStack, Level pLevel, LivingEntity pEntityLiving, int pTimeLeft) {
        if (!(pEntityLiving instanceof Player player)) return;

        int usedDuration = this.getUseDuration(pStack) - pTimeLeft;
        float power = getPowerForTime(usedDuration);

        // ✅ 核心修改 1: 强制只有拉满 (1.0) 才能发射
        if (power < 1.0F) {
            // 如果没拉满，播放一个类似“取消”的声音，或者什么都不做
            return;
        }

        if (!pLevel.isClientSide) {
            ArrowEntity arrow = new ArrowEntity(pLevel, player);

            // 如果需要，保留之前的强制位置修正代码，或者使用原版默认
            // arrow.moveTo(player.getX(), player.getEyeY() - 0.1, player.getZ(), player.getYRot(), player.getXRot());

            // 发射
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
            arrow.setCritArrow(true); // 必定暴击

            pLevel.addFreshEntity(arrow);

            // 只有发射成功才进入冷却
            if (!player.getAbilities().instabuild) {
                player.getCooldowns().addCooldown(this, COOLDOWN);
            }
        }

        pLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F / (pLevel.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
    }

    // ==================================================
    // ✅ 核心修改 2: 利用“耐久度条”显示蓄力进度
    // ==================================================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 只在客户端判断
        return isBeingUsedByClientPlayer(stack) || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (isBeingUsedByClientPlayer(stack)) {
            float progress = getPullProgress(stack);
            return Math.round(progress * 13.0F);
        }
        return super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (isBeingUsedByClientPlayer(stack)) {
            float progress = getPullProgress(stack);
            // 没满是黄色(0xFFFF00)，满了变绿色(0x00FF00)
            return progress >= 1.0F ? 0x00FF00 : 0xFFFF00;
        }
        return super.getBarColor(stack);
    }

    /**
     * 辅助方法：计算拉弓进度 (0.0 - 1.0)
     */
    private float getPullProgress(ItemStack stack) {
        // 由于这几个方法通常在客户端调用渲染，直接拿客户端玩家是安全的
        // 但为了防止服务端崩，加个 try-catch 或者 Dist 检查最好，不过在Item里直接用通常也没事
        try {
            net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                int usedDuration = this.getUseDuration(stack) - player.getUseItemRemainingTicks();
                // 20.0F 是拉满所需时间
                return Math.min((float)usedDuration / 20.0F, 1.0F);
            }
        } catch (Exception e) {
            // 服务端没有 Minecraft 类，忽略
        }
        return 0.0F;
    }

    /**
     * 辅助方法：检查这个物品是否正在被客户端玩家使用
     */
    private boolean isBeingUsedByClientPlayer(ItemStack stack) {
        try {
            // 必须检查 dist，防止服务端加载类失败 (NoClassDefFoundError)
            // 虽然 isBarVisible 大概率只在客户端调，但保险起见
            if (net.minecraftforge.fml.loading.FMLLoader.getDist() == Dist.CLIENT) {
                net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
                return player != null && player.isUsingItem() && player.getUseItem() == stack;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
