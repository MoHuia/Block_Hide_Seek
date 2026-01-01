package com.mohuia.block_hide_seek.hitbox;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class ObbUtil {
    private ObbUtil() {}

    /**
     * 从 capability 读取 aabbX/Y/Z（当作 OBB 的 sizeX/Y/Z）：
     * - 底面对齐玩家脚底 y=player.getY()
     * - 水平中心对齐玩家 x/z
     * - yaw 对齐玩家朝向 player.getYRot()
     */
    public static Optional<VirtualOBB> getPlayerObb(Player player) {
        return player.getCapability(GameDataProvider.CAP).resolve().map(data -> {
            float sizeX = data.getAABBX();
            float sizeY = data.getAABBY();
            float sizeZ = data.getAABBZ();

            double x = player.getX();
            double yBase = player.getY();
            double z = player.getZ();

            Vec3 center = new Vec3(x, yBase + sizeY * 0.5, z);
            float yaw = player.getYRot();

            return new VirtualOBB(center, sizeX, sizeY, sizeZ, yaw);
        });
    }
}
