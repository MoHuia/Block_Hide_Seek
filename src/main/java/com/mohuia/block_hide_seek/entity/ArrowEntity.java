package com.mohuia.block_hide_seek.entity;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.game.GameLoopManager;
import com.mohuia.block_hide_seek.hitbox.ObbRaycast;
import com.mohuia.block_hide_seek.hitbox.ObbUtil;
import com.mohuia.block_hide_seek.hitbox.VirtualOBB;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;

public class ArrowEntity extends AbstractArrow {

    public ArrowEntity(EntityType<? extends AbstractArrow> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public ArrowEntity(Level pLevel, LivingEntity pShooter) {
        // 使用 EntityInit 中注册的类型
        super(EntityInit.SEEKER_ARROW.get(), pShooter, pLevel);
        this.setBaseDamage(0); // 逻辑秒杀，无需物理伤害
    }

    @Override
    public void tick() {
        // 在服务端进行 OBB 碰撞检测
        if (!this.level().isClientSide && !this.inGround && this.getOwner() instanceof ServerPlayer shooter) {
            checkObbCollision(shooter);
        }

        super.tick();

        // 客户端特效：飞行轨迹粒子
        if (this.level().isClientSide && !this.inGround) {
            this.level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    /**
     * 手动进行 OBB 射线检测
     */
    private void checkObbCollision(ServerPlayer shooter) {
        Vec3 currentPos = this.position();
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();

        if (speed < 0.001) return;

        Vec3 dir = velocity.normalize();

        // 扫描附近玩家
        for (ServerPlayer target : this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().expandTowards(velocity).inflate(2.0))) {
            if (target == shooter || target.isSpectator()) continue;

            // 检查目标是否是躲藏者
            boolean isHider = target.getCapability(GameDataProvider.CAP)
                    .map(cap -> !cap.isSeeker()).orElse(false);

            if (!isHider) continue;

            // 获取 OBB
            Optional<VirtualOBB> obbOpt = ObbUtil.getPlayerObb(target);
            if (obbOpt.isEmpty()) continue;

            // 射线检测
            double hitDist = ObbRaycast.hitDistance(currentPos, dir, speed, obbOpt.get());

            if (hitDist >= 0 && hitDist <= speed) {
                // 命中逻辑
                onHitHiderLogic(shooter, target);
                this.discard();
                return;
            }
        }

        // 扫描附近的替身 (Decoy) - 如果你也想让弓能打碎替身的话
        for (DecoyEntity decoy : this.level().getEntitiesOfClass(DecoyEntity.class, this.getBoundingBox().expandTowards(velocity).inflate(2.0))) {
            Optional<VirtualOBB> obbOpt = ObbUtil.getDecoyObb(decoy);
            if (obbOpt.isEmpty()) continue;

            double hitDist = ObbRaycast.hitDistance(currentPos, dir, speed, obbOpt.get());
            if (hitDist >= 0 && hitDist <= speed) {
                // 复用 GameLoopManager 里的逻辑不太方便，这里手动写一下销毁逻辑
                // 或者你在 GameLoopManager 暴露一个 public static void breakDecoy(DecoyEntity decoy, ServerPlayer attacker)
                decoy.discard();
                this.level().broadcastEntityEvent(decoy, (byte) 60); // 播放粒子等
                this.discard();
                return;
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult pResult) {
        if (pResult.getEntity() instanceof ServerPlayer target
                && this.getOwner() instanceof ServerPlayer shooter) {
            onHitHiderLogic(shooter, target);
        }
        super.onHitEntity(pResult);
    }

    private void onHitHiderLogic(ServerPlayer shooter, ServerPlayer target) {
        // 调用 GameLoopManager 的秒杀逻辑
        GameLoopManager.catchHiderImmediately(shooter, target);
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
