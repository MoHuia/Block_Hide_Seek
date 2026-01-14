package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.client.render.ArrowRenderer;
import com.mohuia.block_hide_seek.entity.EntityInit;
import net.minecraft.client.renderer.entity.NoopRenderer;
// 注意：这里没有任何 TippedArrowRenderer 的导入了
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ✅ 使用你刚才写的 SeekerArrowRenderer
        event.registerEntityRenderer(EntityInit.SEEKER_ARROW.get(), ArrowRenderer::new);

        // ✅ 诱饵使用空渲染
        event.registerEntityRenderer(EntityInit.DECOY_ENTITY.get(), NoopRenderer::new);
    }
}
