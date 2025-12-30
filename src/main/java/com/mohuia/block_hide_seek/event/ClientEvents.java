package com.mohuia.block_hide_seek.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.client.KeyInit;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT)
public class ClientEvents {

    // 【核心】新增一个静态变量，用来存储锁定的角度
    // 如果为 null，说明玩家正在移动，方块自由旋转
    // 如果有值，说明玩家静止，方块渲染强制锁定在这个角度
    private static Float lockedBodyYaw = null;

    // ==================================================
    //                  渲染逻辑 (方块渲染)
    // ==================================================

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker()) return;

            BlockState state = cap.getDisguise();
            if (state != null && state.getRenderShape() == RenderShape.MODEL) {
                event.setCanceled(true);
                renderDisguise(event, state);
            }
        });
    }

    private static void renderDisguise(RenderPlayerEvent.Pre event, BlockState state) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        poseStack.translate(0.0D, 0.0D, 0.0D);

        // --- 旋转逻辑的核心修改 ---
        float renderYaw;

        // 1. 如果有锁定值，且玩家确实没在动，就强制使用锁定值
        // (判断 event.getEntity() 是否是当前客户端玩家，防止渲染其他人时出问题)
        if (lockedBodyYaw != null && event.getEntity() == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            // 2. 否则（移动中 或 其他玩家），使用原本的身体朝向
            renderYaw = event.getEntity().yBodyRot;
        }

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));
        // -------------------------

        poseStack.translate(-0.5D, 0.0D, -0.5D);

        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var model = blockRenderer.getBlockModel(state);
        var buffer = event.getMultiBufferSource().getBuffer(ItemBlockRenderTypes.getRenderType(state, false));

        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(), buffer, state, model, 1.0F, 1.0F, 1.0F,
                event.getPackedLight(), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
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

    // ==================================================
    //           逻辑处理：计算锁定 & 位置吸附
    // ==================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SRequestConfig());
        }

        handleAutoAlign(player);
    }

    private static void handleAutoAlign(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            // 条件：躲藏者 + 已变身
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 检测 WASD 输入
                boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;

                if (hasInput || !player.onGround()) {
                    // 状态A：正在移动 或 在空中
                    // 解除锁定，让方块随身体自由转动
                    lockedBodyYaw = null;
                } else {
                    // 状态B：静止且在地面 -> 执行吸附逻辑
                    performSnap(player);
                }
            } else {
                // 如果不是躲藏者，确保变量被重置
                lockedBodyYaw = null;
            }
        });
    }

    private static void performSnap(LocalPlayer player) {
        // --- 1. 位置吸附 (平滑) ---
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();

        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;

        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;

        // 应用位置
        player.setPos(newX, player.getY(), newZ);

        // --- 2. 角度锁定 (Visual Lock) ---

        // 如果还没有锁定 (刚停下来)，立刻计算最近的 90 度并锁死
        if (lockedBodyYaw == null) {
            float currentBodyYaw = player.yBodyRot;
            lockedBodyYaw = (float) (Math.round(currentBodyYaw / 90.0f) * 90.0f);
        }

        // 强行把玩家的数据层 BodyRot 也设置为锁定值
        // 虽然原版 Minecraft 可能会试图因为转头而改变它，
        // 但我们在 renderDisguise 里有双重保险，直接读 lockedBodyYaw，无视数据层的波动
        player.setYBodyRot(lockedBodyYaw);
    }

    @Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.OPEN_CONFIG);
        }
    }
}
