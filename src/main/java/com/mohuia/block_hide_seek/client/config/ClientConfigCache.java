package com.mohuia.block_hide_seek.client.config;

public class ClientConfigCache {
    public static int duration = 300;
    public static int hits = 5;
    public static int seekers = 1;

    public static void update(int d, int h, int s) {
        duration = d;
        hits = h;
        seekers = s;
    }
}
