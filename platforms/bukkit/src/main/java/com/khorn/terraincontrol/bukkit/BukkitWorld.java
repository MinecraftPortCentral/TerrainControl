package com.khorn.terraincontrol.bukkit;

import com.khorn.terraincontrol.*;
import com.khorn.terraincontrol.bukkit.generator.BiomeCacheWrapper;
import com.khorn.terraincontrol.bukkit.generator.TCChunkGenerator;
import com.khorn.terraincontrol.bukkit.generator.TCWorldChunkManager;
import com.khorn.terraincontrol.bukkit.generator.TCWorldProvider;
import com.khorn.terraincontrol.bukkit.generator.structures.*;
import com.khorn.terraincontrol.bukkit.util.NBTHelper;
import com.khorn.terraincontrol.configuration.BiomeConfig;
import com.khorn.terraincontrol.configuration.BiomeLoadInstruction;
import com.khorn.terraincontrol.configuration.WorldConfig;
import com.khorn.terraincontrol.configuration.WorldSettings;
import com.khorn.terraincontrol.customobjects.CustomObjectStructureCache;
import com.khorn.terraincontrol.generator.biome.BiomeGenerator;
import com.khorn.terraincontrol.generator.biome.OldBiomeGenerator;
import com.khorn.terraincontrol.generator.biome.OutputType;
import com.khorn.terraincontrol.logging.LogMarker;
import com.khorn.terraincontrol.util.ChunkCoordinate;
import com.khorn.terraincontrol.util.NamedBinaryTag;
import com.khorn.terraincontrol.util.minecraftTypes.DefaultBiome;
import com.khorn.terraincontrol.util.minecraftTypes.DefaultMaterial;
import com.khorn.terraincontrol.util.minecraftTypes.TreeType;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraft.world.gen.feature.WorldGenBigTree;
import net.minecraft.world.gen.feature.WorldGenCanopyTree;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenForest;
import net.minecraft.world.gen.feature.WorldGenMegaJungle;
import net.minecraft.world.gen.feature.WorldGenMegaPineTree;
import net.minecraft.world.gen.feature.WorldGenSavannaTree;
import net.minecraft.world.gen.feature.WorldGenShrub;
import net.minecraft.world.gen.feature.WorldGenSwamp;
import net.minecraft.world.gen.feature.WorldGenTaiga1;
import net.minecraft.world.gen.feature.WorldGenTaiga2;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraft.world.gen.structure.MapGenStructure;

import org.bukkit.craftbukkit.CraftWorld;

import java.util.*;

public class BukkitWorld implements LocalWorld
{
    // Initially false, set to true when enabled once
    private boolean initialized;

    private TCChunkGenerator generator;
    private WorldServer world;
    private WorldSettings settings;
    private CustomObjectStructureCache structureCache;
    private String name;
    private BiomeGenerator biomeManager;

    private static int nextBiomeId = DefaultBiome.values().length;

    private static final int MAX_BIOMES_COUNT = 1024;
    private static final int MAX_SAVED_BIOMES_COUNT = 256;
    private static final int STANDARD_WORLD_HEIGHT = 128;

    private final Map<String, LocalBiome> biomeNames = new HashMap<String, LocalBiome>();

    public StrongholdGen strongholdGen;
    public VillageGen villageGen;
    public MineshaftGen mineshaftGen;
    public RareBuildingGen rareBuildingGen;
    public NetherFortressGen netherFortressGen;

    private WorldGenTrees tree;
    private WorldGenSavannaTree acaciaTree;
    private WorldGenBigTree bigTree;
    private WorldGenForest birchTree;
    private WorldGenTrees cocoaTree;
    private WorldGenCanopyTree darkOakTree;
    private WorldGenShrub groundBush;
    private WorldGenBigMushroom hugeMushroom;
    private WorldGenMegaPineTree hugeTaigaTree1;
    private WorldGenMegaPineTree hugeTaigaTree2;
    private WorldGenMegaJungle jungleTree;
    private WorldGenForest longBirchTree;
    private WorldGenSwamp swampTree;
    private WorldGenTaiga1 taigaTree1;
    private WorldGenTaiga2 taigaTree2;

    private Chunk[] chunkCache;

    private BiomeGenBase[] biomeGenBaseArray;

    public BukkitWorld(String _name)
    {
        this.name = _name;
    }

    @Override
    public LocalBiome createBiomeFor(BiomeConfig biomeConfig, BiomeIds biomeIds)
    {
        BukkitBiome biome;
        if (biomeConfig.defaultSettings.isCustomBiome)
        {
            biome = BukkitBiome.forCustomBiome(biomeConfig, biomeIds);
        } else
        {
            biome = BukkitBiome.forVanillaBiome(biomeConfig, BiomeGenBase.getBiome(biomeIds.getSavedId()));
        }

        this.biomeNames.put(biome.getName(), biome);

        return biome;
    }

    @Override
    public int getMaxBiomesCount()
    {
        return MAX_BIOMES_COUNT;
    }

    @Override
    public int getMaxSavedBiomesCount()
    {
        return MAX_SAVED_BIOMES_COUNT;
    }

    @Override
    public int getFreeBiomeId()
    {
        return nextBiomeId++;
    }

    @Override
    public BukkitBiome getBiomeById(int id)
    {
        return (BukkitBiome) settings.biomes[id];
    }

    @Override
    public LocalBiome getBiomeByName(String name)
    {
        return this.biomeNames.get(name);
    }

    @Override
    public Collection<? extends BiomeLoadInstruction> getDefaultBiomes()
    {
        // Loop through all default biomes and create the default
        // settings for them
        List<BiomeLoadInstruction> standardBiomes = new ArrayList<BiomeLoadInstruction>();
        for (DefaultBiome defaultBiome : DefaultBiome.values())
        {
            int id = defaultBiome.Id;
            BiomeLoadInstruction instruction = defaultBiome.getLoadInstructions(BukkitMojangSettings.fromId(id), STANDARD_WORLD_HEIGHT);
            standardBiomes.add(instruction);
        }

        return standardBiomes;
    }

    @Override
    public int[] getBiomesUnZoomed(int[] biomeArray, int x, int z, int x_size, int z_size, OutputType outputType)
    {
        if (this.biomeManager != null)
            return this.biomeManager.getBiomesUnZoomed(biomeArray, x, z, x_size, z_size, outputType);

        biomeGenBaseArray = this.world.provider.worldChunkMgr.getBiomesForGeneration(biomeGenBaseArray, x, z, x_size, z_size);
        if (biomeArray == null || biomeArray.length < x_size * z_size)
            biomeArray = new int[x_size * z_size];
        for (int i = 0; i < x_size * z_size; i++)
            biomeArray[i] = biomeGenBaseArray[i].biomeID;
        return biomeArray;
    }

    @Override
    public int[] getBiomes(int[] biomeArray, int x, int z, int x_size, int z_size, OutputType outputType)
    {
        if (this.biomeManager != null)
            return this.biomeManager.getBiomes(biomeArray, x, z, x_size, z_size, outputType);

        biomeGenBaseArray = this.world.provider.worldChunkMgr.getBiomeGenAt(biomeGenBaseArray, x, z, x_size, z_size, true);
        if (biomeArray == null || biomeArray.length < x_size * z_size)
            biomeArray = new int[x_size * z_size];
        for (int i = 0; i < x_size * z_size; i++)
            biomeArray[i] = biomeGenBaseArray[i].biomeID;
        return biomeArray;
    }

    @Override
    public int getCalculatedBiomeId(int x, int z)
    {
        if (this.biomeManager != null)
            return this.biomeManager.getBiome(x, z);
        return this.world.provider.worldChunkMgr.getBiomeGenAt(x, z).biomeID;
    }

    @Override
    public double getBiomeFactorForOldBM(int index)
    {
        OldBiomeGenerator oldBiomeGenerator = (OldBiomeGenerator) this.biomeManager;
        return oldBiomeGenerator.oldTemperature1[index] * oldBiomeGenerator.oldWetness[index];
    }

    @Override
    public void prepareDefaultStructures(int chunkX, int chunkZ, boolean dry) // prepareDefaultStructures
    {
        if (this.settings.worldConfig.strongholdsEnabled)
            ((MapGenStructure)this.strongholdGen).func_151539_a(null, this.world, chunkX, chunkZ, null); // Cast to MapGenStructure to fix mapping issue
        if (this.settings.worldConfig.mineshaftsEnabled)
            ((MapGenStructure)this.mineshaftGen).func_151539_a(null, this.world, chunkX, chunkZ, null);
        if (this.settings.worldConfig.villagesEnabled && dry)
            ((MapGenStructure)this.villageGen).func_151539_a(null, this.world, chunkX, chunkZ, null);
        if (this.settings.worldConfig.rareBuildingsEnabled)
            ((MapGenStructure)this.rareBuildingGen).func_151539_a(null, this.world, chunkX, chunkZ, null);
        if (this.settings.worldConfig.netherFortressesEnabled)
            ((MapGenStructure)this.netherFortressGen).func_151539_a(null, this.world, chunkX, chunkZ, null);
    }

    @Override
    public void PlaceDungeons(Random rand, int x, int y, int z)
    {
        new WorldGenDungeons().generate(this.world, rand, x, y, z);
    }

    @Override
    public boolean PlaceTree(TreeType type, Random rand, int x, int y, int z)
    {
        switch (type)
        {
            case Tree:
                return tree.generate(this.world, rand, x, y, z);
            case BigTree:
                bigTree.setScale(1.0D, 1.0D, 1.0D);
                return bigTree.generate(this.world, rand, x, y, z);
            case Forest:
            case Birch:
                return birchTree.generate(this.world, rand, x, y, z);
            case TallBirch:
                return longBirchTree.generate(this.world, rand, x, y, z);
            case HugeMushroom:
                hugeMushroom.setScale(1.0D, 1.0D, 1.0D);
                return hugeMushroom.generate(this.world, rand, x, y, z);
            case SwampTree:
                return swampTree.generate(this.world, rand, x, y, z);
            case Taiga1:
                return taigaTree1.generate(this.world, rand, x, y, z);
            case Taiga2:
                return taigaTree2.generate(this.world, rand, x, y, z);
            case JungleTree:
                return jungleTree.generate(this.world, rand, x, y, z);
            case GroundBush:
                return groundBush.generate(this.world, rand, x, y, z);
            case CocoaTree:
                return cocoaTree.generate(this.world, rand, x, y, z);
            case Acacia:
                return acaciaTree.generate(this.world, rand, x, y, z);
            case DarkOak:
                return darkOakTree.generate(this.world, rand, x, y, z);
            case HugeTaiga1:
                return hugeTaigaTree1.generate(this.world, rand, x, y, z);
            case HugeTaiga2:
                return hugeTaigaTree2.generate(this.world, rand, x, y, z);
            default:
                throw new AssertionError("Failed to handle tree of type " + type.toString());
        }
    }

    @Override
    public boolean placeDefaultStructures(Random rand, ChunkCoordinate chunkCoord)
    {
        int chunkX = chunkCoord.getChunkX();
        int chunkZ = chunkCoord.getChunkZ();

        boolean isVillagePlaced = false;
        if (this.settings.worldConfig.strongholdsEnabled)
            ((MapGenStructure)this.strongholdGen).generateStructuresInChunk(this.world, rand, chunkX, chunkZ);
        if (this.settings.worldConfig.mineshaftsEnabled)
            ((MapGenStructure)this.mineshaftGen).generateStructuresInChunk(this.world, rand, chunkX, chunkZ);
        if (this.settings.worldConfig.villagesEnabled)
            isVillagePlaced = ((MapGenStructure)this.villageGen).generateStructuresInChunk(this.world, rand, chunkX, chunkZ);
        if (this.settings.worldConfig.rareBuildingsEnabled)
            ((MapGenStructure)this.rareBuildingGen).generateStructuresInChunk(this.world, rand, chunkX, chunkZ);
        if (this.settings.worldConfig.netherFortressesEnabled)
            ((MapGenStructure)this.netherFortressGen).generateStructuresInChunk(this.world, rand, chunkX, chunkZ);

        return isVillagePlaced;
    }

    @Override
    public void replaceBlocks()
    {
        if (this.settings.worldConfig.BiomeConfigsHaveReplacement)
        {
            // Like all other populators, this populator uses an offset of 8
            // blocks from the chunk start.
            // This is what happens when the top left chunk has it's biome
            // replaced:
            // +--------+--------+ . = no changes in biome for now
            // |........|........| # = biome is replaced
            // |....####|####....|
            // |....####|####....| The top left chunk is saved as chunk 0
            // +--------+--------+ in the cache, the top right chunk as 1,
            // |....####|####....| the bottom left as 2 and the bottom
            // |....####|####....| right chunk as 3.
            // |........|........|
            // +--------+--------+
            replaceBlocks(this.chunkCache[0], 8, 8);
            replaceBlocks(this.chunkCache[1], 0, 8);
            replaceBlocks(this.chunkCache[2], 8, 0);
            replaceBlocks(this.chunkCache[3], 0, 0);
        }
    }

    private void replaceBlocks(Chunk rawChunk, int startXInChunk, int startZInChunk)
    {
        int endXInChunk = startXInChunk + 8;
        int endZInChunk = startZInChunk + 8;
        int worldStartX = rawChunk.xPosition * 16;
        int worldStartZ = rawChunk.zPosition * 16;

        ExtendedBlockStorage[] sectionsArray = rawChunk.getBlockStorageArray();

        for (ExtendedBlockStorage section : sectionsArray)
        {
            if (section == null)
                continue;

            for (int sectionX = startXInChunk; sectionX < endXInChunk; sectionX++)
            {
                for (int sectionZ = startZInChunk; sectionZ < endZInChunk; sectionZ++)
                {
                    LocalBiome biome = this.getCalculatedBiome(worldStartX + sectionX, worldStartZ + sectionZ);
                    if (biome != null && biome.getBiomeConfig().replacedBlocks.hasReplaceSettings())
                    {
                        LocalMaterialData[][] replaceArray = biome.getBiomeConfig().replacedBlocks.compiledInstructions;
                        for (int sectionY = 0; sectionY < 16; sectionY++)
                        {
                            Block block = section.getBlockByExtId(sectionX, sectionY, sectionZ);
                            int blockId = Block.getIdFromBlock(block);
                            if (replaceArray[blockId] == null)
                                continue;

                            int y = section.getYLocation() + sectionY;
                            if (y >= replaceArray[blockId].length)
                                break;

                            BukkitMaterialData replaceTo = (BukkitMaterialData) replaceArray[blockId][y];
                            if (replaceTo == null || replaceTo.getBlockId() == blockId)
                                continue;

                            // section.setBlock(....)
                            section.func_150818_a(sectionX, sectionY, sectionZ, replaceTo.internalBlock());
                            section.setExtBlockMetadata(sectionX, sectionY, sectionZ, replaceTo.getBlockData());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void placePopulationMobs(LocalBiome biome, Random random, ChunkCoordinate chunkCoord)
    {
        SpawnerAnimals.performWorldGenSpawning(this.world, ((BukkitBiome) biome).getHandle(), chunkCoord.getChunkX() * 16 + 8, chunkCoord.getChunkZ() * 16 + 8, 16, 16, random);
    }

    private Chunk getChunk(int x, int y, int z)
    {
        if (y < TerrainControl.WORLD_DEPTH || y >= TerrainControl.WORLD_HEIGHT)
            return null;

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (this.chunkCache == null)
        {
            // Blocks requested outside population step
            // (Tree growing, /tc spawn, etc.)
           return world.getChunkFromChunkCoords(chunkX, chunkZ); 
        }

        // Restrict to chunks we are currently populating
        Chunk topLeftCachedChunk = this.chunkCache[0];
        int indexX = (chunkX - topLeftCachedChunk.xPosition);
        int indexZ = (chunkZ - topLeftCachedChunk.zPosition);
        if ((indexX == 0 || indexX == 1) && (indexZ == 0 || indexZ == 1))
        {
            return this.chunkCache[indexX | (indexZ << 1)];
        } else
        {
            // Outside area
            if (this.settings.worldConfig.populationBoundsCheck)
            {
                return null;
            }
            if (world.getChunkProvider().chunkExists(chunkX, chunkZ))
            {
                return world.getChunkFromChunkCoords(chunkX, chunkZ);
            }
            return null;
        }
    }

    @Override
    public int getLiquidHeight(int x, int z)
    {
        for (int y = getHighestBlockYAt(x, z) - 1; y > 0; y--)
        {
            LocalMaterialData material = getMaterial(x, y, z);
            if (material.isLiquid())
            {
                return y + 1;
            } else if (material.isSolid())
            {
                // Failed to find a liquid
                return -1;
            }
        }
        return -1;
    }

    @Override
    public int getSolidHeight(int x, int z)
    {
        for (int y = getHighestBlockYAt(x, z) - 1; y > 0; y--)
        {
            LocalMaterialData material = getMaterial(x, y, z);
            if (material.isSolid())
            {
                return y + 1;
            }
        }
        return -1;
    }

    @Override
    public boolean isEmpty(int x, int y, int z)
    {
        Chunk chunk = this.getChunk(x, y, z);
        if (chunk == null)
        {
            return true;
        }
        return chunk.getBlock(x & 0xF, y, z & 0xF).getMaterial().equals(Material.air);
    }

    @Override
    public LocalMaterialData getMaterial(int x, int y, int z)
    {
        Chunk chunk = this.getChunk(x, y, z);
        if (chunk == null)
        {
            return new BukkitMaterialData(DefaultMaterial.AIR, 0);
        }

        z &= 0xF;
        x &= 0xF;

        return new BukkitMaterialData(chunk.getBlock(x, y, z), chunk.getBlockMetadata(x, y, z));
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material)
    {
        /*
         * This method usually breaks on every Minecraft update. Always check
         * whether the names are still correct. Often, you'll also need to
         * rewrite parts of this method for newer block place logic.
         */

        if (y < TerrainControl.WORLD_DEPTH || y >= TerrainControl.WORLD_HEIGHT)
        {
            return;
        }

        Block block = ((BukkitMaterialData) material).internalBlock();

        // Get chunk from (faster) custom cache
        Chunk chunk = this.getChunk(x, y, z);

        if (chunk == null)
        {
            // Chunk is unloaded
            return;
        }

        // Temporarily make remote, so that torches etc. don't pop off
        boolean oldStatic = world.isRemote;
        world.isRemote = true;
        chunk.func_150807_a(x & 15, y, z & 15, block, material.getBlockData());
        world.isRemote = oldStatic;

        // Relight and update players
        world.func_147451_t(x, y, z); // world.updateAllLightTypes
        if (!world.isRemote)
        {
            world.markBlockForUpdate(x, y, z);
        }
    }

    @Override
    public int getHighestBlockYAt(int x, int z)
    {
        Chunk chunk = this.getChunk(x, 0, z);
        if (chunk == null)
        {
            return -1;
        }
        z &= 0xF;
        x &= 0xF;
        int y = chunk.getHeightValue(x, z);
        int maxSearchY = y + 5; // Don't search too far away
        // while(chunk.getBlock(...) != ...)
        while (chunk.getBlock(x, y, z) != Blocks.air && y <= maxSearchY)
        {
            // Fix for incorrect lightmap
            y += 1;
        }
        return y;
    }

    @Override
    public void startPopulation(ChunkCoordinate chunkCoord)
    {
        if (this.chunkCache != null)
        {
            throw new IllegalStateException("Chunk is already being populated");
        }

        // Initialize cache
        this.chunkCache = new Chunk[4];
        for (int indexX = 0; indexX <= 1; indexX++)
        {
            for (int indexZ = 0; indexZ <= 1; indexZ++)
            {
                this.chunkCache[indexX | (indexZ << 1)] = world.getChunkFromChunkCoords(chunkCoord.getChunkX() + indexX, chunkCoord.getChunkZ() + indexZ);
            }
        }
    }

    @Override
    public void endPopulation()
    {
        if (this.chunkCache == null)
        {
            throw new IllegalStateException("Population has already ended");
        }
        this.chunkCache = null;
    }

    @Override
    public int getLightLevel(int x, int y, int z)
    {
        // Actually, this calculates the block and skylight as it were day.
        return world.getFullBlockLightValue(x, y, z);
    }

    @Override
    public boolean isLoaded(int x, int y, int z)
    {
        return this.getChunk(x, y, z) != null;
    }

    @Override
    public WorldSettings getSettings()
    {
        return this.settings;
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public long getSeed()
    {
        return world.getSeed();
    }

    @Override
    public int getHeightCap()
    {
        return settings.worldConfig.worldHeightCap;
    }

    @Override
    public int getHeightScale()
    {
        return settings.worldConfig.worldHeightScale;
    }

    public TCChunkGenerator getChunkGenerator()
    {
        return this.generator;
    }

    public World getWorld()
    {
        return this.world;
    }

    /**
     * Sets the new settings and deprecates any references to the old
     * settings, if any.
     * 
     * @param worldConfig The new settings.
     */
    public void setSettings(WorldSettings newSettings)
    {
        if (this.settings == null)
        {
            this.settings = newSettings;
        } else
        {
            throw new IllegalStateException("Settings are already set");
        }
    }

    /**
     * Loads all settings again from disk.
     */
    public void reloadSettings()
    {
        this.biomeNames.clear();
        this.settings.reload();
    }

    /**
     * Enables/reloads this BukkitWorld. If you are reloading, don't forget to
     * set the new settings first using {@link #setSettings(WorldConfig)}.
     * 
     * @param world The world that needs to be enabled.
     */
    public void enable(org.bukkit.World world)
    {
        WorldServer mcWorld = ((CraftWorld) world).getHandle();

        // Do the things that always need to happen, whether we are enabling
        // for the first time or reloading
        this.world = mcWorld;

        // Inject our own WorldProvider
        if (mcWorld.provider.getDimensionName().equals("Overworld"))
        {
            // Only replace the worldProvider if it's the overworld
            // Replacing other dimensions causes a lot of glitches
            mcWorld.provider = new TCWorldProvider(this, this.world.provider);
        }

        // Inject our own BiomeManager (called WorldChunkManager)
        Class<? extends BiomeGenerator> biomeModeClass = this.settings.worldConfig.biomeMode;
        if (biomeModeClass != TerrainControl.getBiomeModeManager().VANILLA)
        {
            TCWorldChunkManager worldChunkManager = new TCWorldChunkManager(this);
            mcWorld.provider.worldChunkMgr = worldChunkManager;

            BiomeGenerator biomeManager = TerrainControl.getBiomeModeManager().create(biomeModeClass, this,
                    new BiomeCacheWrapper(worldChunkManager));
            worldChunkManager.setBiomeManager(biomeManager);
            setBiomeManager(biomeManager);
        }

        if (!initialized)
        {
            // Things that need to be done only when enabling
            // for the first time
            this.structureCache = new CustomObjectStructureCache(this);

            switch (this.settings.worldConfig.ModeTerrain)
            {
                case Normal:
                case OldGenerator:
                    this.strongholdGen = new StrongholdGen(settings);
                    this.villageGen = new VillageGen(settings);
                    this.mineshaftGen = new MineshaftGen();
                    this.rareBuildingGen = new RareBuildingGen(settings);
                    this.netherFortressGen = new NetherFortressGen();
                case NotGenerate:
                case TerrainTest:
                    this.generator.onInitialize(this);
                    break;
                case Default:
                    break;
            }

        this.tree = new WorldGenTrees(false);
        this.acaciaTree = new WorldGenSavannaTree(false);
        this.cocoaTree = new WorldGenTrees(false, 5, 3, 3, true);
        this.bigTree = new WorldGenBigTree(false);
        this.birchTree = new WorldGenForest(false, false);
        this.darkOakTree = new WorldGenCanopyTree(false);
        this.longBirchTree = new WorldGenForest(false, true);
        this.swampTree = new WorldGenSwamp();
        this.taigaTree1 = new WorldGenTaiga1();
        this.taigaTree2 = new WorldGenTaiga2(false);
        this.hugeMushroom = new WorldGenBigMushroom();
        this.hugeTaigaTree1 = new WorldGenMegaPineTree(false, false);
        this.hugeTaigaTree2 = new WorldGenMegaPineTree(false, true);
        this.jungleTree = new WorldGenMegaJungle(false, 10, 20, 3, 3);
        this.groundBush = new WorldGenShrub(3, 0);

            this.initialized = true;
        } else
        {
            // Things that need to be done only on reloading
            this.structureCache.reload(this);
        }
    }

    /**
     * Cleans up references of itself in Minecraft's native code.
     */
    public void disable()
    {
        // Restore old world provider if replaced
        if (world.provider instanceof TCWorldProvider)
        {
            world.provider = ((TCWorldProvider) world.provider).getOldWorldProvider();
        }
    }

    public void setChunkGenerator(TCChunkGenerator _generator)
    {
        this.generator = _generator;
    }

    public void setBiomeManager(BiomeGenerator manager)
    {
        this.biomeManager = manager;
    }

    @Override
    public BukkitBiome getCalculatedBiome(int x, int z)
    {
        return getBiomeById(getCalculatedBiomeId(x, z));
    }

    @Override
    public int getBiomeId(int x, int z)
    {
        return world.getBiomeGenForCoords(x, z).biomeID;
    }

    @Override
    public LocalBiome getBiome(int x, int z)
    {
        return getBiomeById(world.getBiomeGenForCoords(x, z).biomeID);
    }

    @Override
    public void attachMetadata(int x, int y, int z, NamedBinaryTag tag)
    {
        // Convert Tag to a native nms tag
        NBTTagCompound nmsTag = NBTHelper.getNMSFromNBTTagCompound(tag);
        // Add the x, y and z position to it
        nmsTag.setInteger("x", x);
        nmsTag.setInteger("y", y);
        nmsTag.setInteger("z", z);
        // Add that data to the current tile entity in the world
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity != null)
        {
            tileEntity.readFromNBT(nmsTag);
        } else
        {
            TerrainControl.log(LogMarker.DEBUG, "Skipping tile entity with id {}, cannot be placed at {},{},{} on id {}", new Object[] {
                    nmsTag.getString("id"), x, y, z, getMaterial(x, y, z)});
        }
    }

    @Override
    public NamedBinaryTag getMetadata(int x, int y, int z)
    {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity == null)
        {
            return null;
        }
        NBTTagCompound nmsTag = new NBTTagCompound();
        tileEntity.writeToNBT(nmsTag);
        nmsTag.removeTag("x");
        nmsTag.removeTag("y");
        nmsTag.removeTag("z");
        return NBTHelper.getNBTFromNMSTagCompound(null, nmsTag);
    }

    @Override
    public CustomObjectStructureCache getStructureCache()
    {
        return this.structureCache;
    }

    @Override
    public boolean canBiomeManagerGenerateUnzoomed()
    {
        if (this.biomeManager != null)
            return biomeManager.canGenerateUnZoomed();
        return true;
    }

}
