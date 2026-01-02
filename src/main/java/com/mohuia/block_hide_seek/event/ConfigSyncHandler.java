package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncConfig;
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 专门处理配置同步的事件
 */
@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID)
public class ConfigSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 1. 获取服务端当前配置
            ServerGameConfig config = ServerGameConfig.get(player.level());

            // 2. 发送独立的数据包给该玩家
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncConfig(
                            config.gameDurationSeconds,
                            config.hitsToConvert,
                            config.seekerCount
                    )
            );
        }
    }
}
