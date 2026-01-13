package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.client.hud.ClientGameCache;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.entity.DecoyEntity; // ✅ 导入替身实体
import com.mohuia.block_hide_seek.event.GameEndEvent;
import com.mohuia.block_hide_seek.event.GameStartEvent;
import com.mohuia.block_hide_seek.hitbox.ObbRaycast;
import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB; // ✅ 导入 OBB 类
import com.mohuia.block_hide_seek.item.ModItems;
import com.mohuia.block_hide_seek.item.SeekerWandItem;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2COpenSelectScreen;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import com.mohuia.block_hide_seek.packet.S2C.S2CUpdateHudPacket;
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import com.mohuia.block_hide_seek.world.MapExtraIntegration;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB; // ✅ 导入 AABB
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * 游戏核心循环管理器
 */
public class GameLoopManager {
    //游戏运行状态标记，默认关闭
    private static boolean isGameRunning = false;
    //剩余时间，默认0
    private static int ticksRemaining = 0;
    //受击模拟常量
    private static final int FAKE_IFRAMES_TICKS = 10;
    private static final int FAKE_HURT_ANIM_TICKS = 10;
    private static final float FAKE_KNOCKBACK = 0.4F;

    // ✅ 定义抓捕者速度加成的 UUID (确保唯一性)
    private static final UUID SEEKER_SPEED_UUID = UUID.fromString("c0d3b45e-1234-5678-9abc-def012345678");
    // ✅ 定义 5% 的速度加成
    private static final AttributeModifier SEEKER_SPEED_BOOST = new AttributeModifier(
            SEEKER_SPEED_UUID, "Seeker Speed Bonus", 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL
    );

    public static boolean isGameRunning() {
        return isGameRunning;
    }

    //              ----游戏流程控制----
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
        // ✅ 游戏开始前清理上一局残留的诱饵
        cleanupDecoys(level);

        isGameRunning = true;
        ticksRemaining = config.gameDurationSeconds * 20;
        resetAllPlayers(level);
        Collections.shuffle(players);

        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        String mapTag = config.gameMapTag;
        MapExtraIntegration mapData = MapExtraIntegration.get(level);
        boolean shouldTeleport = mapTag != null && !mapTag.isEmpty();

        //分配抓捕者
        for (int i = 0; i < config.seekerCount; i++) {
            ServerPlayer p = players.get(i);
            makeSeeker(p, true);
            if (shouldTeleport) {
                BlockPos pos = mapData.getRandomPos(mapTag, level);
                if (pos != null) {
                    p.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                }
            }
        }
        //分配躲藏者
        for (int i = config.seekerCount; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            makeHider(p, allowedBlocks);
            if (shouldTeleport) {
                BlockPos pos = mapData.getRandomPos(mapTag, level);
                if (pos != null) {
                    p.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                }
            }
        }

        MinecraftForge.EVENT_BUS.post(new GameStartEvent(level));
        broadcast(level, Component.literal("游戏开始！限时 " + config.gameDurationSeconds + " 秒！").withStyle(ChatFormatting.GREEN));
        broadcastHudUpdate(level, true);
    }

    private static void startDebugMode(ServerPlayer player, ServerLevel level) {
        isGameRunning = true;
        ticksRemaining = 6000;
        resetPlayerState(player);

        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        makeHider(player, allowedBlocks);
        player.sendSystemMessage(Component.literal("🛠️已进入单人调试模式").withStyle(ChatFormatting.GOLD));
        broadcastHudUpdate(level, true);
    }

    public static void stopGame(ServerLevel level, WinnerType winner, Component reason) {
        if (!isGameRunning) return;
        isGameRunning = false;

        // ✅ 游戏结束时，清理场上所有诱饵
        cleanupDecoys(level);

        MinecraftForge.EVENT_BUS.post(new GameEndEvent(level, winner, reason));

        ServerGameConfig config = ServerGameConfig.get(level);
        String lobbyTag = config.lobbyTag;
        MapExtraIntegration mapData = MapExtraIntegration.get(level);
        boolean shouldTeleportLobby = lobbyTag != null && !lobbyTag.isEmpty();

        for (ServerPlayer player : level.players()) {
            resetPlayerState(player);
            if (shouldTeleportLobby) {
                BlockPos pos = mapData.getRandomPos(lobbyTag, level);
                if (pos != null) {
                    player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                }
            }
        }

        broadcast(level, Component.literal("🛑 游戏结束！").append(reason).withStyle(ChatFormatting.GOLD));
        broadcastHudUpdate(level, false);
    }

    public static void tick(ServerLevel level) {
        if (!isGameRunning) return;
        ticksRemaining--;

        if (ticksRemaining <= 0) {
            stopGame(level, WinnerType.HIDERS, Component.literal("时间到！躲藏者获胜！🎉"));
            return;
        }

        if (ticksRemaining % 1200 == 0) {
            broadcast(level, Component.literal("⏳ 剩余时间: " + (ticksRemaining / 20 / 60) + " 分钟"));
        }

        if (ticksRemaining == 200) {
            broadcast(level, Component.literal("⏳ 最后 10 秒！").withStyle(ChatFormatting.RED));
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "title @a title {\"text\":\"10\", \"color\":\"red\"}"
            );
        }

        if (ticksRemaining % 20 == 0) {
            checkSeekerWinCondition(level);
        }

        if (ticksRemaining % 20 == 0) {
            broadcastHudUpdate(level, true);
        }
    }

    private static void checkSeekerWinCondition(ServerLevel level) {
        long hiderCount = level.players().stream().filter(p -> {
            if (p.isSpectator()) return false;
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            return cap != null && !cap.isSeeker();
        }).count();

        if (hiderCount == 0) {
            stopGame(level, WinnerType.SEEKERS, Component.literal("⚔️ 抓捕者胜利！"));
        }
    }

    // ==========================================
    //              玩家与替身互动逻辑 (OBB 射线检测)
    // ==========================================

    private static double getReach(ServerPlayer attacker) {
        double reach = 3.5;
        try {
            var attr = attacker.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (attr != null) reach = Math.max(reach, attr.getValue());
        } catch (Throwable ignored) {
        }
        return reach;
    }

    /**
     * 抓捕者左键攻击触发 (修改版：支持击中玩家和替身)
     */
    public static void onSeekerLeftClickRaycast(ServerPlayer attacker, boolean debugParticles) {
        if (!isGameRunning) return;
        attacker.getCapability(GameDataProvider.CAP).ifPresent(atCap -> {
            if (!atCap.isSeeker()) return;

            ServerLevel level = attacker.serverLevel();
            Vec3 origin = attacker.getEyePosition();
            Vec3 dir = attacker.getLookAngle().normalize();
            double reach = getReach(attacker);

            if (debugParticles) {
                spawnDebugRay(level, origin, dir, reach);
            }

            // ✅ 核心改动：查找最近的目标（可能是玩家，也可能是替身）
            RaycastResult result = raycastFindClosestTarget(attacker, origin, dir, reach);

            if (result == null) return; // 没打中任何东西

            // 情况A：击中玩家
            if (result.type == TargetType.PLAYER && result.player != null) {
                result.player.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
                    if (vicCap.isSeeker()) return;
                    if (isInIFrames(result.player)) return;

                    simulateVanillaLikeHit(attacker, result.player);
                    handleHiderHit(attacker, result.player, vicCap);
                });
            }
            // 情况B：击中替身
            else if (result.type == TargetType.DECOY && result.decoy != null) {
                handleDecoyHit(attacker, result.decoy);
            }
        });
    }

    /**
     * 统一扫描：寻找射线路径上最近的 "玩家" 或 "替身"
     */
    private static RaycastResult raycastFindClosestTarget(ServerPlayer attacker, Vec3 origin, Vec3 dir, double reach) {
        ServerLevel level = attacker.serverLevel();
        double bestDist = Double.POSITIVE_INFINITY;
        RaycastResult bestResult = null;

        // 1. 扫描所有躲藏者 (Player)
        for (ServerPlayer p : level.players()) {
            if (p == attacker || p.isSpectator()) continue;

            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            if (cap == null || cap.isSeeker() || cap.isInvisible()) continue;

            // 获取玩家 OBB
            Optional<VirtualOBB> obbOpt = ObbUtil.getPlayerObb(p);
            if (obbOpt.isEmpty()) continue;

            double t = ObbRaycast.hitDistance(origin, dir, reach, obbOpt.get());
            if (t >= 0.0 && t < bestDist) {
                bestDist = t;
                bestResult = new RaycastResult(p, t);
            }
        }

        // 2. 扫描范围内的替身 (DecoyEntity)
        AABB searchBox = attacker.getBoundingBox().inflate(reach);
        List<DecoyEntity> decoys = level.getEntitiesOfClass(DecoyEntity.class, searchBox);

        for (DecoyEntity decoy : decoys) {
            // 获取替身 OBB (需确保 ObbUtil.getDecoyObb 已实现)
            Optional<VirtualOBB> obbOpt = ObbUtil.getDecoyObb(decoy);
            if (obbOpt.isEmpty()) continue;

            double t = ObbRaycast.hitDistance(origin, dir, reach, obbOpt.get());
            if (t >= 0.0 && t < bestDist) {
                bestDist = t;
                bestResult = new RaycastResult(decoy, t);
            }
        }

        return bestResult;
    }

    /**
     * 处理击中替身的逻辑
     */
    private static void handleDecoyHit(ServerPlayer attacker, DecoyEntity decoy) {
        // 播放破碎音效
        attacker.level().playSound(null, decoy.getX(), decoy.getY(), decoy.getZ(),
                SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 1.0f, 1.5f);

        // 提示消息
        attacker.displayClientMessage(Component.literal("💥 击碎了替身！").withStyle(ChatFormatting.GRAY), true);

        // 销毁替身
        decoy.discard();

        // 播放粒子
        ((ServerLevel)attacker.level()).sendParticles(ParticleTypes.CLOUD,
                decoy.getX(), decoy.getY() + 0.5, decoy.getZ(),
                5, 0.2, 0.2, 0.2, 0.1);
    }

    private static void spawnDebugRay(ServerLevel level, Vec3 origin, Vec3 dirNorm, double dist) {
        int steps = (int) Math.max(8, dist * 16);
        double step = dist / steps;
        for (int i = 0; i <= steps; i++) {
            Vec3 p = origin.add(dirNorm.scale(step * i));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // ==========================================
    //              数据结构
    // ==========================================

    private enum TargetType { PLAYER, DECOY }

    /**
     * 统一结果类，存储击中的目标类型和距离
     */
    private static final class RaycastResult {
        final TargetType type;
        final ServerPlayer player;
        final DecoyEntity decoy;
        final double dist;

        // 构造玩家结果
        RaycastResult(ServerPlayer player, double dist) {
            this.type = TargetType.PLAYER;
            this.player = player;
            this.decoy = null;
            this.dist = dist;
        }

        // 构造替身结果
        RaycastResult(DecoyEntity decoy, double dist) {
            this.type = TargetType.DECOY;
            this.player = null;
            this.decoy = decoy;
            this.dist = dist;
        }
    }

    private static void handleHiderHit(ServerPlayer attacker, ServerPlayer victim, com.mohuia.block_hide_seek.data.IGameData vicCap) {
        ServerGameConfig config = ServerGameConfig.get(attacker.level());

        vicCap.incrementHitCount();
        int currentHits = vicCap.getHitCount();
        int maxHits = config.hitsToConvert;

        attacker.displayClientMessage(
                Component.literal("🗡️ 击中目标！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.YELLOW),
                true
        );
        victim.displayClientMessage(
                Component.literal("🛡️ 你受到了攻击！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.RED),
                true
        );

        if (currentHits >= maxHits) {
            broadcast(attacker.serverLevel(), victim.getDisplayName().copy().append(" 被抓住了，变成了抓捕者！").withStyle(ChatFormatting.YELLOW));
            makeSeeker(victim, false);
            // ✅ 立即更新 HUD
            broadcastHudUpdate(attacker.serverLevel(), true);
            checkSeekerWinCondition(attacker.serverLevel());
        }
    }

    private static boolean isInIFrames(ServerPlayer victim) {
        return victim.invulnerableTime > 0 || victim.hurtTime > 0;
    }

    private static void simulateVanillaLikeHit(ServerPlayer attacker, ServerPlayer victim) {
        double d0 = attacker.getX() - victim.getX();
        double d1 = attacker.getZ() - victim.getZ();

        while (d0 * d0 + d1 * d1 < 1.0E-4D) {
            d0 = (Math.random() - Math.random()) * 0.01D;
            d1 = (Math.random() - Math.random()) * 0.01D;
        }

        victim.knockback(FAKE_KNOCKBACK, d0, d1);
        victim.invulnerableTime = FAKE_IFRAMES_TICKS;
        victim.hurtTime = FAKE_HURT_ANIM_TICKS;
        victim.hurtDuration = FAKE_HURT_ANIM_TICKS;
        victim.level().broadcastEntityEvent(victim, (byte) 2);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.0F);
        victim.hurtMarked = true;
    }

    // ==========================================
    //              角色分配辅助方法
    // ==========================================

    private static void makeSeeker(ServerPlayer player, boolean isStart) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(true);
            cap.setDisguise(null); // ✅ 清理伪装
            cap.setHitCount(0);
            syncData(player, true, null);
        });

        player.addTag("role_seeker");
        // ✅ 必须移除这个 Tag，否则抓捕者会像躲藏者一样隐藏血条
        player.removeTag("bhs_hide_health");

        player.setHealth(player.getMaxHealth());
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());

        // ✅ 1. 发放抓捕者装备 (剑 + 指南针/雷达)
        ItemStack radar = new ItemStack(ModItems.RADAR.get(), 1);
        ItemStack bow = new ItemStack(ModItems.BOW.get(),1); // ⚠️ 注意：如果你有 ModItems.RADAR，请在这里替换为 new ItemStack(ModItems.RADAR.get());

        player.getInventory().add(radar);
        player.getInventory().add(bow);

        // ✅ 2. 给予 5% 移动速度加成
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            // 先尝试移除，防止重复叠加
            speedAttr.removeModifier(SEEKER_SPEED_UUID);
            speedAttr.addTransientModifier(SEEKER_SPEED_BOOST);
        }

        // ✅ 3. 如果不是游戏开始(即抓捕到了躲藏者)，给剩余的躲藏者发放奖励
        if (!isStart) {
            distributeHiderBonus(player.serverLevel());
        }

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        Component titleText = Component.literal("你成为了抓捕者！")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        player.connection.send(new ClientboundSetTitleTextPacket(titleText));

        String subStr = isStart ? "去抓捕所有躲藏者！" : "你被抓住了，加入抓捕阵营！";
        Component subText = Component.literal(subStr).withStyle(ChatFormatting.GOLD);
        player.connection.send(new ClientboundSetSubtitleTextPacket(subText));

        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * ✅ 辅助方法：给所有存活的躲藏者发放奖励
     */
    private static void distributeHiderBonus(ServerLevel level) {
        // ==================================================
        // ✅ 定义：追加奖励物品 (每死一个队友给一个)
        // ==================================================
        ItemStack vanish = new ItemStack(ModItems.VANISH.get(),1);

        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;

            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 如果是躲藏者 (!isSeeker)，就发奖励
                if (!cap.isSeeker()) {
                    boolean added = p.getInventory().add(vanish.copy());

                    if (added) {
                        p.displayClientMessage(Component.literal("🎁 队友被抓！获得生存补给！").withStyle(ChatFormatting.GREEN), true);
                        p.playSound(SoundEvents.NOTE_BLOCK_CHIME.get(), 1.0f, 1.5f);
                    }
                }
            });
        }
    }

    private static void makeHider(ServerPlayer player, List<BlockState> options) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setHitCount(0);
            cap.setDisguise(null);
            syncData(player, false, null);
        });

        player.addTag("bhs_hide_health");

        // 1. 清空背包
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());

        // ==================================================
        // ✅ 新增：发放躲藏者【初始奖励】
        // ==================================================
        ItemStack vanish = new ItemStack(ModItems.VANISH.get(),1);
        ItemStack seeker_wand = new ItemStack(ModItems.SEEKER_WAND.get(),1);
        ItemStack decoy = new ItemStack(ModItems.DECOY.get(),1);

        player.getInventory().add(decoy);
        player.getInventory().add(vanish);
        player.getInventory().add(seeker_wand);
        // ==================================================

        List<BlockState> myOptions = new ArrayList<>(options);
        Collections.shuffle(myOptions);
        myOptions = myOptions.subList(0, Math.min(myOptions.size(), 4));

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenSelectScreen(myOptions));
    }

    private static void resetPlayerState(ServerPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setDisguise(null);
            cap.setHitCount(0);
            syncData(player, false, null);
        });

        player.removeTag("role_seeker");
        player.removeTag("bhs_hide_health");

        // ✅ 清除属性修改器 (移除速度加成)
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SEEKER_SPEED_UUID);
        }

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
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            PacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new S2CSyncGameData(
                            player.getId(),
                            seeker,
                            block,
                            cap.getModelWidth(), cap.getModelHeight(),
                            cap.getAABBX(), cap.getAABBY(), cap.getAABBZ()
                    )
            );
        });
        player.refreshDimensions();
    }

    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(msg);
        }
    }

    private static void broadcastHudUpdate(ServerLevel level, boolean isRunning) {
        List<ClientGameCache.PlayerInfo> list = new ArrayList<>();

        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;

            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                net.minecraft.world.item.ItemStack disguise = net.minecraft.world.item.ItemStack.EMPTY;
                if (cap.getDisguise() != null) {
                    disguise = new net.minecraft.world.item.ItemStack(cap.getDisguise().getBlock());
                }

                list.add(new ClientGameCache.PlayerInfo(
                        p.getUUID(),
                        p.getGameProfile().getName(),
                        cap.isSeeker(),
                        disguise
                ));
            });
        }

        PacketHandler.INSTANCE.send(
                PacketDistributor.DIMENSION.with(level::dimension),
                new S2CUpdateHudPacket(isRunning, ticksRemaining, list)
        );
    }

    // ==========================================
    //              清理逻辑
    // ==========================================

    /**
     * 清理地图上的所有伪装实体
     */
    private static void cleanupDecoys(ServerLevel level) {
        List<DecoyEntity> toRemove = new ArrayList<>();

        // 使用 EntityTypeTest 高效查找指定类型的实体
        for (DecoyEntity entity : level.getEntities(EntityTypeTest.forClass(DecoyEntity.class), e -> true)) {
            toRemove.add(entity);
        }

        // 遍历删除
        for (DecoyEntity entity : toRemove) {
            entity.discard();
        }
    }

    public static void catchHiderImmediately(ServerPlayer seeker, ServerPlayer hider) {
        if (!isGameRunning) return;

        hider.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker()) return;

            // 广播消息
            broadcast(hider.serverLevel(), net.minecraft.network.chat.Component.literal("🏹 ")
                    .append(seeker.getDisplayName())
                    .append(" 射杀了 ")
                    .append(hider.getDisplayName())
                    .withStyle(net.minecraft.ChatFormatting.RED));

            // 变为抓捕者
            makeSeeker(hider, false);
            // ✅ 立即更新 HUD
            broadcastHudUpdate(hider.serverLevel(), true);

            // 检查胜利
            checkSeekerWinCondition(hider.serverLevel());
        });
    }
}
