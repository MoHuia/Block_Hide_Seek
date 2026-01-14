package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
import com.mohuia.block_hide_seek.hitbox.ObbRaycast;
import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.List;
import java.util.Optional;

public class GameCombatManager {

    private static final int FAKE_IFRAMES_TICKS = 10;
    private static final int FAKE_HURT_ANIM_TICKS = 10;
    private static final float FAKE_KNOCKBACK = 0.4F;

    public static void performSeekerAttack(ServerPlayer attacker, boolean debugParticles) {
        if (!GameLoopManager.isGameRunning()) return;

        attacker.getCapability(GameDataProvider.CAP).ifPresent(atCap -> {
            if (!atCap.isSeeker()) return;

            ServerLevel level = attacker.serverLevel();
            Vec3 origin = attacker.getEyePosition();
            Vec3 dir = attacker.getLookAngle().normalize();
            double reach = getReach(attacker);

            if (debugParticles) spawnDebugRay(level, origin, dir, reach);

            RaycastResult result = raycastFindClosestTarget(attacker, origin, dir, reach);
            if (result == null) return;

            if (result.type == TargetType.PLAYER && result.player != null) {
                processPlayerHit(attacker, result.player);
            } else if (result.type == TargetType.DECOY && result.decoy != null) {
                processDecoyHit(attacker, result.decoy);
            }
        });
    }

    private static void processPlayerHit(ServerPlayer attacker, ServerPlayer victim) {
        victim.getCapability(GameDataProvider.CAP).ifPresent(vicCap -> {
            if (vicCap.isSeeker() || isInIFrames(victim)) return;

            simulateVanillaLikeHit(attacker, victim);

            vicCap.incrementHitCount();
            int currentHits = vicCap.getHitCount();
            int maxHits = ServerGameConfig.get(attacker.level()).hitsToConvert;

            attacker.displayClientMessage(Component.literal("🗡️ 击中目标！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.YELLOW), true);
            victim.displayClientMessage(Component.literal("🛡️ 你受到了攻击！ (" + currentHits + "/" + maxHits + ")").withStyle(ChatFormatting.RED), true);

            if (currentHits >= maxHits) {
                GameNetworkHelper.broadcast(attacker.serverLevel(), victim.getDisplayName().copy().append(" 被抓住了，变成了抓捕者！").withStyle(ChatFormatting.YELLOW));

                // 核心逻辑回调：转换职业并检查胜利
                GameRoleManager.makeSeeker(victim, false);
                GameLoopManager.checkSeekerWinCondition(attacker.serverLevel()); // 通知主循环检查
                GameNetworkHelper.updateHud(attacker.serverLevel(), true, GameLoopManager.getTicksRemaining());
            }
        });
    }

    // 供外部调用（例如弓箭击杀）
    public static void catchHiderImmediately(ServerPlayer seeker, ServerPlayer hider) {
        if (!GameLoopManager.isGameRunning()) return;
        hider.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker()) return;

            GameNetworkHelper.broadcast(hider.serverLevel(), Component.literal("🏹 ").append(seeker.getDisplayName()).append(" 射杀了 ").append(hider.getDisplayName()).withStyle(ChatFormatting.RED));

            GameRoleManager.makeSeeker(hider, false);
            GameLoopManager.checkSeekerWinCondition(hider.serverLevel());
            GameNetworkHelper.updateHud(hider.serverLevel(), true, GameLoopManager.getTicksRemaining());
        });
    }

    private static void processDecoyHit(ServerPlayer attacker, DecoyEntity decoy) {
        attacker.level().playSound(null, decoy.getX(), decoy.getY(), decoy.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 1.0f, 1.5f);
        attacker.displayClientMessage(Component.literal("💥 击碎了替身！").withStyle(ChatFormatting.GRAY), true);
        decoy.discard();
        ((ServerLevel)attacker.level()).sendParticles(ParticleTypes.CLOUD, decoy.getX(), decoy.getY() + 0.5, decoy.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
    }

    // --- 内部辅助方法 (私有) ---

    private static RaycastResult raycastFindClosestTarget(ServerPlayer attacker, Vec3 origin, Vec3 dir, double reach) {
        ServerLevel level = attacker.serverLevel();
        double bestDist = Double.POSITIVE_INFINITY;
        RaycastResult bestResult = null;

        // 扫描玩家
        for (ServerPlayer p : level.players()) {
            if (p == attacker || p.isSpectator()) continue;
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);
            if (cap == null || cap.isSeeker() || cap.isInvisible()) continue;

            Optional<VirtualOBB> obbOpt = ObbUtil.getPlayerObb(p);
            if (obbOpt.isEmpty()) continue;
            double t = ObbRaycast.hitDistance(origin, dir, reach, obbOpt.get());
            if (t >= 0.0 && t < bestDist) {
                bestDist = t;
                bestResult = new RaycastResult(p, t);
            }
        }

        // 扫描替身
        AABB searchBox = attacker.getBoundingBox().inflate(reach);
        for (DecoyEntity decoy : level.getEntitiesOfClass(DecoyEntity.class, searchBox)) {
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

    private static double getReach(ServerPlayer attacker) {
        double reach = 3.5;
        try {
            var attr = attacker.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (attr != null) reach = Math.max(reach, attr.getValue());
        } catch (Throwable ignored) {}
        return reach;
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
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.0F);
        victim.hurtMarked = true;
    }

    private static void spawnDebugRay(ServerLevel level, Vec3 origin, Vec3 dirNorm, double dist) {
        int steps = (int) Math.max(8, dist * 16);
        double step = dist / steps;
        for (int i = 0; i <= steps; i++) {
            Vec3 p = origin.add(dirNorm.scale(step * i));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private enum TargetType { PLAYER, DECOY }
    private static final class RaycastResult {
        final TargetType type;
        final ServerPlayer player;
        final DecoyEntity decoy;
        final double dist;
        RaycastResult(ServerPlayer player, double dist) { this.type = TargetType.PLAYER; this.player = player; this.decoy = null; this.dist = dist; }
        RaycastResult(DecoyEntity decoy, double dist) { this.type = TargetType.DECOY; this.player = null; this.decoy = decoy; this.dist = dist; }
    }
}
