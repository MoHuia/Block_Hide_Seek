package com.mohuia.block_hide_seek;

import com.mohuia.block_hide_seek.client.hud.BlockHuntHud;
import com.mohuia.block_hide_seek.client.ObbDebugRender;
import com.mohuia.block_hide_seek.command.BlockHuntCommand;
import com.mohuia.block_hide_seek.entity.EntityInit;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
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

        // 注册 HUD
        modEventBus.addListener(this::registerOverlays);

        // 4. 将自己注册到 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void InitAll(IEventBus bus) {
        ModItems.register(bus);
        EntityInit.ENTITIES.register(bus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ObbDebugRender());
        });
    }

    private void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("bhs_game_hud", new BlockHuntHud());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BlockHuntCommand.register(event.getDispatcher());
    }
}
