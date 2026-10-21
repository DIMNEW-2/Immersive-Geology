/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulverised coal boiler: every cell of the structure is solid to the player.
 */
public class CoalBoilerShape extends GenericShape
{
	public static final CoalBoilerShape GETTER = new CoalBoilerShape();

	private CoalBoilerShape()
	{
	}

	@NotNull
	@Override
	protected List<AABB> getShape(BlockPos posInMultiblock)
	{
		List<AABB> shape = new ArrayList<>();
		shape.add(new AABB(0, 0, 0, 1, 1, 1));
		return shape;
	}
}
