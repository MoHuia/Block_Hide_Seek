package com.mohuia.block_hide_seek;

import com.mohuia.block_hide_seek.command.BlockHuntCommand;
import com.mohuia.block_hide_seek.item.ModItems; // <--- 导入刚才写的注册类
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BlockHideSeek.MODID)
public class BlockHideSeek {
    public static final String MODID = "block_hide_seek";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlockHideSeek(FMLJavaModLoadingContext context) { // 1.20.1 建议直接用构造注入 context
        IEventBus modEventBus = context.getModEventBus();

        // 1. 初始化所有注册项 (物品、方块、标签页等)
        InitAll(modEventBus);

        // 2. 监听通用设置事件 (网络包注册等)
        modEventBus.addListener(this::commonSetup);

        // 3. 将自己注册到 Forge 事件总线 (用于监听指令注册、服务器Tick等)
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void InitAll(IEventBus bus) {
        // --- 在这里调用注册方法 ---
        LOGGER.info("正在注册 Block Hide Seek 物品与标签页...");
        ModItems.register(bus); // <--- 【关键】注册物品和创造模式栏

        // 如果你将来有方块注册类 (ModBlocks)，也写在这里：
        // ModBlocks.register(bus);

        // 如果有音效注册类 (ModSounds)，也写在这里：
        // ModSounds.register(bus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 使用 enqueueWork 确保在主线程执行注册
        event.enqueueWork(() -> {
            LOGGER.info("正在初始化 Block Hide Seek 网络系统...");
            PacketHandler.register(); // <--- 注册网络包
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BlockHuntCommand.register(event.getDispatcher()); // <--- 注册指令
    }
}
