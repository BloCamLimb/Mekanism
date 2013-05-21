package mekanism.common;

import java.util.Map;

import mekanism.common.BlockMachine.MachineType;
import mekanism.common.RecipeHandler.Recipe;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class TileEntityCombiner extends TileEntityAdvancedElectricMachine
{
	public TileEntityCombiner()
	{
		super("Combiner.ogg", "Combiner", "/mods/mekanism/gui/GuiCombiner.png", 50, 1, 200, MachineType.COMBINER.baseEnergy, 200);
	}
	
	@Override
	public Map getRecipes()
	{
		return Recipe.COMBINER.get();
	}
	
	@Override
	public int getFuelTicks(ItemStack itemstack)
	{
		if(itemstack.getItem() instanceof ItemBlock && itemstack.itemID == Block.cobblestone.blockID)
		{
			return 200;
		}
		return 0;
	}
}
