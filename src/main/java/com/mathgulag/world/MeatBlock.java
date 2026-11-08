package com.mathgulag.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Carne viva: suelta esporas rojas de vez en cuando. */
public class MeatBlock extends Block {
    public MeatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(26) == 0) {
            double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
            double y = pos.getY() + 0.2 + random.nextDouble() * 0.7;
            double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
            level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y, z, 0.0, 0.008, 0.0);
        }
    }
}
