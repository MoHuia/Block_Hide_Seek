package com.mohuia.block_hide_seek.client.vfx;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

//获取obj模型文件
public final class ObjMesh {

    public static final class Vert {
        public final Vector3f pos;
        public final Vector3f normal;
        public final Vector2f uv;

        public Vert(Vector3f pos, Vector3f normal, Vector2f uv) {
            this.pos = pos;
            this.normal = normal;
            this.uv = uv;
        }
    }

    // 三角形列表：每 3 个 Vert 为一个三角面
    private final List<Vert> triangles;

    private ObjMesh(List<Vert> triangles) {
        this.triangles = triangles;
    }

    public List<Vert> triangles() {
        return triangles;
    }

    public void render(PoseStack poseStack, VertexConsumer vc,
                       float r, float g, float b, float a,
                       int packedLight) {
        var pose = poseStack.last().pose();
        var normalMat = poseStack.last().normal();

        for (Vert v : triangles) {
            vc.vertex(pose, v.pos.x, v.pos.y, v.pos.z)
                    .color(r, g, b, a)
                    .uv(v.uv.x, v.uv.y)
                    .overlayCoords(OverlayTexture.NO_OVERLAY) // ✅ 关键：补齐 UV1
                    .uv2(packedLight)
                    .normal(normalMat, v.normal.x, v.normal.y, v.normal.z)
                    .endVertex();
        }
    }

    public static ObjMesh load(ResourceManager rm, ResourceLocation objLoc) throws Exception {
        Resource res = rm.getResourceOrThrow(objLoc);

        List<Vector3f> positions = new ArrayList<>();
        List<Vector2f> uvs = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();

        // 去重：OBJ 的 face 顶点是 (posIdx, uvIdx, nIdx) 组合
        Map<Key, Integer> dedup = new HashMap<>();
        List<Vert> uniqueVerts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(res.open(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        positions.add(new Vector3f(x, y, z));
                    }
                    case "vt" -> {
                        float u = Float.parseFloat(parts[1]);
                        float v = Float.parseFloat(parts[2]);
                        // OBJ 的 v 通常是从下往上；MC 贴图坐标通常从上往下，你可以按需要翻转
                        uvs.add(new Vector2f(u, 1.0f - v));
                    }
                    case "vn" -> {
                        float nx = Float.parseFloat(parts[1]);
                        float ny = Float.parseFloat(parts[2]);
                        float nz = Float.parseFloat(parts[3]);
                        normals.add(new Vector3f(nx, ny, nz));
                    }
                    case "f" -> {
                        // f 后面可能 3 个也可能更多（ngon）
                        int faceCount = parts.length - 1;
                        int[] faceVertIndices = new int[faceCount];

                        for (int i = 0; i < faceCount; i++) {
                            String token = parts[i + 1];
                            Idx idx = parseFaceToken(token, positions.size(), uvs.size(), normals.size());

                            Key key = new Key(idx.vi, idx.vti, idx.vni);
                            Integer existing = dedup.get(key);
                            int vertIndex;
                            if (existing != null) {
                                vertIndex = existing;
                            } else {
                                Vector3f p = positions.get(idx.vi);
                                Vector2f uv = (idx.vti >= 0) ? uvs.get(idx.vti) : new Vector2f(0, 0);
                                Vector3f n = (idx.vni >= 0) ? normals.get(idx.vni) : new Vector3f(0, 1, 0);
                                vertIndex = uniqueVerts.size();
                                uniqueVerts.add(new Vert(p, n, uv));
                                dedup.put(key, vertIndex);
                            }
                            faceVertIndices[i] = vertIndex;
                        }

                        // 三角化：扇形 (0, i, i+1)
                        for (int i = 1; i < faceCount - 1; i++) {
                            indices.add(faceVertIndices[0]);
                            indices.add(faceVertIndices[i]);
                            indices.add(faceVertIndices[i + 1]);
                        }
                    }
                    default -> {
                        // ignore: o, g, s, mtllib, usemtl ...
                    }
                }
            }
        }

        // 展开为三角形顶点列表（每 3 个为三角形）
        List<Vert> tris = new ArrayList<>(indices.size());
        for (int idx : indices) {
            tris.add(uniqueVerts.get(idx));
        }
        return new ObjMesh(tris);
    }

    private static final class Key {
        final int vi, vti, vni;
        Key(int vi, int vti, int vni) { this.vi = vi; this.vti = vti; this.vni = vni; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return vi == k.vi && vti == k.vti && vni == k.vni;
        }
        @Override public int hashCode() { return Objects.hash(vi, vti, vni); }
    }

    private static final class Idx {
        final int vi;   // 0-based
        final int vti;  // 0-based or -1
        final int vni;  // 0-based or -1
        Idx(int vi, int vti, int vni) { this.vi = vi; this.vti = vti; this.vni = vni; }
    }

    private static Idx parseFaceToken(String token, int vCount, int vtCount, int vnCount) {
        // token formats:
        // v
        // v/vt
        // v//vn
        // v/vt/vn
        String[] p = token.split("/");
        int vi = parseObjIndex(p[0], vCount);
        int vti = -1;
        int vni = -1;

        if (p.length >= 2 && !p[1].isEmpty()) vti = parseObjIndex(p[1], vtCount);
        if (p.length == 3 && !p[2].isEmpty()) vni = parseObjIndex(p[2], vnCount);

        return new Idx(vi, vti, vni);
    }

    private static int parseObjIndex(String s, int size) {
        int raw = Integer.parseInt(s);
        // OBJ: 1-based positive, negative means relative to end
        int idx = (raw > 0) ? (raw - 1) : (size + raw);
        if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException("OBJ index out of range: " + s);
        return idx;
    }

    public void renderPosColorOnly(PoseStack poseStack, VertexConsumer vc,
                                   float r, float g, float b, float a) {
        var pose = poseStack.last().pose();
        for (Vert v : triangles) {
            vc.vertex(pose, v.pos.x, v.pos.y, v.pos.z)
                    .color(r, g, b, a)
                    .endVertex();
        }
    }
}
