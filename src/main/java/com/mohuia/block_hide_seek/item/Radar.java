package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.C2S.C2SRadarScanRequest;
import com.mohuia.block_hide_seek.packet.S2C.S2CRevealDisguise;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
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
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class Radar extends Item implements GeoItem{
    //静态变量，用于配置
    public static int SEARCH_RANGE = 50;
    public static int COOLDOWN_TICKS = 60;
    //动画模型
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public Radar(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new RadarRenderer();
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 这里等会加“拿起/使用”动画逻辑
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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

                PacketHandler.sendToPlayer(
                        new S2CRevealDisguise(nearestTarget.getUUID(), 3000), // 3000ms = 3秒
                        (ServerPlayer) player
                );

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
