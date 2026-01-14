package com.mohuia.block_hide_seek.network; // 注意包名是 network

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.packet.C2S.*;
import com.mohuia.block_hide_seek.packet.S2C.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        // 游戏运行配置相关
        INSTANCE.registerMessage(id++, C2SToggleWhitelist.class, C2SToggleWhitelist::encode, C2SToggleWhitelist::decode, C2SToggleWhitelist::handle);
        INSTANCE.registerMessage(id++, S2CSyncConfig.class, S2CSyncConfig::encode, S2CSyncConfig::decode, S2CSyncConfig::handle);

        INSTANCE.registerMessage(id++, S2CSyncMapTags.class, S2CSyncMapTags::encode, S2CSyncMapTags::decode, S2CSyncMapTags::handle);
        INSTANCE.registerMessage(id++, C2SRequestMapTags.class, C2SRequestMapTags::encode, C2SRequestMapTags::decode, C2SRequestMapTags::handle);

        INSTANCE.registerMessage(id++, S2COpenSelectScreen.class, S2COpenSelectScreen::encode, S2COpenSelectScreen::decode, S2COpenSelectScreen::handle);
        INSTANCE.registerMessage(id++, S2CSyncGameData.class, S2CSyncGameData::encode, S2CSyncGameData::decode, S2CSyncGameData::handle);

        // 配置相关
        INSTANCE.registerMessage(id++, C2SRequestConfig.class, C2SRequestConfig::encode, C2SRequestConfig::decode, C2SRequestConfig::handle);
        INSTANCE.registerMessage(id++, S2COpenConfigScreen.class, S2COpenConfigScreen::encode, S2COpenConfigScreen::decode, S2COpenConfigScreen::handle);
        INSTANCE.registerMessage(id++, C2SUpdateGameSettings.class, C2SUpdateGameSettings::encode, C2SUpdateGameSettings::decode, C2SUpdateGameSettings::handle);
        INSTANCE.registerMessage(id++, C2SSelectBlock.class, C2SSelectBlock::encode, C2SSelectBlock::decode, C2SSelectBlock::handle);

        // 静默更新广播
        INSTANCE.registerMessage(id++, S2CUpdateConfigGui.class, S2CUpdateConfigGui::encode, S2CUpdateConfigGui::decode, S2CUpdateConfigGui::handle);

        // 模型尺寸
        INSTANCE.registerMessage(id++, S2CRequestModelData.class, S2CRequestModelData::encode, S2CRequestModelData::decode, S2CRequestModelData::handle);
        INSTANCE.registerMessage(id++, C2SModelSizeResponse.class, C2SModelSizeResponse::encode, C2SModelSizeResponse::decode, C2SModelSizeResponse::handle);

        // 左键检查
        INSTANCE.registerMessage(id++, C2SAttackRaycast.class, C2SAttackRaycast::encode, C2SAttackRaycast::decode, C2SAttackRaycast::handle);

        // Caps 锁定朝向
        INSTANCE.registerMessage(id++, C2SSetYawLock.class, C2SSetYawLock::encode, C2SSetYawLock::decode, C2SSetYawLock::handle);
        INSTANCE.registerMessage(id++, S2CSyncYawLock.class, S2CSyncYawLock::encode, S2CSyncYawLock::decode, S2CSyncYawLock::handle);

        // HUD
        INSTANCE.registerMessage(id++, S2CUpdateHudPacket.class, S2CUpdateHudPacket::encode, S2CUpdateHudPacket::decode, S2CUpdateHudPacket::handle);

        // 雷达
        INSTANCE.registerMessage(id++, C2SRadarScanRequest.class, C2SRadarScanRequest::encode, C2SRadarScanRequest::decode, C2SRadarScanRequest::handle);
        INSTANCE.registerMessage(id++, S2CRadarScanSync.class, S2CRadarScanSync::encode, S2CRadarScanSync::decode, S2CRadarScanSync::handle);
        // 雷达显示伪装（私密包）
        INSTANCE.registerMessage(id++, S2CRevealDisguise.class, S2CRevealDisguise::encode, S2CRevealDisguise::decode, S2CRevealDisguise::handle);
    }

    // 补全缺失的发送方法
    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToAll(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}
