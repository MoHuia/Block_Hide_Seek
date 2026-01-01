package com.mohuia.block_hide_seek.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyInit {
    // 打开配置菜单 (默认 H)
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.block_hide_seek.config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.category.block_hide_seek"
    );

    // 切换位置自动对齐 (默认 左Alt)
    public static final KeyMapping TOGGLE_ALIGN = new KeyMapping(
            "key.block_hide_seek.toggle_align",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.category.block_hide_seek"
    );

    // 锁定模型旋转方向 (默认 Caps Lock)
    public static final KeyMapping LOCK_ROTATION = new KeyMapping(
            "key.block_hide_seek.lock_rotation",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_CAPS_LOCK,
            "key.category.block_hide_seek"
    );
}
