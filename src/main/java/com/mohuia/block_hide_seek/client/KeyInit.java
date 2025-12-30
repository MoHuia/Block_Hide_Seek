package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyInit {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.block_hide_seek.config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, // 默认 P 键
            "key.category.block_hide_seek"
    );
}
