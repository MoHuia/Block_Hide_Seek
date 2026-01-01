package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.game.GameLoopManager;
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

    @SubscribeEvent
    public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            GameLoopManager.onPlayerAttack(attacker, victim);
        }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels().forEach(level -> {
                if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    GameLoopManager.tick(level);
                }
            });
        }
    }

    // ==========================================
    //           核心：物理与逻辑修正
    // ==========================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在服务端处理 (Phase.END 确保在原版移动逻辑之后执行)
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;

        // --- 1. 强制刷新碰撞箱 (解决 1x1 洞口卡住/回弹问题) ---
        // 获取 "当前缓存的物理高度"
        float actualHeight = player.getBbHeight();
        // 获取 "理论上应该有的高度" (通过 Mixin 计算的)
        float expectedHeight = player.getDimensions(player.getPose()).height;

        // 如果误差超过 1cm，说明碰撞箱过时了，立刻强制刷新！
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }
        // ----------------------------------------------------

        // --- 2. 自动对齐逻辑 ---
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null && player.onGround()) {
                boolean hasInput = Math.abs(player.xxa) > 0.01 || Math.abs(player.zza) > 0.01;
                boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.005;

                if (!hasInput && !isMoving) {
                    alignToGrid(player);
                }
            }
        });
    }

    private static void alignToGrid(Player player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;

        double currentX = player.getX();
        double currentZ = player.getZ();

        double lerpFactor = 0.2;

        double newX = currentX + (targetX - currentX) * lerpFactor;
        double newZ = currentZ + (targetZ - currentZ) * lerpFactor;

        if (Math.abs(targetX - currentX) < 0.01) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.01) newZ = targetZ;

        float currentYaw = player.getYRot();
        float targetYaw = Math.round(currentYaw / 90.0f) * 90.0f;

        float newYaw = currentYaw + (targetYaw - currentYaw) * 0.2f;
        if (Math.abs(targetYaw - currentYaw) < 1.0f) newYaw = targetYaw;

        player.setPos(newX, player.getY(), newZ);
        player.setYRot(newYaw);
        player.setYBodyRot(newYaw);
        player.setYHeadRot(newYaw);
    }
}
