/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.logic;

import blusunrize.immersiveengineering.api.energy.AveragingEnergyStorage;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IServerTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.RedstoneControl;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IInitialMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockLogic;
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.CapabilityPosition;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.ShapeType;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.StoredCapability;
import blusunrize.immersiveengineering.api.utils.CapabilityReference;
import blusunrize.immersiveengineering.client.utils.TextUtils;
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.interfaces.MBOverlayText;
import blusunrize.immersiveengineering.common.fluids.ArrayFluidHandler;
import blusunrize.immersiveengineering.common.util.Utils;
import com.igteam.immersivegeology.common.block.helper.IGReceiveOnlyEnergy;
import com.igteam.immersivegeology.common.block.multiblocks.logic.SteamCondenserLogic.State;
import com.igteam.immersivegeology.common.block.multiblocks.logic.helper.IGMultiblockState;
import com.igteam.immersivegeology.common.block.multiblocks.shapes.SteamCondenserShape;
import com.igteam.immersivegeology.core.material.data.enums.MiscEnum;
import com.igteam.immersivegeology.core.material.helper.flags.BlockCategoryFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Surface condenser (поверхностный конденсатор) with its condensate pumps - the cold end of the
 * steam cycle.
 * <p>
 * Exhaust steam from the turbine gives up its latent heat to the tube bundle and drops into the
 * hotwell as condensate; that condensate is the boiler's feed water, so recovering it is what closes
 * the water-steam cycle. The heat does not vanish: it is carried away by the circulating water loop,
 * which leaves the condenser as heated water on its way to the cooling tower. Condensing one bucket
 * of steam needs several buckets of cooling water, exactly as the real heat balance demands.
 */
public class SteamCondenserLogic implements IMultiblockLogic<State>, IServerTickableComponent<State>, MBOverlayText<State>
{
	public static final BlockPos REDSTONE_IN = new BlockPos(2, 1, 3);

	public static final int ENERGY_CAPACITY = 32000;
	/** FE per tick for the condensate and circulating water pumps. */
	public static final int PUMP_POWER = 60;
	public static final int STEAM_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	public static final int COOLING_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	public static final int CONDENSATE_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	public static final int HOT_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	/** mB of exhaust steam condensed per tick at full cooling water flow. */
	public static final int CONDENSE_RATE = 8;
	/** mB of circulating water heated per mB of condensed steam. */
	public static final int COOLING_WATER_RATIO = 6;

	private static final CapabilityPosition STEAM_INPUT = new CapabilityPosition(1, 2, 0, RelativeBlockFace.FRONT);
	private static final CapabilityPosition COOLING_INPUT = new CapabilityPosition(0, 1, 1, RelativeBlockFace.RIGHT);
	private static final CapabilityPosition CONDENSATE_OUTPUT = new CapabilityPosition(1, 0, 3, RelativeBlockFace.BACK);
	private static final CapabilityPosition HOT_OUTPUT = new CapabilityPosition(3, 1, 1, RelativeBlockFace.LEFT);
	// Neighbour positions the machine pushes into: one step outside the structure, queried with the
	// direction pointing away from the machine, the same way the steam turbine reaches its pipes.
	private static final BlockPos CONDENSATE_NEIGHBOUR = new BlockPos(1, 0, 4);
	private static final BlockPos HOT_NEIGHBOUR = new BlockPos(4, 1, 1);
	private static final CapabilityPosition ENERGY_INPUT = new CapabilityPosition(1, 1, 3, RelativeBlockFace.BACK);

	@Override
	public void tickServer(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final boolean enabled = state.rsState.isEnabled(context);
		final boolean wasActive = state.renderAsActive;

		boolean working = false;
		if(enabled && state.energy.getEnergyStored() >= PUMP_POWER)
		{
			// Condense exhaust steam into feed water; the latent heat goes into the cooling loop.
			int steam = Math.min(CONDENSE_RATE, state.steamTank.getFluidAmount());
			int coolingSpace = Math.min(state.coolingTank.getFluidAmount(),
					state.hotTank.getSpace() / Math.max(1, COOLING_WATER_RATIO));
			int condensed = Math.min(steam, coolingSpace);
			if(condensed > 0)
			{
				state.steamTank.drain(condensed, FluidAction.EXECUTE);
				state.coolingTank.drain(condensed * COOLING_WATER_RATIO, FluidAction.EXECUTE);
				state.condensateTank.fill(new FluidStack(Fluids.WATER, condensed), FluidAction.EXECUTE);
				state.hotTank.fill(new FluidStack(MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID),
						condensed * COOLING_WATER_RATIO), FluidAction.EXECUTE);
				working = true;
			}
			// Pump any turbine condensate that arrives as water straight through to the hotwell.
			int passthrough = Math.min(CONDENSE_RATE, state.steamTank.getFluidAmount());
			if(passthrough > 0 && state.steamTank.getFluid().getFluid() == Fluids.WATER)
			{
				int space = state.condensateTank.getSpace();
				int moved = Math.min(passthrough, space);
				if(moved > 0)
				{
					state.steamTank.drain(moved, FluidAction.EXECUTE);
					state.condensateTank.fill(new FluidStack(Fluids.WATER, moved), FluidAction.EXECUTE);
					working = true;
				}
			}
			if(working)
				state.energy.extractEnergy(PUMP_POWER, false);
		}

		// Push condensate to the boiler and heated water to the cooling tower.
		state.condensateDelivered = pushFluid(state, state.condensateTank, state.condensateOutputCap);
		state.hotDelivered = pushFluid(state, state.hotTank, state.hotOutputCap);

		state.renderAsActive = working;
		if(wasActive != state.renderAsActive)
			context.requestMasterBESync();
		if(working)
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
	public State createInitialState(IInitialMultiblockContext<State> capability)
	{
		return new State(capability);
	}

	@Override
	public Function<BlockPos, VoxelShape> shapeGetter(ShapeType shapeType)
	{
		return SteamCondenserShape.GETTER;
	}

	@Override
	public <T> LazyOptional<T> getCapability(IMultiblockContext<State> ctx, CapabilityPosition position, Capability<T> cap)
	{
		final State state = ctx.getState();
		if(cap == ForgeCapabilities.ENERGY && (position.side() == null || ENERGY_INPUT.equals(position)))
			return state.energyCap.cast(ctx);
		if(cap == ForgeCapabilities.FLUID_HANDLER)
		{
			if(STEAM_INPUT.equalsOrNullFace(position))
				return state.steamCap.cast(ctx);
			if(COOLING_INPUT.equalsOrNullFace(position))
				return state.coolingCap.cast(ctx);
			if(CONDENSATE_OUTPUT.equalsOrNullFace(position))
				return state.condensateCap.cast(ctx);
			if(HOT_OUTPUT.equalsOrNullFace(position))
				return state.hotCap.cast(ctx);
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
		if(state.steamTank.getFluidAmount() > 0 && state.coolingTank.getFluidAmount() <= 0)
			lines.add(Component.translatable("immersivegeology.steam_condenser.no_cooling_water").withStyle(ChatFormatting.RED));
		lines.add(Component.translatable("immersivegeology.steam_condenser.condensate",
				state.condensateTank.getFluidAmount(), CONDENSATE_CAPACITY));
		if(Utils.isFluidRelatedItemStack(player.getItemInHand(InteractionHand.MAIN_HAND)))
		{
			lines.add(TextUtils.formatFluidStack(state.steamTank.getFluid()));
			lines.add(TextUtils.formatFluidStack(state.hotTank.getFluid()));
		}
		return lines;
	}

	public static class State implements IGMultiblockState
	{
		public final RedstoneControl.RSState rsState = RedstoneControl.RSState.enabledByDefault();
		public final AveragingEnergyStorage energy = new AveragingEnergyStorage(ENERGY_CAPACITY);

		public final FluidTank steamTank = new FluidTank(STEAM_CAPACITY, SteamCondenserLogic::isExhaust);
		public final FluidTank coolingTank = new FluidTank(COOLING_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);
		public final FluidTank condensateTank = new FluidTank(CONDENSATE_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);
		public final FluidTank hotTank = new FluidTank(HOT_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID));

		public boolean renderAsActive = false;
		public int condensateDelivered = 0;
		public int hotDelivered = 0;

		private final StoredCapability<IEnergyStorage> energyCap;
		private final StoredCapability<IFluidHandler> steamCap;
		private final StoredCapability<IFluidHandler> coolingCap;
		private final StoredCapability<IFluidHandler> condensateCap;
		private final StoredCapability<IFluidHandler> hotCap;
		private final CapabilityReference<IFluidHandler> condensateOutputCap;
		private final CapabilityReference<IFluidHandler> hotOutputCap;

		public State(IInitialMultiblockContext<State> ctx)
		{
			Runnable changedAndSync = () -> {
				ctx.getMarkDirtyRunnable().run();
				ctx.getSyncRunnable().run();
			};
			this.energyCap = new StoredCapability<>(IGReceiveOnlyEnergy.of(this.energy));
			this.steamCap = new StoredCapability<>(new ArrayFluidHandler(steamTank, false, true, changedAndSync));
			this.coolingCap = new StoredCapability<>(new ArrayFluidHandler(coolingTank, false, true, changedAndSync));
			this.condensateCap = new StoredCapability<>(new ArrayFluidHandler(condensateTank, true, false, changedAndSync));
			this.hotCap = new StoredCapability<>(new ArrayFluidHandler(hotTank, true, false, changedAndSync));
			this.condensateOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER,
					CONDENSATE_NEIGHBOUR, RelativeBlockFace.BACK);
			this.hotOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER,
					HOT_NEIGHBOUR, RelativeBlockFace.LEFT);
		}

		@Override
		public void readSaveNBT(CompoundTag nbt)
		{
			steamTank.readFromNBT(nbt.getCompound("steam"));
			coolingTank.readFromNBT(nbt.getCompound("cooling"));
			condensateTank.readFromNBT(nbt.getCompound("condensate"));
			hotTank.readFromNBT(nbt.getCompound("hot"));
			energy.deserializeNBT(nbt.get("energy"));
		}

		@Override
		public void writeSaveNBT(CompoundTag nbt)
		{
			nbt.put("steam", steamTank.writeToNBT(new CompoundTag()));
			nbt.put("cooling", coolingTank.writeToNBT(new CompoundTag()));
			nbt.put("condensate", condensateTank.writeToNBT(new CompoundTag()));
			nbt.put("hot", hotTank.writeToNBT(new CompoundTag()));
			nbt.put("energy", energy.serializeNBT());
		}

		@Override
		public void writeSyncNBT(CompoundTag nbt)
		{
			writeSaveNBT(nbt);
			nbt.putBoolean("renderActive", renderAsActive);
		}

		@Override
		public void readSyncNBT(CompoundTag nbt)
		{
			readSaveNBT(nbt);
			renderAsActive = nbt.getBoolean("renderActive");
		}

		@Override
		public void invalidate(@NotNull IMultiblockContext<?> ctx)
		{
			energyCap.get(ctx).invalidate();
			steamCap.get(ctx).invalidate();
			coolingCap.get(ctx).invalidate();
			condensateCap.get(ctx).invalidate();
			hotCap.get(ctx).invalidate();
		}
	}

	private static boolean isExhaust(FluidStack stack)
	{
		Fluid fluid = stack.getFluid();
		return fluid == Fluids.WATER || fluid == MiscEnum.Steam.getFluid(BlockCategoryFlags.FLUID);
	}
}
