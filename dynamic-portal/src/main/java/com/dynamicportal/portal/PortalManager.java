package com.dynamicportal.portal;

import com.dynamicportal.config.ConfigManager;
import com.dynamicportal.data.PortalData;
import com.dynamicportal.data.PortalDataManager;
import com.dynamicportal.util.TimeUtil;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

/**
 * Owns the full lifecycle of the dynamic portal: building/removing its
 * blocks, finding safe relocation spots, tracking the relocation timer, and
 * answering "is this block part of the protected portal" queries used by the
 * protection listener.
 *
 * All world mutation happens synchronously on the main thread, as required
 * for safe Bukkit API usage.
 */
public class PortalManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PortalDataManager dataManager;
    private final Random random = new Random();

    private BukkitTask checkerTask;

    public PortalManager(JavaPlugin plugin, ConfigManager config, PortalDataManager dataManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
    }

    /**
     * Called once on startup after all worlds are loaded. Creates the portal
     * if one has never existed, immediately relocates it if the server was
     * offline past its relocation time, and starts the periodic checker.
     */
    public void initialize() {
        PortalData data = dataManager.getData();

        if (!data.isExists()) {
            plugin.getLogger().info("No existing portal found. Creating the initial portal...");
            World world = resolveTargetWorld(null);
            if (world == null) {
                plugin.getLogger().warning("No valid/allowed world is currently loaded. "
                        + "The portal will not be created until a valid world is available "
                        + "(use /portal create once one is).");
            } else {
                buildAt(world, null);
            }
        } else if (config.isRelocationEnabled() && System.currentTimeMillis() >= data.getNextRelocation()) {
            plugin.getLogger().info("The relocation interval elapsed while the server was offline. "
                    + "Relocating the portal now...");
            relocate(null, null);
        } else {
            plugin.getLogger().info("Existing portal loaded: " + describe(data));
        }

        startChecker();
    }

    private void startChecker() {
        long period = 20L * 60L; // check once per minute
        checkerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!config.isRelocationEnabled()) {
                return;
            }
            PortalData data = dataManager.getData();
            if (data.isExists() && System.currentTimeMillis() >= data.getNextRelocation()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] Relocation interval elapsed, relocating portal.");
                }
                relocate(null, null);
            }
        }, period, period);
    }

    public void shutdown() {
        if (checkerTask != null) {
            checkerTask.cancel();
            checkerTask = null;
        }
    }

    // ---------------- Public actions ----------------

    /**
     * Builds the portal at a freshly-chosen safe location in the given world
     * (or the resolved default/random world if targetWorld is null), removing
     * any existing portal first.
     */
    public boolean relocate(World targetWorld, CommandSender initiator) {
        World world = targetWorld != null ? targetWorld : resolveTargetWorld(null);
        if (world == null) {
            if (initiator != null) {
                initiator.sendMessage(config.msg("world-invalid"));
            }
            return false;
        }

        removePortalBlocks();

        Location location = buildAt(world, null);
        if (location == null) {
            plugin.getLogger().warning("Failed to find a safe relocation spot in world '" + world.getName() + "'.");
            if (initiator != null) {
                initiator.sendMessage(config.msg("no-safe-location"));
            }
            return false;
        }

        Bukkit.broadcastMessage(config.msg("moved"));
        return true;
    }

    /**
     * Creates the portal at the currently stored location if one exists, or
     * finds a new safe location if not. Used by /portal create.
     */
    public boolean createPortal(CommandSender sender) {
        PortalData data = dataManager.getData();
        World world;

        if (data.isExists()) {
            world = Bukkit.getWorld(data.getWorld());
            if (world == null) {
                world = resolveTargetWorld(null);
            }
        } else {
            world = resolveTargetWorld(null);
        }

        if (world == null) {
            if (sender != null) {
                sender.sendMessage(config.msg("world-invalid"));
            }
            return false;
        }

        Location forced = null;
        if (data.isExists() && world.getName().equals(data.getWorld())) {
            // Recreate at the exact stored coordinates rather than picking a new spot.
            forced = new Location(world, data.getX(), data.getY(), data.getZ());
        }

        Location location = buildAt(world, forced);
        if (location == null) {
            if (sender != null) {
                sender.sendMessage(config.msg("no-safe-location"));
            }
            return false;
        }

        if (sender != null) {
            sender.sendMessage(config.msg("portal-created",
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ()),
                    "world", world.getName()));
        }
        return true;
    }

    public boolean removePortal(CommandSender sender) {
        PortalData data = dataManager.getData();
        if (!data.isExists()) {
            if (sender != null) {
                sender.sendMessage(config.msg("portal-not-found"));
            }
            return false;
        }
        removePortalBlocks();
        if (sender != null) {
            sender.sendMessage(config.msg("portal-removed"));
        }
        return true;
    }

    // ---------------- Building / removing blocks ----------------

    private Location buildAt(World world, Location forced) {
        Location location = forced != null ? forced : SafeLocationFinder.find(world, config, random);
        if (location == null) {
            return null;
        }

        generateStructure(location);

        int width = config.getWidth();
        int height = config.getHeight();

        PortalData data = dataManager.getData();
        data.setWorld(world.getName());
        data.setX(location.getBlockX());
        data.setY(location.getBlockY());
        data.setZ(location.getBlockZ());
        data.setWidth(width);
        data.setHeight(height);
        data.setExists(true);

        long now = System.currentTimeMillis();
        // Only reset the "created" timestamp / timer on an actual new placement, not on
        // a manual /portal create that rebuilds at the exact same stored coordinates and time.
        data.setCreatedAt(now);
        data.setNextRelocation(now + TimeUtil.daysToMillis(config.getIntervalDays()));

        data.setMinX(location.getBlockX());
        data.setMinY(location.getBlockY());
        data.setMinZ(location.getBlockZ());
        data.setMaxX(location.getBlockX() + width - 1);
        data.setMaxY(location.getBlockY() + height - 1);
        data.setMaxZ(location.getBlockZ());

        dataManager.save();

        plugin.getLogger().info("Portal generated in '" + world.getName() + "' at "
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());

        return location;
    }

    private void generateStructure(Location base) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        int width = config.getWidth();
        int height = config.getHeight();
        Material frameMaterial = config.getFrameMaterial();

        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Block block = world.getBlockAt(bx + x, by + y, bz);
                boolean edge = (x == 0 || x == width - 1 || y == 0 || y == height - 1);

                if (edge) {
                    block.setType(frameMaterial, false);
                } else {
                    block.setType(Material.NETHER_PORTAL, false);
                    BlockData blockData = block.getBlockData();
                    if (blockData instanceof Orientable orientable) {
                        orientable.setAxis(Axis.X);
                        block.setBlockData(orientable, false);
                    }
                }
            }
        }
    }

    private void removePortalBlocks() {
        PortalData data = dataManager.getData();
        if (!data.isExists()) {
            return;
        }

        World world = Bukkit.getWorld(data.getWorld());
        if (world != null) {
            Material frameMaterial = config.getFrameMaterial();
            for (int x = data.getMinX(); x <= data.getMaxX(); x++) {
                for (int y = data.getMinY(); y <= data.getMaxY(); y++) {
                    Block block = world.getBlockAt(x, y, data.getMinZ());
                    Material type = block.getType();
                    if (type == frameMaterial || type == Material.NETHER_PORTAL) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        data.setExists(false);
        dataManager.save();
    }

    // ---------------- Queries ----------------

    /**
     * True if the given block falls inside the current portal's bounding box
     * (frame or interior), and should therefore be fully protected.
     */
    public boolean isProtectedBlock(Block block) {
        PortalData data = dataManager.getData();
        if (!data.isExists() || block == null) {
            return false;
        }
        World world = block.getWorld();
        if (world == null || !world.getName().equals(data.getWorld())) {
            return false;
        }

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        return x >= data.getMinX() && x <= data.getMaxX()
                && y >= data.getMinY() && y <= data.getMaxY()
                && z == data.getMinZ();
    }

    public boolean isProtectedLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return isProtectedBlock(loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public World resolveTargetWorld(String override) {
        if (override != null) {
            return Bukkit.getWorld(override);
        }

        if (config.isRandomWorldSelection()) {
            List<String> allowed = config.getAllowedWorlds();
            if (!allowed.isEmpty()) {
                String name = allowed.get(random.nextInt(allowed.size()));
                World world = Bukkit.getWorld(name);
                if (world != null) {
                    return world;
                }
            }
        }

        World defaultWorld = Bukkit.getWorld(config.getDefaultWorld());
        if (defaultWorld != null) {
            return defaultWorld;
        }

        for (String name : config.getAllowedWorlds()) {
            World fallback = Bukkit.getWorld(name);
            if (fallback != null) {
                return fallback;
            }
        }

        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public String pickRandomAllowedWorld() {
        List<String> allowed = config.getAllowedWorlds();
        if (allowed.isEmpty()) {
            return null;
        }
        return allowed.get(random.nextInt(allowed.size()));
    }

    public Location getPortalLocation() {
        PortalData data = dataManager.getData();
        if (!data.isExists()) {
            return null;
        }
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, data.getX(), data.getY(), data.getZ());
    }

    /**
     * A safe location to teleport a player to (just in front of the portal),
     * rather than inside the frame itself.
     */
    public Location getTeleportLocation() {
        PortalData data = dataManager.getData();
        if (!data.isExists()) {
            return null;
        }
        World world = Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return null;
        }
        double centerX = data.getX() + (data.getWidth() / 2.0);
        double frontZ = data.getZ() - 1.0 + 0.5;
        Location loc = new Location(world, centerX, data.getY() + 1, frontZ);
        loc.setYaw(180f);
        return loc;
    }

    public PortalDataManager getDataManager() {
        return dataManager;
    }

    public ConfigManager getConfig() {
        return config;
    }

    private String describe(PortalData data) {
        return data.getWorld() + " (" + data.getX() + ", " + data.getY() + ", " + data.getZ() + ")";
    }
}
