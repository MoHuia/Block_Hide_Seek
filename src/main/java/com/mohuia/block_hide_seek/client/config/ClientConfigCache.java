package com.mohuia.block_hide_seek.client.config;

import java.util.ArrayList;
import java.util.List;

public class ClientConfigCache {
    public static int duration = 300;
    public static int hits = 5;
    public static int seekers = 1;

    public static String hiderSpawnTag = "";
    public static String lobbySpawnTag = "";

    public static void update(int d, int h, int s) {
        duration = d;
        hits = h;
        seekers = s;
    }

    public static List<String> availableTags = new ArrayList<>();

    public static void update(int d, int h, int s, String currentHiderTag, String currentLobbyTag) {
        duration = d;
        hits = h;
        seekers = s;
        // 保存同步过来的标签
        hiderSpawnTag = currentHiderTag;
        lobbySpawnTag = currentLobbyTag;
    }
}
