/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.recipe.serializer;

import blusunrize.immersiveengineering.api.crafting.IERecipeSerializer;
import com.google.gson.JsonObject;
import com.igteam.immersivegeology.common.block.multiblocks.recipe.BoilerFuel;
import com.igteam.immersivegeology.core.registration.IGMultiblockProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.conditions.ICondition.IContext;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public class BoilerFuelSerializer extends IERecipeSerializer<BoilerFuel>
{
	@Override
	public ItemStack getIcon()
	{
		return IGMultiblockProvider.COAL_BOILER.iconStack();
	}

	@Override
	public BoilerFuel readFromJson(ResourceLocation recipeId, JsonObject json, IContext context)
	{
		Ingredient input = Ingredient.fromJson(json.getAsJsonObject("input"));
		int burnTime = GsonHelper.getAsInt(json, "burnTime", 100);
		int steam = GsonHelper.getAsInt(json, "steam", 0);
		int water = GsonHelper.getAsInt(json, "water", steam / 2);
		int flueGas = GsonHelper.getAsInt(json, "flue_gas", 0);
		float bottomAsh = GsonHelper.getAsFloat(json, "bottom_ash_chance", 0.25f);
		float efficiency = GsonHelper.getAsFloat(json, "efficiency", 0.9f);
		return new BoilerFuel(recipeId, input, burnTime, steam, water, flueGas, bottomAsh, efficiency);
	}

	@Nullable
	@Override
	public BoilerFuel fromNetwork(@Nonnull ResourceLocation recipeId, @Nonnull FriendlyByteBuf buffer)
	{
		Ingredient input = Ingredient.fromNetwork(buffer);
		int burnTime = buffer.readInt();
		int steam = buffer.readInt();
		int water = buffer.readInt();
		int flueGas = buffer.readInt();
		float bottomAsh = buffer.readFloat();
		float efficiency = buffer.readFloat();
		return new BoilerFuel(recipeId, input, burnTime, steam, water, flueGas, bottomAsh, efficiency);
	}

	@Override
	public void toNetwork(@Nonnull FriendlyByteBuf buffer, @Nonnull BoilerFuel recipe)
	{
		recipe.input.toNetwork(buffer);
		buffer.writeInt(recipe.burnTime);
		buffer.writeInt(recipe.steamOutput);
		buffer.writeInt(recipe.waterInput);
		buffer.writeInt(recipe.flueGasOutput);
		buffer.writeFloat(recipe.bottomAshChance);
		buffer.writeFloat(recipe.efficiency);
	}
}
