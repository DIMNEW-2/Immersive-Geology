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
 * Steam Condenser (steam_condenser), part of the coal CHP chain. The structure template lives in
 * data/immersivegeology/structures/multiblocks/steam_condenser.nbt.
 */
public class IGSteamCondenserMultiblock extends IGTemplateMultiblock
{
	public static final IGSteamCondenserMultiblock INSTANCE = new IGSteamCondenserMultiblock();

	public IGSteamCondenserMultiblock()
	{
		super(new ResourceLocation(IGLib.MODID, "multiblocks/steam_condenser"),
				new BlockPos(1, 0, 1),
				new BlockPos(1, 1, 3),
				new BlockPos(4, 3, 4),
				IGMultiblockProvider.STEAM_CONDENSER);
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
		consumer.accept(new IGClientMultiblockProperties(this, 2.0, 0.5, 2.0));
	}

	@Override
	public String getName()
	{
		return "Steam Condenser";
	}
}
