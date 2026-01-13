package com.mohuia.block_hide_seek.client.render;

import com.mohuia.block_hide_seek.entity.ArrowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ArrowRenderer extends net.minecraft.client.renderer.entity.ArrowRenderer<ArrowEntity> {
    // 使用原版普通箭矢的纹理
    public static final ResourceLocation NORMAL_ARROW_LOCATION = ResourceLocation.parse("textures/entity/projectiles/arrow.png");
    public ArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ArrowEntity entity) {
        return NORMAL_ARROW_LOCATION;
    }
}
