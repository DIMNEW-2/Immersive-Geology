/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.core.material.data.misc;

import com.igteam.immersivegeology.core.material.data.types.MaterialMisc;
import com.igteam.immersivegeology.core.material.helper.flags.BlockCategoryFlags;
import com.igteam.immersivegeology.core.material.helper.flags.IFlagType;
import com.igteam.immersivegeology.core.material.helper.flags.ItemCategoryFlags;
import com.igteam.immersivegeology.core.material.helper.flags.ModFlags;
import net.minecraftforge.fluids.FluidType.Properties;

import java.util.function.BiFunction;

/**
 * Network hot water (сетевая вода): the product of a cogeneration plant. A ТЭЦ sells heat as well
 * as electricity - extraction steam from the turbine is condensed in the network water heaters and
 * the heated water is pumped out to the district heating network. It doubles as preheated feed
 * water, which is exactly what regenerative feed water heating does in a real plant.
 */
public class MaterialHotWater extends MaterialMisc
{
	public MaterialHotWater()
	{
		super();
		removeMaterialFlags(ItemCategoryFlags.values());
		removeMaterialFlags(ModFlags.values());
		removeMaterialFlags(BlockCategoryFlags.values());
		addFlags(BlockCategoryFlags.FLUID);
	}

	@Override
	protected BiFunction<IFlagType<?>, Integer, Integer> materialColorFunction()
	{
		// Warm amber tint so the heat loop reads at a glance in tanks and pipes
		return (flag, integer) -> (0xffc07038);
	}

	@Override
	public Properties getFluidProperties(IFlagType<?> flag)
	{
		// District heating supply runs around 130-150 C
		return super.getFluidProperties(flag).temperature(420);
	}
}
