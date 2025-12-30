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
        if (backend.getDisguise() != null) {
            tag.put("block", NbtUtils.writeBlockState(backend.getDisguise()));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("isSeeker")) {
            backend.setSeeker(nbt.getBoolean("isSeeker"));
        }
        if (nbt.contains("block")) {
            backend.setDisguise(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("block")));
        } else {
            backend.setDisguise(null);
        }
    }

    // 【建议添加】使 LazyOptional 失效，这是 Forge 的最佳实践，防止内存泄漏
    public void invalidate() {
        optional.invalidate();
    }
}
