package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRadarScanRequest;
import net.minecraft.network.chat.Component;
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

        // ✅ 1. 客户端逻辑：读取缓存，打包发给服务端
//        if (level.isClientSide) {
//            // 获取单例中的面数
//            RADAR_RANGE.rebuild(player);
//            int faceCount = GeometryCache.getInstance().getQuadCount();
//            // 发送包到服务端 (让服务端去广播给所有人)
//            ModMessage.sendToServer(new PacketShareQuadCount(faceCount));
//        }
        // ✅ 1. 客户端逻辑：读取缓存，打包发给服务端
        if (level.isClientSide) {
            PacketHandler.sendToServer(new C2SRadarScanRequest());
        }

        // ✅ 2. 服务端逻辑：原有的搜人功能
        if (!level.isClientSide){
            AABB searchArea = player.getBoundingBox().inflate((double)SEARCH_RANGE);
            List<Player> players = level.getEntitiesOfClass(Player.class, searchArea, p -> p != player && !p.isSpectator());

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
                double actualDistance = Math.sqrt(minDistance);
                // 播放声音（自己听到）
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);

                // 给被发现的人发消息（可选）
                nearestTarget.displayClientMessage(
                        Component.literal("👁你已被抓捕者发现！").withStyle(style -> style.withColor(0xFF0000).withBold(true)),
                        true
                );

                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.2F);

                player.displayClientMessage(Component.literal("§c❌范围内没有其他玩家"), false);
                player.getCooldowns().addCooldown(this, 20);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(UsedHand));
    }
}
