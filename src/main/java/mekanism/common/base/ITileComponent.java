package mekanism.common.base;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import net.minecraft.nbt.NBTTagCompound;

public interface ITileComponent {

    void tick();

    void read(NBTTagCompound nbtTags);

    void read(ByteBuf buf);

    void write(NBTTagCompound nbtTags);

    void write(ByteBuf buf);

    void invalidate();
}