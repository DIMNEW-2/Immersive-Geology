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

import java.util.function.BiFunction;

/**
 * Fly ash (зола-унос): the fine mineral fraction that leaves a pulverised coal furnace suspended
 * in the flue gas. Real plants recover 99%+ of it in electrostatic precipitators or bag houses;
 * it is a pozzolan, so once collected it is a valuable concrete additive rather than waste.
 */
public class MaterialFlyAsh extends MaterialMisc
{
	public MaterialFlyAsh()
	{
		super();
		removeMaterialFlags(ItemCategoryFlags.values());
		removeMaterialFlags(ModFlags.values());
		removeMaterialFlags(BlockCategoryFlags.values());
		// POWDER is the fly ash (зола-унос) recovered from the flue gas, GRIT the coarse bottom ash
		// (шлак) raked out of the furnace hopper.
		addFlags(ItemCategoryFlags.POWDER, ItemCategoryFlags.GRIT);
	}

	@Override
	protected BiFunction<IFlagType<?>, Integer, Integer> materialColorFunction()
	{
		// A light, slightly warm grey - class F fly ash
		return (flag, integer) -> (0xffa6a39c);
	}
}
