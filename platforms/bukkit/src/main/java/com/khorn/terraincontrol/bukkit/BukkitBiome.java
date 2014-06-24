package com.khorn.terraincontrol.bukkit;

import net.minecraft.world.biome.BiomeGenBase;

import com.khorn.terraincontrol.BiomeIds;
import com.khorn.terraincontrol.LocalBiome;
import com.khorn.terraincontrol.bukkit.util.WorldHelper;
import com.khorn.terraincontrol.configuration.BiomeConfig;

/**
 * The BukkitBiome is basically a wrapper for the BiomeBase. If you look at
 * the constructor and the method you will see that this is the case.
 */
public class BukkitBiome implements LocalBiome
{
    private final BiomeGenBase biomeBase;
    private final boolean isCustom;

    private final BiomeIds biomeIds;
    private final BiomeConfig biomeConfig;

    /**
     * Wraps the vanilla biome into a LocalBiome instance.
     * 
     * @param biome The vanilla biome to wrap.
     * @return The wrapped biome.
     */
    public static BukkitBiome forVanillaBiome(BiomeConfig biomeConfig, BiomeGenBase biome)
    {
        return new BukkitBiome(biomeConfig, biome);
    }

    /**
     * Creates and registers a new custom biome with the given name and ids.
     * 
     * @param name Name of the custom biome.
     * @param biomeIds Ids of the custom biome.
     * @return The custom biome.
     */
    public static BukkitBiome forCustomBiome(BiomeConfig biomeConfig, BiomeIds biomeIds)
    {
        return new BukkitBiome(biomeConfig, CustomBiome.createInstance(biomeConfig.name, biomeIds));
    }

    protected BukkitBiome(BiomeConfig biomeConfig, BiomeGenBase biome)
    {
        this.biomeBase = biome;
        this.biomeIds = new BiomeIds(WorldHelper.getGenerationId(biomeBase), biomeBase.biomeID);
        this.biomeConfig = biomeConfig;
        this.isCustom = biome instanceof CustomBiome;
    }

    @Override
    public boolean isCustom()
    {
        return this.isCustom;
    }

    public BiomeGenBase getHandle()
    {
        return biomeBase;
    }

    @Override
    public void setEffects()
    {
        if (isCustom)
        {
            ((CustomBiome) this.biomeBase).setEffects(this.biomeConfig);
        }
    }

    @Override
    public String getName()
    {
        return this.biomeConfig.name;
    }

    @Override
    public BiomeIds getIds()
    {
        return this.biomeIds;
    }

    @Override
    public float getTemperatureAt(int x, int y, int z)
    {
        return this.biomeBase.getFloatTemperature(x, y, z);
    }

    @Override
    public BiomeConfig getBiomeConfig()
    {
        return this.biomeConfig;
    }
}