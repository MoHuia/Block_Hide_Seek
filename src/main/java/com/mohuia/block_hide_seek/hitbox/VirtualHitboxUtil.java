package com.mohuia.block_hide_seek.hitbox;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class VirtualHitboxUtil {
    private VirtualHitboxUtil() {}

    /**
     * 调试：虚拟箱尺寸 = capability 的 modelWidth/modelHeight，
     * 但高度额外 + extraHeightBlocks，并且 yaw 对齐玩家朝向（OBB）。
     *
     * 规则：
     * - 底面对齐 player.getY()
     * - 水平中心对齐 player.getX/Z
     * - yaw 对齐 player.getYRot()
     */
    public static Optional<VirtualOBB> getDebugVirtualBox(Player player, float extraHeightBlocks) {
        return player.getCapability(GameDataProvider.CAP).resolve().map(data -> {
            float sizeX = data.getModelWidth();
            float sizeY = data.getModelHeight() + extraHeightBlocks;
            float sizeZ = data.getModelWidth(); // 保持你原先“底面正方形”的习惯

            double x = player.getX();
            double yBase = player.getY();
            double z = player.getZ();

            Vec3 center = new Vec3(x, yBase + sizeY * 0.5, z);
            float yaw = player.getYRot();

            return new VirtualOBB(center, sizeX, sizeY, sizeZ, yaw);
        });
    }

    /**
     * 更“真实”：使用最终生效的 dimensions（你的 mixin 会改它），并在高度上 + extraHeightBlocks。
     * 同样 yaw 对齐玩家（OBB）。
     */
    public static VirtualOBB getFinalDimensionsBox(Player player, float extraHeightBlocks) {
        var dims = player.getDimensions(player.getPose());
        float sizeX = dims.width;
        float sizeY = dims.height + extraHeightBlocks;
        float sizeZ = dims.width;

        double x = player.getX();
        double yBase = player.getY();
        double z = player.getZ();

        Vec3 center = new Vec3(x, yBase + sizeY * 0.5, z);
        float yaw = player.getYRot();

        return new VirtualOBB(center, sizeX, sizeY, sizeZ, yaw);
    }

    // 兼容：如果你某些地方临时只接受 AABB，就用包围AABB
    public static Optional<AABB> getDebugVirtualBoxEnclosingAABB(Player player, float extraHeightBlocks) {
        return getDebugVirtualBox(player, extraHeightBlocks).map(VirtualOBB::toEnclosingAABB);
    }

    public static AABB getFinalDimensionsBoxEnclosingAABB(Player player, float extraHeightBlocks) {
        return getFinalDimensionsBox(player, extraHeightBlocks).toEnclosingAABB();
    }
}
