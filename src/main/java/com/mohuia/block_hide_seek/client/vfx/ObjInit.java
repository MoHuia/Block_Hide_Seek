package com.mohuia.block_hide_seek.client.vfx;

import com.mohuia.block_hide_seek.BlockHideSeek;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//注册obj到总线
@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID, bus=Mod.EventBusSubscriber.Bus.MOD, value= Dist.CLIENT)
public final class ObjInit {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(VfxAssets::loadClientAssets);
    }
}