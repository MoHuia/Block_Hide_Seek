package com.mohuia.block_hide_seek.world;

import com.mohuia.block_hide_seek.BlockHideSeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries; // 关键导入

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockWhitelistData extends SavedData {
    // 使用 Set 防止重复添加
    private final Set<String> allowedBlocks = new HashSet<>();

    public static BlockWhitelistData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            // 数据保存在主世界 (Overworld)
            return serverLevel.getServer().getLevel(Level.OVERWORLD).getDataStorage()
                    .computeIfAbsent(BlockWhitelistData::load, BlockWhitelistData::new, BlockHideSeek.MODID + "_whitelist");
        }
        return new BlockWhitelistData();
    }

    public void addBlock(BlockState state) {
        // 使用 ForgeRegistries 获取 ID
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key != null) {
            if (allowedBlocks.add(key.toString())) {
                setDirty(); // 标记数据已修改，需要保存
            }
        }
    }

    public void removeBlock(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key != null) {
            if (allowedBlocks.remove(key.toString())) {
                setDirty();
            }
        }
    }

    public List<BlockState> getAllowedStates() {
        List<BlockState> list = new ArrayList<>();
        for (String id : allowedBlocks) {
            // 使用 tryParse 解析字符串
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc != null) {
                // 使用 ForgeRegistries 获取方块
                Block block = ForgeRegistries.BLOCKS.getValue(loc);
                // 校验：如果 ID 不存在或解析为空气，则跳过
                if (block != null && block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR) {
                    list.add(block.defaultBlockState());
                }
            }
        }
        return list;
    }

    // --- NBT 读写 ---

    public static BlockWhitelistData load(CompoundTag tag) {
        BlockWhitelistData data = new BlockWhitelistData();
        ListTag list = tag.getList("blocks", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.allowedBlocks.add(list.getString(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String id : allowedBlocks) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("blocks", list);
        return tag;
    }
}
