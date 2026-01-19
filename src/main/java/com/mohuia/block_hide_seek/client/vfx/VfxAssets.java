package com.mohuia.block_hide_seek.client.vfx;



import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class VfxAssets {

    public static ObjMesh ARROW_TRAIL;
    public static void loadClientAssets() {
        try {
            ARROW_TRAIL = ObjMesh.load(
                    Minecraft.getInstance().getResourceManager(),
                    ResourceLocation.fromNamespaceAndPath(
                            "block_hide_seek",
                            "models/vfx/carrier.obj"
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load arrow_trail.obj", e);
        }
    }
}
