package com.mohuia.block_hide_seek.client;

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
    public static void update(boolean running, int time, List<PlayerInfo> allPlayers) {
        isGameRunning = running;
        timeRemaining = time;
        hiders.clear();
        seekers.clear();

        if (allPlayers != null) {
            for (PlayerInfo p : allPlayers) {
                if (p.isSeeker) {
                    seekers.add(p);
                } else {
                    hiders.add(p);
                }
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

        // 1. --- 关键：添加你自己 ---
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            hiders.add(new PlayerInfo(
                    localPlayer.getUUID(),             // 真实的 UUID，HUD 靠这个识别是不是你
                    localPlayer.getName().getString(), // 真实名字
                    false,                             // 设为躲藏者
                    new ItemStack(Items.DIAMOND_BLOCK) // 你的伪装是钻石块
            ));
        }

        // 2. 添加几个假 Hider 凑数
        for (int i = 1; i <= 4; i++) {
            hiders.add(new PlayerInfo(
                    UUID.randomUUID(),
                    "Bot_Hider_" + i,
                    false,
                    new ItemStack(i % 2 == 0 ? Items.CRAFTING_TABLE : Items.OAK_LOG)
            ));
        }

        // 3. 添加几个假 Seeker
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
