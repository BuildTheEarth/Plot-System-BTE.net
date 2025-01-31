/*
 * The MIT License (MIT)
 *
 *  Copyright © 2021-2022, Alps BTE <bte.atchli@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.alpsbte.plotsystem.core.system.plot.generator;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.commands.BaseCommand;
import com.alpsbte.plotsystem.core.system.plot.PlotType;
import com.alpsbte.plotsystem.core.system.plot.world.OnePlotWorld;
import com.alpsbte.plotsystem.core.system.plot.world.PlotWorld;
import com.alpsbte.plotsystem.core.system.plot.world.CityPlotWorld;
import com.alpsbte.plotsystem.utils.io.config.ConfigPaths;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.core.system.plot.PlotHandler;
import com.alpsbte.plotsystem.core.system.plot.PlotManager;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.enums.Status;
import com.alpsbte.plotsystem.utils.io.language.LangPaths;
import com.alpsbte.plotsystem.utils.io.language.LangUtil;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.util.EditSessionBuilder;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public abstract class AbstractPlotGenerator {
    private final Plot plot;
    private final Builder builder;
    private final PlotWorld world;
    private final double plotVersion;
    private final PlotType plotType;

    /**
     * Generates a new plot in the plot world
     * @param plot - plot which should be generated
     * @param builder - builder of the plot
     */
    public AbstractPlotGenerator(@NotNull Plot plot, @NotNull Builder builder) throws SQLException {
        this(plot, builder, builder.getPlotTypeSetting());
    }

    /**
     * Generates a new plot in the given world
     * @param plot - plot which should be generated
     * @param builder - builder of the plot
     * @param plotType - type of the plot
     */
    public AbstractPlotGenerator(@NotNull Plot plot, @NotNull Builder builder, @NotNull PlotType plotType) throws SQLException {
        this(plot, builder, plotType, plot.getVersion() <= 2 || plotType.hasOnePlotPerWorld() ? new OnePlotWorld(plot) : new CityPlotWorld(plot));
    }

    /**
     * Generates a new plot in the given world
     * @param plot - plot which should be generated
     * @param builder - builder of the plot
     * @param world - world of the plot
     */
    private AbstractPlotGenerator(@NotNull Plot plot, @NotNull Builder builder, @NotNull PlotType plotType, @NotNull PlotWorld world) {
        this.plot = plot;
        this.builder = builder;
        this.world = world;
        this.plotVersion = plot.getVersion();
        this.plotType = plotType;

        if (init()) {
            Exception exception = null;
            try {
                if (plotType.hasOnePlotPerWorld() || !world.isWorldGenerated()) {
                    new PlotWorldGenerator(world.getWorldName());
                } else if (!world.isWorldLoaded() && !world.loadWorld()) throw new Exception("Could not load world");
                generateOutlines(plot.getOutlinesSchematic(), plotVersion >= 3 ? plot.getEnvironmentSchematic() : null);
                createPlotProtection();
            } catch (Exception ex) {
                exception = ex;
            }

            try {
                this.onComplete(exception != null, false);
            } catch (SQLException ex) {
                exception = ex;
            }

            if (exception != null) {
                PlotHandler.abandonPlot(plot);
                onException(exception);
            }
        }
    }


    /**
     * Executed before plot generation
     * @return true if initialization was successful
     */
    protected abstract boolean init();


    /**
     * Generates plot schematic and outlines
     * @param plotSchematic - plot schematic file
     * @param environmentSchematic - environment schematic file
     */
    protected void generateOutlines(@NotNull File plotSchematic, @Nullable File environmentSchematic) throws IOException, WorldEditException, SQLException {
        final class OnlyAirMask extends ExistingBlockMask {
            public OnlyAirMask(Extent extent) {
                super(extent);
            }

            @Override
            public boolean test(Vector vector) {
                return this.getExtent().getLazyBlock(vector).getType() == 0;
            }
        }

        World weWorld = new BukkitWorld(world.getBukkitWorld());
        EditSession editSession = new EditSessionBuilder(weWorld).fastmode(true).build();

        if(plotVersion >= 3 && plotType.hasEnvironment() && environmentSchematic != null && environmentSchematic.exists()){
            editSession.setMask(new OnlyAirMask(weWorld));
            pasteSchematic(editSession, environmentSchematic, world, false);
        }

        pasteSchematic(editSession, plotSchematic, world, true);

        // If the player is playing in his own world, then additionally generate the plot in the city world
        if (PlotWorld.isOnePlotWorld(world.getWorldName()) && plotVersion >= 3 && plot.getStatus() != Status.completed) {
            // Generate city plot world if it doesn't exist
            new AbstractPlotGenerator(plot, builder, PlotType.CITY_INSPIRATION_MODE) {
                @Override
                protected boolean init() {
                    return true;
                }

                @Override
                protected void createPlotProtection() {}

                @Override
                protected void onComplete(boolean failed, boolean unloadWorld) throws SQLException {
                    super.onComplete(true, true);
                }

                @Override
                protected void onException(Throwable ex) {
                    Bukkit.getLogger().log(Level.WARNING, "Could not generate plot in city world " + world.getWorldName() + "!", ex);
                }
            };
        }
    }


    /**
     * Creates plot protection
     */
    protected void createPlotProtection() throws StorageException, SQLException, IOException {
        RegionContainer regionContainer = PlotSystem.DependencyManager.getWorldGuard().getRegionContainer();
        RegionManager regionManager = regionContainer.get(world.getBukkitWorld());

        if (regionManager != null) {
            // Create build region for plot from the outline of the plot
            ProtectedRegion protectedBuildRegion = new ProtectedPolygonalRegion(world.getRegionName(), plot.getOutline(), PlotWorld.MIN_WORLD_HEIGHT, PlotWorld.MAX_WORLD_HEIGHT);
            protectedBuildRegion.setPriority(100);

            // Create protected plot region for plot
            World weWorld = new BukkitWorld(world.getBukkitWorld());
            CylinderRegion cylinderRegion = new CylinderRegion(weWorld, plot.getCenter(), new Vector2D(PlotWorld.PLOT_SIZE, PlotWorld.PLOT_SIZE), PlotWorld.MIN_WORLD_HEIGHT, PlotWorld.MAX_WORLD_HEIGHT);
            ProtectedRegion protectedRegion = new ProtectedPolygonalRegion(world.getRegionName() + "-1", cylinderRegion.polygonize(-1), PlotWorld.MIN_WORLD_HEIGHT, PlotWorld.MAX_WORLD_HEIGHT);
            protectedRegion.setPriority(50);

            // Add plot owner
            DefaultDomain owner = protectedBuildRegion.getOwners();
            owner.addPlayer(builder.getUUID());
            protectedBuildRegion.setOwners(owner);
            protectedRegion.setOwners(owner);


            // Set permissions
            protectedBuildRegion.setFlag(DefaultFlag.BUILD, StateFlag.State.ALLOW);
            protectedBuildRegion.setFlag(DefaultFlag.BUILD.getRegionGroupFlag(), RegionGroup.OWNERS);
            protectedRegion.setFlag(DefaultFlag.BUILD, StateFlag.State.DENY);
            protectedRegion.setFlag(DefaultFlag.BUILD.getRegionGroupFlag(), RegionGroup.ALL);
            protectedRegion.setFlag(DefaultFlag.ENTRY, StateFlag.State.ALLOW);
            protectedRegion.setFlag(DefaultFlag.ENTRY.getRegionGroupFlag(), RegionGroup.ALL);

            FileConfiguration config = PlotSystem.getPlugin().getConfigManager().getCommandsConfig();
            List<String> allowedCommandsNonBuilder = config.getStringList(ConfigPaths.ALLOWED_COMMANDS_NON_BUILDERS);
            allowedCommandsNonBuilder.removeIf(c -> c.equals("/cmd1"));
            for (BaseCommand baseCommand : PlotSystem.getPlugin().getCommandManager().getBaseCommands()) {
                allowedCommandsNonBuilder.addAll(Arrays.asList(baseCommand.getNames()));
                for (String command : baseCommand.getNames()) {
                    allowedCommandsNonBuilder.add("/" + command);
                }
            }
            List<String> blockedCommandsBuilders = config.getStringList(ConfigPaths.BLOCKED_COMMANDS_BUILDERS);
            blockedCommandsBuilders.removeIf(c -> c.equals("/cmd1"));

            protectedRegion.setFlag(DefaultFlag.BLOCKED_CMDS, new HashSet<>(blockedCommandsBuilders));
            protectedRegion.setFlag(DefaultFlag.BLOCKED_CMDS.getRegionGroupFlag(), RegionGroup.OWNERS);
            protectedRegion.setFlag(DefaultFlag.ALLOWED_CMDS, new HashSet<>(allowedCommandsNonBuilder));
            protectedRegion.setFlag(DefaultFlag.ALLOWED_CMDS.getRegionGroupFlag(), RegionGroup.NON_OWNERS);


            // Add regions and save changes
            if (regionManager.hasRegion(world.getRegionName())) regionManager.removeRegion(world.getRegionName());
            if (regionManager.hasRegion(world.getRegionName() + "-1")) regionManager.removeRegion(world.getRegionName() + "-1");
            regionManager.addRegion(protectedBuildRegion);
            regionManager.addRegion(protectedRegion);
            regionManager.saveChanges();
        } else Bukkit.getLogger().log(Level.WARNING, "Region Manager is null!");
    }


    /**
     * Gets invoked when generation is completed
     * @param failed - true if generation has failed
     * @param unloadWorld - try to unload world after generation
     * @throws SQLException - caused by a database exception
     */
    protected void onComplete(boolean failed, boolean unloadWorld) throws SQLException {
        if (!failed) {
            builder.setPlot(plot.getID(), builder.getFreeSlot());
            plot.setPlotType(plotType);
            plot.setStatus(Status.unfinished);
            plot.setPlotOwner(builder.getPlayer().getUniqueId().toString());
            PlotManager.clearCache(builder.getUUID());
        }

        // Unload plot world if it is not needed anymore
        if (unloadWorld) world.unloadWorld(false);
    }


    /**
     * Gets invoked when an exception has occurred
     * @param ex - caused exception
     */
    protected void onException(Throwable ex) {
        Bukkit.getLogger().log(Level.SEVERE, "An error occurred while generating plot!", ex);
        builder.getPlayer().sendMessage(Utils.getErrorMessageFormat(LangUtil.get(builder.getPlayer(), LangPaths.Message.Error.ERROR_OCCURRED)));
        builder.getPlayer().playSound(builder.getPlayer().getLocation(), Utils.ErrorSound,1,1);
    }


    /**
     * @return - plot object
     */
    public Plot getPlot() {
        return plot;
    }


    /**
     * @return - builder object
     */
    public Builder getBuilder() {
        return builder;
    }



    /**
     * Pastes the schematic to the plot center in the given world
     * @param schematicFile - plot/environment schematic file
     * @param world - world to paste in
     */
    public static void pasteSchematic(@Nullable EditSession editSession, File schematicFile, PlotWorld world, boolean clearArea) throws IOException, MaxChangedBlocksException, SQLException {
        if (world.loadWorld()) {
            World weWorld = new BukkitWorld(world.getBukkitWorld());
            if (editSession == null) editSession = new EditSessionBuilder(weWorld).fastmode(true).build();
            if (clearArea) {
                Polygonal2DRegion polyRegion = new Polygonal2DRegion(weWorld, world.getPlot().getOutline(), 0, PlotWorld.MAX_WORLD_HEIGHT);
                editSession.replaceBlocks(polyRegion, null, new BaseBlock(0));
                editSession.flushQueue();
            }

            FaweAPI.load(schematicFile).paste(editSession, world.getPlot().getCenter().setY(world.getPlotHeight()), false);
            editSession.flushQueue();
        }
    }
}
