package com.mohuia.block_hide_seek.client.config;

import com.mohuia.block_hide_seek.item.Decoy;
import com.mohuia.block_hide_seek.item.Vanish;

import java.util.ArrayList;
import java.util.List;

public class ClientConfigCache {
    public static int duration = 300;
    public static int hits = 5;
    public static int seekers = 1;
    public static int radarRange = 50;//雷达默认范围
    public static int radarCooldown = 60;//雷达默认冷却
    public static int vanishMana = 200;//最大蓝量
    public static int decoyCount = 3;//最大放置数量
    public static int decoyCooldown = 600;//冷却


    public static String hiderSpawnTag = "";
    public static String lobbySpawnTag = "";

    public static void update(int d, int h, int s) {
        duration = d;
        hits = h;
        seekers = s;
    }

    public static List<String> availableTags = new ArrayList<>();

    public static void update(int d, int h, int s, String currentHiderTag, String currentLobbyTag,int rRange, int rCd, int vMana,int dCount,int dCd) {
        duration = d;
        hits = h;
        seekers = s;
        // 保存同步过来的标签
        hiderSpawnTag = currentHiderTag;
        lobbySpawnTag = currentLobbyTag;

        radarRange = rRange;
        radarCooldown = rCd;

        vanishMana = vMana;
        Vanish.MAX_MANA = vMana;

        decoyCount = dCount;
        decoyCooldown = dCd;
        Decoy.MAX_DECOYS = dCount;
        Decoy.COOLDOWN = dCd;
    }
}
