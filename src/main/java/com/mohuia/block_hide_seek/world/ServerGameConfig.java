package com.mohuia.block_hide_seek.world;

import com.mohuia.block_hide_seek.BlockHideSeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class ServerGameConfig extends SavedData {

    public int gameDurationSeconds = 300; // 默认 5分钟
    public int hitsToConvert = 5;         // 默认 挨打5下变抓捕者
    public int seekerCount = 1;           // 默认 1个抓捕者
    public String gameMapTag = ""; // 游戏地图（出生点）
    public String lobbyTag = "";   // 大厅（结束点）
    public int radarRange = 50;       // 雷达范围
    public int radarCooldown = 60;    // 雷达冷却 (ticks)
    public int vanishMana = 200;      // 默认 200 ticks (10秒)
    public int decoyCount = 3;         //默认最大3个
    public int decoyCooldown = 600;     //默认冷却30s

    public static ServerGameConfig get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getLevel(Level.OVERWORLD).getDataStorage()
                    .computeIfAbsent(ServerGameConfig::load, ServerGameConfig::new, BlockHideSeek.MODID + "_game_config");
        }
        return new ServerGameConfig();
    }

    // NBT 读写
    public static ServerGameConfig load(CompoundTag tag) {
        ServerGameConfig data = new ServerGameConfig();
        data.gameDurationSeconds = tag.getInt("duration");
        data.hitsToConvert = tag.getInt("hits");
        data.seekerCount = tag.getInt("seekers");
        //防止数据丢失
        if (tag.contains("map_tag")) {data.gameMapTag = tag.getString("map_tag");}
        if (tag.contains("lobby_tag")) {data.lobbyTag = tag.getString("lobby_tag");}
        if (tag.contains("radar_range")) {data.radarRange = tag.getInt("radar_range");}
        if (tag.contains("radar_cd")) {data.radarCooldown = tag.getInt("radar_cd");}
        if (tag.contains("vanish_mana")) { data.vanishMana = tag.getInt("vanish_mana");}
        if (tag.contains("decoy_count")) data.decoyCount = tag.getInt("decoy_count");
        if (tag.contains("decoy_cd")) data.decoyCooldown = tag.getInt("decoy_cd");
        // 保底修正
        if (data.gameDurationSeconds <= 0) data.gameDurationSeconds = 300;
        if (data.hitsToConvert <= 0) data.hitsToConvert = 5;
        if (data.seekerCount <= 0) data.seekerCount = 1;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("duration", gameDurationSeconds);
        tag.putInt("hits", hitsToConvert);
        tag.putInt("seekers", seekerCount);
        tag.putString("map_tag", gameMapTag == null ? "" : gameMapTag);
        tag.putString("lobby_tag", lobbyTag == null ? "" : lobbyTag);
        tag.putInt("radar_range", radarRange);
        tag.putInt("radar_cd", radarCooldown);
        tag.putInt("vanish_mana", vanishMana);
        tag.putInt("decoy_count", decoyCount);
        tag.putInt("decoy_cd", decoyCooldown);
        return tag;
    }
}
