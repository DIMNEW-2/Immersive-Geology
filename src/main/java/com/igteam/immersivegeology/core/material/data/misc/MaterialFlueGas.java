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
import com.igteam.immersivegeology.core.material.helper.flags.MaterialFlags;
import com.igteam.immersivegeology.core.material.helper.flags.ModFlags;
import net.minecraftforge.fluids.FluidType.Properties;

import java.util.function.BiFunction;

/**
 * Flue gas (дымовые газы): the combustion products that leave the boiler furnace. It carries the
 * sensible heat of the fire plus the fly ash fraction, so it has to be cleaned in an electrostatic
 * precipitator before it may leave the stack.
 */
public class MaterialFlueGas extends MaterialMisc
{
	public MaterialFlueGas()
	{
		super();
		removeMaterialFlags(ItemCategoryFlags.values());
		removeMaterialFlags(ModFlags.values());
		removeMaterialFlags(BlockCategoryFlags.values());
		addFlags(BlockCategoryFlags.FLUID);
		addFlags(MaterialFlags.IS_GAS);
	}

	@Override
	protected BiFunction<IFlagType<?>, Integer, Integer> materialColorFunction()
	{
		// Sooty grey-brown
		return (flag, integer) -> (0xff4d4743);
	}

	@Override
	public Properties getFluidProperties(IFlagType<?> flag)
	{
		// Flue gas leaves the convective pass at roughly 150 C once the air preheater took its share
		return super.getFluidProperties(flag).temperature(430);
	}
}
