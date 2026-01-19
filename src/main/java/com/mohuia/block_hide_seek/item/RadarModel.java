package com.mohuia.block_hide_seek.item;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RadarModel extends GeoModel<Radar> {

    private static final String MODID = "block_hide_seek";

    @Override
    public ResourceLocation getModelResource(Radar animatable) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "geo/radar.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(Radar animatable) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "textures/item/radar.png");
    }

    @Override
    public ResourceLocation getAnimationResource(Radar animatable) {
        return ResourceLocation.fromNamespaceAndPath(MODID, "animations/radar.animation.json");
    }
}