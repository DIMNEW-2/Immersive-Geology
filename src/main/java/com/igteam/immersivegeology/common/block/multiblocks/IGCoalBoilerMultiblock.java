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
 * Coal Boiler (coal_boiler), part of the coal CHP chain. The structure template lives in
 * data/immersivegeology/structures/multiblocks/coal_boiler.nbt.
 */
public class IGCoalBoilerMultiblock extends IGTemplateMultiblock
{
	public static final IGCoalBoilerMultiblock INSTANCE = new IGCoalBoilerMultiblock();

	public IGCoalBoilerMultiblock()
	{
		super(new ResourceLocation(IGLib.MODID, "multiblocks/coal_boiler"),
				new BlockPos(2, 1, 2),
				new BlockPos(2, 1, 4),
				new BlockPos(5, 7, 5),
				IGMultiblockProvider.COAL_BOILER);
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
		return 9;
	}

	@Override
	public void initializeClient(Consumer<ClientMultiblocks.MultiblockManualData> consumer)
	{
		consumer.accept(new IGClientMultiblockProperties(this, 2.5, 0.5, 2.5));
	}

	@Override
	public String getName()
	{
		return "Coal Boiler";
	}
}
