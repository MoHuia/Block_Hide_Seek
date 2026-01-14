package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.client.hud.ClientGameCache;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import com.mohuia.block_hide_seek.packet.S2C.S2CUpdateHudPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class GameNetworkHelper {

    public static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(msg);
        }
    }

    public static void syncPlayerData(ServerPlayer player, boolean seeker, net.minecraft.world.level.block.state.BlockState block) {
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

    public static void updateHud(ServerLevel level, boolean isRunning, int ticksRemaining) {
        List<ClientGameCache.PlayerInfo> list = new ArrayList<>();

        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;

            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                ItemStack disguise = ItemStack.EMPTY;
                if (cap.getDisguise() != null) {
                    disguise = new ItemStack(cap.getDisguise().getBlock());
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
}
