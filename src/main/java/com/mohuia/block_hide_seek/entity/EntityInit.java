package com.mohuia.block_hide_seek.entity;

import com.mohuia.block_hide_seek.BlockHideSeek;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityInit {
    // 创建实体注册器
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BlockHideSeek.MODID);

    // 注册 "decoy" 实体
    public static final RegistryObject<EntityType<DecoyEntity>> DECOY_ENTITY = ENTITIES.register("decoy",
            () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f) // 碰撞箱大小：宽1.0，高1.0
                    .clientTrackingRange(64) // 渲染距离
                    .updateInterval(2) // 更新频率
                    .build(BlockHideSeek.MODID + ":decoy"));

    //注册神弓
    public static final RegistryObject<EntityType<ArrowEntity>> SEEKER_ARROW = ENTITIES.register("seeker_arrow",
            () -> EntityType.Builder.<ArrowEntity>of(ArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(BlockHideSeek.MODID + ":seeker_arrow"));
}
