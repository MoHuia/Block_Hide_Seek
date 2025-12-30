package com.mohuia.block_hide_seek.command;

import com.mohuia.block_hide_seek.game.WinnerType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mohuia.block_hide_seek.data.GameDataProvider; // 导入数据能力
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
        );
    }


    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            com.mohuia.block_hide_seek.game.GameLoopManager.startGame(player);
        }
        return 1;
    }

    // --- 设置抓捕者逻辑 ---
    private static void setupSeeker(ServerPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 1. 修改数据
            cap.setSeeker(true);
            cap.setDisguise(null); // 抓捕者不能有伪装

            // 2. 同步数据给所有人 (让大家知道他是抓捕者，且没有伪装)
            PacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new PacketHandler.S2CSyncGameData(player.getId(), true, null)
            );
        });

        // 3. 发送提示消息
//        player.sendSystemMessage(Component.literal("⚔️ §c你被选中成为了抓捕者！§r\n找出所有伪装的方块！"));
//        player.sendSystemMessage(Component.literal("§7(等待躲藏者选择方块...)"));

        // 4. (可选) 给抓捕者发点装备，或者清空背包
        // player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());
        // player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
    }

    // --- 设置躲藏者逻辑 ---
    private static void setupHider(ServerPlayer player, List<BlockState> fullWhitelist) {
        // 1. 确保重置状态 (防止上一局是抓捕者，这一局变成躲藏者时状态没变)
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            // 这里不需要 setDisguise，因为等下选完方块会自动设置
        });

        // 2. 随机抽取 4 个选项
        List<BlockState> randomOptions = pickRandomBlocks(fullWhitelist, 4);

        // 3. 发送选择界面包
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketHandler.S2COpenSelectScreen(randomOptions)
        );

        // 4. 发送提示
//        player.sendSystemMessage(Component.literal("🥸 §a你是躲藏者！§r\n请尽快在屏幕上选择你的伪装！"));
    }

    private static List<BlockState> pickRandomBlocks(List<BlockState> source, int count) {
        List<BlockState> copy = new ArrayList<>(source);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(copy.size(), count));
    }
}
