package com.mohuia.block_hide_seek.world;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class DisguiseManager {

    /**
     * 核心变身方法：处理所有来自客户端或指令的变身请求
     *
     * @param player     变身的玩家
     * @param blockState 目标方块
     * @param modelW     模型碰撞宽度
     * @param modelH     模型碰撞高度
     * @param obbX       OBB 真实宽度 (可选，若没有则传 modelW)
     * @param obbY       OBB 真实高度 (可选，若没有则传 modelH)
     * @param obbZ       OBB 真实深度 (可选，若没有则传 modelW)
     */
    public static void setDisguise(ServerPlayer player, BlockState blockState, float modelW, float modelH, float obbX, float obbY, float obbZ) {
        if (player == null || blockState == null) return;

        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 1. 设置身份状态
            cap.setSeeker(false);

            // 2. 设置伪装方块
            cap.setDisguise(blockState);

            // 3. 【核心】设置模型尺寸 (Hitbox 和 OBB)
            cap.setModelSize(modelW, modelH);
            cap.setAABBSize(obbX, obbY, obbZ);

            // 4. 同步数据给所有人 (Tracked Entity + Self)
            // 这样其他玩家能看到伪装，且自己的客户端能更新 Hitbox
            PacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new S2CSyncGameData(
                            player.getId(),
                            false, // isSeeker
                            blockState,
                            modelW, modelH,
                            obbX, obbY, obbZ
                    )
            );

            // 5. 刷新服务端碰撞箱 (重要！)
            player.refreshDimensions();

            // 6. 播放变身音效
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 7. 发送系统消息 (可选，仅给变身者)
            // player.sendSystemMessage(Component.literal("✨ 变身成功: " + blockState.getBlock().getName().getString()));
        });
    }

    /**
     * 清除伪装，变回普通人
     */
    public static void clearDisguise(ServerPlayer player) {
        if (player == null) return;

        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 1. 清除伪装数据
            cap.setDisguise(null);
            // 这里看需求：是变成 Seeker 还是变成无伪装的 Hider？
            // 默认保持原有阵营，或者重置为 Hider
            // cap.setSeeker(false);

            // 2. 恢复原版玩家尺寸 (0.6 x 1.8)
            // OBB 尺寸也跟随恢复，否则如果 OBB 很大，debug 看起来会很怪
            float defW = 0.6f;
            float defH = 1.8f;
            cap.setModelSize(defW, defH);
            cap.setAABBSize(defW, defH, defW);

            // 3. 同步
            PacketHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new S2CSyncGameData(
                            player.getId(),
                            cap.isSeeker(), // 保持当前阵营
                            null,           // null 代表清除伪装
                            defW, defH,
                            defW, defH, defW
                    )
            );

            // 4. 刷新
            player.refreshDimensions();

            // 5. 音效
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0f, 1.0f);

            player.sendSystemMessage(Component.literal("✨ 已解除伪装，恢复原样！"));
        });
    }
}
