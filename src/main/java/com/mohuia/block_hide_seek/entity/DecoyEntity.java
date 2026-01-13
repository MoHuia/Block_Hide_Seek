package com.mohuia.block_hide_seek.entity;

import com.mohuia.block_hide_seek.data.GameDataProvider;
import com.mohuia.block_hide_seek.data.IGameData; // ✅ 1. 导入 IGameData 接口
// import com.mohuia.block_hide_seek.entity.EntityInit;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class DecoyEntity extends Entity {

    private static final EntityDataAccessor<BlockState> DISGUISE_BLOCK =
            SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.BLOCK_STATE);

    public DecoyEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public DecoyEntity(Level level, double x, double y, double z, BlockState state, float yaw) {
        // 这里假设 EntityInit.DECOY_ENTITY 存在
        this(EntityInit.DECOY_ENTITY.get(), level);
        this.setPos(x, y, z);
        this.setRot(yaw, 0);
        this.setDisguiseBlock(state);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DISGUISE_BLOCK, Blocks.STONE.defaultBlockState());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        BlockState state = getDisguiseBlock();
        if (state == null || state.isAir()) {
            return super.getDimensions(pose);
        }

        VoxelShape shape = state.getShape(this.level(), this.blockPosition());
        if (shape.isEmpty()) {
            return super.getDimensions(pose);
        }

        double xSize = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double ySize = shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y);
        double zSize = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);

        float width = (float) Math.max(xSize, zSize);
        float height = (float) ySize;

        width = Math.max(0.2f, width);
        height = Math.max(0.2f, height);

        return EntityDimensions.fixed(width, height);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DISGUISE_BLOCK.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    public void setDisguiseBlock(BlockState state) {
        this.entityData.set(DISGUISE_BLOCK, state);
        this.refreshDimensions();
    }

    public BlockState getDisguiseBlock() {
        return this.entityData.get(DISGUISE_BLOCK);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;

        if (source.getEntity() instanceof ServerPlayer attacker) {

            boolean isSeeker = attacker.getCapability(GameDataProvider.CAP)
                    .map(cap -> cap.isSeeker()) // 这样写最稳妥，不需要知道 IGameData 在哪里定义的静态方法
                    .orElse(false);

            if (isSeeker) {
                triggerDecoyEffect();
                this.discard();
                return true;
            } else {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                this.discard();
                return true;
            }
        }
        return false;
    }

    private void triggerDecoyEffect() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, getDisguiseBlock()),
                    this.getX(), this.getY() + 0.5, this.getZ(),
                    20, 0.3, 0.3, 0.3, 0.1
            );
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("DisguiseBlock")) {
            BlockState state = NbtUtils.readBlockState(this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("DisguiseBlock"));
            this.setDisguiseBlock(state);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("DisguiseBlock", NbtUtils.writeBlockState(getDisguiseBlock()));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
