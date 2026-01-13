package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRadarScanRequest;
import com.mohuia.block_hide_seek.packet.S2C.S2CRevealDisguise;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.List;

public class Radar extends Item {
    public static int SEARCH_RANGE = 50;
    public static int COOLDOWN_TICKS = 60;

    public Radar(Properties properties){
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand UsedHand){

        // 1. 客户端逻辑：请求播放视觉特效
        if (level.isClientSide) {
            PacketHandler.sendToServer(new C2SRadarScanRequest());
        }

        // 2. 服务端逻辑：计算最近目标 (判定逻辑)
        if (!level.isClientSide){
            ServerGameConfig config = ServerGameConfig.get(level);
            int actualRange = (config.radarRange > 0) ? config.radarRange : SEARCH_RANGE;
            int actualCooldown = (config.radarCooldown >= 0) ? config.radarCooldown : COOLDOWN_TICKS;

            AABB searchArea = player.getBoundingBox().inflate((double)actualRange);

            // ✅ 修复核心：增加过滤器，只筛选“躲藏者”
            List<Player> players = level.getEntitiesOfClass(Player.class, searchArea, p -> {
                // 1. 排除自己和旁观者
                if (p == player || p.isSpectator()) return false;

                // 2. 核心：检查 Capability
                // 如果是抓捕者 (isSeeker == true)，则返回 false (排除)
                // 只有躲藏者 (!isSeeker)，才返回 true (保留)
                return p.getCapability(GameDataProvider.CAP)
                        .map(cap -> !cap.isSeeker())
                        .orElse(false);
            });

            Player nearestTarget = null;
            double minDistance = Double.MAX_VALUE;

            for (Player target : players){
                double distance = player.distanceToSqr(target);
                if(distance < minDistance){
                    minDistance = distance;
                    nearestTarget = target;
                }
            }

            if (nearestTarget != null){
                // 找到目标 (此时一定是躲藏者)
                double actualDistance = Math.sqrt(minDistance);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);

                PacketHandler.sendToPlayer(
                        new S2CRevealDisguise(nearestTarget.getUUID(), 3000),
                        (ServerPlayer) player
                );
                nearestTarget.displayClientMessage(
                        Component.literal("👁你已被抓捕者发现！").withStyle(style -> style.withColor(0xFF0000).withBold(true)),
                        true
                );

                player.getCooldowns().addCooldown(this, actualCooldown);
            } else {
                // 没找到目标
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.2F);

                player.displayClientMessage(Component.literal("§c❌ 范围内没有躲藏者"), false);
                player.getCooldowns().addCooldown(this, actualCooldown);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(UsedHand));
    }
}
