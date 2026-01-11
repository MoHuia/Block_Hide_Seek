package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.BlockHideSeek;
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
            ITEMS.register("seeker_wand", SeekerWandItem::new);
    //雷达
    public static final RegistryObject<Item> RADAR =
            ITEMS.register("radar", () -> new Radar(new Item.Properties()));
    // 隐身粉尘
    public static final RegistryObject<Item> VANISH =
            ITEMS.register("vanish", () -> new Vanish(new Item.Properties()));
    //诱饵道具
    public static final RegistryObject<Item> DECOY =
            ITEMS.register("decoy", Decoy::new);


    //注册“躲猫猫”标签页
    public static final RegistryObject<CreativeModeTab> BLOCK_HUNT_TAB = CREATIVE_TABS.register("block_hunt_tab", () -> CreativeModeTab.builder()
            // 设置图标
            .icon(() -> new ItemStack(SEEKER_WAND.get()))
            // 设置标题
            .title(Component.translatable("creativetab.block_hide_seek"))
            .displayItems((parameters, output) -> {
                // 只要是在 ITEMS 里注册过的，统统自动加进来
                for (RegistryObject<Item> item : ITEMS.getEntries()) {
                    output.accept(item.get());
                }
            })
            .build());

    //统一注册方法
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
}
