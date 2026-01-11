package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

public class Vanish extends Item {

    // 隐身持续 10 秒
    private static final int DURATION = 10 * 20;

    public Vanish(Properties p) {
        super(p);
    }

    // ==========================================
    // ✅ 1. 修改名字颜色 (重点在这里)
    // ==========================================
    @Override
    public Component getName(ItemStack pStack) {
        // 使用 AQUA (淡青色) 让它看起来像是稀有道具
        // 如果想要灰色，就把 AQUA 改成 GRAY
        return Component.translatable(this.getDescriptionId(pStack))
                .withStyle(ChatFormatting.AQUA);
    }

    // ==========================================
    // ✅ 2. 添加悬浮提示 (Lore)
    // ==========================================
    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        // 添加一行灰色的解释文字
        pTooltipComponents.add(Component.literal("右键使用：化作一团烟雾消失 (10秒)")
                .withStyle(ChatFormatting.GRAY));
        pTooltipComponents.add(Component.literal("被动效果：消除脚步声与脚印")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (cap.isSeeker()) {
                    sp.sendSystemMessage(Component.literal("❌ 抓捕者无法使用！").withStyle(ChatFormatting.RED));
                    return;
                }
                if (cap.isInvisible()) {
                    sp.sendSystemMessage(Component.literal("❌ 已经在隐身中了！").withStyle(ChatFormatting.RED));
                    return;
                }

                // 1. 设置状态
                cap.setInvisible(true);
                cap.setInvisibilityTimer(DURATION);

                // 2. 扣除物品
                if (!sp.isCreative()) {
                    stack.shrink(1);
                }

                // 3. 同步
                PacketHandler.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                        new S2CSyncGameData(sp.getId(), cap)
                );

                // 4. 特效
                playVanishEffect(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ());
                sp.displayClientMessage(Component.literal("👻隐身模式启动！").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 纯净的厚重烟雾特效
     */
    public static void playVanishEffect(ServerLevel level, double x, double y, double z) {
        // 使用 CAMPFIRE_COSY_SMOKE (质感细腻的白灰雾)
        // 数量: 200 (制造厚度，不透光)
        // 范围: 0.8 / 1.2 / 0.8 (覆盖全身)
        // 速度: 0.02 (缓慢弥散，而不是快速喷射，更有雾的感觉)
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 200, 0.8, 1.2, 0.8, 0.02);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }
}
