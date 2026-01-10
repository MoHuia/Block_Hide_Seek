package com.mohuia.block_hide_seek.item;

import com.mapextra.item.Radar;
import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.item.SeekerWandItem; // 假设你之前创建的手杖类在这个包
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    //创建物品注册器
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BlockHideSeek.MODID);

    //创建创造模式标签页注册器
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlockHideSeek.MODID);
    //注册物品
    //魔法手杖
    public static final RegistryObject<Item> SEEKER_WAND =
            ITEMS.register("seeker_wand", () -> new Radar(new Item.Properties()));
    //雷达
    public static final RegistryObject<Item> RADAR =
            ITEMS.register("radar", () -> new Radar(new Item.Properties()));

    //注册“躲猫猫”标签页
    public static final RegistryObject<CreativeModeTab> BLOCK_HUNT_TAB = CREATIVE_TABS.register("block_hunt_tab", () -> CreativeModeTab.builder()
            // 【关键】设置图标为手杖
            .icon(() -> new ItemStack(SEEKER_WAND.get()))
            // 设置标题 (需要在 en_us.json 添加 "creativetab.block_hide_seek": "Block Hide Seek")
            .title(Component.translatable("creativetab.block_hide_seek"))
            // 设置内容：把手杖放进去
            .displayItems((parameters, output) -> {
                output.accept(SEEKER_WAND.get());
                output.accept(RADAR.get());
            })
            .build());

    //统一注册方法
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
}
