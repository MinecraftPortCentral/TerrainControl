package com.khorn.terraincontrol.bukkit.generator;

import com.khorn.terraincontrol.bukkit.BukkitWorld;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;

/**
 * We extend this file to be able to set the sea level.
 * In Minecraft this is used in a few places such as spawning algorithms for villages.
 * The value seem to be hardcoded in CraftWorld and we are a bit unsure about if that matters.
 * At least it should be a good thing that we set the value here.
 */
public class TCWorldProvider extends WorldProviderSurface
{
    protected BukkitWorld localWorld;
    private final WorldProvider oldWorldProvider;

    public TCWorldProvider(BukkitWorld localWorld, WorldProvider oldWorldProvider)
    {
        this.localWorld = localWorld;
        this.oldWorldProvider = oldWorldProvider;
        super.registerWorld(localWorld.getWorld());
        super.isHellWorld = oldWorldProvider.isHellWorld;
        super.hasNoSky = oldWorldProvider.hasNoSky;
    }

    @Override
    public int getAverageGroundLevel ()
    {
        return localWorld.getSettings().worldConfig.waterLevelMax;
    }

    @Override
    public String getDimensionName()
    {
        return "Overworld";
    }

    /**
     * Returns the world provider that was replaced by the current world provider.
     * When the plugin disables, this needs to be restored.
     * 
     * @return The old world provider.
     */
    public WorldProvider getOldWorldProvider()
    {
        return oldWorldProvider;
    }
}