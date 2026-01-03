package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.client.hud.ClientGameCache; // 确保引用了这个
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.event.GameEndEvent;
import com.mohuia.block_hide_seek.event.GameStartEvent;
import com.mohuia.block_hide_seek.hitbox.ObbRaycast;
import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2COpenSelectScreen;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import com.mohuia.block_hide_seek.packet.S2C.S2CUpdateHudPacket; // 确保引用了这个
import com.mohuia.block_hide_seek.world.BlockWhitelistData;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 游戏核心循环管理器
 */
public class GameLoopManager {
    //游戏运行状态标记，默认关闭
    private static boolean isGameRunning = false;
    //剩余时间，默认0
    private static int ticksRemaining = 0;
    //受击模拟常量
    //伪造的无敌帧，0.5s，防止被秒杀，10 tick = 0.5s
    private static final int FAKE_IFRAMES_TICKS = 10;
    //伪造受伤动画时长，0.5s
    private static final int FAKE_HURT_ANIM_TICKS = 10;
    //伪造鸡腿力度
    private static final float FAKE_KNOCKBACK = 0.4F;

    //标准的getter获取方法，获取当前游戏状态
    //为了让其他事件或系统知道游戏运行状态，所以提供了public供他们只读访问isGameRunning()
    public static boolean isGameRunning() {
        return isGameRunning;
    }

    //              ----游戏流程控制----
    public static void startGame(ServerPlayer starter) {
        //防止游戏重复开始，逻辑：如果游戏已经开始，发送消息并return驳回
        if (isGameRunning) {
            starter.sendSystemMessage(Component.literal("❌ 游戏已经在进行中了！"));
            return;
        }
        //获取服务器当前的地图世界
        ServerLevel level = starter.serverLevel();
        //获取当前世界的所有玩家列表，存入集合中
        List<ServerPlayer> players = new ArrayList<>(level.players());
        //如果玩家只有一个人，进入调试模式
        if (players.size() == 1) {
            startDebugMode(starter, level);
            return;
        }
        //读取游戏配置文件，比如游戏时长，抓捕者人数等
        ServerGameConfig config = ServerGameConfig.get(level);
        //如果玩家小于两人或者抓捕者比人多，return驳回
        //这里的小于2是做逻辑多层，一般不会输出这里的消息
        if (players.size() < 2) {
            starter.sendSystemMessage(Component.literal("❌ 人数不足，至少需要 2 人！"));
            return;
        }
        if (config.seekerCount >= players.size()) {
            starter.sendSystemMessage(Component.literal("❌ 抓捕者人数必须小于总人数！"));
            return;
        }
        //前面的没问题的话，则一切正常，正式开始游戏
        isGameRunning = true;
        //把游戏配置里设置的游戏时间x20，换算成tick
        ticksRemaining = config.gameDurationSeconds * 20;
        //将所有玩家状态重置
        resetAllPlayers(level);
        //打乱玩家顺序，以便于分配抓捕者和躲藏者
        Collections.shuffle(players);
        //获取白名单里的方块
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        //如果没有，默认给一个工作台，防止返回空值
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());
        //前面Collections.shuffle(players);已将玩家顺序打乱,拿排在随机顺序的前面的人变成抓捕者（由配置文件决定循环次数），循环是为了做停止
        for (int i = 0; i < config.seekerCount; i++) {
            makeSeeker(players.get(i), true);
        }
        //i = config.seekerCount已经把抓捕者人数分出去了，剩下的都变成躲藏者
        for (int i = config.seekerCount; i < players.size(); i++) {
            makeHider(players.get(i), allowedBlocks);
        }
        //发送游戏开始事件（通知其他模组或插件）
        MinecraftForge.EVENT_BUS.post(new GameStartEvent(level));
        //大喇叭全服广播
        broadcast(level, Component.literal("游戏开始！限时 " + config.gameDurationSeconds + " 秒！").withStyle(ChatFormatting.GREEN));
        //游戏开始：立刻通知客户端显示 HUD (true)
        broadcastHudUpdate(level, true);
    }

    //                          ----单人调试模式----
    private static void startDebugMode(ServerPlayer player, ServerLevel level) {
        isGameRunning = true;
        // 给个 5 分钟测试
        ticksRemaining = 6000;
        //重置玩家状态,因为只有一个人所以这里获取的玩家只能是自己
        resetPlayerState(player);
        //分配方块
        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());
        //强制让自己变成躲藏者
        makeHider(player, allowedBlocks);
        player.sendSystemMessage(Component.literal("🛠️已进入单人调试模式").withStyle(ChatFormatting.GOLD));
        // 调试开始：立刻通知显示 HUD
        broadcastHudUpdate(level, true);
    }

    //                          ----游戏强制停止----
    public static void stopGame(ServerLevel level, WinnerType winner, Component reason) {
        //如果已经停了，就不往下走了
        if (!isGameRunning) return;
        //让游戏运行状态为false，停止
        isGameRunning = false;
        //发送结束事件,使用GameEndEvent是为了让外界检测到游戏停止事件
        MinecraftForge.EVENT_BUS.post(new GameEndEvent(level, winner, reason));
        //重置玩家状态
        resetAllPlayers(level);
        broadcast(level, Component.literal("🛑 游戏结束！").append(reason).withStyle(ChatFormatting.GOLD));
        //关键修改：游戏停止后，发送 false 包，通知客户端隐藏 HUD
        broadcastHudUpdate(level, false);
    }

    //       ----相当于游戏的心跳----
    public static void tick(ServerLevel level) {
        //游戏没开始啥也不干
        if (!isGameRunning) return;
        //倒计时减 1 tick,
        ticksRemaining--;
        //如果倒计时减到0了
        if (ticksRemaining <= 0) {
            //停止游戏，胜者为躲藏者
            stopGame(level, WinnerType.HIDERS, Component.literal("时间到！躲藏者获胜！🎉"));
            return;
        }
        //每分钟广播一次时间
        if (ticksRemaining % 1200 == 0) {
            broadcast(level, Component.literal("⏳ 剩余时间: " + (ticksRemaining / 20 / 60) + " 分钟"));
        }
        //就剩最后10s广播一次
        if (ticksRemaining == 200) {
            broadcast(level, Component.literal("⏳ 最后 10 秒！").withStyle(ChatFormatting.RED));
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "title @a title {\"text\":\"10\", \"color\":\"red\"}"
            );
        }
        //每秒检查一次躲藏者是否被抓
        if (ticksRemaining % 20 == 0) {
            checkSeekerWinCondition(level);
        }

        // 每秒同步 HUD 数据
        if (ticksRemaining % 20 == 0) {
            broadcastHudUpdate(level,true);
        }
    }

    //          ----检查抓捕者是否胜利----
    private static void checkSeekerWinCondition(ServerLevel level) {
        //数数还有几个躲藏者活着
        long hiderCount = level.players().stream().filter(p -> {
            //旁观者不算
            if (p.isSpectator()) return false;
            //获取玩家的数据包
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            //如果数据包存在，且他不是 Seekers (那就是躲藏者)
            return cap != null && !cap.isSeeker();
        }).count();
        //如果躲藏者数量为 0，抓捕者胜利
        if (hiderCount == 0) {
            stopGame(level, WinnerType.SEEKERS, Component.literal("⚔️ 抓捕者胜利！"));
        }
    }

    //              ----玩家互动逻辑 (射线检测)----
    //获取玩家手有多长
    private static double getReach(ServerPlayer attacker) {
        //默认3.5格
        double reach = 3.5;
        try {
            //尝试获取其它模组修改过的攻击距离属性
            var attr = attacker.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (attr != null) reach = Math.max(reach, attr.getValue());
        } catch (Throwable ignored) {
        }
        return reach;
    }

    //                 ----当抓捕者左键方块或实体触发----
    public static void onSeekerLeftClickRaycast(ServerPlayer attacker, boolean debugParticles) {
        if (!isGameRunning) return;
        //检查是不是抓捕者
        attacker.getCapability(GameDataProvider.CAP).ifPresent(atCap -> {
            //如果是躲藏者就驳回，直接return
            if (!atCap.isSeeker()) return;

            ServerLevel level = attacker.serverLevel();
            // 眼睛的位置 (起点)
            Vec3 origin = attacker.getEyePosition();
            // 视线的方向 (方向)
            Vec3 dir = attacker.getLookAngle().normalize();
            // 射程
            double reach = getReach(attacker);
            // 调试模式：显示一条粒子射线，看看打哪了
            if (debugParticles) {
                spawnDebugRay(level, origin, dir, reach);
            }
            //核心计算：看看这条视线有没有穿过任何一个躲藏者的伪装方块
            RaycastTarget target = raycastFindClosestHiderOBB(attacker, origin, dir, reach);
            // 没打中，也是空的，则驳回
            if (target == null) return;
            //如果打中了
            target.victim.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
                //队友免伤
                if (vicCap.isSeeker()) return;
                //对方还在无敌时间里，不掉血
                if (isInIFrames(target.victim)) return;
                //播放被打的声音、击退效果 (因为是代码触发，不是原版攻击，要手动演一遍)
                simulateVanillaLikeHit(attacker, target.victim);
                //处理游戏数据 (比如被打第3下就变身)
                handleHiderHit(attacker, target.victim, vicCap);
            });
        });
    }

    private static RaycastTarget raycastFindClosestHiderOBB(ServerPlayer attacker, Vec3 origin, Vec3 dir, double reach) {
        ServerLevel level = attacker.serverLevel();
        ServerPlayer bestVictim = null;
        double bestT = Double.POSITIVE_INFINITY;

        for (ServerPlayer p : level.players()) {
            if (p == attacker) continue;
            if (p.isSpectator()) continue;

            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            if (cap == null) continue;
            if (cap.isSeeker()) continue;

            var obbOpt = ObbUtil.getPlayerObb(p);
            if (obbOpt.isEmpty()) continue;

            double t = ObbRaycast.hitDistance(origin, dir, reach, obbOpt.get());
            if (t >= 0.0 && t < bestT) {
                bestT = t;
                bestVictim = p;
            }
        }

        if (bestVictim == null) return null;
        return new RaycastTarget(bestVictim, bestT);
    }

    private static void spawnDebugRay(ServerLevel level, Vec3 origin, Vec3 dirNorm, double dist) {
        int steps = (int) Math.max(8, dist * 16);
        double step = dist / steps;
        for (int i = 0; i <= steps; i++) {
            Vec3 p = origin.add(dirNorm.scale(step * i));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static final class RaycastTarget {
        final ServerPlayer victim;
        final double t;

        RaycastTarget(ServerPlayer victim, double t) {
            this.victim = victim;
            this.t = t;
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
            cap.setDisguise(null);
            cap.setHitCount(0);
            syncData(player, true, null);
        });

        player.addTag("role_seeker");
        player.addTag("bhs_hide_health");

        player.setHealth(player.getMaxHealth());
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        Component titleText = Component.literal("你成为了抓捕者！")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        player.connection.send(new ClientboundSetTitleTextPacket(titleText));

        String subStr = isStart ? "去抓捕所有躲藏者！" : "你被抓住了，加入抓捕阵营！";
        Component subText = Component.literal(subStr).withStyle(ChatFormatting.GOLD);
        player.connection.send(new ClientboundSetSubtitleTextPacket(subText));

        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void makeHider(ServerPlayer player, List<BlockState> options) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setHitCount(0);
            cap.setDisguise(null);
            syncData(player, false, null);
        });

        player.addTag("bhs_hide_health");

        List<BlockState> myOptions = new ArrayList<>(options);
        Collections.shuffle(myOptions);
        myOptions = myOptions.subList(0, Math.min(myOptions.size(), 4));

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenSelectScreen(myOptions));
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

        player.removeTag("role_seeker");
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
        // ✅ 升级：发送全量数据，防止重置尺寸导致变小
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            PacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new S2CSyncGameData(
                            player.getId(),
                            seeker,
                            block,
                            cap.getModelWidth(), cap.getModelHeight(), // 物理尺寸
                            cap.getAABBX(), cap.getAABBY(), cap.getAABBZ() // OBB尺寸
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


    private static void broadcastHudUpdate(ServerLevel level,boolean isRunning) {
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
                new S2CUpdateHudPacket(true, ticksRemaining, list)
        );
    }
}
