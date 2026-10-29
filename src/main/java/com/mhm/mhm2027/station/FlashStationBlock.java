package com.mhm.mhm2027.station;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Flash Station: where firmware is written into a gateway, and where kernels are
 * installed. Holds a firmware slot and a Data Shard slot, like a furnace holds its items.
 */
public class FlashStationBlock extends BaseEntityBlock {
    public static final MapCodec<FlashStationBlock> CODEC = simpleCodec(FlashStationBlock::new);
    /** How close a player must be to count as at a station: for kernel installs, and to keep a flash going. */
    public static final double REACH = 6.0;

    public FlashStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlashStationBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlocks.FLASH_STATION_ENTITY.get(), FlashStationBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FlashStationBlockEntity station) {
            player.openMenu(station);
        }
        return InteractionResult.SUCCESS;
    }

    /** Whether a Flash Station is within {@link #REACH} of the player. */
    public static boolean inReach(Player player) {
        Level level = player.level();
        int radius = (int) Math.ceil(REACH);
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.FLASH_STATION.get()) && near(player, pos)) {
                return true;
            }
        }
        return false;
    }

    static boolean near(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) <= REACH * REACH;
    }
}
