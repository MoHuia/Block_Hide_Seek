package com.mohuia.block_hide_seek.data;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// implements IGameData: 意思是"我承诺实现 IGameData 里规定的所有功能"。
public class GameDataImp implements IGameData {

    // private: 私有的。意思是只有这个类自己能直接改这俩变量。
    // 别人想改？必须通过下面的 public 方法（setSeeker 等）。
    // 这叫"封装"，防止别人乱改数据导致出错。
    private boolean isSeeker = false; // 默认不是抓捕者
    private BlockState disguise = null; // 默认没有伪装（null）

    // @Override: 告诉 Java 编译器，下面这个方法是接口里规定好的，我正在实现它。
    // 如果你拼写错了方法名，编译器会报错提醒你。
    @Override
    public boolean isSeeker() {
        return this.isSeeker; // 返回上面存的变量
    }

    @Override
    public void setSeeker(boolean isSeeker) {
        this.isSeeker = isSeeker; // 把外部传进来的值赋给上面的变量
    }

    @Override
    public @Nullable BlockState getDisguise() {
        return this.disguise;
    }

    @Override
    public void setDisguise(@Nullable BlockState state) {
        this.disguise = state;
    }

    // 实现复制逻辑
    @Override
    public void copyFrom(IGameData other) {
        // 把 other 里的数据拿出来，存到 this (自己) 里面
        this.isSeeker = other.isSeeker();
        this.disguise = other.getDisguise();
    }
}
