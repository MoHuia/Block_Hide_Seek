package com.mohuia.block_hide_seek.data;

import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface IGameData {
    //问是否为抓捕者
    boolean isSeeker();
    //然后设置是不是抓捕者
    void setSeeker(boolean isSeeker);
    //@Nullable表述这个返回值可能是空的，防止空指针崩溃
    @Nullable
    BlockState getDisguise();
    //设置伪装
    void setDisguise(@Nullable BlockState state);
    //把另一个IGameData的数据完全复制过来，因为玩家重生时原来的标签会被去掉，把旧的信息抄到新的上
    void copyFrom(IGameData other);
}
