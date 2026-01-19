package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mohuia.block_hide_seek.item.Radar.SEARCH_RANGE;

public final class S2CRadarScanService {


    private S2CRadarScanService() {}

    public static void broadcastScan(ServerPlayer sender) {
        double ox = sender.getX();
        double oy = sender.getY();
        double oz = sender.getZ();
        long startTick = sender.level().getGameTime();

        List<S2CRadarScanSync.Target> targets = new ArrayList<>();
        AtomicInteger debugCount = new AtomicInteger(0);

        sender.sendSystemMessage(Component.literal("=== 📡 雷达调试日志 ===").withStyle(ChatFormatting.GOLD));

        // 获取所有玩家
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (p == sender) continue; // 排除自己
            if (p.isSpectator()) continue; // 排除旁观者

            // 距离检查
            double dx = p.getX() - ox;
            double dz = p.getZ() - oz;
            double r = Math.sqrt(dx*dx + dz*dz);
            if (r > SEARCH_RANGE) continue;

            // 阵营检查
            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                boolean isSeeker = cap.isSeeker();

                // 🎨 构建调试消息
                Component roleText = isSeeker
                        ? Component.literal("抓捕者 (忽略)").withStyle(ChatFormatting.GREEN)
                        : Component.literal("躲藏者 (锁定)").withStyle(ChatFormatting.RED);

                sender.sendSystemMessage(Component.literal(" -> 发现目标: ")
                        .append(p.getDisplayName())
                        .append(" | 身份: ")
                        .append(roleText));
                // 核心逻辑：只有不是抓捕者才加入列表
                if (!isSeeker) {
                    // ✅ 给目标玩家失明 0 级，持续 5 秒（5 * 20 tick）
                    p.addEffect(new MobEffectInstance(
                            MobEffects.BLINDNESS,
                            5 * 20,   // 持续时间（tick）
                            0,        // 等级 0
                            false,    // 是否环境效果
                            false,    // 是否显示粒子
                            true      // 是否显示图标
                    ));
                    targets.add(new S2CRadarScanSync.Target(
                            p.getUUID(), p.getX(), p.getY(), p.getZ()
                    ));
                    debugCount.incrementAndGet();
                }
            });
        }

        if (debugCount.get() == 0) {
            sender.sendSystemMessage(Component.literal(" -> 未发现有效躲藏者").withStyle(ChatFormatting.GRAY));
        }

        sender.sendSystemMessage(Component.literal("=======================").withStyle(ChatFormatting.GOLD));

        // 发送包给客户端进行渲染
        S2CRadarScanSync pkt = new S2CRadarScanSync(ox, oy, oz, startTick, targets);
        PacketHandler.sendToAll(pkt);
    }
}
