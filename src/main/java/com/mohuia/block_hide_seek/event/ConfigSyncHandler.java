package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncConfig;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncMapTags;
import com.mohuia.block_hide_seek.world.MapExtraIntegration; // ✅ 引入集成类
import com.mohuia.block_hide_seek.world.ServerGameConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID)
public class ConfigSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();

            // 1. 获取服务端当前配置
            ServerGameConfig config = ServerGameConfig.get(level);

            // 2. ✅ 获取 MapExtra 的地图数据
            // 使用我们新建的集成类，而不是原来的 PosSavedData
            MapExtraIntegration mapData = MapExtraIntegration.get(level);

            // 3. 发送当前设置
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncConfig(
                            config.gameDurationSeconds,
                            config.hitsToConvert,
                            config.seekerCount,
                            config.gameMapTag,
                            config.lobbyTag,
                            config.radarRange,
                            config.radarCooldown,
                            config.vanishMana,
                            config.decoyCount,
                            config.decoyCooldown
                    )
            );

            // 4. 发送所有可用的标签列表
            // mapData.getAllTags() 现在会返回 "mapextra_positions" 文件里的真实标签
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncMapTags(mapData.getAllTags())
            );
        }
    }
}
