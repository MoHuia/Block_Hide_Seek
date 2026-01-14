package com.mohuia.block_hide_seek.game;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.item.ModItems;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2COpenSelectScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GameRoleManager {

    private static final UUID SEEKER_SPEED_UUID = UUID.fromString("c0d3b45e-1234-5678-9abc-def012345678");
    private static final AttributeModifier SEEKER_SPEED_BOOST = new AttributeModifier(
            SEEKER_SPEED_UUID, "Seeker Speed Bonus", 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL
    );

    public static void makeSeeker(ServerPlayer player, boolean isStart) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(true);
            cap.setDisguise(null);
            cap.setHitCount(0);
            GameNetworkHelper.syncPlayerData(player, true, null);
        });

        player.addTag("role_seeker");
        player.removeTag("bhs_hide_health");
        player.setHealth(player.getMaxHealth());
        clearInventory(player);

        // 发放装备
        player.getInventory().add(new ItemStack(ModItems.RADAR.get(), 1));
        player.getInventory().add(new ItemStack(ModItems.BOW.get(), 1));

        // 速度加成
        applySeekerAttributes(player);

        // 标题与音效
        sendSeekerTitles(player, isStart);

        // 如果是中途变身，给队友发奖励
        if (!isStart) {
            distributeHiderBonus(player.serverLevel());
        }
    }

    public static void makeHider(ServerPlayer player, List<BlockState> options) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setHitCount(0);
            cap.setDisguise(null);
            GameNetworkHelper.syncPlayerData(player, false, null);
        });

        player.addTag("bhs_hide_health");
        clearInventory(player);

        // 初始物品
        player.getInventory().add(new ItemStack(ModItems.DECOY.get(), 1));
        player.getInventory().add(new ItemStack(ModItems.VANISH.get(), 1));
        player.getInventory().add(new ItemStack(ModItems.SEEKER_WAND.get(), 1));

        // 打开选方块界面
        List<BlockState> myOptions = new ArrayList<>(options);
        Collections.shuffle(myOptions);
        myOptions = myOptions.subList(0, Math.min(myOptions.size(), 4));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenSelectScreen(myOptions));
    }

    public static void resetPlayer(ServerPlayer player) {
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            cap.setSeeker(false);
            cap.setDisguise(null);
            cap.setHitCount(0);
            GameNetworkHelper.syncPlayerData(player, false, null);
        });

        player.removeTag("role_seeker");
        player.removeTag("bhs_hide_health");

        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(SEEKER_SPEED_UUID);

        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        clearInventory(player);
    }

    private static void clearInventory(ServerPlayer player) {
        player.getInventory().clearOrCountMatchingItems(p -> true, -1, player.inventoryMenu.getCraftSlots());
    }

    private static void applySeekerAttributes(ServerPlayer player) {
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SEEKER_SPEED_UUID);
            speedAttr.addTransientModifier(SEEKER_SPEED_BOOST);
        }
    }

    private static void sendSeekerTitles(ServerPlayer player, boolean isStart) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        Component titleText = Component.literal("你成为了抓捕者！").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        player.connection.send(new ClientboundSetTitleTextPacket(titleText));

        String subStr = isStart ? "去抓捕所有躲藏者！" : "你被抓住了，加入抓捕阵营！";
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subStr).withStyle(ChatFormatting.GOLD)));
        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void distributeHiderBonus(ServerLevel level) {
        ItemStack vanish = new ItemStack(ModItems.VANISH.get(), 1);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            p.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                if (!cap.isSeeker()) {
                    if (p.getInventory().add(vanish.copy())) {
                        p.displayClientMessage(Component.literal("🎁 队友被抓！获得生存补给！").withStyle(ChatFormatting.GREEN), true);
                        p.playSound(SoundEvents.NOTE_BLOCK_CHIME.get(), 1.0f, 1.5f);
                    }
                }
            });
        }
    }
}
