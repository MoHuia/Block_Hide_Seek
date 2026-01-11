package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.render.DecoyRenderer;
import com.mohuia.block_hide_seek.entity.EntityInit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// ⚠️ 注意：必须是 Bus.MOD，且必须是 Dist.CLIENT
@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 告诉游戏：当需要渲染 "DECOY_ENTITY" 时，请使用 "DecoyRenderer"
        event.registerEntityRenderer(EntityInit.DECOY_ENTITY.get(), DecoyRenderer::new);
    }
}
