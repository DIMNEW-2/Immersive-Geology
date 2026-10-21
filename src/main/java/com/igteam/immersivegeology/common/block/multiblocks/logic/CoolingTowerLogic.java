/*
 * Muddykat
 * Copyright (c) 2025
 *
 * This code is licensed under "GNU LESSER GENERAL PUBLIC LICENSE"
 * Details can be found in the license file in the root folder of this project
 */

package com.igteam.immersivegeology.common.block.multiblocks.logic;

import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IClientTickableComponent;
import blusunrize.immersiveengineering.api.multiblocks.blocks.component.IServerTickableComponent;
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
import com.igteam.immersivegeology.common.block.multiblocks.logic.CoolingTowerLogic.State;
import com.igteam.immersivegeology.common.block.multiblocks.logic.helper.IGMultiblockState;
import com.igteam.immersivegeology.common.block.multiblocks.shapes.CoolingTowerShape;
import com.igteam.immersivegeology.core.material.data.enums.MiscEnum;
import com.igteam.immersivegeology.core.material.helper.flags.BlockCategoryFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
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
 * Natural draft hyperbolic cooling tower (градирня) - the cold sink of the circulating water loop.
 * <p>
 * Heated water from the condenser is sprayed over the fill packing near the top; air drawn in
 * through the louvers at the base rises up the shell and evaporates a small fraction of it, which
 * is what actually removes the heat. The cooled water collects in the basin and is pumped back to
 * the condenser. Being a natural draft tower it needs no power at all - its performance is set by
 * the atmosphere: cold dry air and altitude improve evaporation, rain and humid heat choke it - and
 * the evaporated fraction has to be replaced with make-up water, exactly like a real tower's basin.
 */
public class CoolingTowerLogic implements IMultiblockLogic<State>, IServerTickableComponent<State>,
		IClientTickableComponent<State>, MBOverlayText<State>
{
	public static final int HOT_CAPACITY = 16 * FluidType.BUCKET_VOLUME;
	public static final int COLD_CAPACITY = 32 * FluidType.BUCKET_VOLUME;
	public static final int MAKEUP_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
	/** mB cooled per tick in still, temperate air. */
	public static final int BASE_COOLING_RATE = 24;
	/** Share of the cooled flow that leaves the tower as evaporation and drift. */
	public static final float EVAPORATION_FRACTION = 0.02f;

	private static final CapabilityPosition HOT_INPUT = new CapabilityPosition(3, 4, 6, RelativeBlockFace.BACK);
	private static final CapabilityPosition COLD_OUTPUT = new CapabilityPosition(3, 0, 6, RelativeBlockFace.BACK);
	private static final CapabilityPosition MAKEUP_INPUT = new CapabilityPosition(0, 0, 3, RelativeBlockFace.RIGHT);
	private static final BlockPos COLD_NEIGHBOUR = new BlockPos(3, 0, 7);

	@Override
	public void tickServer(IMultiblockContext<State> context)
	{
		final State state = context.getState();
		final Level level = context.getLevel().getRawLevel();
		final boolean wasActive = state.renderAsActive;

		state.performance = ambientPerformance(context);
		int rate = Math.max(1, Math.round(BASE_COOLING_RATE * state.performance));

		boolean dryBasin = state.makeupTank.getFluidAmount() <= 0;
		if(dryBasin)
			rate /= 2;

		int moved = Math.min(rate, state.hotTank.getFluidAmount());
		moved = Math.min(moved, state.coldTank.getSpace());
		if(moved > 0)
		{
			state.hotTank.drain(moved, FluidAction.EXECUTE);
			state.coldTank.fill(new FluidStack(Fluids.WATER, moved), FluidAction.EXECUTE);
			state.evaporationBuffer += moved * EVAPORATION_FRACTION;
			while(state.evaporationBuffer >= 1f && state.makeupTank.getFluidAmount() >= 1)
			{
				state.evaporationBuffer -= 1f;
				state.makeupTank.drain(1, FluidAction.EXECUTE);
				state.evaporated++;
			}
			state.renderAsActive = true;
		}
		else
		{
			state.renderAsActive = false;
		}

		state.coldDelivered = pushFluid(state, state.coldTank, state.coldOutputCap);

		if(wasActive != state.renderAsActive)
			context.requestMasterBESync();
		if(moved > 0)
			context.markMasterDirty();
	}

	/**
	 * Evaporative cooling performance from the surrounding atmosphere. Cold air holds less moisture
	 * so it evaporates more readily, rain means the air is already saturated, and thin air at
	 * altitude lowers the partial pressure of the vapour - all three show up here.
	 */
	private float ambientPerformance(IMultiblockContext<State> context)
	{
		final Level level = context.getLevel().getRawLevel();
		final Vec3 top = context.getLevel().toAbsolute(new Vec3(3.5, 8.5, 3.5));
		BlockPos topPos = new BlockPos((int) top.x, (int) top.y, (int) top.z);
		float temperature = level.getBiome(topPos).value().getBaseTemperature();
		float performance = Mth.clamp(1.15f - 0.15f * temperature, 0.55f, 1.35f);
		if(level.isRainingAt(topPos))
			performance *= 0.85f;
		performance += Mth.clamp((topPos.getY() - 64) * 0.002f, -0.1f, 0.15f);
		return Mth.clamp(performance, 0.4f, 1.5f);
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
		if(!state.renderAsActive || level.getGameTime() % 2 != 0)
			return;
		// The evaporation plume drifting out of the shell
		Vec3 rim = context.getLevel().toAbsolute(new Vec3(3.5, 8.9, 3.5));
		level.addParticle(ParticleTypes.CLOUD, rim.x + level.getRandom().nextFloat(-2f, 2f), rim.y,
				rim.z + level.getRandom().nextFloat(-2f, 2f), 0, 0.1, 0);
		Vec3 fill = context.getLevel().toAbsolute(new Vec3(3.5, 2.4, 3.5));
		level.addParticle(ParticleTypes.SPLASH, fill.x + level.getRandom().nextFloat(-1.5f, 1.5f), fill.y,
				fill.z + level.getRandom().nextFloat(-1.5f, 1.5f), 0, 0, 0);
	}

	@Override
	public State createInitialState(IInitialMultiblockContext<State> capability)
	{
		return new State(capability);
	}

	@Override
	public Function<BlockPos, VoxelShape> shapeGetter(ShapeType shapeType)
	{
		return CoolingTowerShape.GETTER;
	}

	@Override
	public <T> LazyOptional<T> getCapability(IMultiblockContext<State> ctx, CapabilityPosition position, Capability<T> cap)
	{
		if(cap != ForgeCapabilities.FLUID_HANDLER)
			return LazyOptional.empty();
		final State state = ctx.getState();
		if(HOT_INPUT.equalsOrNullFace(position))
			return state.hotCap.cast(ctx);
		if(COLD_OUTPUT.equalsOrNullFace(position))
			return state.coldCap.cast(ctx);
		if(MAKEUP_INPUT.equalsOrNullFace(position))
			return state.makeupCap.cast(ctx);
		return LazyOptional.empty();
	}

	@Nullable
	@Override
	public List<Component> getOverlayText(State state, Player player, boolean isServer)
	{
		if(state == null)
			return null;
		List<Component> lines = new ArrayList<>();
		if(state.makeupTank.getFluidAmount() <= 0)
			lines.add(Component.translatable("immersivegeology.cooling_tower.dry_basin").withStyle(ChatFormatting.RED));
		lines.add(Component.translatable("immersivegeology.cooling_tower.performance",
				String.format("%.0f", state.performance * 100f)));
		lines.add(Component.translatable("immersivegeology.cooling_tower.basin",
				state.coldTank.getFluidAmount(), COLD_CAPACITY));
		if(Utils.isFluidRelatedItemStack(player.getItemInHand(InteractionHand.MAIN_HAND)))
		{
			lines.add(TextUtils.formatFluidStack(state.hotTank.getFluid()));
			lines.add(TextUtils.formatFluidStack(state.coldTank.getFluid()));
		}
		return lines;
	}

	public static class State implements IGMultiblockState
	{
		public final FluidTank hotTank = new FluidTank(HOT_CAPACITY,
				stack -> stack.getFluid() == MiscEnum.HotWater.getFluid(BlockCategoryFlags.FLUID));
		public final FluidTank coldTank = new FluidTank(COLD_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);
		public final FluidTank makeupTank = new FluidTank(MAKEUP_CAPACITY, stack -> stack.getFluid() == Fluids.WATER);

		public float performance = 1f;
		public float evaporationBuffer = 0f;
		public int evaporated = 0;
		public int coldDelivered = 0;
		public boolean renderAsActive = false;

		private final StoredCapability<IFluidHandler> hotCap;
		private final StoredCapability<IFluidHandler> coldCap;
		private final StoredCapability<IFluidHandler> makeupCap;
		private final CapabilityReference<IFluidHandler> coldOutputCap;

		public State(IInitialMultiblockContext<State> ctx)
		{
			Runnable changedAndSync = () -> {
				ctx.getMarkDirtyRunnable().run();
				ctx.getSyncRunnable().run();
			};
			this.hotCap = new StoredCapability<>(new ArrayFluidHandler(hotTank, false, true, changedAndSync));
			this.coldCap = new StoredCapability<>(new ArrayFluidHandler(coldTank, true, false, changedAndSync));
			this.makeupCap = new StoredCapability<>(new ArrayFluidHandler(makeupTank, false, true, changedAndSync));
			this.coldOutputCap = ctx.getCapabilityAt(ForgeCapabilities.FLUID_HANDLER, COLD_NEIGHBOUR, RelativeBlockFace.BACK);
		}

		@Override
		public void readSaveNBT(CompoundTag nbt)
		{
			hotTank.readFromNBT(nbt.getCompound("hot"));
			coldTank.readFromNBT(nbt.getCompound("cold"));
			makeupTank.readFromNBT(nbt.getCompound("makeup"));
			evaporationBuffer = nbt.getFloat("evap_buffer");
			evaporated = nbt.getInt("evaporated");
		}

		@Override
		public void writeSaveNBT(CompoundTag nbt)
		{
			nbt.put("hot", hotTank.writeToNBT(new CompoundTag()));
			nbt.put("cold", coldTank.writeToNBT(new CompoundTag()));
			nbt.put("makeup", makeupTank.writeToNBT(new CompoundTag()));
			nbt.putFloat("evap_buffer", evaporationBuffer);
			nbt.putInt("evaporated", evaporated);
		}

		@Override
		public void writeSyncNBT(CompoundTag nbt)
		{
			writeSaveNBT(nbt);
			nbt.putBoolean("renderActive", renderAsActive);
			nbt.putFloat("performance", performance);
		}

		@Override
		public void readSyncNBT(CompoundTag nbt)
		{
			readSaveNBT(nbt);
			renderAsActive = nbt.getBoolean("renderActive");
			performance = nbt.getFloat("performance");
		}

		@Override
		public void invalidate(@NotNull IMultiblockContext<?> ctx)
		{
			hotCap.get(ctx).invalidate();
			coldCap.get(ctx).invalidate();
			makeupCap.get(ctx).invalidate();
		}
	}
}
