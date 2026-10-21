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
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.MultiblockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.RelativeBlockFace;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.ShapeType;
import blusunrize.immersiveengineering.api.multiblocks.blocks.util.StoredCapability;
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.interfaces.MBOverlayText;
import blusunrize.immersiveengineering.common.fluids.ArrayFluidHandler;
import blusunrize.immersiveengineering.common.util.DroppingMultiblockOutput;
import com.igteam.immersivegeology.common.block.helper.IGReceiveOnlyEnergy;
import com.igteam.immersivegeology.common.block.multiblocks.logic.ElectrostaticPrecipitatorLogic.State;
import com.igteam.immersivegeology.common.block.multiblocks.logic.helper.IGMultiblockState;
import com.igteam.immersivegeology.common.block.multiblocks.shapes.ElectrostaticPrecipitatorShape;
import com.igteam.immersivegeology.core.material.data.enums.MiscEnum;
import com.igteam.immersivegeology.core.material.helper.flags.BlockCategoryFlags;
import com.igteam.immersivegeology.core.material.helper.flags.ItemCategoryFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Electrostatic precipitator (электрофильтр) - the flue gas cleaning stage of the coal CHP chain.
 * <p>
 * Dirty gas from the boiler is pulled through three electrode fields. Corona discharge charges the
 * suspended fly ash, the charged particles drift onto the collection plates and rapping gear shakes
 * them down into the hoppers below. A real precipitator captures 99%+ of the dust but only while its
 * rectifier sets are energised: without power the gas simply backs up and the boiler loses its draft.
 * The cleaned gas leaves through the stack on the roof.
 */
public class ElectrostaticPrecipitatorLogic implements IMultiblockLogic<State>, IServerTickableComponent<State>,
		IClientTickableComponent<State>, MBOverlayText<State>
{
	public static final BlockPos REDSTONE_IN = new BlockPos(2, 3, 3);

	public static final int ENERGY_CAPACITY = 64000;
	public static final int GAS_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	/** mB of flue gas the induced draft fan can pull through per tick. */
	public static final int GAS_THROUGHPUT = 24;
	/** FE spent per mB of gas cleaned - corona discharge plus the rapping gear. */
	public static final int ENERGY_PER_MB = 16;
	/** Fly ash recovered per mB of cleaned gas; ~1 item per two buckets of gas. */
	public static final float ASH_PER_MB = 1f / 2000f;
	/** Collection efficiency of a fully energised field. */
	public static final float CAPTURE_EFFICIENCY = 0.99f;

	private static final CapabilityPosition ENERGY_INPUT = new CapabilityPosition(2, 1, 3, RelativeBlockFace.BACK);
	private static final CapabilityPosition GAS_INPUT = new CapabilityPosition(2, 1, 0, RelativeBlockFace.FRONT);
	private static final MultiblockFace ASH_OUTPUT = new MultiblockFace(2, 0, 4, RelativeBlockFace.FRONT);

	@Override
	public void tickServer(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final boolean wasActive = state.renderAsActive;
		final boolean enabled = state.rsState.isEnabled(context);

		int gas = Math.min(GAS_THROUGHPUT, state.gasTank.getFluidAmount());
		int affordable = (int) (state.energy.getEnergyStored() / (float) ENERGY_PER_MB);
		gas = enabled ? Math.min(gas, affordable) : 0;

		if(gas >= 1)
		{
			state.gasTank.drain(gas, FluidAction.EXECUTE);
			state.energy.extractEnergy(gas * ENERGY_PER_MB, false);
			state.ashBuffer += gas * ASH_PER_MB * CAPTURE_EFFICIENCY;
			state.gasCleaned += gas;
			while(state.ashBuffer >= 1f)
			{
				state.ashBuffer -= 1f;
				state.ashCollected++;
				state.ashOutput.insertOrDrop(new ItemStack(MiscEnum.FlyAsh.getItem(ItemCategoryFlags.POWDER)), context.getLevel());
			}
			state.renderAsActive = true;
		}
		else
		{
			state.renderAsActive = false;
		}

		if(wasActive != state.renderAsActive)
			context.requestMasterBESync();
		if(gas >= 1)
			context.markMasterDirty();
	}

	@Override
	public void tickClient(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final Level level = context.getLevel().getRawLevel();
		if(!state.renderAsActive || level.getGameTime() % 4 != 0)
			return;
		// Clean, mostly invisible plume leaving the stack
		Vec3 stack = context.getLevel().toAbsolute(new Vec3(2.5, 8.8, 0.5));
		level.addParticle(ParticleTypes.CLOUD, stack.x + level.getRandom().nextFloat(-0.2f, 0.2f), stack.y,
				stack.z + level.getRandom().nextFloat(-0.2f, 0.2f), 0, 0.08, 0);
		// Corona sparks inside the electrode fields
		Vec3 field = context.getLevel().toAbsolute(new Vec3(2.5, 3.5, 1.5));
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, field.x + level.getRandom().nextFloat(-0.8f, 0.8f),
				field.y + level.getRandom().nextFloat(-1f, 1f), field.z + level.getRandom().nextFloat(-0.6f, 0.6f), 0, 0, 0);
	}

	@Override
	public void dropExtraItems(State state, Consumer<ItemStack> drop)
	{
	}

	@Override
	public State createInitialState(IInitialMultiblockContext<State> capability)
	{
		return new State(capability);
	}

	@Override
	public Function<BlockPos, VoxelShape> shapeGetter(ShapeType shapeType)
	{
		return ElectrostaticPrecipitatorShape.GETTER;
	}

	@Override
	public <T> LazyOptional<T> getCapability(IMultiblockContext<State> ctx, CapabilityPosition position, Capability<T> cap)
	{
		final State state = ctx.getState();
		if(cap == ForgeCapabilities.ENERGY && (position.side() == null || ENERGY_INPUT.equals(position)))
			return state.energyCap.cast(ctx);
		if(cap == ForgeCapabilities.FLUID_HANDLER && GAS_INPUT.equalsOrNullFace(position))
			return state.gasCap.cast(ctx);
		return LazyOptional.empty();
	}

	@Nullable
	@Override
	public List<Component> getOverlayText(State state, Player player, boolean isServer)
	{
		if(state == null)
			return null;
		List<Component> lines = new ArrayList<>();
		if(state.gasTank.getFluidAmount() >= GAS_CAPACITY - 64)
			lines.add(Component.translatable("immersivegeology.electrostatic_precipitator.backpressure").withStyle(ChatFormatting.RED));
		lines.add(Component.translatable("immersivegeology.electrostatic_precipitator.gas", state.gasTank.getFluidAmount(), GAS_CAPACITY));
		lines.add(Component.translatable("immersivegeology.electrostatic_precipitator.ash", state.ashCollected));
		lines.add(Component.translatable("immersivegeology.electrostatic_precipitator.efficiency",
				String.format("%.1f", CAPTURE_EFFICIENCY * 100f)));
		return lines;
	}

	public static class State implements IGMultiblockState
	{
		public final RedstoneControl.RSState rsState = RedstoneControl.RSState.enabledByDefault();
		public final AveragingEnergyStorage energy = new AveragingEnergyStorage(ENERGY_CAPACITY);
		public final FluidTank gasTank = new FluidTank(GAS_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.FlueGas.getFluid(BlockCategoryFlags.FLUID));

		public float ashBuffer = 0f;
		public int ashCollected = 0;
		public int gasCleaned = 0;
		public boolean renderAsActive = false;

		private final StoredCapability<IEnergyStorage> energyCap;
		private final StoredCapability<IFluidHandler> gasCap;
		private final DroppingMultiblockOutput ashOutput;

		public State(IInitialMultiblockContext<State> ctx)
		{
			Runnable changedAndSync = () -> {
				ctx.getMarkDirtyRunnable().run();
				ctx.getSyncRunnable().run();
			};
			this.energyCap = new StoredCapability<>(IGReceiveOnlyEnergy.of(this.energy));
			this.gasCap = new StoredCapability<>(new ArrayFluidHandler(gasTank, false, true, changedAndSync));
			this.ashOutput = new DroppingMultiblockOutput(ASH_OUTPUT, ctx);
		}

		@Override
		public void readSaveNBT(CompoundTag nbt)
		{
			gasTank.readFromNBT(nbt.getCompound("gas"));
			energy.deserializeNBT(nbt.get("energy"));
			ashBuffer = nbt.getFloat("ash_buffer");
			ashCollected = nbt.getInt("ash_collected");
			gasCleaned = nbt.getInt("gas_cleaned");
		}

		@Override
		public void writeSaveNBT(CompoundTag nbt)
		{
			nbt.put("gas", gasTank.writeToNBT(new CompoundTag()));
			nbt.put("energy", energy.serializeNBT());
			nbt.putFloat("ash_buffer", ashBuffer);
			nbt.putInt("ash_collected", ashCollected);
			nbt.putInt("gas_cleaned", gasCleaned);
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
			gasCap.get(ctx).invalidate();
		}
	}
}
