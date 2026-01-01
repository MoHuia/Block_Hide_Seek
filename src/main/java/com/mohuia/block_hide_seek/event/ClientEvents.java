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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

    private static Float lockedBodyYaw = null;

    // ==================================================
    //                  渲染逻辑 (核心修改)
    // ==================================================

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (cap.isSeeker()) return;

            BlockState state = cap.getDisguise();
            if (state != null && state.getRenderShape() != RenderShape.INVISIBLE) {
                event.setCanceled(true);
                renderDisguise(event, state);
            }
        });
    }

    private static void renderDisguise(RenderPlayerEvent.Pre event, BlockState state) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // 1. 旋转锁定逻辑
        float renderYaw;
        if (lockedBodyYaw != null && event.getEntity() == Minecraft.getInstance().player) {
            renderYaw = lockedBodyYaw;
        } else {
            renderYaw = event.getEntity().yBodyRot;
        }
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-renderYaw));

        // 2. 分流渲染
        if (state.getRenderShape() == RenderShape.MODEL) {
            // 普通方块 (门、石头、花)
            renderStandardBlock(event, poseStack, state);
        } else {
            // 复杂方块 (实体、Blockbench模型、旗帜)
            renderComplexBlockAsItem(event, poseStack, state);
        }

        poseStack.popPose();
    }

    // 渲染普通方块的方法 (保持原样，只是提取出来了)
    // 【修改 1】修复门、高花只显示一半的问题
    private static void renderStandardBlock(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        poseStack.pushPose();

        // 移到脚底中心 (方块模型原点是 0,0,0角落，所以要 -0.5)
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var buffer = event.getMultiBufferSource().getBuffer(ItemBlockRenderTypes.getRenderType(state, false));

        // 1. 渲染当前方块 (比如门的下半截)
        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(), buffer, state, blockRenderer.getBlockModel(state),
                1.0F, 1.0F, 1.0F, event.getPackedLight(), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
        );

        // 2. 检测是否是双层方块 (门、高草丛、向日葵等)
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            var half = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF);

            // 如果当前是“下半截”，我们就顺便把“上半截”也画了
            if (half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                BlockState upperState = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);

                poseStack.translate(0.0D, 1.0D, 0.0D); // 向上移动一格
                blockRenderer.getModelRenderer().renderModel(
                        poseStack.last(), buffer, upperState, blockRenderer.getBlockModel(upperState),
                        1.0F, 1.0F, 1.0F, event.getPackedLight(), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null
                );
            }
        }

        poseStack.popPose();
    }

    // 【新增】渲染复杂方块的方法 (Blockbench/BE)
    // 【修改 2】修复方块陷在地里的问题
    private static void renderComplexBlockAsItem(RenderPlayerEvent.Pre event, PoseStack poseStack, BlockState state) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) return;

        poseStack.pushPose();

        // 关键修复：
        // 物品渲染的默认原点通常是物品的【中心点】。
        // 而玩家渲染的原点是【脚底】。
        // 如果不移动，物品的一半会在脚底以上，一半在土里。
        // 所以我们向上移动 0.5 (即半个方块高度)，让它的中心点位于腰部，底部正好贴地。
        poseStack.translate(0.0D, 0.5D, 0.0D);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.NONE, // 保持原始比例 (1:1) 和朝向
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                poseStack,
                event.getMultiBufferSource(),
                event.getEntity().level(),
                0
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
    //           逻辑处理 (保持不变)
    // ==================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        float expectedHeight = player.getDimensions(player.getPose()).height;
        float actualHeight = player.getBbHeight();
        if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
            player.refreshDimensions();
        }

        while (KeyInit.OPEN_CONFIG.consumeClick()) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2SRequestConfig());
        }

        handleAutoAlign(player);
    }

    private static void handleAutoAlign(LocalPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {
                boolean hasInput = Math.abs(player.input.forwardImpulse) > 0 || Math.abs(player.input.leftImpulse) > 0;
                if (hasInput || !player.onGround()) {
                    lockedBodyYaw = null;
                } else {
                    performSnap(player);
                }
            } else {
                lockedBodyYaw = null;
            }
        });
    }

    private static void performSnap(LocalPlayer player) {
        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;
        double currentX = player.getX();
        double currentZ = player.getZ();

        double newX = currentX + (targetX - currentX) * 0.2;
        double newZ = currentZ + (targetZ - currentZ) * 0.2;

        if (Math.abs(targetX - currentX) < 0.005) newX = targetX;
        if (Math.abs(targetZ - currentZ) < 0.005) newZ = targetZ;

        player.setPos(newX, player.getY(), newZ);

        if (lockedBodyYaw == null) {
            float currentBodyYaw = player.yBodyRot;
            lockedBodyYaw = (float) (Math.round(currentBodyYaw / 90.0f) * 90.0f);
        }
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
