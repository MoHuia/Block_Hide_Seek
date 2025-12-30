package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientHooks {

    public static void openGui(List<BlockState> options) {
        Minecraft.getInstance().setScreen(new SelectScreen(options));
    }

    public static void openConfigGui(List<BlockState> currentList) {
        Minecraft.getInstance().setScreen(new ConfigScreen(currentList));
    }

    public static void handleSync(int entityId, boolean isSeeker, BlockState disguise) {
        if (Minecraft.getInstance().level == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(entityId);

        if (entity instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                cap.setSeeker(isSeeker);
                cap.setDisguise(disguise);
                player.refreshDimensions();
            });
        }
    }
}
