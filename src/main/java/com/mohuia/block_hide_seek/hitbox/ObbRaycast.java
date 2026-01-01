package com.mohuia.block_hide_seek.hitbox;

import net.minecraft.world.phys.Vec3;

/**
 * OBB Raycast：仅支持绕 Y 轴旋转的 OBB
 * 提供：返回命中距离 t（射线参数，dir 已归一化时 t=距离）
 */
public final class ObbRaycast {
    private ObbRaycast() {}

    /** 返回最近命中距离；若未命中返回 -1 */
    public static double hitDistance(Vec3 origin, Vec3 dirNormalized, double maxDist, VirtualOBB obb) {
        // 1) 转到 OBB 局部空间：p' = R(-yaw) * (p - center)
        Vec3 oc = origin.subtract(obb.getCenter());

        double yawRad = obb.getYawDegrees() * Math.PI / 180.0;
        double cos = Math.cos(-yawRad);
        double sin = Math.sin(-yawRad);

        // 局部坐标：x/z 绕Y旋转，y不变
        double ox = oc.x * cos - oc.z * sin;
        double oy = oc.y;
        double oz = oc.x * sin + oc.z * cos;

        double dx = dirNormalized.x * cos - dirNormalized.z * sin;
        double dy = dirNormalized.y;
        double dz = dirNormalized.x * sin + dirNormalized.z * cos;

        // 2) slab test：对局部 AABB(-hx..hx, -hy..hy, -hz..hz)
        double tMin = 0.0;
        double tMax = maxDist;

        double hx = obb.getHalfX();
        double hy = obb.getHalfY();
        double hz = obb.getHalfZ();

        // X
        double[] out = slab(ox, dx, -hx, hx, tMin, tMax);
        if (out == null) return -1;
        tMin = out[0]; tMax = out[1];

        // Y
        out = slab(oy, dy, -hy, hy, tMin, tMax);
        if (out == null) return -1;
        tMin = out[0]; tMax = out[1];

        // Z
        out = slab(oz, dz, -hz, hz, tMin, tMax);
        if (out == null) return -1;
        tMin = out[0]; tMax = out[1];

        if (tMax < 0.0) return -1;     // 整体在射线反方向
        if (tMax < tMin) return -1;

        // 进入点就是 tMin；如果起点在盒子里，tMin 可能为 0
        return tMin;
    }

    /** 仅返回是否命中（保留你原来的 boolean 用法） */
    public static boolean hit(Vec3 origin, Vec3 dirNormalized, double maxDist, VirtualOBB obb) {
        return hitDistance(origin, dirNormalized, maxDist, obb) >= 0.0;
    }

    /**
     * slab 返回 {newTMin, newTMax}；若不相交返回 null
     */
    private static double[] slab(double o, double d, double min, double max, double tMin, double tMax) {
        final double EPS = 1e-9;

        if (Math.abs(d) < EPS) {
            // 平行该轴：起点不在范围内则不可能相交
            if (o < min || o > max) return null;
            return new double[]{tMin, tMax};
        }

        double inv = 1.0 / d;
        double t1 = (min - o) * inv;
        double t2 = (max - o) * inv;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }

        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);

        if (tMax < tMin) return null;
        return new double[]{tMin, tMax};
    }
}