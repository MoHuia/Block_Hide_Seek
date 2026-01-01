package com.mohuia.block_hide_seek.hitbox;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 仅支持绕Y轴(yaw)旋转的 OBB（Oriented Bounding Box）。
 * - sizeX/sizeY/sizeZ 为总长度
 * - center 为世界坐标中心
 */
public final class VirtualOBB {
    private final Vec3 center;
    private final float halfX;
    private final float halfY;
    private final float halfZ;

    private final float yawRad;
    private final float cosYaw;
    private final float sinYaw;

    public VirtualOBB(Vec3 center, float sizeX, float sizeY, float sizeZ, float yawDegrees) {
        this.center = center;
        this.halfX = sizeX * 0.5f;
        this.halfY = sizeY * 0.5f;
        this.halfZ = sizeZ * 0.5f;

        this.yawRad = yawDegrees * ((float) Math.PI / 180.0f);
        this.cosYaw = Mth.cos(this.yawRad);
        this.sinYaw = Mth.sin(this.yawRad);
    }

    public Vec3 getCenter() { return center; }
    public float getHalfX() { return halfX; }
    public float getHalfY() { return halfY; }
    public float getHalfZ() { return halfZ; }

    public float getYawDegrees() {
        return yawRad * (180.0f / (float) Math.PI);
    }

    /**
     * 把局部坐标系中的方向向量 (lx, ly, lz) 旋转到世界坐标（只绕Y轴）。
     * 局部X：左右；局部Z：前后
     */
    private Vec3 localToWorldDir(double lx, double ly, double lz) {
        double wx = lx * cosYaw - lz * sinYaw;
        double wz = lx * sinYaw + lz * cosYaw;
        return new Vec3(wx, ly, wz);
    }

    /**
     * 8个角点（世界坐标）。顺序不保证组成面顺序，调试/包围盒可用。
     */
    public Vec3[] getCornersWorld() {
        Vec3[] corners = new Vec3[8];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double lx = sx * halfX;
                    double ly = sy * halfY;
                    double lz = sz * halfZ;
                    Vec3 rotated = localToWorldDir(lx, ly, lz);
                    corners[i++] = center.add(rotated);
                }
            }
        }
        return corners;
    }

    /**
     * OBB 的包围AABB（世界轴对齐），用于粗略裁剪/临时兼容 AABB API。
     */
    public AABB toEnclosingAABB() {
        Vec3[] c = getCornersWorld();
        double minX = c[0].x, minY = c[0].y, minZ = c[0].z;
        double maxX = c[0].x, maxY = c[0].y, maxZ = c[0].z;

        for (int i = 1; i < c.length; i++) {
            minX = Math.min(minX, c[i].x);
            minY = Math.min(minY, c[i].y);
            minZ = Math.min(minZ, c[i].z);
            maxX = Math.max(maxX, c[i].x);
            maxY = Math.max(maxY, c[i].y);
            maxZ = Math.max(maxZ, c[i].z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 返回线框12条边（24个点，pairs形式：0-1,2-3...）
     * 你渲染时按 pairs 画线即可。
     */
    public Vec3[] getWireframeEdges() {
        // 固定顺序的8个顶点：底面 0-3，顶面 4-7
        // 0: (-,-,-) 1:(+,-,-) 2:(+,-,+) 3:(-,-,+)
        // 4: (-,+,-) 5:(+,+,-) 6:(+,+,+) 7:(-,+,+)
        Vec3[] v = new Vec3[8];

        double[] xs = new double[]{-halfX, +halfX};
        double[] ys = new double[]{-halfY, +halfY};
        double[] zs = new double[]{-halfZ, +halfZ};

        v[0] = center.add(localToWorldDir(xs[0], ys[0], zs[0]));
        v[1] = center.add(localToWorldDir(xs[1], ys[0], zs[0]));
        v[2] = center.add(localToWorldDir(xs[1], ys[0], zs[1]));
        v[3] = center.add(localToWorldDir(xs[0], ys[0], zs[1]));

        v[4] = center.add(localToWorldDir(xs[0], ys[1], zs[0]));
        v[5] = center.add(localToWorldDir(xs[1], ys[1], zs[0]));
        v[6] = center.add(localToWorldDir(xs[1], ys[1], zs[1]));
        v[7] = center.add(localToWorldDir(xs[0], ys[1], zs[1]));

        int[][] edges = new int[][]{
                {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom
                {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top
                {0, 4}, {1, 5}, {2, 6}, {3, 7}  // vertical
        };

        Vec3[] out = new Vec3[24];
        int o = 0;
        for (int[] e : edges) {
            out[o++] = v[e[0]];
            out[o++] = v[e[1]];
        }
        return out;
    }
}
