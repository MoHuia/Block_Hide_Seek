package com.mohuia.block_hide_seek.data;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class GameDataImp implements IGameData {

    private float modelWidth = 0.5f;
    private float modelHeight = 1.0f;

    // 现在这三个当成 OBB 的 sizeX/sizeY/sizeZ（总长度）
    private float AABBx = 1.0f;
    private float AABBy = 1.0f;
    private float AABBz = 1.0f;

    private boolean yawLocked = false;
    private float lockedYaw = 0.0f;

    @Override
    public void setModelSize(float width, float height) {
        this.modelWidth = width;
        this.modelHeight = height;
    }

    @Override
    public float getModelWidth() {
        return modelWidth;
    }

    @Override
    public float getModelHeight() {
        return modelHeight;
    }

    @Override
    public float getAABBX() {
        return AABBx;
    }

    @Override
    public float getAABBY() {
        return AABBy;
    }

    @Override
    public float getAABBZ() {
        return AABBz;
    }

    @Override
    public void setAABBSize(float x, float y, float z) {
        this.AABBx = x;
        this.AABBy = y;
        this.AABBz = z;
    }

    private boolean isSeeker = false;
    private BlockState disguise = null;
    private int hitCount = 0;

    @Override
    public boolean isSeeker() {
        return this.isSeeker;
    }

    @Override
    public void setSeeker(boolean isSeeker) {
        this.isSeeker = isSeeker;
    }

    @Override
    public @Nullable BlockState getDisguise() {
        return this.disguise;
    }

    @Override
    public void setDisguise(@Nullable BlockState state) {
        this.disguise = state;
    }

    @Override
    public int getHitCount() {
        return this.hitCount;
    }

    @Override
    public void setHitCount(int count) {
        this.hitCount = count;
    }

    @Override
    public void incrementHitCount() {
        this.hitCount++;
    }

    @Override
    public void copyFrom(IGameData other) {
        this.isSeeker = other.isSeeker();
        this.disguise = other.getDisguise();
        this.hitCount = other.getHitCount();

        this.modelWidth = other.getModelWidth();
        this.modelHeight = other.getModelHeight();

        this.AABBx = other.getAABBX();
        this.AABBy = other.getAABBY();
        this.AABBz = other.getAABBZ();
    }

    @Override
    public boolean isYawLocked() {
        return yawLocked;
    }

    @Override
    public void setYawLocked(boolean locked) {
        this.yawLocked = locked;
    }

    @Override
    public float getLockedYaw() {
        return lockedYaw;
    }

    @Override
    public void setLockedYaw(float yaw) {
        this.lockedYaw = yaw;
    }
}
