package com.mohuia.block_hide_seek;

import com.mohuia.block_hide_seek.command.BlockHuntCommand;
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

    public BlockHideSeek(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        InitAll(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    public void InitAll(IEventBus bus) {
        // 如果你有物品注册类，例如 ModItems.ITEMS.register(bus); 写在这里
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 使用 enqueueWork 确保在主线程执行注册
        event.enqueueWork(() -> {
            LOGGER.info("正在初始化 Block Hide Seek 网络系统...");
            PacketHandler.register(); // <--- 关键：注册你的网络包
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BlockHuntCommand.register(event.getDispatcher()); // <--- 关键：注册指令
    }
}
