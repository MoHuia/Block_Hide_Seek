package com.mohuia.block_hide_seek.game;

public enum WinnerType {
    SEEKERS, // 抓捕者胜
    HIDERS,  // 躲藏者胜
    DRAW,    // 平局 (或强制停止)
    NONE     // 无结果
}
