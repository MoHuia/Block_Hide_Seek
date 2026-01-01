package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeekerWandItem extends Item {

    public SeekerWandItem() {
        super(new Properties()
                .stacksTo(16)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    /**
     * 左键点击方块触发 (利用挖掘事件)
     * 逻辑：如果玩家是创造模式 -> 获取点击的方块 -> 设置伪装 -> 取消破坏方块
     */
    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        // 1. 只在服务端运行逻辑
        if (player.level().isClientSide) {
            return false;
        }

        // 2. 权限检查：只有创造模式可以用左键变身
        if (!player.isCreative()) {
            return false; // 普通生存模式玩家左键就是正常挖掘，不触发变身
        }

        // 3. 获取目标方块
        BlockState targetState = player.level().getBlockState(pos);
        if (targetState.isAir()) return false;

        // 4. 执行变身逻辑
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setDisguise(targetState);

            // 5. 同步数据
            if (player instanceof ServerPlayer serverPlayer) {
                PacketHandler.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> serverPlayer),
                        new PacketHandler.S2CSyncGameData(player.getId(), false, targetState)
                );

                // 6. 反馈消息 & 音效
                serverPlayer.sendSystemMessage(Component.literal("🪄 [创造模式] 已快速变身为: " + targetState.getBlock().getName().getString())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        });

        // 7. 返回 true 表示 "取消方块破坏事件" (这样左键就不会把方块打碎了)
        return true;
    }

    /**
     * 右键点击空气/方块触发 (打开菜单)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {

            // --- 获取随机列表逻辑 ---
            BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
            List<BlockState> allAllowed = new ArrayList<>(whitelistData.getAllowedStates());

            if (allAllowed.isEmpty()) {
                allAllowed.add(Blocks.CRAFTING_TABLE.defaultBlockState());
            }

            Collections.shuffle(allAllowed);
            int pickCount = Math.min(allAllowed.size(), 4);
            List<BlockState> options = allAllowed.subList(0, pickCount);

            // 发包打开 UI
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new PacketHandler.S2COpenSelectScreen(options)
            );
            // ----------------

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 消耗物品 (非创造模式)
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            player.getCooldowns().addCooldown(this, 20);
            return InteractionResultHolder.consume(itemStack);
        }

        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.literal("🖱️ 右键: 打开随机伪装菜单 (消耗品)").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("🖱️ 左键(仅创造): 变成指针指向的方块").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}
