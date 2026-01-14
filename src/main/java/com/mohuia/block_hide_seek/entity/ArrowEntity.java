package com.mohuia.block_hide_seek.entity;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.game.GameCombatManager;
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
        super(EntityInit.SEEKER_ARROW.get(), pShooter, pLevel);
        this.setBaseDamage(0);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.inGround && this.getOwner() instanceof ServerPlayer shooter) {
            checkObbCollision(shooter);
        }

        super.tick();

        if (this.level().isClientSide && !this.inGround) {
            this.level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    private void checkObbCollision(ServerPlayer shooter) {
        Vec3 currentPos = this.position();
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();

        if (speed < 0.001) return;

        Vec3 dir = velocity.normalize();

        // 扫描附近玩家
        for (ServerPlayer target : this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().expandTowards(velocity).inflate(2.0))) {
            if (target == shooter || target.isSpectator()) continue;

            boolean isHider = target.getCapability(GameDataProvider.CAP)
                    .map(cap -> !cap.isSeeker()).orElse(false);

            if (!isHider) continue;

            Optional<VirtualOBB> obbOpt = ObbUtil.getPlayerObb(target);
            if (obbOpt.isEmpty()) continue;

            double hitDist = ObbRaycast.hitDistance(currentPos, dir, speed, obbOpt.get());

            if (hitDist >= 0 && hitDist <= speed) {
                onHitHiderLogic(shooter, target);
                this.discard();
                return;
            }
        }

        // 扫描附近的替身 (Decoy)
        for (DecoyEntity decoy : this.level().getEntitiesOfClass(DecoyEntity.class, this.getBoundingBox().expandTowards(velocity).inflate(2.0))) {
            Optional<VirtualOBB> obbOpt = ObbUtil.getDecoyObb(decoy);
            if (obbOpt.isEmpty()) continue;

            double hitDist = ObbRaycast.hitDistance(currentPos, dir, speed, obbOpt.get());
            if (hitDist >= 0 && hitDist <= speed) {
                decoy.discard();
                this.level().broadcastEntityEvent(decoy, (byte) 60);
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
        GameCombatManager.catchHiderImmediately(shooter, target);
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
