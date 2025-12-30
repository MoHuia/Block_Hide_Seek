package com.mohuia.block_hide_seek.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockHuntCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blockhunt")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(BlockHuntCommand::startGame))
        );
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        try {
            // 1. 获取全局白名单
            BlockWhitelistData data = BlockWhitelistData.get(ctx.getSource().getServer().overworld());
            List<BlockState> fullWhitelist = new ArrayList<>(data.getAllowedStates());

            // 保底逻辑：如果白名单太少，自动填充默认方块
            if (fullWhitelist.isEmpty()) {
                fullWhitelist.add(Blocks.CRAFTING_TABLE.defaultBlockState());
                fullWhitelist.add(Blocks.FURNACE.defaultBlockState());
                fullWhitelist.add(Blocks.HAY_BLOCK.defaultBlockState());
                fullWhitelist.add(Blocks.TNT.defaultBlockState());
            }

            // 2. 遍历每一个在线玩家
            List<ServerPlayer> players = ctx.getSource().getLevel().players();

            for (ServerPlayer player : players) {
                // 3. 为每个玩家单独随机抽取 4 个
                List<BlockState> randomOptions = pickRandomBlocks(fullWhitelist, 4);

                // 4. 只把这 4 个发给该玩家
                PacketHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketHandler.S2COpenSelectScreen(randomOptions)
                );
            }

            ctx.getSource().sendSuccess(() -> Component.literal("游戏开始！已为 " + players.size() + " 名玩家分配随机方块。"), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 辅助方法：从列表中随机抽取 n 个不重复的元素
     */
    private static List<BlockState> pickRandomBlocks(List<BlockState> source, int count) {
        // 复制一份列表以防修改原数据
        List<BlockState> copy = new ArrayList<>(source);

        // 打乱顺序
        Collections.shuffle(copy);

        // 截取前 count 个 (如果不够 count 个，就全取)
        return copy.subList(0, Math.min(copy.size(), count));
    }
}
