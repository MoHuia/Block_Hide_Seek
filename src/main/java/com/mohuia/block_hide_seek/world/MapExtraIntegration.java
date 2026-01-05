package com.mohuia.block_hide_seek.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapExtraIntegration extends SavedData {
    private static final String DATA_ID = "mapextra_positions";

    private static MapExtraIntegration INSTANCE_CACHE = null;

    // 记录上次真正从磁盘加载文件的时间戳
    private static long lastLoadedTime = 0;

    // ✅ 优化：记录上次"检查"文件状态的时间
    private static long lastCheckTimestamp = 0;
    // ✅ 优化：设置 2000ms (2秒) 的冷却时间，避免每 tick 都访问硬盘
    private static final long CHECK_INTERVAL_MS = 2000;

    private final Map<String, List<BlockPos>> tagMap = new HashMap<>();

    public MapExtraIntegration() {}

    public static MapExtraIntegration get(ServerLevel level) {
        long now = System.currentTimeMillis();

        // ✅ 第一道防线 (性能优化核心)：
        // 如果缓存已经存在，且距离上次检查文件状态还不到 2 秒，
        // 直接返回内存中的缓存，跳过所有的文件 IO 操作。
        if (INSTANCE_CACHE != null && (now - lastCheckTimestamp < CHECK_INTERVAL_MS)) {
            return INSTANCE_CACHE;
        }

        // 更新"上次检查时间"
        lastCheckTimestamp = now;

        File dataDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").toFile();
        File file = new File(dataDir, DATA_ID + ".dat");

        // ✅ 第二道防线 (热重载逻辑)：
        // 只有当文件确实存在，且磁盘上的修改时间比我们内存里的新时，才清除缓存
        if (INSTANCE_CACHE != null && file.exists() && file.lastModified() > lastLoadedTime) {
            System.out.println("[BlockHideSeek] 检测到 MapExtra 数据变化，正在重新加载...");
            INSTANCE_CACHE = null; // 清除旧缓存，强制重新读取
        }

        // ✅ 第三道防线 (缓存命中)：
        // 如果经过上面的检查，缓存依然有效，直接返回
        if (INSTANCE_CACHE != null) {
            return INSTANCE_CACHE;
        }

        // ✅ 真正读取磁盘 (冷启动或数据更新时执行)
        if (file.exists()) {
            try (FileInputStream stream = new FileInputStream(file)) {
                // 读取 MapExtra 标准的 Compressed NBT
                CompoundTag root = NbtIo.readCompressed(stream);
                if (root.contains("data")) {
                    INSTANCE_CACHE = load(root.getCompound("data"));
                    // 记录这次读取的文件时间戳
                    lastLoadedTime = file.lastModified();
                    return INSTANCE_CACHE;
                }
            } catch (IOException e) {
                System.err.println("[BlockHideSeek] 读取 MapExtra 数据失败: " + e.getMessage());
            }
        }

        // 文件不存在或读取失败，返回空对象防止空指针
        INSTANCE_CACHE = new MapExtraIntegration();
        return INSTANCE_CACHE;
    }

    // 手动清除缓存的方法 (调试用)
    public static void clearCache() {
        INSTANCE_CACHE = null;
        lastLoadedTime = 0;
        lastCheckTimestamp = 0;
    }

    public static MapExtraIntegration load(CompoundTag nbt) {
        MapExtraIntegration data = new MapExtraIntegration();
        if (nbt.contains("tag_map")) {
            CompoundTag mapsTag = nbt.getCompound("tag_map");
            for (String key : mapsTag.getAllKeys()) {
                ListTag listTag = mapsTag.getList(key, Tag.TAG_LONG);
                List<BlockPos> positions = new ArrayList<>();
                for (Tag t : listTag) {
                    if (t instanceof LongTag longTag) {
                        positions.add(BlockPos.of(longTag.getAsLong()));
                    }
                }
                data.tagMap.put(key, positions);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        return compoundTag; // 我们只读不写
    }

    // ================= 对外接口 =================

    public List<String> getAllTags() {
        return new ArrayList<>(tagMap.keySet());
    }

    public BlockPos getRandomPos(String tag, ServerLevel level) {
        if (tag == null || tag.isEmpty()) return null;
        List<BlockPos> list = tagMap.get(tag);
        if (list == null || list.isEmpty()) return null;
        // 使用 ServerLevel 自带的随机源
        return list.get(level.random.nextInt(list.size()));
    }
}
