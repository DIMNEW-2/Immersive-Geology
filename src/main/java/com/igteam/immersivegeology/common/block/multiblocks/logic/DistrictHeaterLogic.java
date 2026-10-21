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
import com.igteam.immersivegeology.common.block.multiblocks.logic.DistrictHeaterLogic.State;
import com.igteam.immersivegeology.common.block.multiblocks.logic.helper.IGMultiblockState;
import com.igteam.immersivegeology.common.block.multiblocks.shapes.DistrictHeaterShape;
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
 * Network water heater (сетевой подогреватель) - the cogeneration stage that turns the plant into a
 * ТЭЦ instead of a plain power station.
 * <p>
 * Steam bled from the turbine (теплофикационный отбор) condenses on the tube bundle and gives its
 * latent heat to the district heating network water, which leaves as hot supply water while the
 * condensate returns to the feed water system. Selling heat this way is what lifts a real
 * cogeneration plant's fuel utilisation from ~40% to 70% and above: the same fuel that makes
 * electricity also heats the town.
 */
public class DistrictHeaterLogic implements IMultiblockLogic<State>, IServerTickableComponent<State>, MBOverlayText<State>
{
	public static final BlockPos REDSTONE_IN = new BlockPos(2, 2, 2);

	public static final int ENERGY_CAPACITY = 32000;
	/** FE per tick for the network circulation pumps. */
	public static final int PUMP_POWER = 40;
	public static final int STEAM_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	public static final int NETWORK_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	public static final int HOT_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	public static final int CONDENSATE_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	/** mB of extraction steam condensed per tick. */
	public static final int CONDENSE_RATE = 6;
	/** mB of network water heated per mB of condensed extraction steam. */
	public static final int NETWORK_RATIO = 4;

	private static final CapabilityPosition STEAM_INPUT = new CapabilityPosition(1, 3, 0, RelativeBlockFace.FRONT);
	private static final CapabilityPosition NETWORK_INPUT = new CapabilityPosition(0, 1, 1, RelativeBlockFace.RIGHT);
	private static final CapabilityPosition HOT_OUTPUT = new CapabilityPosition(2, 1, 1, RelativeBlockFace.LEFT);
	private static final CapabilityPosition CONDENSATE_OUTPUT = new CapabilityPosition(1, 0, 2, RelativeBlockFace.BACK);
	private static final CapabilityPosition ENERGY_INPUT = new CapabilityPosition(0, 0, 1, RelativeBlockFace.RIGHT);
	private static final BlockPos HOT_NEIGHBOUR = new BlockPos(3, 1, 1);
	private static final BlockPos CONDENSATE_NEIGHBOUR = new BlockPos(1, 0, 3);

	@Override
	public void tickServer(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final boolean enabled = state.rsState.isEnabled(context);
		final boolean wasActive = state.renderAsActive;

		boolean working = false;
		if(enabled && state.energy.getEnergyStored() >= PUMP_POWER)
		{
			int steam = Math.min(CONDENSE_RATE, state.steamTank.getFluidAmount());
			int network = Math.min(state.networkTank.getFluidAmount(),
					state.hotTank.getSpace() / Math.max(1, NETWORK_RATIO));
			int condensed = Math.min(steam, network);
			if(condensed > 0)
			{
				state.steamTank.drain(condensed, FluidAction.EXECUTE);
				state.networkTank.drain(condensed * NETWORK_RATIO, FluidAction.EXECUTE);
				state.hotTank.fill(new FluidStack(MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID),
						condensed * NETWORK_RATIO), FluidAction.EXECUTE);
				state.condensateTank.fill(new FluidStack(Fluids.WATER, condensed), FluidAction.EXECUTE);
				state.heatDelivered += condensed * NETWORK_RATIO;
				state.energy.extractEnergy(PUMP_POWER, false);
				working = true;
			}
		}

		state.hotDelivered = pushFluid(state, state.hotTank, state.hotOutputCap);
		state.condensateDelivered = pushFluid(state, state.condensateTank, state.condensateOutputCap);

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
		return DistrictHeaterShape.GETTER;
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
			if(NETWORK_INPUT.equalsOrNullFace(position))
				return state.networkCap.cast(ctx);
			if(HOT_OUTPUT.equalsOrNullFace(position))
				return state.hotCap.cast(ctx);
			if(CONDENSATE_OUTPUT.equalsOrNullFace(position))
				return state.condensateCap.cast(ctx);
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
		if(state.steamTank.getFluidAmount() > 0 && state.networkTank.getFluidAmount() <= 0)
			lines.add(Component.translatable("immersivegeology.district_heater.no_return_water").withStyle(ChatFormatting.RED));
		lines.add(Component.translatable("immersivegeology.district_heater.supply",
				state.hotTank.getFluidAmount(), HOT_CAPACITY));
		lines.add(Component.translatable("immersivegeology.district_heater.delivered", state.heatDelivered / 1000));
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

		public final FluidTank steamTank = new FluidTank(STEAM_CAPACITY, DistrictHeaterLogic::isExtractionSteam);
		public final FluidTank networkTank = new FluidTank(NETWORK_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);
		public final FluidTank hotTank = new FluidTank(HOT_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID));
		public final FluidTank condensateTank = new FluidTank(CONDENSATE_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);

		public boolean renderAsActive = false;
		public int heatDelivered = 0;
		public int hotDelivered = 0;
		public int condensateDelivered = 0;

		private final StoredCapability<IEnergyStorage> energyCap;
		private final StoredCapability<IFluidHandler> steamCap;
		private final StoredCapability<IFluidHandler> networkCap;
		private final StoredCapability<IFluidHandler> hotCap;
		private final StoredCapability<IFluidHandler> condensateCap;
		private final CapabilityReference<IFluidHandler> hotOutputCap;
		private final CapabilityReference<IFluidHandler> condensateOutputCap;

		public State(IInitialMultiblockContext<State> ctx)
		{
			Runnable changedAndSync = () -> {
				ctx.getMarkDirtyRunnable().run();
				ctx.getSyncRunnable().run();
			};
			this.energyCap = new StoredCapability<>(IGReceiveOnlyEnergy.of(this.energy));
			this.steamCap = new StoredCapability<>(new ArrayFluidHandler(steamTank, false, true, changedAndSync));
			this.networkCap = new StoredCapability<>(new ArrayFluidHandler(networkTank, false, true, changedAndSync));
			this.hotCap = new StoredCapability<>(new ArrayFluidHandler(hotTank, true, false, changedAndSync));
			this.condensateCap = new StoredCapability<>(new ArrayFluidHandler(condensateTank, true, false, changedAndSync));
			this.hotOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER, HOT_NEIGHBOUR, RelativeBlockFace.LEFT);
			this.condensateOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER, CONDENSATE_NEIGHBOUR, RelativeBlockFace.BACK);
		}

		@Override
		public void readSaveNBT(CompoundTag nbt)
		{
			steamTank.readFromNBT(nbt.getCompound("steam"));
			networkTank.readFromNBT(nbt.getCompound("network"));
			hotTank.readFromNBT(nbt.getCompound("hot"));
			condensateTank.readFromNBT(nbt.getCompound("condensate"));
			energy.deserializeNBT(nbt.get("energy"));
			heatDelivered = nbt.getInt("heat_delivered");
		}

		@Override
		public void writeSaveNBT(CompoundTag nbt)
		{
			nbt.put("steam", steamTank.writeToNBT(new CompoundTag()));
			nbt.put("network", networkTank.writeToNBT(new CompoundTag()));
			nbt.put("hot", hotTank.writeToNBT(new CompoundTag()));
			nbt.put("condensate", condensateTank.writeToNBT(new CompoundTag()));
			nbt.put("energy", energy.serializeNBT());
			nbt.putInt("heat_delivered", heatDelivered);
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
			networkCap.get(ctx).invalidate();
			hotCap.get(ctx).invalidate();
			condensateCap.get(ctx).invalidate();
		}
	}

	private static boolean isExtractionSteam(FluidStack stack)
	{
		Fluid fluid = stack.getFluid();
		return fluid == MiscEnum.HighPressureSteam.getFluid(BlockCategoryFlags.FLUID)
				|| fluid == MiscEnum.Steam.getFluid(BlockCategoryFlags.FLUID);
	}
}
