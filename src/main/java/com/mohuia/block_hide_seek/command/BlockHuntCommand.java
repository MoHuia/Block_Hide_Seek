package com.mohuia.block_hide_seek.command;

import com.mohuia.block_hide_seek.game.WinnerType;
import com.mohuia.block_hide_seek.packet.S2C.S2CRequestModelData;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import com.mohuia.block_hide_seek.world.DisguiseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
                .then(Commands.literal("block").executes(BlockHuntCommand::spawnDebugEntityFromHand))
                        .then(Commands.literal("reset").executes(BlockHuntCommand::resetPlayer))

//                // ✅ 新增：/bhs obb x y z
//                .then(Commands.literal("obb")
//                        .then(Commands.argument("x", FloatArgumentType.floatArg(0.05f, 64f))
//                                .then(Commands.argument("y", FloatArgumentType.floatArg(0.05f, 64f))
//                                        .then(Commands.argument("z", FloatArgumentType.floatArg(0.05f, 64f))
//                                                .executes(BlockHuntCommand::setObbSize)
//                                        )
//                                )
//                        )
//                )
        );
    }

//    private static int setObbSize(CommandContext<CommandSourceStack> ctx) {
//        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
//
//        float x = FloatArgumentType.getFloat(ctx, "x");
//        float y = FloatArgumentType.getFloat(ctx, "y");
//        float z = FloatArgumentType.getFloat(ctx, "z");
//
//        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
//            cap.setAABBSize(x, y, z);
//
//            player.sendSystemMessage(Component.literal("✅ OBB尺寸已设置: x=" + x + ", y=" + y + ", z=" + z)
//                    .withStyle(ChatFormatting.GREEN));
//            PacketHandler.INSTANCE.send(
//                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
//                    new PacketHandler.S2CSyncObb(player.getId(), x, y, z)
//            );
//
//            // ⚠️ 这里按你的要求：不修改其他同步逻辑
//            // 真正要客户端渲染看到效果，你后面需要加一个同步包把 aabbX/Y/Z 发到客户端。
//        });
//
//        return 1;
//    }

    //  处理重置逻辑
    private static int resetPlayer(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            DisguiseManager.clearDisguise(player);
        }
        return 1;
    }

    private static int spawnDebugEntityFromHand(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CRequestModelData()
            );

            player.sendSystemMessage(Component.literal("📡 已发送模型分析请求... 请留意聊天栏返回的数据")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }


    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            com.mohuia.block_hide_seek.game.GameLoopManager.startGame(player);
        }
        return 1;
    }

    private static List<BlockState> pickRandomBlocks(List<BlockState> source, int count) {
        List<BlockState> copy = new ArrayList<>(source);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(copy.size(), count));
    }
}
