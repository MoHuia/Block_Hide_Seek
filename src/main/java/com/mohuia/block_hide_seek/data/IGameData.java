package com.mohuia.block_hide_seek.data;

import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface IGameData {
    void setModelSize(float width, float height);
    float getModelWidth();
    float getModelHeight();

    // 这里继续沿用你已有字段名：实际含义改为 OBB 的 sizeX/sizeY/sizeZ（总长度）
    float getAABBX();
    float getAABBY();
    float getAABBZ();
    void setAABBSize(float x, float y, float z);

    boolean isSeeker();
    void setSeeker(boolean isSeeker);

    @Nullable
    BlockState getDisguise();
    void setDisguise(@Nullable BlockState state);

    int getHitCount();
    void setHitCount(int count);
    void incrementHitCount();

    void copyFrom(IGameData other);

    //锁定
    boolean isYawLocked();
    void setYawLocked(boolean locked);
    float getLockedYaw();
    void setLockedYaw(float yaw);
}
