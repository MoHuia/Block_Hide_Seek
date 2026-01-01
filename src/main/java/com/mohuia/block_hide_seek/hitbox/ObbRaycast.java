package com.mohuia.block_hide_seek.hitbox;

import net.minecraft.world.phys.Vec3;

public final class ObbRaycast {
    private ObbRaycast() {}

    /**
     * 射线(起点origin, 方向dir归一化) 与 仅绕Y轴旋转的OBB 相交测试
     * 返回：是否在 [0, maxDist] 内击中
     */
    public static boolean hit(Vec3 origin, Vec3 dir, double maxDist, VirtualOBB obb) {
        // 1) 转到OBB局部空间：p' = R(-yaw) * (p - center)
        Vec3 oc = origin.subtract(obb.getCenter());

        float yaw = (float) (obb.getYawDegrees() * Math.PI / 180.0);
        float cos = (float) Math.cos(-yaw);
        float sin = (float) Math.sin(-yaw);

        // 局部坐标：x/z 绕Y旋转，y不变
        double ox = oc.x * cos - oc.z * sin;
        double oy = oc.y;
        double oz = oc.x * sin + oc.z * cos;

        double dx = dir.x * cos - dir.z * sin;
        double dy = dir.y;
        double dz = dir.x * sin + dir.z * cos;

        // 2) 在局部空间对 AABB(-hx..hx, -hy..hy, -hz..hz) 做 slab test
        double tMin = 0.0;
        double tMax = maxDist;

        double hx = obb.getHalfX();
        double hy = obb.getHalfY();
        double hz = obb.getHalfZ();

        // X
        if (!slab(ox, dx, -hx, hx, Ref.of(tMin), Ref.of(tMax))) return false;
        tMin = Ref.tMin; tMax = Ref.tMax;

        // Y
        if (!slab(oy, dy, -hy, hy, Ref.of(tMin), Ref.of(tMax))) return false;
        tMin = Ref.tMin; tMax = Ref.tMax;

        // Z
        if (!slab(oz, dz, -hz, hz, Ref.of(tMin), Ref.of(tMax))) return false;
        tMin = Ref.tMin; tMax = Ref.tMax;

        return tMax >= tMin && tMax >= 0.0;
    }

    private static boolean slab(double o, double d, double min, double max, Ref tMinRef, Ref tMaxRef) {
        double tMin = tMinRef.v;
        double tMax = tMaxRef.v;

        final double EPS = 1e-9;
        if (Math.abs(d) < EPS) {
            // 平行该轴：起点不在范围内则不可能相交
            if (o < min || o > max) return false;
            Ref.tMin = tMin; Ref.tMax = tMax;
            return true;
        }

        double inv = 1.0 / d;
        double t1 = (min - o) * inv;
        double t2 = (max - o) * inv;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }

        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);

        if (tMax < tMin) return false;

        Ref.tMin = tMin; Ref.tMax = tMax;
        return true;
    }

    /**
     * 小技巧：避免一堆数组/AtomicDouble，直接用静态暂存（单线程tick里用没问题）
     */
    private static final class Ref {
        static double tMin;
        static double tMax;
        final double v;
        private Ref(double v) { this.v = v; }
        static Ref of(double v) { return new Ref(v); }
    }
}
