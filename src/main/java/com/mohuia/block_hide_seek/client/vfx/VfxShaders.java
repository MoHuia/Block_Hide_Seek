package com.mohuia.block_hide_seek.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
//block_hide_seek
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class VfxShaders {

    public static ShaderInstance OBJ_FOG;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            // 注册 Shader，注意第二个参数 callback
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("block_hide_seek", "obj_fog"),
                            DefaultVertexFormat.NEW_ENTITY),

                    // ⚠️【关键修正在这里】⚠️
                    // 这个回调会在 Shader 加载/重载成功时被调用
                    (shaderInstance) -> {
                        VfxShaders.OBJ_FOG = shaderInstance; // 必须更新静态引用！
                        System.out.println("✅ [DEBUG] Shader 'obj_fog' loaded/reloaded! Instance: " + shaderInstance);
                    }
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
