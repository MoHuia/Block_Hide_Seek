package com.mohuia.block_hide_seek.item;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.entity.DecoyEntity;
import com.mohuia.block_hide_seek.game.GameLoopManager;
import com.mohuia.block_hide_seek.entity.EntityInit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class Decoy extends Item {

    // ✅ 核心逻辑：静态哈希表
    // Key: 玩家的 UUID
    // Value: 玩家放置的所有诱饵的 UUID 列表 (LinkedList当队列用)
    private static final Map<UUID, LinkedList<UUID>> PLAYER_DECOYS = new HashMap<>();

    // 最大放置数量
    private static final int MAX_DECOYS = 3;

    public Decoy() {
        super(new Item.Properties()
                .stacksTo(1)
                .durability(3));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 仅在服务端执行
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = (ServerLevel) level;

            // 1. 检查游戏状态
            if (!GameLoopManager.isGameRunning()) {
                player.sendSystemMessage(Component.literal("❌ 游戏未开始，无法使用道具").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            // 2. 检查玩家身份 (躲藏者且已伪装)
            boolean canUse = serverPlayer.getCapability(GameDataProvider.CAP)
                    .map(cap -> !cap.isSeeker() && cap.getDisguise() != null)
                    .orElse(false);

            if (!canUse) {
                player.sendSystemMessage(Component.literal("❌ 只有伪装后的躲藏者可以使用！").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            BlockState disguise = serverPlayer.getCapability(GameDataProvider.CAP)
                    .map(cap -> cap.getDisguise())
                    .orElse(null);

            if (disguise == null) return InteractionResultHolder.fail(stack);

            // =================================================
            // ✅ 新增：数量限制与清理逻辑
            // =================================================
            UUID playerUUID = player.getUUID();
            // 获取该玩家目前的诱饵列表，没有就创建新的
            LinkedList<UUID> userDecoys = PLAYER_DECOYS.computeIfAbsent(playerUUID, k -> new LinkedList<>());

            // A. 清理无效数据 (比如有些已经被抓捕者打掉了，或者被自己拆了)
            // 迭代器遍历，安全的删除不存在的实体
            userDecoys.removeIf(uuid -> {
                Entity e = serverLevel.getEntity(uuid);
                // 如果实体找不到了(null) 或者 已经死了(!isAlive)，就从列表里移除
                return e == null || !e.isAlive();
            });

            // B. 如果数量已达上限，移除最老的一个 (队列头)
            while (userDecoys.size() >= MAX_DECOYS) {
                UUID oldUuid = userDecoys.removeFirst(); // 移除列表第一个
                Entity oldEntity = serverLevel.getEntity(oldUuid);
                if (oldEntity != null && oldEntity.isAlive()) {
                    oldEntity.discard(); // 让旧实体消失
                    // 播放一个提示音在旧实体位置
                    // level.playSound(null, oldEntity.getX(), oldEntity.getY(), oldEntity.getZ(), SoundEvents.BAMBOO_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
            // =================================================

            // 4. 生成新诱饵
            DecoyEntity decoy = new DecoyEntity(EntityInit.DECOY_ENTITY.get(), level);
            decoy.setPos(player.getX(), player.getY(), player.getZ());
            decoy.setYRot(player.getYRot());
            decoy.setDisguiseBlock(disguise);

            level.addFreshEntity(decoy);

            // ✅ 将新实体的 UUID 加入队尾
            userDecoys.addLast(decoy.getUUID());

            // 5. 冷却与耐久
            player.getCooldowns().addCooldown(this, 600); // 3秒冷却
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));

            player.sendSystemMessage(Component.literal("💨 替身已放置 (" + userDecoys.size() + "/" + MAX_DECOYS + ")").withStyle(ChatFormatting.GREEN));

            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }
}
