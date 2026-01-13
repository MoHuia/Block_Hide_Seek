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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

public class Vanish extends Item {

    // 最大耐久度 (例如 1200 tick = 60秒持续时间)
    // 玩家可以通过附魔"耐久"来延长使用时间
    public static int MAX_MANA = 200;

    public Vanish(Properties p) {
        // 设置最大耐久度
        super(p.durability(MAX_MANA));
    }

    // ==========================================
    // 1. 名字与外观
    // ==========================================
    @Override
    public Component getName(ItemStack pStack) {
        // 使用 AQUA (淡青色) 让它看起来像是稀有道具
        return Component.translatable(this.getDescriptionId(pStack))
                .withStyle(ChatFormatting.AQUA);
    }

    // 开启时显示附魔光效
    @Override
    public boolean isFoil(ItemStack stack) {
        // 这里只是简单的判断，如果物品有NBT标记"isActive"就发光
        // 实际逻辑主要靠 Capability，但在客户端渲染时，NBT更方便读取
        return stack.getOrCreateTag().getBoolean("isActive");
    }

    // ==========================================
    // 2. 耐久条 (蓝条) 设置
    // ==========================================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 只要用过（有损耗）就显示条，或者激活时显示
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // 返回 RGB 颜色：淡蓝色 (类似于法力值)
        return 0x00FFFF;
    }

    // ==========================================
    // 3. 悬浮提示
    // ==========================================
    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        pTooltipComponents.add(Component.literal("右键点击：切换 开启/关闭")
                .withStyle(ChatFormatting.GRAY));
        pTooltipComponents.add(Component.literal("持续消耗耐久，手持时生效")
                .withStyle(ChatFormatting.DARK_GRAY));

        if (pStack.getOrCreateTag().getBoolean("isActive")) {
            pTooltipComponents.add(Component.literal("▶ 正在运行").withStyle(ChatFormatting.GREEN));
        }

        pTooltipComponents.add(Component.literal("被动效果：消除脚步声与脚印")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    // ==========================================
    // 4. 右键切换逻辑 (开关)
    // ==========================================
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            sp.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (cap.isSeeker()) {
                    sp.sendSystemMessage(Component.literal("❌ 抓捕者无法使用！").withStyle(ChatFormatting.RED));
                    return;
                }

                // 获取当前是否隐身
                boolean currentInvisible = cap.isInvisible();
                // 切换状态 (如果开着就关，如果关着就开)
                boolean newState = !currentInvisible;

                // 1. 设置 Capability 状态
                cap.setInvisible(newState);

                // 2. 标记物品 NBT (用于客户端发光渲染 isFoil)
                stack.getOrCreateTag().putBoolean("isActive", newState);

                // 3. 消息提示
                if (newState) {
                    sp.displayClientMessage(Component.literal("👻 隐身启动").withStyle(ChatFormatting.GREEN), true);

                    // ✅ 播放启动音效
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);

                    // ✅ 仅在启动瞬间播放一次大烟雾
                    playStartEffect(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ());
                } else {
                    sp.displayClientMessage(Component.literal("🛑 隐身关闭").withStyle(ChatFormatting.RED), true);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
                }

                // 4. 同步给客户端
                PacketHandler.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                        new S2CSyncGameData(sp.getId(), cap)
                );
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ==========================================
    // 5. 放在背包里时的逻辑 (防止 BUG)
    // ==========================================
    // 如果玩家把开启状态的物品扔掉或放进箱子，它应该自动关闭发光
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && !isSelected) {
            // 如果物品没被拿在手上，但 NBT 还是 active，强制关掉 NBT 显示
            // (实际隐身逻辑在 PlayerTickHandler 处理，这里只处理物品外观)
            if (stack.getOrCreateTag().getBoolean("isActive")) {
                stack.getOrCreateTag().putBoolean("isActive", false);
            }
        }
    }

    /**
     * 启动瞬间的烟雾爆裂特效
     */
    public static void playStartEffect(ServerLevel level, double x, double y, double z) {
        // 这里把数量加多到 50，制造瞬间“砰”的一下消失的感觉
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 50, 0.5, 0.8, 0.5, 0.05);
    }
}
