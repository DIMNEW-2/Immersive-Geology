/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.recipe;

import blusunrize.immersiveengineering.api.crafting.IERecipeSerializer;
import blusunrize.immersiveengineering.api.crafting.IESerializableRecipe;
import blusunrize.immersiveengineering.api.crafting.cache.CachedRecipeList;
import com.igteam.immersivegeology.core.registration.IGRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Fuel schedule for the pulverised coal boiler (котлоагрегат).
 * <p>
 * Real boilers are rated by what they burn. Pulverised coal (угольная пыль) burns out in a second
 * or two and leaves almost no unburned carbon behind, while lump coal (кусковой уголь) burns slower
 * and loses several percent of its heating value into the ash pit, so every fuel carries its own
 * burn time, steam yield, flue gas volume and combustion efficiency. The values follow real lower
 * heating values - lignite ~13 MJ/kg, bituminous ~24 MJ/kg, anthracite ~28 MJ/kg - scaled to the
 * mod's millibucket units.
 */
public class BoilerFuel extends IESerializableRecipe
{
	public static RegistryObject<IERecipeSerializer<BoilerFuel>> SERIALIZER;
	public static final CachedRecipeList<BoilerFuel> RECIPES = new CachedRecipeList<>(IGRecipeTypes.BOILER_FUEL);

	public final Ingredient input;
	/** Ticks one input item keeps the furnace lit. */
	public final int burnTime;
	/** mB of superheated steam produced per input item at nominal boiler efficiency. */
	public final int steamOutput;
	/** mB of feed water evaporated per input item; half the steam, matching the mod's 2:1 expansion. */
	public final int waterInput;
	/** mB of flue gas leaving the convective pass per input item. */
	public final int flueGasOutput;
	/** Chance that finishing one item leaves a bottom ash item in the ash hopper. */
	public final float bottomAshChance;
	/** Share of the fuel's heating value that reaches the water; lump coal loses more. */
	public final float efficiency;

	public BoilerFuel(ResourceLocation id, Ingredient input, int burnTime, int steamOutput, int waterInput,
					  int flueGasOutput, float bottomAshChance, float efficiency)
	{
		super(LAZY_EMPTY, IGRecipeTypes.BOILER_FUEL, id);
		this.input = input;
		this.burnTime = burnTime;
		this.steamOutput = steamOutput;
		this.waterInput = waterInput;
		this.flueGasOutput = flueGasOutput;
		this.bottomAshChance = bottomAshChance;
		this.efficiency = efficiency;
	}

	@Nullable
	public static BoilerFuel getRecipeFor(Level level, ItemStack stack, @Nullable BoilerFuel hint)
	{
		if(stack.isEmpty())
			return null;
		if(hint != null && hint.input.test(stack))
			return hint;
		Iterator<BoilerFuel> recipes = RECIPES.getRecipes(level).iterator();
		BoilerFuel fuel;
		do
		{
			if(!recipes.hasNext())
				return null;
			fuel = recipes.next();
		} while(!fuel.input.test(stack));
		return fuel;
	}

	public static SortedMap<Component, Integer> getManualFuelList(Level level)
	{
		SortedMap<Component, Integer> map = new TreeMap<>(Comparator.comparing(Component::getString, Comparator.naturalOrder()));
		for(BoilerFuel recipe : RECIPES.getRecipes(level))
		{
			ItemStack[] examples = recipe.input.getItems();
			if(examples.length > 0)
				map.put(examples[0].getHoverName(), recipe.steamOutput);
		}
		return map;
	}

	@Override
	protected IERecipeSerializer<?> getIESerializer()
	{
		return SERIALIZER.get();
	}

	@Override
	public ItemStack getResultItem(RegistryAccess access)
	{
		return ItemStack.EMPTY;
	}
}
