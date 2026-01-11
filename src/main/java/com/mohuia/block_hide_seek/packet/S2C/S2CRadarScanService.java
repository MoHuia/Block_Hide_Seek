package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.network.chat.Component;
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

        List<S2CRadarScanSync.Target> targets = new ArrayList<>();
        int foundCount = 0;

        // 获取所有玩家
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (p == sender) continue; // 排除自己
            if (p.isSpectator()) continue; // 排除旁观者

            // 距离检查
            double dx = p.getX() - ox;
            double dz = p.getZ() - oz;
            double r = Math.sqrt(dx*dx + dz*dz);
            if (r > SCAN_RADIUS) continue;

            // 阵营检查
            // 只有当目标是 "躲藏者" (isSeeker == false) 时才加入雷达
            // 如果你希望雷达也能扫到队友，可以删掉这段
            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 如果 cap 存在，且 不是抓捕者 (即躲藏者)，才显示
                if (!cap.isSeeker()) {
                    targets.add(new S2CRadarScanSync.Target(
                            p.getUUID(), p.getX(), p.getY(), p.getZ()
                    ));
                }
            });
        }

        // 无论是否扫到人，波纹特效都必须发送！
        S2CRadarScanSync pkt = new S2CRadarScanSync(ox, oy, oz, startTick, targets);
        PacketHandler.sendToAll(pkt);
    }
}
