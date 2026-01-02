package com.mohuia.block_hide_seek.client;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 客户端钩子类 (Client Hooks)
 * 作用：专门处理那些 "只能在客户端运行" 的逻辑。
 * 为什么需要它？
 * 因为网络包的处理逻辑 (PacketHandler) 是通用的，但打开 GUI 或获取 Minecraft 实例是客户端独有的。
 * 直接在包处理类里写 Minecraft.getInstance() 会导致服务端崩溃。
 * 注意：此类所有方法都必须在【客户端主线程】(Render Thread) 调用。
 */
@OnlyIn(Dist.CLIENT) // ⚠️ 关键注解：告诉模组加载器，这个类只在客户端存在，服务端不存在。
public class ClientHooks {

    /**
     * 打开方块选择界面 (SelectScreen)
     * 触发时机：游戏开始时，服务端通过 S2COpenSelectScreen 包通知躲藏者。
     * @param options 服务端随机生成的、供玩家选择的方块列表
     */
    public static void openGui(List<BlockState> options) {
        // Minecraft.getInstance() 获取客户端单例
        // setScreen() 切换当前显示的 GUI 界面
        Minecraft.getInstance().setScreen(new SelectScreen(options));
    }

    /**
     * 打开白名单配置界面 (ConfigScreen)
     * 触发时机：管理员手持特定物品右键，或输入指令时。
     *
     * @param currentList 当前服务端已有的白名单列表（用于在界面上回显）
     */
    public static void openConfigGui(List<BlockState> currentList) {
        Minecraft.getInstance().setScreen(new ConfigScreen(currentList));
    }

    /**
     * 处理数据同步 (Handle Sync)
     * 作用：当客户端收到服务端发来的 S2CSyncGameData 包时调用。
     * 它负责更新【客户端世界】中那个玩家实体的 Capability 数据。
     * 为什么重要？
     * 服务端知道你是方块，但客户端不知道。如果客户端不更新这个数据，
     * 渲染器 (Renderer) 就不知道该画方块，玩家看到的还是普通史蒂夫。
     *
     * @param entityId 发生变化的实体ID (可能是自己，也可能是别的玩家)
     * @param isSeeker 是否是抓捕者
     * @param disguise 伪装的方块状态 (如果是抓捕者则为 null)
     */
    public static void handleSync(int entityId, boolean isSeeker, BlockState disguise) {
        // 1. 安全检查：如果客户端世界还没加载完 (比如还在登录画面)，直接跳过
        if (Minecraft.getInstance().level == null) return;

        // 2. 根据 ID 在客户端世界找到对应的实体
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);

        // 3. 只有当实体是玩家时才处理 (因为只有玩家有 GameData Capability)
        if (entity instanceof Player player) {
            // 4. 获取 Capability 并更新数据
            player.getCapability(GameDataProvider.CAP).ifPresent(cap -> {
                cap.setSeeker(isSeeker);
                cap.setDisguise(disguise);

                // ⚠️ 注意：基于我们之前的讨论，这里其实还应该同步 modelWidth, modelHeight, aabbX...
                // 如果你的 S2CSyncGameData 包里传了这些参数，记得在这里也 cap.set... 进去。

                // 5. 关键调用：刷新尺寸
                // 这会告诉客户端："嘿，这个实体的个头变了！"
                // 客户端会重新计算碰撞箱渲染、名字条高度、阴影大小等。
                player.refreshDimensions();
            });
        }
    }
}
