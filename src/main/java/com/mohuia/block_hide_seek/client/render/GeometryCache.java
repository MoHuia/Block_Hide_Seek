package com.mohuia.block_hide_seek.client.render;

import com.mohuia.block_hide_seek.data.GameDataProvider; // ✅ 引入数据能力
import com.mohuia.block_hide_seek.packet.S2C.S2CRadarScanSync;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static com.mohuia.block_hide_seek.item.Radar.SEARCH_RANGE;

public class GeometryCache {

    public static final GeometryCache RADAR_RANGE = new GeometryCache();
    public static GeometryCache getInstance() { return RADAR_RANGE; }

    private static final long EXPIRE_TIME = 4000L;
    //private static final int SCAN_RADIUS = 30;
    private static final long EXPAND_MS = 1000L;
    private static final double WAVE_SPEED = SEARCH_RANGE / (EXPAND_MS / 1000.0);

    public static final class ScanTarget {
        public final UUID uuid;
        public final double x, y, z;
        public final double r;
        public final long triggerMs;
        public boolean triggered = false;

        public ScanTarget(UUID uuid, double x, double y, double z, double r, long triggerMs) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z; this.r = r; this.triggerMs = triggerMs;
        }
    }

    public static final class Pulse {
        public final double x, y, z;
        public final long startMs;
        public Pulse(double x, double y, double z, long startMs) { this.x=x; this.y=y; this.z=z; this.startMs=startMs; }
    }

    public static class CacheEntry {
        public double originX, originY, originZ;
        public List<QuadFxAPI.QuadJob> quads;
        public long createTime;
        public final List<ScanTarget> targets;
        public final List<Pulse> pulses = new ArrayList<>();

        public CacheEntry(double originX, double originY, double originZ, List<QuadFxAPI.QuadJob> quads, List<ScanTarget> targets) {
            this.originX = originX; this.originY = originY; this.originZ = originZ;
            this.quads = quads; this.targets = (targets != null) ? targets : new ArrayList<>();
            this.createTime = System.currentTimeMillis();
        }
    }

    private final Deque<CacheEntry> cacheQueue = new LinkedList<>();

    public void offerEntry(CacheEntry entry) {
        removeExpiredEntries();
        cacheQueue.offerLast(entry);
        // ✅ 打印当前队列状态（核心）
        System.out.println(
                "[GeometryCache] offerEntry 入队成功 | " +
                        "queueSize=" + cacheQueue.size() +
                        ", quads=" + entry.quads.size() +
                        ", targets=" + entry.targets.size() +
                        ", origin=(" + entry.originX + "," + entry.originY + "," + entry.originZ + ")"
        );
    }

    private void removeExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        while (!cacheQueue.isEmpty()) {
            CacheEntry firstEntry = cacheQueue.peekFirst();
            if (currentTime - firstEntry.createTime > EXPIRE_TIME) {
                cacheQueue.pollFirst();
            } else break;
        }
    }

    public boolean isEmpty() { removeExpiredEntries(); return cacheQueue.isEmpty(); }
    public Deque<CacheEntry> getCacheQueue() { removeExpiredEntries(); return cacheQueue; }

    public void renderCache(QuadFxAPI.Spot realSpot, double limitRadius, CacheEntry entry) {
        if (entry.quads.isEmpty()) return;
        double maxSq = limitRadius * limitRadius;
        double centerX = entry.originX;
        double centerY = entry.originY;
        double centerZ = entry.originZ;

        for (QuadFxAPI.QuadJob job : entry.quads) {
            double dx = job.cx - centerX;
            double dy = job.cy - centerY;
            double dz = job.cz - centerZ;
            if (dx*dx + dy*dy + dz*dz <= maxSq) realSpot.quad(job);
        }
    }

    // =========================================================
    // 重建逻辑 (核心修改区域)
    // =========================================================
    public void rebuild(Player player) {
        List<QuadFxAPI.QuadJob> tempQuads = new LinkedList<>();
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        Level level = player.level();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        int px = (int) Math.floor(playerX);
        int py = (int) Math.floor(playerY);
        int pz = (int) Math.floor(playerZ);

        // 1) 扫描地形 (保持不变，显示网格)
        for (int x = px - SEARCH_RANGE; x <= px + SEARCH_RANGE; x++) {
            for (int z = pz - SEARCH_RANGE; z <= pz + SEARCH_RANGE; z++) {
                if ((x - px)*(x - px) + (z - pz)*(z - pz) > SEARCH_RANGE * SEARCH_RANGE) continue;
                for (int y = py - 2; y <= py + 3; y++) {
                    mPos.set(x, y, z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) continue;
                    ModelGeometryUtil.extractHybrid(level, mPos, state, tempQuads::add);
                }
            }
        }

        long now = System.currentTimeMillis();
        List<ScanTarget> targets = new ArrayList<>();

        // 2) 扫描玩家 (✅ 修改：增加阵营判断)
        for (Player p : level.players()) {
            if (p == player) continue; // 不扫描自己
            if (p.isSpectator()) continue; // 不扫描旁观者

            // 获取游戏数据 Capability
            var cap = p.getCapability(GameDataProvider.CAP).orElse(null);

            // 🚨 核心逻辑：如果没有 Capability 或者是抓捕者，则跳过
            // 只有 cap.isSeeker() == false (即躲藏者) 才会被加入 targets
            if (cap == null || cap.isSeeker()) {
                continue;
            }

            double dx = p.getX() - playerX;
            double dz = p.getZ() - playerZ;
            double r = Math.sqrt(dx*dx + dz*dz);
            if (r <= SEARCH_RANGE) {
                // 计算波浪到达的时间，产生延迟效果
                long triggerMs = now + (long)((r / WAVE_SPEED) * 1000.0);
                targets.add(new ScanTarget(p.getUUID(), p.getX(), p.getY(), p.getZ(), r, triggerMs));
            }
        }

        // 3) 盔甲架扫描 (❌ 已移除：只扫描真实玩家躲藏者)
        /*
        AABB box = new AABB(playerX - SCAN_RADIUS, playerY - 256, playerZ - SCAN_RADIUS, playerX + SCAN_RADIUS, playerY + 256, playerZ + SCAN_RADIUS);
        for (ArmorStand as : level.getEntitiesOfClass(ArmorStand.class, box)) {
            // ...
        }
        */

        CacheEntry newEntry = new CacheEntry(playerX, playerY, playerZ, tempQuads, targets);
        this.offerEntry(newEntry);
    }

    // 服务端下发数据时的处理 (保持逻辑一致)
    public void offerServerScan(Level level, double originX, double originY, double originZ, List<S2CRadarScanSync.Target> serverTargets) {
        List<QuadFxAPI.QuadJob> tempQuads = rebuildAt(level, originX, originY, originZ);
        long now = System.currentTimeMillis();
        List<ScanTarget> targets = new ArrayList<>();

        if (serverTargets != null) {
            for (S2CRadarScanSync.Target t : serverTargets) {
                // 这里假设服务端发过来的 list 已经是筛选过的
                // 如果服务端发的是所有人，这里也可以尝试在客户端通过 uuid 获取 player 再检查 capability
                // 但通常服务端发包时过滤更高效。目前保持原样，假设 serverTargets 是有效的。
                double dx = t.x - originX;
                double dz = t.z - originZ;
                double r = Math.sqrt(dx*dx + dz*dz);
                if (r <= SEARCH_RANGE) {
                    long triggerMs = now + (long)((r / WAVE_SPEED) * 1000.0);
                    targets.add(new ScanTarget(t.uuid, t.x, t.y, t.z, r, triggerMs));
                }
            }
        }
        CacheEntry entry = new CacheEntry(originX, originY, originZ, tempQuads, targets);
        this.offerEntry(entry);
    }

    private List<QuadFxAPI.QuadJob> rebuildAt(Level level, double centerX, double centerY, double centerZ) {
        List<QuadFxAPI.QuadJob> tempQuads = new LinkedList<>();

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int px = (int) Math.floor(centerX);
        int py = (int) Math.floor(centerY);
        int pz = (int) Math.floor(centerZ);

        for (int x = px - SEARCH_RANGE; x <= px + SEARCH_RANGE; x++) {
            for (int z = pz - SEARCH_RANGE; z <= pz + SEARCH_RANGE; z++) {
                if ((x - px) * (x - px) + (z - pz) * (z - pz) > SEARCH_RANGE * SEARCH_RANGE) continue;
                for (int y = py - 15; y <= py + 15; y++) {
                    mPos.set(x, y, z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) continue;
                    ModelGeometryUtil.extractHybrid(level, mPos, state, tempQuads::add);
                }
            }
        }
        // ✅ 调试：扫描完成后，输出面数量
        System.out.println(
                "[GeometryCache] rebuildAt 完成：" +
                        " quads=" + tempQuads.size() +
                        " (估算三角面=" + (tempQuads.size() * 2) + ")" +
                        " center=(" + px + "," + py + "," + pz + ")"
        );
        return tempQuads;
    }
}
