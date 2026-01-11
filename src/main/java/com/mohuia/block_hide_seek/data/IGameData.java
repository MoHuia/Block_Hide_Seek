package com.mohuia.block_hide_seek.data;

import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface IGameData {
    void setModelSize(float width, float height);
    float getModelWidth();
    float getModelHeight();

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

    boolean isYawLocked();
    void setYawLocked(boolean locked);
    float getLockedYaw();
    void setLockedYaw(float yaw);

    // ✅ 新增：隐身相关接口
    boolean isInvisible();
    void setInvisible(boolean invisible);

    int getInvisibilityTimer();
    void setInvisibilityTimer(int timer);
}
