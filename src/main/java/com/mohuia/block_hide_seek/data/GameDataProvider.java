package com.mohuia.block_hide_seek.data;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GameDataProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IGameData> CAP = CapabilityManager.get(new CapabilityToken<>(){});

    private final GameDataImp backend = new GameDataImp();
    private final LazyOptional<IGameData> optional = LazyOptional.of(() -> backend);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CAP ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isSeeker", backend.isSeeker());
        tag.putInt("hitCount", backend.getHitCount());

        tag.putFloat("modelWidth", backend.getModelWidth());
        tag.putFloat("modelHeight", backend.getModelHeight());

        tag.putFloat("aabbX", backend.getAABBX());
        tag.putFloat("aabbY", backend.getAABBY());
        tag.putFloat("aabbZ", backend.getAABBZ());

        // ✅ 新增：保存隐身数据
        tag.putBoolean("isInvisible", backend.isInvisible());
        tag.putInt("invisibilityTimer", backend.getInvisibilityTimer());

        if (backend.getDisguise() != null) {
            tag.put("block", NbtUtils.writeBlockState(backend.getDisguise()));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("isSeeker")) backend.setSeeker(nbt.getBoolean("isSeeker"));
        if (nbt.contains("hitCount")) backend.setHitCount(nbt.getInt("hitCount"));

        if (nbt.contains("modelWidth")) backend.setModelSize(nbt.getFloat("modelWidth"), backend.getModelHeight());
        if (nbt.contains("modelHeight")) backend.setModelSize(backend.getModelWidth(), nbt.getFloat("modelHeight"));

        if (nbt.contains("aabbX") && nbt.contains("aabbY") && nbt.contains("aabbZ")) {
            backend.setAABBSize(nbt.getFloat("aabbX"), nbt.getFloat("aabbY"), nbt.getFloat("aabbZ"));
        }

        // ✅ 新增：读取隐身数据
        if (nbt.contains("isInvisible")) backend.setInvisible(nbt.getBoolean("isInvisible"));
        if (nbt.contains("invisibilityTimer")) backend.setInvisibilityTimer(nbt.getInt("invisibilityTimer"));

        if (nbt.contains("block")) {
            backend.setDisguise(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("block")));
        } else backend.setDisguise(null);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
