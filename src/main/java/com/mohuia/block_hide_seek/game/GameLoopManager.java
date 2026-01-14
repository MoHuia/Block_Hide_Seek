package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
import com.mohuia.block_hide_seek.event.GameEndEvent;
import com.mohuia.block_hide_seek.event.GameStartEvent;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import com.mohuia.block_hide_seek.world.MapExtraIntegration;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 游戏流程编排者 (Facade / Mediator)
 */
public class GameLoopManager {
    private static boolean isGameRunning = false;
    private static int ticksRemaining = 0;

    public static boolean isGameRunning() { return isGameRunning; }
    public static int getTicksRemaining() { return ticksRemaining; }

    public static void startGame(ServerPlayer starter) {
        if (isGameRunning) {
            starter.sendSystemMessage(Component.literal("❌ 游戏已经在进行中了！"));
            return;
        }
        ServerLevel level = starter.serverLevel();
        List<ServerPlayer> players = new ArrayList<>(level.players());

        if (players.size() == 1) {
            startDebugMode(starter, level);
            return;
        }

        ServerGameConfig config = ServerGameConfig.get(level);
        if (players.size() < 2) {
            starter.sendSystemMessage(Component.literal("❌ 人数不足，至少需要 2 人！"));
            return;
        }
        if (config.seekerCount >= players.size()) {
            starter.sendSystemMessage(Component.literal("❌ 抓捕者人数必须小于总人数！"));
            return;
        }

        cleanupDecoys(level);

        isGameRunning = true;
        ticksRemaining = config.gameDurationSeconds * 20;

        // 重置所有玩家
        for (ServerPlayer p : level.players()) GameRoleManager.resetPlayer(p);
        Collections.shuffle(players);

        // 获取白名单方块
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        // 准备地图传送
        String mapTag = config.gameMapTag;
        MapExtraIntegration mapData = MapExtraIntegration.get(level);
        boolean shouldTeleport = mapTag != null && !mapTag.isEmpty();

        // 分配抓捕者
        for (int i = 0; i < config.seekerCount; i++) {
            ServerPlayer p = players.get(i);
            GameRoleManager.makeSeeker(p, true);
            teleportIfMapSet(p, mapData, mapTag, shouldTeleport, level);
        }
        // 分配躲藏者
        for (int i = config.seekerCount; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            GameRoleManager.makeHider(p, allowedBlocks);
            teleportIfMapSet(p, mapData, mapTag, shouldTeleport, level);
        }

        MinecraftForge.EVENT_BUS.post(new GameStartEvent(level));
        GameNetworkHelper.broadcast(level, Component.literal("游戏开始！限时 " + config.gameDurationSeconds + " 秒！").withStyle(ChatFormatting.GREEN));
        GameNetworkHelper.updateHud(level, true, ticksRemaining);
    }

    private static void startDebugMode(ServerPlayer player, ServerLevel level) {
        isGameRunning = true;
        ticksRemaining = 6000;
        GameRoleManager.resetPlayer(player);

        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        GameRoleManager.makeHider(player, allowedBlocks);
        player.sendSystemMessage(Component.literal("🛠️已进入单人调试模式").withStyle(ChatFormatting.GOLD));
        GameNetworkHelper.updateHud(level, true, ticksRemaining);
    }

    public static void stopGame(ServerLevel level, WinnerType winner, Component reason) {
        if (!isGameRunning) return;
        isGameRunning = false;

        cleanupDecoys(level);
        MinecraftForge.EVENT_BUS.post(new GameEndEvent(level, winner, reason));

        // 传送回大厅
        ServerGameConfig config = ServerGameConfig.get(level);
        String lobbyTag = config.lobbyTag;
        MapExtraIntegration mapData = MapExtraIntegration.get(level);
        boolean shouldTeleportLobby = lobbyTag != null && !lobbyTag.isEmpty();

        for (ServerPlayer player : level.players()) {
            GameRoleManager.resetPlayer(player);
            if (shouldTeleportLobby) {
                BlockPos pos = mapData.getRandomPos(lobbyTag, level);
                if (pos != null) player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            }
        }

        GameNetworkHelper.broadcast(level, Component.literal("🛑 游戏结束！").append(reason).withStyle(ChatFormatting.GOLD));
        GameNetworkHelper.updateHud(level, false, ticksRemaining);
    }

    public static void tick(ServerLevel level) {
        if (!isGameRunning) return;
        ticksRemaining--;

        if (ticksRemaining <= 0) {
            stopGame(level, WinnerType.HIDERS, Component.literal("时间到！躲藏者获胜！🎉"));
            return;
        }

        if (ticksRemaining % 1200 == 0) {
            GameNetworkHelper.broadcast(level, Component.literal("⏳ 剩余时间: " + (ticksRemaining / 20 / 60) + " 分钟"));
        }

        if (ticksRemaining == 200) {
            GameNetworkHelper.broadcast(level, Component.literal("⏳ 最后 10 秒！").withStyle(ChatFormatting.RED));
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "title @a title {\"text\":\"10\", \"color\":\"red\"}"
            );
        }

        if (ticksRemaining % 20 == 0) checkSeekerWinCondition(level);
        if (ticksRemaining % 20 == 0) GameNetworkHelper.updateHud(level, true, ticksRemaining);
    }

    public static void checkSeekerWinCondition(ServerLevel level) {
        long hiderCount = level.players().stream().filter(p -> {
            if (p.isSpectator()) return false;
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            return cap != null && !cap.isSeeker();
        }).count();

        if (hiderCount == 0) {
            stopGame(level, WinnerType.SEEKERS, Component.literal("⚔️ 抓捕者胜利！"));
        }
    }

    private static void cleanupDecoys(ServerLevel level) {
        List<DecoyEntity> toRemove = new ArrayList<>();
        for (DecoyEntity entity : level.getEntities(EntityTypeTest.forClass(DecoyEntity.class), e -> true)) {
            toRemove.add(entity);
        }
        for (DecoyEntity entity : toRemove) entity.discard();
    }

    private static void teleportIfMapSet(ServerPlayer p, MapExtraIntegration mapData, String tag, boolean should, ServerLevel level) {
        if (should) {
            BlockPos pos = mapData.getRandomPos(tag, level);
            if (pos != null) p.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        }
    }
}
