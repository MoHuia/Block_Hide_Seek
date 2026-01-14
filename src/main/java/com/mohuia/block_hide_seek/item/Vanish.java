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

    // 这是一个静态变量，你需要确保：
    // 1. 客户端收到 Config 同步包时，更新这个值。
    // 2. 服务端加载 Config 时，更新这个值。
    public static int MAX_MANA = 100;

    public Vanish(Properties p) {
        // 这里依然传入一个默认值，防止空指针或初始化错误，
        // 但实际逻辑会由下面的重写方法接管。
        super(p.durability(100));
    }

    // ==========================================
    // 🔥 核心修复：动态耐久度逻辑
    // ==========================================

    /**
     * 重写此方法，使物品的最大耐久度动态跟随 MAX_MANA 变量变化。
     * 这样 Config 修改后，无需重启游戏，物品上限就会改变。
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return MAX_MANA;
    }

    /**
     * 重写耐久条长度计算。
     * 默认逻辑是基于构造函数的 maxDamage 计算的，
     * 我们必须重写它以使用动态的 getMaxDamage(stack)。
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        // 这里的逻辑是：(当前耐久 / 最大耐久) * 13像素
        // stack.getDamageValue() 返回的是"已损耗"的值
        return Math.round(13.0F - (float)stack.getDamageValue() * 13.0F / (float)this.getMaxDamage(stack));
    }

    // ==========================================
    // 1. 名字与外观
    // ==========================================
    @Override
    public Component getName(ItemStack pStack) {
        return Component.translatable(this.getDescriptionId(pStack))
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean("isActive");
    }

    // ==========================================
    // 2. 耐久条 (蓝条) 设置
    // ==========================================
    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 只要有损耗就显示
        return stack.isDamaged();
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // 返回 RGB 颜色：淡蓝色
        return 0x00FFFF;
    }

    // ==========================================
    // 3. 悬浮提示
    // ==========================================
    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        pTooltipComponents.add(Component.literal("右键点击：切换 开启/关闭")
                .withStyle(ChatFormatting.GRAY));
        // 这里可以动态显示当前的 Max Mana
        pTooltipComponents.add(Component.literal("持续消耗耐久 (上限: " + MAX_MANA + ")")
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

                boolean currentInvisible = cap.isInvisible();
                boolean newState = !currentInvisible;

                cap.setInvisible(newState);
                stack.getOrCreateTag().putBoolean("isActive", newState);

                if (newState) {
                    sp.displayClientMessage(Component.literal("👻 隐身启动").withStyle(ChatFormatting.GREEN), true);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
                    playStartEffect(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ());
                } else {
                    sp.displayClientMessage(Component.literal("🛑 隐身关闭").withStyle(ChatFormatting.RED), true);
                    level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
                }

                PacketHandler.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                        new S2CSyncGameData(sp.getId(), cap)
                );
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ==========================================
    // 5. 放在背包里时的逻辑
    // ==========================================
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && !isSelected) {
            if (stack.getOrCreateTag().getBoolean("isActive")) {
                stack.getOrCreateTag().putBoolean("isActive", false);
            }
        }
    }

    public static void playStartEffect(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 50, 0.5, 0.8, 0.5, 0.05);
    }
}
