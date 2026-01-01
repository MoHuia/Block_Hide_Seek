package com.mohuia.block_hide_seek.command;

import com.mohuia.block_hide_seek.game.WinnerType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockHuntCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bhs")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(BlockHuntCommand::startGame))
                .then(Commands.literal("stop").executes(ctx -> {
                    com.mohuia.block_hide_seek.game.GameLoopManager.stopGame(
                            ctx.getSource().getLevel(),
                            WinnerType.DRAW,
                            Component.literal("管理员强制停止")
                    );
                    return 1;
                }))
                // 设置伪装为手中方块的指令
                .then(Commands.literal("sethand").executes(BlockHuntCommand::setDisguiseToHand))
                // 【新增】调试指令：分析手中方块模型并回传尺寸
                .then(Commands.literal("block").executes(BlockHuntCommand::spawnDebugEntityFromHand))
        );
    }

    // --- 新增：发送请求包给客户端进行模型分析 ---
    private static int spawnDebugEntityFromHand(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            // 发送请求包给客户端，让客户端去算 BakedModel 的顶点
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketHandler.S2CRequestModelData()
            );

            player.sendSystemMessage(Component.literal("📡 已发送模型分析请求... 请留意聊天栏返回的数据")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    // --- 把伪装设置为手中方块 ---
    private static int setDisguiseToHand(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            // 1. 获取主手物品
            ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            Item item = heldItem.getItem();

            // 2. 判断是否是方块
            if (item instanceof BlockItem blockItem) {
                BlockState state = blockItem.getBlock().defaultBlockState();

                // 3. 应用伪装逻辑
                player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                    cap.setSeeker(false); // 强制设为躲藏者
                    cap.setDisguise(state); // 设置伪装

                    // 4. 同步给所有人
                    PacketHandler.INSTANCE.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                            new PacketHandler.S2CSyncGameData(player.getId(), false, state)
                    );

                    // 5. 反馈消息
                    player.sendSystemMessage(Component.literal("✅ 已将伪装设置为: " + state.getBlock().getName().getString())
                            .withStyle(ChatFormatting.GREEN));
                });
            } else {
                // 如果手里拿的不是方块
                player.sendSystemMessage(Component.literal("❌ 你手里拿的不是方块！").withStyle(ChatFormatting.RED));
            }
        }
        return 1;
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            com.mohuia.block_hide_seek.game.GameLoopManager.startGame(player);
        }
        return 1;
    }

    // 辅助方法：随机挑选方块 (你的逻辑里似乎没用到这个作为指令，保留即可)
    private static List<BlockState> pickRandomBlocks(List<BlockState> source, int count) {
        List<BlockState> copy = new ArrayList<>(source);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(copy.size(), count));
    }
}
