package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.client.config.ConfigScreen;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 客户端钩子类 (Client Hooks)
 * 作用：专门处理那些 "只能在客户端运行" 的逻辑。
 * 包括打开 GUI 和处理客户端数据同步。
 */
@OnlyIn(Dist.CLIENT)
public class ClientHooks {

    /**
     * 打开方块选择界面 (SelectScreen)
     */
    public static void openGui(List<BlockState> options) {
        Minecraft.getInstance().setScreen(new SelectScreen(options));
    }

    /**
     * 打开白名单配置界面 (ConfigScreen)
     */
    public static void openConfigGui(List<BlockState> currentList) {
        Minecraft.getInstance().setScreen(new ConfigScreen(currentList));
    }

    /**
     * 处理数据同步 (Handle Sync)
     * 当收到 S2CSyncGameData 包时调用，更新客户端实体的 Capability。
     */
    public static void handleSync(int entityId, boolean isSeeker, BlockState disguise) {
        if (Minecraft.getInstance().level == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(entityId);

        if (entity instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                cap.setSeeker(isSeeker);
                cap.setDisguise(disguise);
                // 强制刷新玩家尺寸，确保渲染和碰撞箱（在客户端预测中）正确
                player.refreshDimensions();
            });
        }
    }
}
