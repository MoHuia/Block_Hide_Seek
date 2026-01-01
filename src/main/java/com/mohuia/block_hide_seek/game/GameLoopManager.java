package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.event.GameEndEvent;
import com.mohuia.block_hide_seek.event.GameStartEvent;
import com.mohuia.block_hide_seek.hitbox.ObbRaycast;
import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.network.PacketHandler;
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

    private static boolean isGameRunning = false;
    private static int ticksRemaining = 0;
    private static final int FAKE_IFRAMES_TICKS = 10;     // 10 tick = 0.5s
    private static final int FAKE_HURT_ANIM_TICKS = 10;
    private static final float FAKE_KNOCKBACK = 0.4F;

    /**
     * ✅ 供网络包判断用
     */
    public static boolean isGameRunning() {
        return isGameRunning;
    }

    // ==========================================
    //              游戏流程控制
    // ==========================================

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

        ServerGameConfig config = ServerGameConfig.get(level);

        if (players.size() < 2) {
            starter.sendSystemMessage(Component.literal("❌ 人数不足，至少需要 2 人！"));
            return;
        }
        if (config.seekerCount >= players.size()) {
            starter.sendSystemMessage(Component.literal("❌ 抓捕者人数必须小于总人数！"));
            return;
        }

        isGameRunning = true;
        ticksRemaining = config.gameDurationSeconds * 20;

        resetAllPlayers(level);
        Collections.shuffle(players);

        BlockWhitelistData whitelistData = BlockWhitelistData.get(level);
        List<BlockState> allowedBlocks = new ArrayList<>(whitelistData.getAllowedStates());
        if (allowedBlocks.isEmpty()) allowedBlocks.add(Blocks.CRAFTING_TABLE.defaultBlockState());

        for (int i = 0; i < config.seekerCount; i++) {
            makeSeeker(players.get(i), true);
        }

        for (int i = config.seekerCount; i < players.size(); i++) {
            makeHider(players.get(i), allowedBlocks);
        }

        MinecraftForge.EVENT_BUS.post(new GameStartEvent(level));
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

    public static void stopGame(ServerLevel level, WinnerType winner, Component reason) {
        if (!isGameRunning) return;
        isGameRunning = false;

        MinecraftForge.EVENT_BUS.post(new GameEndEvent(level, winner, reason));
        resetAllPlayers(level);
        broadcast(level, Component.literal("🛑 游戏结束！").append(reason).withStyle(ChatFormatting.GOLD));
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
    }

    private static void checkSeekerWinCondition(ServerLevel level) {
        long hiderCount = level.players().stream().filter(p -> {
            if (p.isSpectator()) return false;
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            return cap != null && !cap.isSeeker();
        }).count();

        if (hiderCount == 0) {
            stopGame(level, WinnerType.SEEKERS, Component.literal("⚔️ 所有躲藏者都被抓获！抓捕者胜利！"));
        }
    }

//    // ==========================================
//    //              玩家互动逻辑 (PVP)
//    // ==========================================
//
//    public static void onPlayerAttack(ServerPlayer attacker, ServerPlayer victim) {
//        if (!isGameRunning) return;
//
//        attacker.getCapability(GameDataProvider.CAP).ifPresent(atCap -> {
//            if (!atCap.isSeeker()) return;
//
//            victim.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
//                if (vicCap.isSeeker()) return;
//
//                boolean obbHit = isHitVictimObb(attacker, victim);
//                if (!obbHit) return;
//
//                // ✅ 无敌帧内不重复扣
//                if (isInIFrames(victim)) return;
//
//                // ✅ 原版攻击事件这条路径：也做同样的模拟（否则你会只扣次数但没表现）
//                simulateVanillaLikeHit(attacker, victim,);
//
//                handleHiderHit(attacker, victim, vicCap);
//            });
//        });
//    }
//
//    private static boolean isHitVictimObb(ServerPlayer attacker, ServerPlayer victim) {
//        Vec3 origin = attacker.getEyePosition();
//        Vec3 dir = attacker.getLookAngle().normalize();
//        double reach = getReach(attacker);
//
//        return ObbUtil.getPlayerObb(victim)
//                .map(obb -> ObbRaycast.hit(origin, dir, reach, obb))
//                .orElse(false);
//    }

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
     * ✅ 新增：抓捕者左键触发（不依赖点到实体）
     * - 服务端发射射线
     * - 忽略自己
     * - 用粒子画线 debug（可开关）
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
            System.out.println("服务端发现你点了一次左键");
            // 找最近的、命中 OBB 的躲藏者
            RaycastTarget target = raycastFindClosestHiderOBB(attacker, origin, dir, reach);

            if (target == null) return;

            // 命中才处理
            target.victim.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
                if (vicCap.isSeeker()) return;

                // ✅ 无敌帧内：不重复击退，也不扣次数
                if (isInIFrames(target.victim)) return;

                // ✅ 先模拟受击效果（击退+动画+无敌帧）
                simulateVanillaLikeHit(attacker, target.victim);

                // ✅ 再扣次数（这样无敌帧内不会瞬间耗完）
                handleHiderHit(attacker, target.victim, vicCap);
            });
        });
    }

    private static RaycastTarget raycastFindClosestHiderOBB(ServerPlayer attacker, Vec3 origin, Vec3 dir, double reach) {
        ServerLevel level = attacker.serverLevel();

        ServerPlayer bestVictim = null;
        double bestT = Double.POSITIVE_INFINITY;

        for (ServerPlayer p : level.players()) {
            if (p == attacker) continue;       // ✅ 不检测自己
            if (p.isSpectator()) continue;

            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            if (cap == null) continue;
            if (cap.isSeeker()) continue;      // 只抓躲藏者

            var obbOpt = ObbUtil.getPlayerObb(p);//这里是我的第二个位置
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

    /**
     * ✅ 粒子画线：沿射线每 step 刷一个粒子点
     */
    private static void spawnDebugRay(ServerLevel level, Vec3 origin, Vec3 dirNorm, double dist) {
        int steps = (int) Math.max(8, dist * 16); // 距离越远点越密
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

    /**
     * 命中后先检查：无敌帧内不允许重复扣次数
     */
    private static boolean isInIFrames(ServerPlayer victim) {
        // invulnerableTime：原版无敌帧计时
        // hurtTime：受击动画计时（通常 <= hurtDuration）
        return victim.invulnerableTime > 0 || victim.hurtTime > 0;
    }

    /**
     * 模拟一次“像被玩家近战打中”的效果（不扣血）
     */
    private static void simulateVanillaLikeHit(ServerPlayer attacker, ServerPlayer victim) {
        // ✅ 方向：victim 远离 attacker（以水平为主）
        Vec3 away = victim.position().subtract(attacker.position());

        // 只取水平分量，避免向上/向下看导致击退奇怪
        Vec3 horiz = new Vec3(away.x, 0.0, away.z);
        double len = horiz.length();

        // 兜底：如果正好重叠（len=0），用 attacker 朝向
        double xRatio, zRatio;
        if (len < 1e-6) {
            Vec3 look = attacker.getLookAngle();
            Vec3 lookHoriz = new Vec3(look.x, 0.0, look.z);
            double l2 = lookHoriz.length();
            if (l2 < 1e-6) {
                xRatio = 0.0;
                zRatio = 1.0;
            } else {
                xRatio = lookHoriz.x / l2;
                zRatio = lookHoriz.z / l2;
            }
        } else {
            xRatio = horiz.x / len;
            zRatio = horiz.z / len;
        }

        // 1) 击退
        victim.knockback(FAKE_KNOCKBACK, xRatio, zRatio);

        // 2) 无敌帧 + 受击动画
        victim.invulnerableTime = FAKE_IFRAMES_TICKS;
        victim.hurtTime = FAKE_HURT_ANIM_TICKS;
        victim.hurtDuration = FAKE_HURT_ANIM_TICKS;

        // 3) 客户端受击红光/抖动
        victim.level().broadcastEntityEvent(victim, (byte) 2);

        // 4) 音效（更像原版的两段反馈）
        // 受击音（在 victim 身上播放，附近人都能听见）
        victim.level().playSound(null,
                victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS,
                1.0F, 1.0F
        );

        // 击退/命中反馈音（在 attacker 身上播放）
        attacker.level().playSound(null,
                attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS,
                0.8F, 1.0F
        );

        // 5) 速度同步更及时
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
        Component titleText = Component.literal("👹 你成为了抓捕者！")
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

    private static void playHurtSound(ServerPlayer attacker, ServerPlayer victim) {
        // 在 victim 身上播放（所有附近玩家都听到）
        victim.level().playSound(
                null, // null = 广播给附近所有玩家
                victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.PLAYER_HURT,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
    }
}
