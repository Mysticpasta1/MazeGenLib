package com.mystic.mazegenlib.core.math;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Provides an operation for damaging structures based on a spherical area. The chance of damage decreases
 * with the square of the distance from the center of the sphere.
 * @author SenseiKiwi
 *
 */
public class SphereDecayOperation extends WorldOperation
{
    private Random random;
    private double scaling;
    private double centerX;
    private double centerY;
    private double centerZ;
    private BlockState primaryBlockState;
    private int primaryMetadata;
    private BlockState secondaryBlockState;
    private int secondaryMetadata;

    public SphereDecayOperation(Random random, BlockState primaryBlockState, int primaryMetadata, BlockState secondaryBlockState, int secondaryMetadata)
    {
        super("SphereDecayOperation");
        this.random = random;
        this.primaryBlockState = primaryBlockState;
        this.primaryMetadata = primaryMetadata;
        this.secondaryBlockState = secondaryBlockState;
        this.secondaryMetadata = secondaryMetadata;
    }

    @Override
    protected boolean initialize(World world, int x, int y, int z, int width, int height, int length)
    {
        // Calculate a scaling factor so that the probability of decay
        // at the edge of the largest dimension of our bounds is 20%.
        scaling = Math.max(width - 1, Math.max(height - 1, length - 1)) / 2.0;
        scaling *= scaling * 0.20;

        centerX = x + width / 2.0;
        centerY = y + height / 2.0;
        centerZ = z + length / 2.0;
        return true;
    }

    @Override
    protected boolean applyToBlock(World world, int x, int y, int z)
    {
        // Don't raise any notifications. This operation is only designed to run
        // when a dimension is being generated, which means there are no players around.
        if (!world.isAir(new BlockPos(x, y, z)))
        {
            double dx = (centerX - x - 0.5);
            double dy = (centerY - y - 0.5);
            double dz = (centerZ - z - 0.5);
            double squareDistance = dx * dx + dy * dy + dz * dz;

            if (squareDistance < 0.5 || random.nextDouble() < scaling / squareDistance)
            {
                world.setBlockState(new BlockPos(x, y, z), primaryBlockState, primaryMetadata, 1);
            }
            else if (random.nextDouble() < scaling / squareDistance)
            {
                world.setBlockState(new BlockPos(x, y, z), secondaryBlockState, secondaryMetadata, 1);
            }
        }
        return true;
    }
}
