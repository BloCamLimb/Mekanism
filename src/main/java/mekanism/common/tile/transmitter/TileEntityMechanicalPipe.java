package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.TileNetworkList;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ByteBufType;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.PipeTier;
import mekanism.common.transmitters.grid.FluidNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TileEntityMechanicalPipe extends TileEntityTransmitter<IFluidHandler, FluidNetwork, FluidStack> implements IFluidHandlerWrapper {

    public PipeTier tier = PipeTier.BASIC;

    public float currentScale;

    public FluidTank buffer = new FluidTank(Fluid.BUCKET_VOLUME);

    public FluidStack lastWrite;
    public CapabilityWrapperManager<IFluidHandlerWrapper, FluidHandlerWrapper> manager = new CapabilityWrapperManager<>(IFluidHandlerWrapper.class, FluidHandlerWrapper.class);

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = PipeTier.get(baseTier);
        buffer.setCapacity(getCapacity());
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            updateShare();
            IFluidHandler[] connectedAcceptors = PipeUtils.getConnectedAcceptors(getPos(), getWorld());
            for (EnumFacing side : getConnections(ConnectionType.PULL)) {
                IFluidHandler container = connectedAcceptors[side.ordinal()];
                if (container != null) {
                    FluidStack received = container.drain(getAvailablePull(), false);
                    if (received != null && received.amount != 0 && takeFluid(received, false) == received.amount) {
                        container.drain(takeFluid(received, true), true);
                    }
                }
            }
        }
        super.update();
    }

    @Override
    public void updateShare() {
        if (getTransmitter().hasTransmitterNetwork() && getTransmitter().getTransmitterNetworkSize() > 0) {
            FluidStack last = getSaveShare();
            if ((last != null && !(lastWrite != null && lastWrite.amount == last.amount && lastWrite.getFluid() == last.getFluid())) || (last == null && lastWrite != null)) {
                lastWrite = last;
                markDirty();
            }
        }
    }

    private FluidStack getSaveShare() {
        FluidNetwork transmitterNetwork = getTransmitter().getTransmitterNetwork();
        if (getTransmitter().hasTransmitterNetwork() && transmitterNetwork.buffer != null) {
            int remain = transmitterNetwork.buffer.amount % transmitterNetwork.transmittersSize();
            int toSave = transmitterNetwork.buffer.amount / transmitterNetwork.transmittersSize();
            if (transmitterNetwork.firstTransmitter().equals(getTransmitter())) {
                toSave += remain;
            }
            return PipeUtils.copy(transmitterNetwork.buffer, toSave);
        }
        return null;
    }

    @Override
    public void onChunkUnload() {
        if (!getWorld().isRemote && getTransmitter().hasTransmitterNetwork()) {
            if (lastWrite != null && getTransmitter().getTransmitterNetwork().buffer != null) {
                getTransmitter().getTransmitterNetwork().buffer.amount -= lastWrite.amount;
                if (getTransmitter().getTransmitterNetwork().buffer.amount <= 0) {
                    getTransmitter().getTransmitterNetwork().buffer = null;
                }
            }
        }
        super.onChunkUnload();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = PipeTier.values()[nbtTags.getInteger("tier")];
        }
        buffer.setCapacity(getCapacity());
        if (nbtTags.hasKey("cacheFluid")) {
            buffer.setFluid(FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cacheFluid")));
        } else {
            buffer.setFluid(null);
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        if (lastWrite != null && lastWrite.amount > 0) {
            nbtTags.setTag("cacheFluid", lastWrite.writeToNBT(new NBTTagCompound()));
        } else {
            nbtTags.removeTag("cacheFluid");
        }
        nbtTags.setInteger("tier", tier.ordinal());
        return nbtTags;
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.FLUID;
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @Override
    public boolean isValidAcceptor(TileEntity acceptor, EnumFacing side) {
        return PipeUtils.isValidAcceptorOnSide(acceptor, side);
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        if (!super.isValidTransmitter(tileEntity)) {
            return false;
        }
        if (!(tileEntity instanceof TileEntityMechanicalPipe)) {
            return true;
        }
        FluidStack buffer = getBufferWithFallback();
        FluidStack otherBuffer = ((TileEntityMechanicalPipe) tileEntity).getBufferWithFallback();
        return buffer == null || otherBuffer == null || buffer.isFluidEqual(otherBuffer);
    }

    @Override
    public FluidNetwork createNewNetwork() {
        return new FluidNetwork();
    }

    @Override
    public FluidNetwork createNetworkByMerging(Collection<FluidNetwork> networks) {
        return new FluidNetwork(networks);
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    public int getCapacity() {
        return tier.getPipeCapacity();
    }

    @Nullable
    @Override
    public FluidStack getBuffer() {
        return buffer == null ? null : buffer.getFluid();
    }

    @Override
    public void takeShare() {
        if (getTransmitter().hasTransmitterNetwork() && getTransmitter().getTransmitterNetwork().buffer != null && lastWrite != null) {
            getTransmitter().getTransmitterNetwork().buffer.amount -= lastWrite.amount;
            buffer.setFluid(lastWrite);
        }
    }

    @Override
    public int fill(EnumFacing from, @Nonnull FluidStack resource, boolean doFill) {
        return takeFluid(resource, doFill);
    }

    @Override
    public boolean canFill(EnumFacing from, @Nonnull FluidStack fluid) {
        return getConnectionType(from) == ConnectionType.NORMAL;
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        if (from != null && getConnectionType(from) != ConnectionType.NONE) {
            //Our buffer or the network's buffer if we have a network
            return getAllTanks();
        }
        return PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        if (getTransmitter().hasTransmitterNetwork()) {
            FluidNetwork network = getTransmitter().getTransmitterNetwork();
            return new FluidTankInfo[]{new FluidTankInfo(network.getBuffer(), network.getCapacity())};
        }
        return new FluidTankInfo[]{buffer.getInfo()};
    }

    public int getPullAmount() {
        return tier.getPipePullAmount();
    }

    @Override
    public IFluidHandler getCachedAcceptor(EnumFacing side) {
        TileEntity tile = getCachedTile(side);
        if (CapabilityUtils.hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite())) {
            return CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
        }
        return null;
    }

    public int getAvailablePull() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return Math.min(getPullAmount(), getTransmitter().getTransmitterNetwork().getFluidNeeded());
        }
        return Math.min(getPullAmount(), buffer.getCapacity() - buffer.getFluidAmount());
    }

    public int takeFluid(FluidStack fluid, boolean doEmit) {
        if (getTransmitter().hasTransmitterNetwork()) {
            return getTransmitter().getTransmitterNetwork().emit(fluid, doEmit);
        }
        return buffer.fill(fluid, doEmit);
    }

    @Override
    public boolean upgrade(int tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal == tier.ordinal() + 1) {
            tier = PipeTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public void readPacket(ByteBuf buf, ByteBufType type) {
        tier = PipeTier.values()[buf.readInt()];
        super.readPacket(buf, type);
    }

    @Override
    public void writePacket(ByteBuf buf, ByteBufType type) {
        buf.writeInt(tier.ordinal());
        super.writePacket(buf, type);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(manager.getWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }
}