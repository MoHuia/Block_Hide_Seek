package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.client.render.DisguiseRenderHelper; // 导入新组件
import com.mohuia.block_hide_seek.packet.C2S.C2SAttackRaycast;
import com.mohuia.block_hide_seek.packet.C2S.C2SRequestConfig;
import com.mohuia.block_hide_seek.packet.C2S.C2SSetYawLock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.KeyInit;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static Float lockedBodyYaw = null;
    private static boolean isAlignActive = false;
    private static float alignYawStart = 0.0f;
    private static float alignYawTarget = 0.0f;
    private static float alignYawT = 1.0f;
    private static final float ALIGN_YAW_DURATION_SEC = 0.28f;
    private static boolean isRotationLocked = false;
    private static long lastSendMs = 0;

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isInvisible()) {
                event.setCanceled(true);
                return;
            }
            if (cap.isSeeker()) return;

            BlockState state = cap.getDisguise();
            if (state != null && state.getRenderShape() != RenderShape.INVISIBLE) {
                event.setCanceled(true);
                // 调用代理渲染方法
                renderDisguise(event, state);
            }
        });
    }

    private static void renderDisguise(RenderPlayerEvent.Pre event, BlockState state) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Player player = event.getEntity();
        float renderYaw;

        // 1. 角度计算逻辑 (保持原样)
        if (lockedBodyYaw != null && player == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            float partialTick = event.getPartialTick();
            renderYaw = player.getViewYRot(partialTick);
        }
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        // 2. 准备参数并调用 Helper
        BlockPos lightPos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        int light = LevelRenderer.getLightColor(player.level(), lightPos);

        // ✅ 核心渲染现在调用组件
        DisguiseRenderHelper.renderDisguiseBlock(
                state,
                poseStack,
                event.getMultiBufferSource(),
                player.level(),
                lightPos,
                light
        );

        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (!cap.isSeeker() && cap.getDisguise() != null) {
                    event.setResult(Event.Result.DENY);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.tickCount % 1200 == 0) {
            DisguiseRenderHelper.clearCache(); // 清理组件缓存
        }

        float expectedHeight = player.getDimensions(player.getPose()).height;
        float actualHeight = player.getBbHeight();
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }

        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new C2SRequestConfig());
        }

        handleControlLogic(player);
    }

    private static void handleControlLogic(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker() || cap.getDisguise() == null) {
                resetStates();
                return;
            }
            boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;
            boolean isJumping = player.input.jumping;
            boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.005;

            if (hasInput || isJumping || (!player.onGround() && isMoving)) {
                if (isAlignActive || isRotationLocked) {
                    resetStates();
                    player.displayClientMessage(Component.literal("§c已解除锁定"), true);
                    PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(false, 0.0f));
                }
            } else {
                if (KeyInit.TOGGLE_ALIGN.consumeClick()) {
                    if (!isAlignActive) {
                        isAlignActive = true;
                        alignYawStart = player.getYRot();
                        float viewYaw = player.getViewYRot(1.0f);
                        alignYawTarget = (float) (Math.round(viewYaw / 90.0f) * 90.0f);
                        alignYawT = 0.0f;
                    }
                }
                if (KeyInit.LOCK_ROTATION.consumeClick()) {
                    isRotationLocked = !isRotationLocked;
                    if (isRotationLocked) {
                        lockedBodyYaw = player.getViewYRot(1.0f);
                        player.displayClientMessage(Component.literal("§a方向锁定: 开启 (自由视角)"), true);
                        PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(true, lockedBodyYaw));
                    } else {
                        lockedBodyYaw = null;
                        player.displayClientMessage(Component.literal("§c方向锁定: 关闭"), true);
                        PacketHandler.INSTANCE.sendToServer(new C2SSetYawLock(false, 0.0f));
                    }
                }
                if (isAlignActive) performPositionSnap(player);
                if (!isRotationLocked) lockedBodyYaw = null;
            }
        });
    }

    private static void resetStates() {
        isAlignActive = false;
        isRotationLocked = false;
        lockedBodyYaw = null;
    }

    private static void performPositionSnap(LocalPlayer player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();

        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;

        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;

        player.setPos(newX, player.getY(), newZ);
    }

    @Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.OPEN_CONFIG);
            event.register(KeyInit.TOGGLE_ALIGN);
            event.register(KeyInit.LOCK_ROTATION);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 50) return;
        lastSendMs = now;
        PacketHandler.INSTANCE.sendToServer(new C2SAttackRaycast(false));
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isAlignActive) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        float dtSec = mc.getFrameTime() / 20.0f;
        performYawSnapPerFrame(player, dtSec);
    }

    private static void performYawSnapPerFrame(LocalPlayer player, float dtSec) {
        if (alignYawT >= 1.0f) return;
        float step = dtSec / ALIGN_YAW_DURATION_SEC;
        alignYawT = Math.min(1.0f, alignYawT + step);
        float eased = easeInOutCubic(alignYawT);
        float delta = Mth.wrapDegrees(alignYawTarget - alignYawStart);
        float yawNow = alignYawStart + delta * eased;
        player.setYRot(yawNow);
        player.setYHeadRot(yawNow);
        player.setYBodyRot(yawNow);
        player.yRotO = yawNow;
        player.yBodyRotO = yawNow;
        if (alignYawT >= 1.0f) {
            isAlignActive = false;
        }
    }

    private static float easeInOutCubic(float t) {
        return (t < 0.5f) ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0f) / 2.0f;
    }
}
