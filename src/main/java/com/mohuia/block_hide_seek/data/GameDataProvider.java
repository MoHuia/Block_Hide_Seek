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
    // 实际的数据存储对象（真正的「数据背包」，存放具体数据）
    private final GameDataImp backend = new GameDataImp();
    // 延迟可选对象：提供安全的数据访问入口
    private final LazyOptional<IGameData> optional = LazyOptional.of(() -> backend);

    // 这个方法是 ICapabilitySerializable 接口的实现方法，
    // 核心作用是「给外部代码提供数据访问入口」，
    // 相当于「检查外部请求的是不是我们的『数据背包』，如果是就打开安全门，不是就返回空」。
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CAP ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isSeeker", backend.isSeeker());
        tag.putInt("hitCount", backend.getHitCount());
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
        if (nbt.contains("hitCount")) {
            backend.setHitCount(nbt.getInt("hitCount"));
        }
    }

    // 【建议添加】使 LazyOptional 失效，这是 Forge 的最佳实践，防止内存泄漏
    public void invalidate() {
        optional.invalidate();
    }
}
