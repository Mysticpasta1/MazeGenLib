package com.mystic.mazegenlib.mazes;

import com.mystic.mazegenlib.core.interfaces.IEdge;
import com.mystic.mazegenlib.core.interfaces.IGraphNode;
import com.mystic.mazegenlib.core.math.BoundingBox;
import com.mystic.mazegenlib.core.math.DirectedGraph;
import com.mystic.mazegenlib.core.math.Point3D;
import com.mystic.mazegenlib.core.math.SphereDecayOperation;
import com.mystic.mazegenlib.core.roomstuff.DoorwayData;
import com.mystic.mazegenlib.core.roomstuff.PartitionNode;
import com.mystic.mazegenlib.core.roomstuff.RoomData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;

public class MazeBuilder
{
    private static final int POCKET_WALL_GAP = 4;
    private static final int DECORATION_CHANCE = 1;
    private static final int MAX_DECORATION_CHANCE = 3;

    public static void generate(World world, int x, int y, int z, Random random)
    {
        MazeDesign design = MazeDesigner.generate(random);
        Point3D offset = new Point3D(x - design.width() / 2, y - design.height() - 1, z - design.length() / 2);
        SphereDecayOperation decay = new SphereDecayOperation(random, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD, Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);

        buildRooms(design.getLayout(), world, offset);
        carveDoorways(design.getLayout(), world, offset, decay, random);
        applyRandomDestruction(design, world, offset, decay, random);
    }

    private static void applyRandomDestruction(MazeDesign design, World world,
                                               Point3D offset, SphereDecayOperation decay, Random random)
    {
        final int DECAY_BOX_SIZE = 7;
        final int DECAY_OPERATIONS = 5 + random.nextInt(5);
        final int DECAY_ATTEMPTS = 20;

        int x, y, z;
        int successes = 0;
        int attempts = 0;
        PartitionNode root = design.getRootPartition();

        for (; successes < DECAY_OPERATIONS && attempts < DECAY_ATTEMPTS; attempts++)
        {
            // Select the coordinates at which to apply the decay operation
            x = random.nextInt(design.width()) - DECAY_BOX_SIZE / 2;
            y = random.nextInt(design.height()) - DECAY_BOX_SIZE / 2;
            z = random.nextInt(design.length()) - DECAY_BOX_SIZE / 2;

            // Check that the decay operation would not impact any protected areas
            // and mark the affected areas as decayed
            if (markDecayArea(x, y, z, DECAY_BOX_SIZE, root))
            {
                // Apply decay
                decay.apply(world, offset.getX() + x, offset.getY() + y, offset.getZ() + z,
                        DECAY_BOX_SIZE, DECAY_BOX_SIZE, DECAY_BOX_SIZE);
                successes++;
            }
        }
    }

    private static boolean markDecayArea(int x, int y, int z, int DECAY_BOX_SIZE, PartitionNode<RoomData> root)
    {
        // Check if a given PartitionNode intersects the decay area. If it's a leaf, then check
        // if it's protected or not. Otherwise, check its children. The specific area is valid
        // if and only if there are no protected rooms and at least one (unprotected) room in it.
        // Also list the unprotected rooms to mark them if the decay operation will proceed.

        RoomData room;
        PartitionNode<RoomData> partition;
        ArrayList<RoomData> targets = new ArrayList<RoomData>();
        Stack<PartitionNode<RoomData>> nodes = new Stack<PartitionNode<RoomData>>();
        BoundingBox decayBounds = new BoundingBox(x, y, z, DECAY_BOX_SIZE, DECAY_BOX_SIZE, DECAY_BOX_SIZE);

        // Use depth-first search to explore all intersecting partitions
        nodes.push(root);
        while (!nodes.isEmpty())
        {
            partition = nodes.pop();
            if (decayBounds.intersects(partition))
            {
                if (partition.isLeaf())
                {
                    room = partition.getData();
                    if (room.isProtected())
                        return false;
                    targets.add(room);
                }
                else
                {
                    if (partition.leftChild() != null)
                        nodes.push(partition.leftChild());
                    if (partition.rightChild() != null)
                        nodes.push(partition.rightChild());
                }
            }
        }
        // If execution has reached this point, then there were no protected rooms.
        // Mark all intersecting rooms as decayed.
        for (RoomData target : targets)
        {
            target.setDecayed(true);
        }
        return !targets.isEmpty();
    }

    private static void buildRooms(DirectedGraph<RoomData, DoorwayData> layout, World world, Point3D offset)
    {
        for (IGraphNode<RoomData, DoorwayData> node : layout.nodes())
        {
            PartitionNode room = node.data().getPartitionNode();
            buildBox(world, offset, room.minCorner(), room.maxCorner(), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        }
    }

    private static void carveDoorways(DirectedGraph<RoomData, DoorwayData> layout, World world,
                                      Point3D offset, SphereDecayOperation decay, Random random)
    {
        char axis;
        Point3D lower;
        DoorwayData doorway;

        for (IGraphNode<RoomData, DoorwayData> node : layout.nodes())
        {
            for (IEdge<RoomData, DoorwayData> passage : node.outbound())
            {
                // Carve out the passage
                doorway = passage.data();
                axis = doorway.axis();
                lower = doorway.minCorner();
                carveDoorway(world, axis, offset.getX() + lower.getX(), offset.getY() + lower.getY(),
                        offset.getZ() + lower.getZ(), doorway.width(), doorway.height(), doorway.length(),
                        decay, random);

                // If this is a vertical passage, then mark the upper room as decayed
                if (axis == DoorwayData.Y_AXIS)
                {
                    passage.tail().data().setDecayed(true);
                }
            }
        }
    }

    private static void carveDoorway(World world, char axis, int x, int y, int z, int width, int height,
                                     int length, SphereDecayOperation decay, Random random)
    {
        final int MIN_DOUBLE_DOOR_SPAN = 10;

        int gap;
        int rx;
        int rz;
        switch (axis)
        {
            case DoorwayData.X_AXIS:
                if (length >= MIN_DOUBLE_DOOR_SPAN)
                {
                    gap = (length - 2) / 3;
                    carveDoorAlongX(world, x, y + 1, z + gap);
                    carveDoorAlongX(world, x, y + 1, z + length - gap - 1);
                }
                else if (length > 3)
                {
                    switch (random.nextInt(3))
                    {
                        case 0:
                            carveDoorAlongX(world, x, y + 1, z + (length - 1) / 2);
                            break;
                        case 1:
                            carveDoorAlongX(world, x, y + 1, z + 2);
                            break;
                        case 2:
                            carveDoorAlongX(world, x, y + 1, z + length - 3);
                            break;
                    }
                }
                else
                {
                    carveDoorAlongX(world, x, y + 1, z + 1);
                }
                break;
            case DoorwayData.Z_AXIS:
                if (width >= MIN_DOUBLE_DOOR_SPAN)
                {
                    gap = (width - 2) / 3;
                    carveDoorAlongZ(world, x + gap, y + 1, z);
                    carveDoorAlongZ(world, x + width - gap - 1, y + 1, z);
                }
                else if (length > 3)
                {
                    switch (random.nextInt(3))
                    {
                        case 0:
                            carveDoorAlongZ(world, x + (width - 1) / 2, y + 1, z);
                            break;
                        case 1:
                            carveDoorAlongZ(world, x + 2, y + 1, z);
                            break;
                        case 2:
                            carveDoorAlongZ(world, x + width - 3, y + 1, z);
                            break;
                    }
                }
                else
                {
                    carveDoorAlongZ(world, x + 1, y + 1, z);
                }
                break;
            case DoorwayData.Y_AXIS:
                gap = Math.min(width, length) - 2;
                if (gap > 1)
                {
                    if (gap > 6)
                    {
                        gap = 6;
                    }
                    rx = x + random.nextInt(width - gap - 1) + 1;
                    rz = z + random.nextInt(length - gap - 1) + 1;
                    carveHole(world, rx + gap / 2, y, rz + gap / 2);
                    decay.apply(world, rx, y - 1, rz, gap, 4, gap);
                }
                else
                {
                    carveHole(world, x + 1, y, z + 1);
                }
                break;
        }
    }

    private static void carveDoorAlongX(World world, int x, int y, int z)
    {
        setBlockDirectly(world, x, y, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x, y + 1, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x + 1, y, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x + 1, y + 1, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
    }

    private static void carveDoorAlongZ(World world, int x, int y, int z)
    {
        setBlockDirectly(world, x, y, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x, y + 1, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x, y, z + 1, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x, y + 1, z + 1, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
    }

    private static void carveHole(World world, int x, int y, int z)
    {
        setBlockDirectly(world, x, y, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        setBlockDirectly(world, x, y + 1, z, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
    }

    private static void buildBox(World world, Point3D offset, Point3D minCorner, Point3D maxCorner, BlockState blockState, int metadata)
    {
        int minX = minCorner.getX() + offset.getX();
        int minY = minCorner.getY() + offset.getY();
        int minZ = minCorner.getZ() + offset.getZ();

        int maxX = maxCorner.getX() + offset.getX();
        int maxY = maxCorner.getY() + offset.getY();
        int maxZ = maxCorner.getZ() + offset.getZ();

        int x, y, z;

        for (x = minX; x <= maxX; x++)
        {
            for (z = minZ; z <= maxZ; z++)
            {
                setBlockDirectly(world, x, minY, z, blockState, metadata);
                setBlockDirectly(world, x, maxY, z, blockState, metadata);
            }
        }
        for (x = minX; x <= maxX; x++)
        {
            for (y = minY + 1; y < maxY; y++)
            {
                setBlockDirectly(world, x, y, minZ, blockState, metadata);
                setBlockDirectly(world, x, y, maxZ, blockState, metadata);
            }
        }
        for (z = minZ + 1; z < maxZ; z++)
        {
            for (y = minY + 1; y < maxY; y++)
            {
                setBlockDirectly(world, minX, y, z, blockState, metadata);
                setBlockDirectly(world, maxX, y, z, blockState, metadata);
            }
        }
    }

    private static void setBlockDirectly(World world, int x, int y, int z, BlockState blockState, int metadata)
    {
        if (blockState != Blocks.AIR.getDefaultState() && blockState == null)
        {
            return;
        }

        world.setBlockState(new BlockPos(x, y, z), blockState, metadata);
    }
}