package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID)
public class CommonEvents {

    @SubscribeEvent
    public static void onAttachCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(
                    ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "game_data"),
                    new GameDataProvider()
            );
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer observer) {
            target.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> observer),
                        new PacketHandler.S2CSyncGameData(target.getId(), cap.isSeeker(), cap.getDisguise()));
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(GameDataProvider.CAP).ifPresent(oldCap -> {
            event.getEntity().getCapability(GameDataProvider.CAP).ifPresent(newCap -> {
                newCap.copyFrom(oldCap);
            });
        });
    }

    // ==========================================
    //           新增：自动对齐逻辑
    // ==========================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在服务端处理位移 (防止客户端预测不一致导致的鬼畜抖动)
        // Phase.END 确保在原版移动逻辑之后执行修正
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;

        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 条件：不是抓捕者 + 已变身 + 脚踩地面 (空中不对齐)
            if (!cap.isSeeker() && cap.getDisguise() != null && player.onGround()) {

                // xxa 是左右移动输入, zza 是前后移动输入
                // 如果绝对值 > 0.01 说明玩家按下了键盘
                boolean hasInput = Math.abs(player.xxa) > 0.01 || Math.abs(player.zza) > 0.01;

                // 检查实际运动速度 (平方和)
                boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.005;

                // 只有当玩家【没有按键】且【基本停下来】时，才开始吸附
                if (!hasInput && !isMoving) {
                    alignToGrid(player);
                }
            }
        });
    }

    /**
     * 将玩家平滑吸附到方块中心，并对齐 90 度旋转
     */
    private static void alignToGrid(Player player) {
        // 1. 计算目标位置 (方块中心)
        // Math.floor(x) + 0.5 永远是格子的正中心
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;

        double currentX = player.getX();
        double currentZ = player.getZ();

        // 平滑系数 (0.0 ~ 1.0)，越大吸得越快。0.2 比较自然
        double lerpFactor = 0.2;

        double newX = currentX + (targetX - currentX) * lerpFactor;
        double newZ = currentZ + (targetZ - currentZ) * lerpFactor;

        // 如果距离非常近了，直接锁定，避免无限微积分运算
        if (Math.abs(targetX - currentX) < 0.01) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.01) newZ = targetZ;

        // 2. 计算目标角度 (最近的 90 度倍数)
        float currentYaw = player.getYRot();
        // 这里的逻辑是：除以90 -> 四舍五入 -> 乘回90
        float targetYaw = Math.round(currentYaw / 90.0f) * 90.0f;

        // 角度平滑旋转
        float newYaw = currentYaw + (targetYaw - currentYaw) * 0.2f;
        if (Math.abs(targetYaw - currentYaw) < 1.0f) newYaw = targetYaw;

        // 3. 应用位置和旋转
        // 注意：必须同时设置 YBodyRot (身体朝向)，否则方块渲染可能会歪
        player.setPos(newX, player.getY(), newZ);
        player.setYRot(newYaw);
        player.setYBodyRot(newYaw);
        player.setYHeadRot(newYaw);
    }
}
