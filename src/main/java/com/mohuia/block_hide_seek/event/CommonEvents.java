package com.mohuia.block_hide_seek.event;

import com.mohuia.block_hide_seek.BlockHideSeek;
import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.game.GameLoopManager;
import com.mohuia.block_hide_seek.item.Vanish;
import com.mohuia.block_hide_seek.network.PacketHandler;
import com.mohuia.block_hide_seek.packet.S2C.S2CSyncGameData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    // 3. 玩家 Tick 处理 (物理修正 + 隐身消耗逻辑)
    // ==========================================

    /**
     * 1. 解决 "变小后无法钻洞" 的问题。
     * 2. 处理隐身道具的耐久消耗逻辑。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 1. 基础检查：必须是服务端，必须是 Tick 结束阶段
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        if (event.player instanceof ServerPlayer player) {
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {

                // --- A. 物理碰撞箱修正 ---
                if (!cap.isSeeker() && cap.getDisguise() != null) {
                    // 获取 "当前实际生效的碰撞箱高度"
                    float actualHeight = player.getBbHeight();
                    // 获取 "根据姿态和伪装应该有的理论高度"
                    float expectedHeight = player.getDimensions(player.getPose()).height;

                    // 误差检测：如果误差超过 1cm，说明碰撞箱滞后了
                    if (Math.abs(expectedHeight - actualHeight) > 0.01f) {
                        player.refreshDimensions();
                    }
                }

                // --- B. ✅ 新增：隐身耐久消耗逻辑 ---
                if (cap.isInvisible()) {
                    ItemStack mainHandItem = player.getMainHandItem();
                    ItemStack offHandItem = player.getOffhandItem();

                    ItemStack vanishStack = null;

                    // 检查主手或副手是否持有 Vanish 道具
                    if (mainHandItem.getItem() instanceof Vanish) {
                        vanishStack = mainHandItem;
                    } else if (offHandItem.getItem() instanceof Vanish) {
                        vanishStack = offHandItem;
                    }

                    // 情况 1: 玩家没有手持隐身道具 -> 强制解除隐身
                    if (vanishStack == null) {
                        disableInvisibility(player, cap, "❌ 手持物品切换，隐身失效！");
                        return;
                    }

                    // 情况 2: 玩家手持道具 -> 扣除耐久
                    // (不再持续播放烟雾)

                    // 扣除 1点耐久
                    // hurt方法参数：(伤害值, 随机源, 玩家)
                    boolean broken = vanishStack.hurt(1, player.getRandom(), player);

                    // 确保物品 NBT 状态是 active (为了让它发光)
                    if (!vanishStack.getOrCreateTag().getBoolean("isActive")) {
                        vanishStack.getOrCreateTag().putBoolean("isActive", true);
                    }

                    if (broken) {
                        // 物品碎了 -> 销毁物品并解除隐身
                        vanishStack.shrink(1);
                        player.playSound(net.minecraft.sounds.SoundEvents.ITEM_BREAK, 1.0f, 1.0f);
                        disableInvisibility(player, cap, "能量耗尽，隐身结束！");
                    }
                }
            });
        }
    }

    /**
     * 辅助方法：统一处理关闭隐身
     */
    private static void disableInvisibility(ServerPlayer player, com.mohuia.block_hide_seek.data.IGameData cap, String message) {
        // 1. 修改数据
        cap.setInvisible(false);

        // 2. 发送消息
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);

        // 3. 同步数据包
        PacketHandler.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new S2CSyncGameData(player.getId(), cap)
        );

        // 4. 确保手上的物品不再发光 (如果有的话)
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof Vanish) {
            stack.getOrCreateTag().putBoolean("isActive", false);
        }
    }
}
