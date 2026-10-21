/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.recipe.builder;

import blusunrize.immersiveengineering.api.crafting.builders.IEFinishedRecipe;
import com.igteam.immersivegeology.common.block.multiblocks.recipe.BoilerFuel;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Datagen builder for {@link BoilerFuel} schedules. Steam is the headline number; feed water is
 * always half of it because the mod expands water 2:1 into steam, and the flue gas volume follows
 * the real mass ratio of about 1.5 kg of gas per kg of steam.
 */
public class BoilerFuelBuilder extends IEFinishedRecipe<BoilerFuelBuilder>
{
	private BoilerFuelBuilder(Ingredient input, int burnTime, int steam, int water, int flueGas, float bottomAsh, float efficiency)
	{
		super(BoilerFuel.SERIALIZER.get());
		this.maxResultCount = 0;
		this.addInput(input);
		this.addWriter(obj -> obj.add("input", input.toJson()));
		this.addWriter(obj -> obj.addProperty("burnTime", burnTime));
		this.addWriter(obj -> obj.addProperty("steam", steam));
		this.addWriter(obj -> obj.addProperty("water", water));
		this.addWriter(obj -> obj.addProperty("flue_gas", flueGas));
		this.addWriter(obj -> obj.addProperty("bottom_ash_chance", bottomAsh));
		this.addWriter(obj -> obj.addProperty("efficiency", efficiency));
	}

	public static BoilerFuelBuilder builder(Ingredient input, int burnTime, int steam, int flueGas, float bottomAsh, float efficiency)
	{
		return new BoilerFuelBuilder(input, burnTime, steam, steam / 2, flueGas, bottomAsh, efficiency);
	}
}
