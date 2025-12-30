package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// 只在客户端存在
@OnlyIn(Dist.CLIENT)
public class ClientGameHandler {

    /**
     * 处理从服务端发来的同步数据包
     * @param entityId 变身的玩家ID
     * @param isSeeker 是否是抓捕者
     * @param disguise 变身的方块
     */
    public static void handleSync(int entityId, boolean isSeeker, BlockState disguise) {
        // 获取客户端世界
        if (Minecraft.getInstance().level == null) return;

        // 根据 ID 找到那个玩家
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);

        if (entity instanceof Player player) {
            // 更新 Capability 数据，这样渲染代码就能读到最新的变身状态了
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                cap.setSeeker(isSeeker);
                cap.setDisguise(disguise);
                // 强制刷新玩家尺寸，防止碰撞箱不更新
                player.refreshDimensions();
            });
        }
    }
}
