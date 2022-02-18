package com.mystic.mazegenlib;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mystic.mazegenlib.mazes.MazeBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;

public class MazeGenLib implements ModInitializer {

	@Override
	public void onInitialize() {
		registerCommand();

	}
	public void registerCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(CommandManager.literal("maze").requires(source -> source.hasPermissionLevel(4)).then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
					.executes(ctx -> execute(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos")))));
		});
	}

	private int execute(CommandContext<ServerCommandSource> ctx, BlockPos pos) throws CommandSyntaxException {
		final PlayerEntity self = ctx.getSource().getPlayer();
		self.getWorld().updateNeighbors(pos, self.getWorld().getBlockState(pos).getBlock());
		MazeBuilder.generate(self.getWorld(), pos.getX(), pos.getY(), pos.getZ(), self.getRandom());
		return 1;
	}
}
