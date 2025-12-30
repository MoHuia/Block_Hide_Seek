package com.mohuia.block_hide_seek.game;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;


import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.event.GameEndEvent;
import com.mohuia.block_hide_seek.event.GameStartEvent;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 游戏核心循环管理器
 * 负责游戏的开始、结束、倒计时、胜负判定以及玩家阵营管理
 */
public class GameLoopManager {

    // 标记游戏是否正在进行中
    private static boolean isGameRunning = false;
    // 游戏剩余时间（单位：Tick，20 ticks = 1秒）
    private static int ticksRemaining = 0;

    // ==========================================
    //              游戏流程控制
    // ==========================================

    /**
     * 开始游戏主逻辑
     * @param starter 触发开始指令的玩家
     */
    public static void startGame(ServerPlayer starter) {
        if (isGameRunning) {
            starter.sendSystemMessage(Component.literal("❌ 游戏已经在进行中了！"));
            return;
        }

        ServerLevel level = starter.serverLevel();
        List<ServerPlayer> players = new ArrayList<>(level.players());

        // 调试模式：单人测试
        if (players.size() == 1) {
            startDebugMode(starter, level);
            return;
        }

        // 读取配置
        ServerGameConfig config = ServerGameConfig.get(level);

        // 人数校验
        if (players.size() < 2) {
            starter.sendSystemMessage(Component.literal("❌ 人数不足，至少需要 2 人！"));
            return;
        }
        if (config.seekerCount >= players.size()) {
            starter.sendSystemMessage(Component.literal("❌ 抓捕者人数必须小于总人数！"));
            return;
        }

        // 初始化游戏状态
        isGameRunning = true;
        ticksRemaining = config.gameDurationSeconds * 20;

        // 重置状态
        resetAllPlayers(level);
        Collections.shuffle(players);

        // 获取白名单
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        // 分配抓捕者
        for (int i = 0; i < config.seekerCount; i++) {
            makeSeeker(players.get(i), true);
        }

        // 分配躲藏者
        for (int i = config.seekerCount; i < players.size(); i++) {
            makeHider(players.get(i), allowedBlocks);
        }

        // 抛出开始事件 (供 KubeJS 监听传送)
        MinecraftForge.EVENT_BUS.post(new GameStartEvent(level));

        // 广播
        broadcast(level, Component.literal("游戏开始！限时 " + config.gameDurationSeconds + " 秒！").withStyle(ChatFormatting.GREEN));
    }

    private static void startDebugMode(ServerPlayer player, ServerLevel level) {
        isGameRunning = false;
        resetPlayerState(player);
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());
        makeHider(player, allowedBlocks);
        player.sendSystemMessage(Component.literal("🛠️已进入单人调试模式").withStyle(ChatFormatting.GOLD));
    }

    /**
     * 停止游戏
     */
    public static void stopGame(ServerLevel level, WinnerType winner, Component reason) {
        if (!isGameRunning) return; // 防止重复停止

        isGameRunning = false;

        // 1. 发送 Forge 事件 (供 KubeJS 监听结束逻辑)
        MinecraftForge.EVENT_BUS.post(new GameEndEvent(level, winner, reason));

        // 2. 重置所有人
        resetAllPlayers(level);

        // 3. 广播
        broadcast(level, Component.literal("🛑 游戏结束！").append(reason).withStyle(ChatFormatting.GOLD));
    }

    /**
     * 游戏主循环 (Tick)
     * 需要在 GameTickHandler 中被调用
     */
    public static void tick(ServerLevel level) {
        if (!isGameRunning) return;

        ticksRemaining--;

        // --- 1. 胜利条件 A：时间耗尽 -> 躲藏者胜利 ---
        if (ticksRemaining <= 0) {
            stopGame(level, WinnerType.HIDERS, Component.literal("时间到！躲藏者获胜！🎉"));
            return;
        }

        // --- 2. 倒计时广播 ---
        if (ticksRemaining % 1200 == 0) { // 每分钟
            broadcast(level, Component.literal("⏳ 剩余时间: " + (ticksRemaining / 20 / 60) + " 分钟"));
        }
        if (ticksRemaining == 200) { // 最后10秒
            broadcast(level, Component.literal("⏳ 最后 10 秒！").withStyle(ChatFormatting.RED));
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "title @a title {\"text\":\"10\", \"color\":\"red\"}"
            );
        }

        // --- 3. 胜利条件 B：保底检测 (防止 handleHiderHit 未触发) ---
        if (ticksRemaining % 20 == 0) {
            checkSeekerWinCondition(level);
        }
    }

    /**
     * 【新增】检查是否抓捕者获胜（所有人都变成了抓捕者）
     */
    private static void checkSeekerWinCondition(ServerLevel level) {
        // 统计还有多少个活着的躲藏者
        long hiderCount = level.players().stream().filter(p -> {
            if (p.isSpectator()) return false;
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            // 如果 cap 存在且不是抓捕者，那就是躲藏者
            return cap != null && !cap.isSeeker();
        }).count();

        // 如果没有躲藏者了，抓捕者胜
        if (hiderCount == 0) {
            stopGame(level, WinnerType.SEEKERS, Component.literal("⚔️ 所有躲藏者都被抓获！抓捕者胜利！"));
        }
    }

    // ==========================================
    //              玩家互动逻辑 (PVP)
    // ==========================================

    public static void onPlayerAttack(ServerPlayer attacker, ServerPlayer victim) {
        if (!isGameRunning) return;

        attacker.getCapability(GameDataProvider.CAP).ifPresent(atCap -> {
            if (atCap.isSeeker()) {
                victim.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
                    if (!vicCap.isSeeker()) {
                        handleHiderHit(attacker, victim, vicCap);
                    }
                });
            }
        });
    }

    private static void handleHiderHit(ServerPlayer attacker, ServerPlayer victim, com.mohuia.block_hide_seek.data.IGameData vicCap) {
        ServerGameConfig config = ServerGameConfig.get(attacker.level());

        vicCap.incrementHitCount();
        int currentHits = vicCap.getHitCount();
        int maxHits = config.hitsToConvert;

        // Action Bar 提示
        attacker.displayClientMessage(
                Component.literal("🗡️ 击中目标！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.YELLOW),
                true
        );
        victim.displayClientMessage(
                Component.literal("🛡️ 你受到了攻击！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.RED),
                true
        );

        // 达到上限，转换阵营
        if (currentHits >= maxHits) {
            broadcast(attacker.serverLevel(), victim.getDisplayName().copy().append(" 被抓住了，变成了抓捕者！").withStyle(ChatFormatting.YELLOW));

            makeSeeker(victim, false); // 变为抓捕者

            // 【关键】立刻检查是否游戏结束（是不是最后一个人）
            checkSeekerWinCondition(attacker.serverLevel());
        }
    }

    // ==========================================
    //              角色分配辅助方法
    // ==========================================

    /**
     * 将玩家设置为抓捕者
     * @param isStart true=游戏刚开始分配; false=中途被抓转化
     */
    private static void makeSeeker(ServerPlayer player, boolean isStart) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(true);
            cap.setDisguise(null);
            cap.setHitCount(0);
            syncData(player, true, null);
        });
        // 【新增】添加身份标签，供 KubeJS 识别
        player.addTag("role_seeker");
        // 1. 添加隐藏血条标签
        player.addTag("bhs_hide_health");

        // 2. 恢复状态
        player.setHealth(player.getMaxHealth());
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());

        // ==================================================
        //              【新增】 发送大标题和音效
        // ==================================================

        // A. 设置标题动画 (淡入: 10 tick, 停留: 60 tick, 淡出: 20 tick)
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));

        // B. 设置主标题内容 (大红字)
        Component titleText = Component.literal("👹 你成为了抓捕者！")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        player.connection.send(new ClientboundSetTitleTextPacket(titleText));

        // C. 设置副标题内容 (根据是开局还是被抓，显示不同提示)
        String subStr = isStart ? "去抓捕所有躲藏者！" : "你被抓住了，加入抓捕阵营！";
        Component subText = Component.literal(subStr).withStyle(ChatFormatting.GOLD);
        player.connection.send(new ClientboundSetSubtitleTextPacket(subText));

        // D. 播放音效 (雷声，增加震撼感)
        // 参数：声音类型, 声音来源, 音量, 音调
        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 如果想要那种更压抑的声音，可以用这个替代上面的雷声：
        // player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void makeHider(ServerPlayer player, List<BlockState> options) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setHitCount(0);
            cap.setDisguise(null);
            syncData(player, false, null);
        });

        // 【新增】添加隐藏血条的标签
        player.addTag("bhs_hide_health");

        List<BlockState> myOptions = new ArrayList<>(options);
        Collections.shuffle(myOptions);
        myOptions = myOptions.subList(0, Math.min(myOptions.size(), 4));

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketHandler.S2COpenSelectScreen(myOptions));
    }

    // ==========================================
    //              通用辅助方法
    // ==========================================

    private static void resetPlayerState(ServerPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setDisguise(null);
            cap.setHitCount(0);
            syncData(player, false, null);
        });

        // 【新增】移除身份标签
        player.removeTag("role_seeker");

        // 【新增】移除隐藏血条的标签，恢复显示
        player.removeTag("bhs_hide_health");

        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());
    }

    private static void resetAllPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            resetPlayerState(player);
        }
    }

    private static void syncData(ServerPlayer player, boolean seeker, BlockState block) {
        PacketHandler.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PacketHandler.S2CSyncGameData(player.getId(), seeker, block)
        );
        player.refreshDimensions();
    }

    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(msg);
        }
    }
}
