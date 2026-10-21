/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks;

import blusunrize.immersiveengineering.api.multiblocks.ClientMultiblocks;
import com.igteam.immersivegeology.core.lib.IGLib;
import com.igteam.immersivegeology.core.registration.IGMultiblockProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Cooling Tower (cooling_tower), part of the coal CHP chain. The structure template lives in
 * data/immersivegeology/structures/multiblocks/cooling_tower.nbt.
 */
public class IGCoolingTowerMultiblock extends IGTemplateMultiblock
{
	public static final IGCoolingTowerMultiblock INSTANCE = new IGCoolingTowerMultiblock();

	public IGCoolingTowerMultiblock()
	{
		super(new ResourceLocation(IGLib.MODID, "multiblocks/cooling_tower"),
				new BlockPos(3, 0, 3),
				new BlockPos(3, 1, 6),
				new BlockPos(7, 9, 7),
				IGMultiblockProvider.COOLING_TOWER);
	}

	@Override
	public boolean canFormWithDefaultHammer()
	{
		return true;
	}

	@Override
	public boolean canBeMirrored()
	{
		return false;
	}

	@Override
	public float getManualScale()
	{
		return 8;
	}

	@Override
	public void initializeClient(Consumer<ClientMultiblocks.MultiblockManualData> consumer)
	{
		consumer.accept(new IGClientMultiblockProperties(this, 3.5, 0.5, 3.5));
	}

	@Override
	public String getName()
	{
		return "Cooling Tower";
	}
}
