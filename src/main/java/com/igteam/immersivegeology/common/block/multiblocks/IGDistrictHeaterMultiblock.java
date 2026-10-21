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
 * District Heater (district_heater), part of the coal CHP chain. The structure template lives in
 * data/immersivegeology/structures/multiblocks/district_heater.nbt.
 */
public class IGDistrictHeaterMultiblock extends IGTemplateMultiblock
{
	public static final IGDistrictHeaterMultiblock INSTANCE = new IGDistrictHeaterMultiblock();

	public IGDistrictHeaterMultiblock()
	{
		super(new ResourceLocation(IGLib.MODID, "multiblocks/district_heater"),
				new BlockPos(1, 1, 1),
				new BlockPos(1, 0, 1),
				new BlockPos(3, 4, 3),
				IGMultiblockProvider.DISTRICT_HEATER);
	}

	@Override
	public boolean canFormWithDefaultHammer()
	{
		return true;
	}

	@Override
	public float getManualScale()
	{
		return 16;
	}

	@Override
	public void initializeClient(Consumer<ClientMultiblocks.MultiblockManualData> consumer)
	{
		consumer.accept(new IGClientMultiblockProperties(this, 1.5, 0.5, 1.5));
	}

	@Override
	public String getName()
	{
		return "District Heater";
	}
}
