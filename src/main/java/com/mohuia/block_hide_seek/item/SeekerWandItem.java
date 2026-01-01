package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.client.ClientModelHelper;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeekerWandItem extends Item {

    public SeekerWandItem() {
        super(new Properties()
                .stacksTo(1) // 一次性道具，或者不可堆叠
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    // ==========================================
    //            左键逻辑：调试变身 (仅创造)
    // ==========================================

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        // 1. 【严格限制】仅限创造模式
        // 生存模式玩家左键只会像普通物品一样敲击方块
        if (!player.isCreative()) {
            return false;
        }

        Level level = player.level();
        BlockState targetState = level.getBlockState(pos);

        // 2. 基础检查
        if (targetState.isAir() || targetState.getRenderShape() == RenderShape.INVISIBLE) {
            return false;
        }

        // 3. 客户端逻辑：计算模型并请求变身
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    handleClientTransform(player, targetState)
            );
        }

        // 4. 返回 true 阻止方块被破坏
        // 创造模式下左键通常会瞬间破坏方块，这里拦截它来实现变身功能
        return true;
    }

    /**
     * 客户端专用：计算尺寸并发送变身包
     */
    private void handleClientTransform(Player player, BlockState worldState) {
        // =====================================================================
        // 🔧 核心逻辑：状态清洗 (State Cleaning)
        // 即使是调试，也要模拟右键的逻辑，使用"干净"的默认状态，防止模型歪斜
        // =====================================================================

        BlockState cleanState = worldState.getBlock().defaultBlockState();

        // 计算尺寸 (使用 cleanState)
        ClientModelHelper.SizeResult result = ClientModelHelper.getSizeResult(cleanState);

        // 发送包
        PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SSelectBlock(
                cleanState,
                result.modelW, result.modelH,
                result.obbX, result.obbY, result.obbZ
        ));

        // 调试反馈
        player.playSound(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f, 1.0f);
        player.displayClientMessage(Component.literal("§d⚡ [Debug] 已强制变身为: " + cleanState.getBlock().getName().getString()), true);
    }


    // ==========================================
    //            右键逻辑：随机菜单 (消耗品)
    // ==========================================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // 只在服务端处理
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {

            // 1. 冷却检查
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(itemStack);
            }

            // 2. 获取随机方块
            List<BlockState> options = getSubSetOfWhitelist(level, 4);

            // 3. 打开 GUI
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new PacketHandler.S2COpenSelectScreen(options)
            );

            // 4. 播放音效
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 5. 【消耗品逻辑】非创造模式扣除物品
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            // 6. 设置冷却 (防止因网络延迟导致的连点)
            player.getCooldowns().addCooldown(this, 20);
        }

        return InteractionResultHolder.consume(itemStack);
    }

    /**
     * 辅助方法：从白名单中随机抽取 N 个方块
     */
    private List<BlockState> getSubSetOfWhitelist(Level level, int count) {
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allAllowed = new ArrayList<>(whitelistData.getAllowedStates());

        if (allAllowed.isEmpty()) {
            allAllowed.add(Blocks.CRAFTING_TABLE.defaultBlockState());
        }

        Collections.shuffle(allAllowed);
        return allAllowed.subList(0, Math.min(allAllowed.size(), count));
    }

    // ==========================================
    //            工具提示
    // ==========================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.literal("🖱️ 右键: 打开随机伪装菜单 (一次性)").withStyle(ChatFormatting.GRAY));
        // 仅在按住 Shift 或创造模式下显示调试信息 (可选优化，这里直接显示)
        tooltipComponents.add(Component.literal("🖱️ 左键(仅创造): Debug - 变身为指向方块").withStyle(ChatFormatting.DARK_PURPLE));
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}
