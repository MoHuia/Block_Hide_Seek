package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class S2CRadarScanService {

    private static final double SCAN_RADIUS = 30.0;

    private S2CRadarScanService() {}

    public static void broadcastScan(ServerPlayer sender) {
        double ox = sender.getX();
        double oy = sender.getY();
        double oz = sender.getZ();

        long startTick = sender.level().getGameTime();

        // 搜集范围内玩家作为 Target
        List<S2CRadarScanSync.Target> targets = new ArrayList<>();
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (p == sender) continue; // 排除自己

            double dx = p.getX() - ox;
            double dz = p.getZ() - oz;
            double r = Math.sqrt(dx*dx + dz*dz);

            if (r <= SCAN_RADIUS) {
                targets.add(new S2CRadarScanSync.Target(
                        p.getUUID(), p.getX(), p.getY(), p.getZ()
                ));
            }
        }

        // 发送同步包给所有人（不仅是发送者，这样旁观者也能看到很酷的效果）
        S2CRadarScanSync pkt = new S2CRadarScanSync(ox, oy, oz, startTick, targets);
        PacketHandler.sendToAll(pkt);
    }
}
