package com.mohuia.block_hide_seek.item;

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
    // 1. 创建物品注册器
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BlockHideSeek.MODID);

    // 2. 创建创造模式标签页注册器
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlockHideSeek.MODID);

    // 3. 注册“伪装手杖”
    // 注意：SeekerWandItem 需要你自己创建（参考上一个回复的代码）
    public static final RegistryObject<Item> SEEKER_WAND = ITEMS.register("seeker_wand", SeekerWandItem::new);

    // 4. 注册“躲猫猫”标签页
    public static final RegistryObject<CreativeModeTab> BLOCK_HUNT_TAB = CREATIVE_TABS.register("block_hunt_tab", () -> CreativeModeTab.builder()
            // 【关键】设置图标为手杖
            .icon(() -> new ItemStack(SEEKER_WAND.get()))
            // 设置标题 (需要在 en_us.json 添加 "creativetab.block_hide_seek": "Block Hide Seek")
            .title(Component.translatable("creativetab.block_hide_seek"))
            // 设置内容：把手杖放进去
            .displayItems((parameters, output) -> {
                output.accept(SEEKER_WAND.get());
                // 未来如果有其他物品，也写在这里，例如：
                // output.accept(ModItems.OTHER_ITEM.get());
            })
            .build());

    // 5. 统一注册方法
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
}
