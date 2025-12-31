package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import net.minecraft.ChatFormatting;
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
                .stacksTo(16)        // 允许堆叠16个，方便携带多个
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // 只在服务端执行逻辑
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {

            // --- 核心逻辑 ---
            BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
            List<BlockState> allAllowed = new ArrayList<>(whitelistData.getAllowedStates());

            if (allAllowed.isEmpty()) {
                allAllowed.add(Blocks.CRAFTING_TABLE.defaultBlockState());
            }

            Collections.shuffle(allAllowed);
            int pickCount = Math.min(allAllowed.size(), 4);
            List<BlockState> options = allAllowed.subList(0, pickCount);

            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new PacketHandler.S2COpenSelectScreen(options)
            );
            // ----------------

            // 播放音效
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 【关键修改】 消耗物品
            // 如果玩家不是创造模式，数量减 1
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1); // <--- 这一句让道具消失（数量-1）
            }

            // 虽然物品没了，但为了防止如果玩家有一组（16个）狂点右键
            // 还是加个冷却比较好
            player.getCooldowns().addCooldown(this, 20); // 1秒冷却

            return InteractionResultHolder.consume(itemStack);
        }

        // 客户端也返回 consume，配合服务端播放手部动画
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.literal("右键点击打开伪装选择界面").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("一次性用品!").withStyle(ChatFormatting.RED)); // <--- 提示玩家是一次性的
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}
