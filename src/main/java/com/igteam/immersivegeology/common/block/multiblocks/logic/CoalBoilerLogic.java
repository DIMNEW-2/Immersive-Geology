/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.logic;

import blusunrize.immersiveengineering.api.energy.AveragingEnergyStorage;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IClientTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IServerTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.RedstoneControl;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IInitialMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockLogic;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.CapabilityPosition;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MBInventoryUtils;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.ShapeType;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.StoredCapability;
import blusunrize.immersiveengineering.api.utils.CapabilityReference;
import blusunrize.immersiveengineering.client.utils.TextUtils;
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.interfaces.MBOverlayText;
import blusunrize.immersiveengineering.common.fluids.ArrayFluidHandler;
import blusunrize.immersiveengineering.common.util.CachedRecipe;
import blusunrize.immersiveengineering.common.util.DroppingMultiblockOutput;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.SlotwiseItemHandler;
import blusunrize.immersiveengineering.common.util.inventory.SlotwiseItemHandler.IOConstraint;
import com.igteam.immersivegeology.common.block.helper.IGReceiveOnlyEnergy;
import com.igteam.immersivegeology.common.block.multiblocks.logic.CoalBoilerLogic.State;
import com.igteam.immersivegeology.common.block.multiblocks.logic.helper.IGMultiblockState;
import com.igteam.immersivegeology.common.block.multiblocks.recipe.BoilerFuel;
import com.igteam.immersivegeology.common.block.multiblocks.shapes.CoalBoilerShape;
import com.igteam.immersivegeology.core.material.data.enums.MiscEnum;
import com.igteam.immersivegeology.core.material.helper.flags.ItemCategoryFlags;
import com.igteam.immersivegeology.core.material.helper.flags.BlockCategoryFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pulverised coal fired steam boiler (пылеугольный котлоагрегат) - the heart of the coal CHP chain.
 * <p>
 * The model follows a real drum boiler: pulverised coal is blown into the furnace where it burns in
 * suspension, the radiant heat of the fire raises the heat of the waterwalls, and that heat
 * evaporates feed water into superheated steam. Everything a real boiler struggles with is here:
 * <ul>
 *     <li><b>Thermal inertia</b> - the furnace has to be brought up to temperature before it
 *     evaporates anything, and it cools down slowly once the fire dies.</li>
 *     <li><b>Draft and auxiliaries</b> - the forced and induced draft fans, the coal feeders and the
 *     feed water pumps all need power. Without it there is no draft, the fire suffocates and the
 *     flue gas has nowhere to go.</li>
 *     <li><b>Pressure and the safety valve</b> - if the steam cannot leave the drum the pressure
 *     climbs, and past the set point the safety valve lifts and wastes steam.</li>
 *     <li><b>Flue gas</b> - every item of fuel leaves dirty gas behind that must be pulled through
 *     an electrostatic precipitator, exactly like a real plant's gas path.</li>
 *     <li><b>Ash</b> - the coarse fraction drops into the ash hopper as bottom ash; the fine fly ash
 *     travels with the gas and is recovered downstream.</li>
 * </ul>
 * Preheated feed water (network hot water) is accepted and rewarded with a few percent of extra
 * efficiency - that is regenerative feed water heating, one of the cheapest efficiency wins a real
 * station has.
 */
public class CoalBoilerLogic implements IMultiblockLogic<State>, IServerTickableComponent<State>,
		IClientTickableComponent<State>, MBOverlayText<State>
{
	public static final BlockPos REDSTONE_IN = new BlockPos(2, 6, 4);

	// Auxiliary power: draft fans, coal feeders, feed water pumps. Real stations spend 5-8% of their
	// gross output on their own auxiliaries, and cannot light up at all without them.
	public static final int ENERGY_CAPACITY = 64000;
	public static final int AUX_POWER_IDLE = 120;
	public static final int AUX_POWER_FIRING = 480;

	public static final int WATER_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	public static final int STEAM_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	public static final int GAS_CAPACITY = 16 * FluidType.BUCKET_VOLUME;

	// Furnace heat and drum pressure, both on a 0..100 scale.
	public static final float MAX_HEAT = 100f;
	public static final float FIRE_MIN_HEAT = 40f;
	private static final float HEAT_GAIN = 0.5f;
	private static final float HEAT_LOSS = 0.15f;
	private static final float HEAT_LOSS_NO_DRAFT = 0.6f;
	private static final float PRESSURE_MAX = 100f;
	private static final float PRESSURE_RISE = 0.6f;
	private static final float PRESSURE_FALL = 0.25f;
	private static final float SAFETY_VALVE_POINT = 90f;
	private static final int SAFETY_VALVE_VENT = 8;

	// Nominal steam output used to judge the load, mB per tick.
	public static final float NOMINAL_STEAM_RATE = 24f;

	// Pulverised coal is fed into the bunker on the roof, ash leaves through the hopper below.
	private static final CapabilityPosition FUEL_INPUT = new CapabilityPosition(2, 6, 2, RelativeBlockFace.UP);
	private static final CapabilityPosition ENERGY_INPUT = new CapabilityPosition(4, 1, 4, RelativeBlockFace.BACK);
	private static final CapabilityPosition WATER_INPUT = new CapabilityPosition(2, 4, 4, RelativeBlockFace.BACK);
	private static final MultiblockFace STEAM_OUTPUT = new MultiblockFace(2, 3, -1, RelativeBlockFace.BACK);
	private static final MultiblockFace GAS_OUTPUT = new MultiblockFace(2, 6, -1, RelativeBlockFace.BACK);
	private static final MultiblockFace ASH_OUTPUT = new MultiblockFace(2, 0, 5, RelativeBlockFace.FRONT);

	@Override
	public void tickServer(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final Level level = context.getLevel().getRawLevel();
		final boolean enabled = state.rsState.isEnabled(context);
		final boolean wasActive = state.renderAsActive;
		final boolean wasVenting = state.safetyValveOpen;

		// --- auxiliaries: no power, no draft, no fire -------------------------------------------------
		final boolean wantsFire = enabled && state.hasFuelToBurn(level);
		final int auxCost = state.burnTicks > 0 ? AUX_POWER_FIRING : (wantsFire ? AUX_POWER_IDLE : 0);
		state.draft = auxCost <= 0 || state.energy.getEnergyStored() >= auxCost;
		if(state.draft && auxCost > 0)
			state.energy.extractEnergy(auxCost, false);

		// --- light the furnace or keep it burning ----------------------------------------------------
		if(enabled && state.draft && state.burnTicks <= 0 && state.heat < MAX_HEAT)
		{
			ItemStack fuel = state.fuelInventory.getStackInSlot(0);
			BoilerFuel recipe = BoilerFuel.getRecipeFor(level, fuel, state.burningFuel);
			if(recipe != null && state.waterTank.getFluidAmount() > 0)
			{
				ItemStack consumed = fuel.copyWithCount(1);
				fuel.shrink(1);
				state.fuelInventory.setStackInSlot(0, fuel.isEmpty() ? ItemStack.EMPTY : fuel);
				state.burningFuel = recipe;
				state.burnTicks = recipe.burnTime;
				state.burnLength = recipe.burnTime;
				state.burnSteam = recipe.steamOutput;
				state.burnWater = recipe.waterInput;
				state.burnGas = recipe.flueGasOutput;
				state.burnAshChance = recipe.bottomAshChance;
				state.burnEfficiency = recipe.efficiency;
				state.fuelConsumed = consumed;
			}
		}

		boolean firing = state.burnTicks > 0 && state.draft && enabled;
		float fireQuality = firing ? Mth.clamp(state.heat / 70f, 0f, 1f) : 0f;

		// --- heat balance -----------------------------------------------------------------------------
		if(firing)
			state.heat = Math.min(MAX_HEAT, state.heat + HEAT_GAIN);
		else if(!state.draft)
			state.heat = Math.max(0f, state.heat - HEAT_LOSS_NO_DRAFT);
		else
			state.heat = Math.max(0f, state.heat - HEAT_LOSS);

		// --- evaporation ------------------------------------------------------------------------------
		float steamProduced = 0f;
		if(firing && state.heat >= FIRE_MIN_HEAT)
		{
			float waterNeeded = (state.burnWater / (float) state.burnLength) * fireQuality;
			float steamWanted = (state.burnSteam / (float) state.burnLength) * fireQuality * state.efficiency;
			float waterAvailable = Math.min(waterNeeded, state.waterTank.getFluidAmount());
			float steamMade = waterAvailable <= 0 ? 0 : steamWanted * (waterAvailable / waterNeeded);
			steamMade = Math.min(steamMade, state.steamTank.getSpace());
			if(steamMade > 0)
			{
				state.waterTank.drain((int) Math.ceil(steamMade / 2f), FluidAction.EXECUTE);
				FluidStack steam = new FluidStack(MiscEnum.HighPressureSteam.getFluid(BlockCategoryFlags.FLUID), (int) steamMade);
				state.steamTank.fill(steam, FluidAction.EXECUTE);
				steamProduced = steamMade;
			}
			float gasMade = Math.min((state.burnGas / (float) state.burnLength) * fireQuality, state.gasTank.getSpace());
			if(gasMade >= 1)
				state.gasTank.fill(new FluidStack(MiscEnum.FlueGas.getFluid(BlockCategoryFlags.FLUID), (int) gasMade), FluidAction.EXECUTE);
			state.gasChoked = state.gasTank.getSpace() < 8;
		}
		else
		{
			state.gasChoked = false;
		}

		if(firing)
		{
			state.burnTicks--;
			if(state.burnTicks <= 0)
			{
				// The burn finished: drop the coarse ash fraction into the hopper.
				if(level.getRandom().nextFloat() < state.burnAshChance)
					state.ashOutput.insertOrDrop(new ItemStack(MiscEnum.FlyAsh.getItem(ItemCategoryFlags.GRIT)), context.getLevel());
				state.burningFuel = null;
			}
		}

		// --- drum pressure and the safety valve -------------------------------------------------------
		state.safetyValveOpen = state.steamTank.getSpace() < 16;
		if(state.safetyValveOpen)
			state.pressure = Math.min(PRESSURE_MAX, state.pressure + PRESSURE_RISE);
		else
			state.pressure = Math.max(0f, state.pressure - PRESSURE_FALL);
		if(state.pressure > SAFETY_VALVE_POINT && state.steamTank.getFluidAmount() >= SAFETY_VALVE_VENT)
			state.steamTank.drain(SAFETY_VALVE_VENT, FluidAction.EXECUTE);

		// --- load tracking and the efficiency readout --------------------------------------------------
		state.load = Mth.lerp(0.05f, state.load, state.steamDelivered / NOMINAL_STEAM_RATE);
		float loadFactor = state.load <= 0.5f ? 0.9f + 0.2f * state.load : 1.0f - 0.15f * (state.load - 0.5f);
		boolean preheated = state.waterTank.getFluid().getFluid() == MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID);
		state.preheatedFeed = preheated;
		float pressureFactor = state.pressure > 30f && state.pressure < 85f ? 1f : 0.94f;
		state.efficiency = Mth.clamp(state.burnEfficiency * loadFactor * (preheated ? 1.06f : 1f) * pressureFactor, 0f, 1f);

		// --- push steam and flue gas out ----------------------------------------------------------------
		state.steamDelivered = pushFluid(state, state.steamTank, state.steamOutputCap);
		pushFluid(state, state.gasTank, state.gasOutputCap);

		state.renderAsActive = firing && state.heat > FIRE_MIN_HEAT;
		if(wasActive != state.renderAsActive || wasVenting != state.safetyValveOpen)
			context.requestMasterBESync();
		if(steamProduced > 0 || state.safetyValveOpen)
			context.markMasterDirty();
	}

	private static int pushFluid(State state, FluidTank tank, CapabilityReference<IFluidHandler> target)
	{
		if(tank.getFluidAmount() <= 0)
			return 0;
		IFluidHandler handler = target.getNullable();
		if(handler == null)
			return 0;
		FluidStack offered = Utils.copyFluidStackWithAmount(tank.getFluid(), tank.getFluidAmount(), false);
		int accepted = handler.fill(offered, FluidAction.SIMULATE);
		if(accepted <= 0)
			return 0;
		int moved = handler.fill(Utils.copyFluidStackWithAmount(offered, Math.min(offered.getAmount(), accepted), false), FluidAction.EXECUTE);
		tank.drain(moved, FluidAction.EXECUTE);
		return moved;
	}

	@Override
	public void tickClient(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final Level level = context.getLevel().getRawLevel();
		if(!state.renderAsActive)
			return;
		if(level.getGameTime() % 3 != 0)
			return;
		// Flame licking out of the burner register
		Vec3 fire = context.getLevel().toAbsolute(new Vec3(2.5, 1.35, 2.5));
		level.addParticle(ParticleTypes.FLAME, fire.x + level.getRandom().nextFloat(-0.4f, 0.4f), fire.y,
				fire.z + level.getRandom().nextFloat(-0.4f, 0.4f), 0, 0.05, 0);
		if(state.safetyValveOpen)
		{
			Vec3 valve = context.getLevel().toAbsolute(new Vec3(2.5, 4.9, 2.5));
			level.addParticle(ParticleTypes.CLOUD, valve.x, valve.y, valve.z, 0, 0.15, 0);
		}
		if(state.gasChoked)
		{
			Vec3 duct = context.getLevel().toAbsolute(new Vec3(2.5, 6.6, 0.4));
			level.addParticle(ParticleTypes.SMOKE, duct.x, duct.y, duct.z, 0, 0.05, 0);
		}
	}

	@Override
	public void dropExtraItems(State state, Consumer<ItemStack> drop)
	{
		MBInventoryUtils.dropItems(state.fuelInventory, drop);
	}

	@Override
	public State createInitialState(IInitialMultiblockContext<State> capability)
	{
		return new State(capability);
	}

	@Override
	public Function<BlockPos, VoxelShape> shapeGetter(ShapeType shapeType)
	{
		return CoalBoilerShape.GETTER;
	}

	@Override
	public <T> LazyOptional<T> getCapability(IMultiblockContext<State> ctx, CapabilityPosition position, Capability<T> cap)
	{
		final State state = ctx.getState();
		if(cap == ForgeCapabilities.ENERGY)
		{
			if(position.side() == null || ENERGY_INPUT.equals(position))
				return state.energyCap.cast(ctx);
		}
		if(cap == ForgeCapabilities.FLUID_HANDLER)
		{
			if(WATER_INPUT.equalsOrNullFace(position))
				return state.waterCap.cast(ctx);
			if(CapabilityPosition.opposing(STEAM_OUTPUT).equalsOrNullFace(position))
				return state.steamCap.cast(ctx);
			if(CapabilityPosition.opposing(GAS_OUTPUT).equalsOrNullFace(position))
				return state.gasCap.cast(ctx);
		}
		if(cap == ForgeCapabilities.ITEM_HANDLER)
		{
			if(FUEL_INPUT.equalsOrNullFace(position))
				return state.fuelCap.cast(ctx);
		}
		return LazyOptional.empty();
	}

	@Nullable
	@Override
	public List<Component> getOverlayText(State state, Player player, boolean isServer)
	{
		if(state == null)
			return null;
		List<Component> lines = new ArrayList<>();
		if(!state.draft)
			lines.add(Component.translatable("immersivegeology.coal_boiler.no_draft").withStyle(ChatFormatting.RED));
		if(state.safetyValveOpen && state.pressure > SAFETY_VALVE_POINT)
			lines.add(Component.translatable("immersivegeology.coal_boiler.safety_valve").withStyle(ChatFormatting.RED));
		if(state.gasChoked)
			lines.add(Component.translatable("immersivegeology.coal_boiler.gas_backpressure").withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("immersivegeology.coal_boiler.heat", (int) state.heat));
		lines.add(Component.translatable("immersivegeology.coal_boiler.pressure", (int) state.pressure));
		lines.add(Component.translatable("immersivegeology.coal_boiler.efficiency", String.format("%.1f", state.efficiency * 100f)));
		if(Utils.isFluidRelatedItemStack(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)))
		{
			lines.add(TextUtils.formatFluidStack(state.steamTank.getFluid()));
			lines.add(TextUtils.formatFluidStack(state.waterTank.getFluid()));
		}
		return lines;
	}

	public static class State implements IGMultiblockState
	{
		public final RedstoneControl.RSState rsState = RedstoneControl.RSState.enabledByDefault();
		public final AveragingEnergyStorage energy = new AveragingEnergyStorage(ENERGY_CAPACITY);

		public final FluidTank waterTank = new FluidTank(WATER_CAPACITY, CoalBoilerLogic::isFeedWater);
		public final FluidTank steamTank = new FluidTank(STEAM_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.HighPressureSteam.getFluid(BlockCategoryFlags.FLUID));
		public final FluidTank gasTank = new FluidTank(GAS_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.FlueGas.getFluid(BlockCategoryFlags.FLUID));

		public final SlotwiseItemHandler fuelInventory;

		public float heat = 0f;
		public float pressure = 0f;
		public float load = 0f;
		public float efficiency = 0f;
		public boolean draft = false;
		public boolean gasChoked = false;
		public boolean safetyValveOpen = false;
		public boolean preheatedFeed = false;
		public boolean renderAsActive = false;

		public int burnTicks = 0;
		public int burnLength = 1;
		public int burnSteam = 0;
		public int burnWater = 0;
		public int burnGas = 0;
		public float burnAshChance = 0f;
		public float burnEfficiency = 0.9f;
		@Nullable
		public BoilerFuel burningFuel = null;
		public ItemStack fuelConsumed = ItemStack.EMPTY;
		public int steamDelivered = 0;

		private final StoredCapability<IEnergyStorage> energyCap;
		private final StoredCapability<IFluidHandler> waterCap;
		private final StoredCapability<IFluidHandler> steamCap;
		private final StoredCapability<IFluidHandler> gasCap;
		private final StoredCapability<IItemHandler> fuelCap;
		private final CapabilityReference<IFluidHandler> steamOutputCap;
		private final CapabilityReference<IFluidHandler> gasOutputCap;
		private final DroppingMultiblockOutput ashOutput;
		private final BiFunction<Level, ItemStack, BoilerFuel> recipeGetter = CachedRecipe.cached(BoilerFuel::getRecipeFor);

		public State(IInitialMultiblockContext<State> ctx)
		{
			final Runnable markDirty = ctx.getMarkDirtyRunnable();
			Runnable changedAndSync = () -> {
				markDirty.run();
				ctx.getSyncRunnable().run();
			};
			this.fuelInventory = new SlotwiseItemHandler(List.of(
					new IOConstraint(true, stack -> recipeGetter.apply(ctx.levelSupplier().get(), stack, null) != null)
			), markDirty);
			this.energyCap = new StoredCapability<>(IGReceiveOnlyEnergy.of(this.energy));
			this.waterCap = new StoredCapability<>(new ArrayFluidHandler(waterTank, false, true, changedAndSync));
			this.steamCap = new StoredCapability<>(new ArrayFluidHandler(steamTank, true, false, changedAndSync));
			this.gasCap = new StoredCapability<>(new ArrayFluidHandler(gasTank, true, false, changedAndSync));
			this.fuelCap = new StoredCapability<>(fuelInventory);
			this.steamOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER, STEAM_OUTPUT.posInMultiblock(), STEAM_OUTPUT.face().getOpposite());
			this.gasOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER, GAS_OUTPUT.posInMultiblock(), GAS_OUTPUT.face().getOpposite());
			this.ashOutput = new DroppingMultiblockOutput(ASH_OUTPUT, ctx);
		}

		public boolean hasFuelToBurn(Level level)
		{
			return recipeGetter.apply(level, fuelInventory.getStackInSlot(0)) != null;
		}

		@Override
		public void readSaveNBT(CompoundTag nbt)
		{
			waterTank.readFromNBT(nbt.getCompound("water"));
			steamTank.readFromNBT(nbt.getCompound("steam"));
			gasTank.readFromNBT(nbt.getCompound("gas"));
			energy.deserializeNBT(nbt.get("energy"));
			fuelInventory.deserializeNBT(nbt.getCompound("fuel"));
			heat = nbt.getFloat("heat");
			pressure = nbt.getFloat("pressure");
			load = nbt.getFloat("load");
			burnTicks = nbt.getInt("burn_ticks");
			burnLength = Math.max(1, nbt.getInt("burn_length"));
			burnSteam = nbt.getInt("burn_steam");
			burnWater = nbt.getInt("burn_water");
			burnGas = nbt.getInt("burn_gas");
			burnAshChance = nbt.getFloat("burn_ash");
			burnEfficiency = nbt.getFloat("burn_efficiency");
			fuelConsumed = ItemStack.of(nbt.getCompound("fuel_stack"));
		}

		@Override
		public void writeSaveNBT(CompoundTag nbt)
		{
			nbt.put("water", waterTank.writeToNBT(new CompoundTag()));
			nbt.put("steam", steamTank.writeToNBT(new CompoundTag()));
			nbt.put("gas", gasTank.writeToNBT(new CompoundTag()));
			nbt.put("energy", energy.serializeNBT());
			nbt.put("fuel", fuelInventory.serializeNBT());
			nbt.putFloat("heat", heat);
			nbt.putFloat("pressure", pressure);
			nbt.putFloat("load", load);
			nbt.putInt("burn_ticks", burnTicks);
			nbt.putInt("burn_length", burnLength);
			nbt.putInt("burn_steam", burnSteam);
			nbt.putInt("burn_water", burnWater);
			nbt.putInt("burn_gas", burnGas);
			nbt.putFloat("burn_ash", burnAshChance);
			nbt.putFloat("burn_efficiency", burnEfficiency);
			nbt.put("fuel_stack", fuelConsumed.serializeNBT());
		}

		@Override
		public void writeSyncNBT(CompoundTag nbt)
		{
			writeSaveNBT(nbt);
			nbt.putBoolean("renderActive", renderAsActive);
			nbt.putBoolean("safety_valve", safetyValveOpen);
			nbt.putBoolean("gas_choked", gasChoked);
			nbt.putBoolean("draft", draft);
			nbt.putFloat("efficiency", efficiency);
		}

		@Override
		public void readSyncNBT(CompoundTag nbt)
		{
			readSaveNBT(nbt);
			renderAsActive = nbt.getBoolean("renderActive");
			safetyValveOpen = nbt.getBoolean("safety_valve");
			gasChoked = nbt.getBoolean("gas_choked");
			draft = nbt.getBoolean("draft");
			efficiency = nbt.getFloat("efficiency");
		}

		@Override
		public void invalidate(@NotNull IMultiblockContext<?> ctx)
		{
			energyCap.get(ctx).invalidate();
			waterCap.get(ctx).invalidate();
			steamCap.get(ctx).invalidate();
			gasCap.get(ctx).invalidate();
			fuelCap.get(ctx).invalidate();
		}
	}

	private static boolean isFeedWater(FluidStack stack)
	{
		Fluid fluid = stack.getFluid();
		return fluid == Fluids.WATER || fluid == MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID);
	}
}
