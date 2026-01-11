package com.mohuia.block_hide_seek.client.hud;

import net.minecraft.client.Minecraft; // ✅ 必须导入这个才能获取“我”
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientGameCache {
    public static boolean isGameRunning = false;
    public static int timeRemaining = 0;

    public static class PlayerInfo {
        public UUID uuid;
        public String name;
        public boolean isSeeker;
        public ItemStack disguiseItem;
        public boolean forceOffline = false;
        public long revealDeadline = 0;
        //用于方块显示动画,0.0完全隐藏,1.0完全释放
        public float revealAnim = 0.0f;

        public PlayerInfo(UUID uuid, String name, boolean isSeeker, ItemStack disguiseItem) {
            this.uuid = uuid;
            this.name = name;
            this.isSeeker = isSeeker;
            this.disguiseItem = disguiseItem;
        }
    }

    public static List<PlayerInfo> hiders = new ArrayList<>();
    public static List<PlayerInfo> seekers = new ArrayList<>();

    // ✅ 必须保留这个方法，不然 Packet 会报错
    public static void update(boolean running, int time, List<PlayerInfo> newPlayers) {
        isGameRunning = running;
        timeRemaining = time;

        // 1. 备份旧的计时器数据
        // 因为 HUD 包不包含 revealDeadline，如果直接覆盖，倒计时就没了
        List<PlayerInfo> oldHiders = new ArrayList<>(hiders);

        hiders.clear();
        seekers.clear();

        if (newPlayers != null) {
            for (PlayerInfo p : newPlayers) {
                // 尝试从旧列表里找回倒计时
                for (PlayerInfo old : oldHiders) {
                    if (old.uuid.equals(p.uuid)) {
                        p.revealDeadline = old.revealDeadline; // 继承倒计时
                        break;
                    }
                }

                if (p.isSeeker) {
                    seekers.add(p);
                } else {
                    hiders.add(p);
                }
            }
        }
    }

    //显示躲藏者方块
    public static void revealDisguise(UUID targetUUID, int durationMs) {
        long deadline = System.currentTimeMillis() + durationMs;
        // 在躲藏者列表里找
        for (PlayerInfo p : hiders) {
            if (p.uuid.equals(targetUUID)) {
                p.revealDeadline = deadline;
                return;
            }
        }
    }

    /**
     * ✅ 调试用：把你自己加进去，这样才能看到 HUD 渲染你的高亮框！
     */
    public static void generateFakeData() {
        isGameRunning = true;
        timeRemaining = 365;
        hiders.clear();
        seekers.clear();

        //关键：添加你自己
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            hiders.add(new PlayerInfo(
                    localPlayer.getUUID(),             // 真实的 UUID，HUD 靠这个识别是不是你
                    localPlayer.getName().getString(), // 真实名字
                    false,                             // 设为躲藏者
                    new ItemStack(Items.DIAMOND_BLOCK) // 你的伪装是钻石块
            ));
        }
        //添加 Bot 1 (假装它是在线的)
        hiders.add(new PlayerInfo(UUID.randomUUID(), "Bot_Online", false, new ItemStack(Items.CRAFTING_TABLE)));
        //添加 Bot 2 (假装它掉线了！测试灰色效果)
        var offlineBot = new PlayerInfo(UUID.randomUUID(), "Bot_Offline", false, new ItemStack(Items.TNT));
        offlineBot.forceOffline = true; // 标记为离线
        hiders.add(offlineBot);

        //添加几个假 Hider 凑数
        for (int i = 1; i <= 4; i++) {
            hiders.add(new PlayerInfo(
                    UUID.randomUUID(),
                    "Bot_Hider_" + i,
                    false,
                    new ItemStack(i % 2 == 0 ? Items.CRAFTING_TABLE : Items.OAK_LOG)
            ));
        }

        //添加几个假 Seeker
        for (int i = 1; i <= 3; i++) {
            seekers.add(new PlayerInfo(
                    UUID.randomUUID(),
                    "Hunter_" + i,
                    true,
                    ItemStack.EMPTY
            ));
        }
    }
}
