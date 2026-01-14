package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.game.GameLoopManager;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BlockHideSeek.MODID)
public class CommonEvents {

    // ==========================================
    // 1. Capability 数据挂载与同步
    // ==========================================

    @SubscribeEvent
    public static void onAttachCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(
                    ResourceLocation.fromNamespaceAndPath(BlockHideSeek.MODID, "game_data"),
                    new GameDataProvider()
            );
        }
    }

    /**
     * 当玩家进入别人的视野范围时 (StartTracking)，同步数据。
     * 否则别人看到的你只是普通玩家，看不到伪装方块。
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer observer) {
            target.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                // 使用完整的构造函数同步所有数据 (包括隐身状态)
                // 确保 observer 知道 target 是否隐身
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> observer),
                        new S2CSyncGameData(target.getId(), cap));
            });
        }
    }

    /**
     * 玩家重生、跨维度传送时，保留伪装数据。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(GameDataProvider.CAP).ifPresent(oldCap -> {
            event.getEntity().getCapability(GameDataProvider.CAP).ifPresent(newCap -> {
                newCap.copyFrom(oldCap);
            });
        });
    }

    // ==========================================
    // 2. 游戏交互逻辑 (修复攻击判定冲突)
    // ==========================================

    /**
     * ✅ 核心修复：拦截原版攻击逻辑
     * 问题描述：当抓捕者离躲藏者很近时，鼠标左键可能直接点中原版实体 Hitbox。
     * 这会导致：
     * 1. 触发原版扣血/击退（不受控制）。
     * 2. 绕过了我们的 OBB 计数逻辑。
     *
     * 解决方案：如果是 Seeker 打 Hider，直接取消原版事件。
     * 所有的判定全权交给 C2SAttackRaycast -> GameLoopManager 处理。
     */
    @SubscribeEvent
    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        // 如果游戏没开始，或者是客户端侧，不管
        if (!GameLoopManager.isGameRunning() || event.getEntity().level().isClientSide) return;

        // 确保攻击者是玩家，受害者也是玩家
        if (!(event.getTarget() instanceof Player victim)) return;
        Player attacker = event.getEntity();

        // 检查身份
        boolean isSeeker = attacker.getCapability(GameDataProvider.CAP)
                .map(data -> data.isSeeker())
                .orElse(false);

        boolean isHider = victim.getCapability(GameDataProvider.CAP)
                .map(data -> !data.isSeeker())
                .orElse(false);

        // 如果是 抓捕者 试图攻击 躲藏者
        if (isSeeker && isHider) {
            // 取消事件！禁止原版判定生效
            event.setCanceled(true);
        }
    }

    // ==========================================
    // 2. 游戏循环驱动
    // ==========================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 只在 Tick 结束时处理，且只处理主世界 (OVERWORLD) 的逻辑，避免多维度重复计算
        if (event.phase == TickEvent.Phase.END) {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
                if (level != null) {
                    GameLoopManager.tick(level);
                }
            }
        }
    }

    // ==========================================
    // 3. 物理碰撞箱修正 (核心优化)
    // ==========================================

    /**
     * 解决 "变小后无法钻洞" 的问题。
     * 原理：Mixin 修改了 dimensions，但服务端实体有时不会立即刷新 boundingBox。
     * 这里强制检查并刷新。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 1. 基础检查：必须是服务端，必须是 Tick 结束阶段
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;

        // 2. 性能优化：只有当玩家处于伪装状态时才进行检查
        player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
            if (!cap.isSeeker() && cap.getDisguise() != null) {

                // 获取 "当前实际生效的碰撞箱高度"
                float actualHeight = player.getBbHeight();
                // 获取 "根据姿态和伪装应该有的理论高度" (由 Mixin/Entity.getDimensions 定义)
                float expectedHeight = player.getDimensions(player.getPose()).height;

                // 3. 误差检测：如果误差超过 1cm，说明碰撞箱滞后了
                if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
                    // 强制刷新：这会重新计算 AABB，让玩家能钻进 1格高的洞
                    player.refreshDimensions();
                }
            }

            // --- B. ✅ 新增：隐身倒计时逻辑 ---
            if (cap.isInvisible()) {
                int timer = cap.getInvisibilityTimer();
                if (timer > 0) {
                    cap.setInvisibilityTimer(timer - 1);

                    // ✅ 每秒 (20 tick) 在 Action Bar 显示一次倒计时
                    if (timer % 20 == 0) {
                        int secondsLeft = timer / 20;
                        ChatFormatting color = secondsLeft <= 3 ? ChatFormatting.RED : ChatFormatting.GREEN;

                        player.displayClientMessage(
                                Component.literal("👻隐身剩余: ")
                                        .append(Component.literal(secondsLeft + "s").withStyle(color, ChatFormatting.BOLD)),
                                true // true 表示显示在 Action Bar (物品栏上方) 而不是聊天框
                        );
                    }

                } else {
                    // 时间到，解除隐身
                    cap.setInvisible(false);

                    // 同步给所有人
                    if (player instanceof ServerPlayer sp) {
                        PacketHandler.INSTANCE.send(
                                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sp),
                                new S2CSyncGameData(sp.getId(), cap)
                        );
                    }

                    // 显形提示
                    player.displayClientMessage(Component.literal("隐身失效！").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
        });

    }
}
