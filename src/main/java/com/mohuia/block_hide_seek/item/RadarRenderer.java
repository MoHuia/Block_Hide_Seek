package com.mohuia.block_hide_seek.item;

import software.bernie.geckolib.renderer.GeoItemRenderer;

public class RadarRenderer extends GeoItemRenderer<Radar> {
    public RadarRenderer() {
        super(new RadarModel());
    }
}