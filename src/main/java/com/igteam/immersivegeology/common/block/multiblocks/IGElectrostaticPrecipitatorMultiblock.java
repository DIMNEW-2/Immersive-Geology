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
 * Electrostatic Precipitator (electrostatic_precipitator), part of the coal CHP chain. The structure template lives in
 * data/immersivegeology/structures/multiblocks/electrostatic_precipitator.nbt.
 */
public class IGElectrostaticPrecipitatorMultiblock extends IGTemplateMultiblock
{
	public static final IGElectrostaticPrecipitatorMultiblock INSTANCE = new IGElectrostaticPrecipitatorMultiblock();

	public IGElectrostaticPrecipitatorMultiblock()
	{
		super(new ResourceLocation(IGLib.MODID, "multiblocks/electrostatic_precipitator"),
				new BlockPos(2, 0, 2),
				new BlockPos(2, 1, 3),
				new BlockPos(5, 9, 4),
				IGMultiblockProvider.ELECTROSTATIC_PRECIPITATOR);
	}

	@Override
	public boolean canFormWithDefaultHammer()
	{
		return true;
	}

	@Override
	public float getManualScale()
	{
		return 9;
	}

	@Override
	public void initializeClient(Consumer<ClientMultiblocks.MultiblockManualData> consumer)
	{
		consumer.accept(new IGClientMultiblockProperties(this, 2.5, 0.5, 2.0));
	}

	@Override
	public String getName()
	{
		return "Electrostatic Precipitator";
	}
}
