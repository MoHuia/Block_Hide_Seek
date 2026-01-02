package com.mohuia.block_hide_seek;

import com.mohuia.block_hide_seek.client.BlockHuntHud;
import com.mohuia.block_hide_seek.client.ObbDebugRender;
import com.mohuia.block_hide_seek.command.BlockHuntCommand;
import com.mohuia.block_hide_seek.item.ModItems;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(BlockHideSeek.MODID)
public class BlockHideSeek {
    public static final String MODID = "block_hide_seek";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlockHideSeek(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 1. 初始化所有注册项
        InitAll(modEventBus);

        // 2. 监听通用设置事件
        modEventBus.addListener(this::commonSetup);

        // 3. 监听客户端设置事件
        modEventBus.addListener(this::clientSetup);

        // ✅✅✅ 【必须补上这一行】 ✅✅✅
        // 只有加上这一行，Forge 才会执行下面的 registerOverlays 方法！
        modEventBus.addListener(this::registerOverlays);

        // 4. 将自己注册到 Forge 事件总线 (用于监听指令注册、服务器Tick等)
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void InitAll(IEventBus bus) {
        LOGGER.info("正在注册 Block Hide Seek 物品与标签页...");
        ModItems.register(bus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("正在初始化 Block Hide Seek 网络系统...");
            PacketHandler.register();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ObbDebugRender());
        });
    }

    /**
     * 现在这行代码终于会被执行了！
     */
    private void registerOverlays(RegisterGuiOverlaysEvent event) {
        LOGGER.info(">>>>>>>>>> [BHS] 正在注册游戏 HUD... <<<<<<<<<<");
        event.registerAboveAll("bhs_game_hud", new BlockHuntHud());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BlockHuntCommand.register(event.getDispatcher());
    }
}
