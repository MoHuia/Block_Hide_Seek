package com.mohuia.block_hide_seek.packet.S2C;

import com.mohuia.block_hide_seek.client.render.GeometryCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CRadarScanSync {
    public final double ox, oy, oz;
    public final long startTick;
    public final List<Target> targets;

    // ✅ 定义内部静态类 Target
    public static final class Target {
        public final UUID uuid;
        public final double x, y, z;

        public Target(UUID uuid, double x, double y, double z) {
            this.uuid = uuid;
            this.x = x; this.y = y; this.z = z;
        }
    }

    // ✅ 修复构造函数：必须接收所有参数
    public S2CRadarScanSync(double ox, double oy, double oz, long startTick, List<Target> targets) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.startTick = startTick;
        this.targets = targets;
    }

    public static void encode(S2CRadarScanSync msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.ox);
        buf.writeDouble(msg.oy);
        buf.writeDouble(msg.oz);
        buf.writeLong(msg.startTick);

        buf.writeInt(msg.targets.size());
        for (Target t : msg.targets) {
            buf.writeUUID(t.uuid);
            buf.writeDouble(t.x);
            buf.writeDouble(t.y);
            buf.writeDouble(t.z);
        }
    }

    public static S2CRadarScanSync decode(FriendlyByteBuf buf) {
        double ox = buf.readDouble();
        double oy = buf.readDouble();
        double oz = buf.readDouble();
        long startTick = buf.readLong();

        int n = buf.readInt();
        List<Target> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            targets.add(new Target(id, x, y, z));
        }

        return new S2CRadarScanSync(ox, oy, oz, startTick, targets);
    }

    public static void handle(S2CRadarScanSync msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // ✅ 仅在客户端执行
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            // 调用 GeometryCache 处理数据并生成渲染任务
            GeometryCache.getInstance().offerServerScan(mc.level, msg.ox, msg.oy, msg.oz, msg.targets);
        });
        ctx.get().setPacketHandled(true);
    }
}
