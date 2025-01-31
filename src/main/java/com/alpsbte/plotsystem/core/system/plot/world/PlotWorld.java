package com.alpsbte.plotsystem.core.system.plot.world;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.core.system.plot.generator.PlotWorldGenerator;
import com.alpsbte.plotsystem.utils.Utils;
import com.boydti.fawe.FaweAPI;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

public class PlotWorld implements IWorld {
    public static final int PLOT_SIZE = 150;
    public static final int MAX_WORLD_HEIGHT = 256;
    public static final int MIN_WORLD_HEIGHT = 5;

    private final MultiverseCore mvCore = PlotSystem.DependencyManager.getMultiverseCore();
    private final String worldName;
    private final Plot plot;

    public PlotWorld(@NotNull String worldName, @Nullable Plot plot) {
        this.worldName = worldName;
        this.plot = plot;
    }

    @Override
    public <T extends PlotWorldGenerator> boolean generateWorld(@NotNull Class<T> generator) {
        throw new UnsupportedOperationException("No world generator set for world " + getWorldName());
    }

    @Override
    public <T extends PlotWorldGenerator> boolean regenWorld(@NotNull Class<T> generator) {
        return deleteWorld() && generateWorld(generator);
    }

    @Override
    public boolean deleteWorld() {
        if (isWorldGenerated() && loadWorld()) {
            if (mvCore.getMVWorldManager().deleteWorld(getWorldName(), true, true) && mvCore.saveWorldConfig()) {
                try {
                    File multiverseInventoriesConfig = new File(PlotSystem.DependencyManager.getMultiverseInventoriesConfigPath(getWorldName()));
                    File worldGuardConfig = new File(PlotSystem.DependencyManager.getWorldGuardConfigPath(getWorldName()));
                    if (multiverseInventoriesConfig.exists()) FileUtils.deleteDirectory(multiverseInventoriesConfig);
                    if (worldGuardConfig.exists()) FileUtils.deleteDirectory(worldGuardConfig);
                } catch (IOException ex) {
                    Bukkit.getLogger().log(Level.WARNING, "Could not delete config files for world " + getWorldName() + "!");
                    return false;
                }
                return true;
            } else Bukkit.getLogger().log(Level.WARNING, "Could not delete world " + getWorldName() + "!");
        }
        return false;
    }

    @Override
    public boolean loadWorld() {
        if(isWorldGenerated()) {
            if (isWorldLoaded()) {
                return true;
            } else return mvCore.getMVWorldManager().loadWorld(getWorldName()) || isWorldLoaded();
        } else Bukkit.getLogger().log(Level.WARNING, "Could not load world " + worldName + " because it is not generated!");
        return false;
    }

    @Override
    public boolean unloadWorld(boolean movePlayers) {
        if (isWorldGenerated()) {
            if(isWorldLoaded()) {
                if (movePlayers && !getBukkitWorld().getPlayers().isEmpty()) {
                    for (Player player : getBukkitWorld().getPlayers()) {
                        player.teleport(Utils.getSpawnLocation());
                    }
                }

                Bukkit.unloadWorld(getBukkitWorld(), true);
                return !isWorldLoaded();
            }
            return true;
        } else Bukkit.getLogger().log(Level.WARNING, "Could not unload world " + worldName + " because it is not generated!");
        return false;
    }

    @Override
    public boolean teleportPlayer(@NotNull Player player) {
        if (loadWorld()) {
            Location spawnLocation = plot != null ? getSpawnPoint(plot.getCenter()) : getBukkitWorld().getSpawnLocation();
            // Set spawn point 1 block above the highest block at the spawn location
            spawnLocation.setY(getBukkitWorld().getHighestBlockYAt((int) spawnLocation.getX(), (int) spawnLocation.getZ()) + 1);

            player.teleport(spawnLocation);
            return true;
        } else Bukkit.getLogger().log(Level.WARNING, "Could not teleport player " + player.getName() + " to world " + worldName + "!");
        return false;
    }

    @Override
    public Location getSpawnPoint(Vector plotVector) {
        if (isWorldGenerated() && loadWorld()) {
            return plotVector == null ? getBukkitWorld().getSpawnLocation() :
                    new Location(getBukkitWorld(), plotVector.getX(), plotVector.getY(), plotVector.getZ());
        }
        return null;
    }

    @Override
    public int getPlotHeight() throws IOException {
        throw new UnsupportedOperationException("No plot height set for " + getWorldName());
    }

    @Override
    public int getPlotHeightCentered() throws IOException {
        if (plot != null) {
            Clipboard clipboard = FaweAPI.load(plot.getOutlinesSchematic()).getClipboard();
            if (clipboard != null) {
                return clipboard.getRegion().getCenter().getBlockY() - clipboard.getMinimumPoint().getBlockY();
            }
        }
        return 0;
    }

    @Override
    public World getBukkitWorld() {
        return Bukkit.getWorld(worldName);
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public String getRegionName() {
       return worldName.toLowerCase(Locale.ROOT);
    }

    @Override
    public ProtectedRegion getProtectedRegion() {
        return getRegion(getRegionName() + "-1");
    }

    @Override
    public ProtectedRegion getProtectedBuildRegion() {
        return getRegion(getRegionName());
    }

    @Override
    public boolean isWorldLoaded() {
        return getBukkitWorld() != null;
    }

    @Override
    public boolean isWorldGenerated() {
        return mvCore.getMVWorldManager().getMVWorld(worldName) != null || mvCore.getMVWorldManager().getUnloadedWorlds().contains(worldName);
    }

    private ProtectedRegion getRegion(String regionName) {
        RegionContainer container = PlotSystem.DependencyManager.getWorldGuard().getRegionContainer();
        if (loadWorld()) {
            RegionManager regionManager = container.get(getBukkitWorld());
            if (regionManager != null) {
                return regionManager.getRegion(regionName);
            } else Bukkit.getLogger().log(Level.WARNING, "Region manager is null");
        }
        return null;
    }

    public Plot getPlot() {
        return plot;
    }


    /**
     * @param worldName - the name of the world
     * @return - true if the world is a plot world
     */
    public static boolean isOnePlotWorld(String worldName) {
        return worldName.toLowerCase(Locale.ROOT).startsWith("p-");
    }

    /**
     * @param worldName - the name of the world
     * @return - true if the world is a city plot world
     */
    public static boolean isCityPlotWorld(String worldName) {
        return worldName.toLowerCase(Locale.ROOT).startsWith("c-");
    }

    /**
     * Returns OnePlotWorld or PlotWorld (CityPlotWorld) class depending on the world name.
     * It won't return the CityPlotWorld class because there is no use case without a plot.
     * @param worldName - name of the world
     * @return - plot world
     * @param <T> - OnePlotWorld or PlotWorld
     */
    @SuppressWarnings("unchecked")
    public static <T extends PlotWorld> T getPlotWorldByName(String worldName) throws SQLException {
        if (isOnePlotWorld(worldName)) {
            return new Plot(Integer.parseInt(worldName.substring(2))).getWorld();
        } else return (T) new PlotWorld(worldName, null);
    }
}
