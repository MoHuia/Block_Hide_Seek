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
        return tag;
    }
}
