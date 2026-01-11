package com.mohuia.block_hide_seek.hitbox;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
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
            float yaw = data.isYawLocked() ? data.getLockedYaw() : player.getYRot();
            return new VirtualOBB(center, sizeX, sizeY, sizeZ, yaw);
        });
    }

    /**
     * ✅ 新增：获取替身/诱饵实体的 OBB
     * 假设 DecoyEntity 已经正确设置了自己的宽和高 (refreshDimensions)
     */
    public static Optional<VirtualOBB> getDecoyObb(DecoyEntity decoy) {
        if (decoy == null || !decoy.isAlive()) return Optional.empty();

        float width = decoy.getBbWidth();
        float height = decoy.getBbHeight();

        // 如果 Decoy 的宽高是 0 (异常情况)，强制给一个最小值
        if (width < 0.1f) width = 0.5f;
        if (height < 0.1f) height = 0.5f;

        double x = decoy.getX();
        double yBase = decoy.getY();
        double z = decoy.getZ();

        // 构造中心点
        Vec3 center = new Vec3(x, yBase + height * 0.5, z);
        float yaw = decoy.getYRot(); // 替身通常直接使用自身的旋转

        return Optional.of(new VirtualOBB(center, width, height, width, yaw));
    }
}
