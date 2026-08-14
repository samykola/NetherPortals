package com.dynamicportal.portal;

import com.dynamicportal.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * Synchronous safe-location search. Runs entirely on the main thread since
 * chunk loading and block reads must not happen off the main thread. This is
 * only invoked on portal creation/relocation (a rare event), so a bounded
 * number of attempts keeps any tick delay negligible.
 */
public final class SafeLocationFinder {

    private static final int MAX_ATTEMPTS = 200;

    private SafeLocationFinder() {
    }

    /**
     * Attempts to find a safe base (bottom-left-front corner) location for the
     * portal frame. Returns null if no safe location could be found within
     * MAX_ATTEMPTS tries.
     */
    public static Location find(World world, ConfigManager config, Random random) {
        if (world == null) {
            return null;
        }

        int width = config.getWidth();
        int height = config.getHeight();
        int minX = config.getMinX();
        int maxX = config.getMaxX();
        int minZ = config.getMinZ();
        int maxZ = config.getMaxZ();
        int minDistance = config.getMinDistanceFromSpawn();

        if (maxX < minX) {
            int tmp = maxX;
            maxX = minX;
            minX = tmp;
        }
        if (maxZ < minZ) {
            int tmp = maxZ;
            maxZ = minZ;
            minZ = tmp;
        }

        Location spawn = world.getSpawnLocation();
        int rangeX = Math.max(1, maxX - minX + 1);
        int rangeZ = Math.max(1, maxZ - minZ + 1);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = minX + random.nextInt(rangeX);
            int z = minZ + random.nextInt(rangeZ);

            double distanceFromSpawn = Math.sqrt(
                    Math.pow(x - spawn.getBlockX(), 2) + Math.pow(z - spawn.getBlockZ(), 2));
            if (distanceFromSpawn < minDistance) {
                continue;
            }

            // Force the target chunk to be loaded before reading blocks (sync, as required).
            world.getChunkAt(x >> 4, z >> 4);

            int y = resolveY(world, config, x, z);
            if (y < world.getMinHeight() + 2 || y > world.getMaxHeight() - height - 2) {
                continue;
            }

            if (isAreaSafe(world, x, y, z, width, height)) {
                return new Location(world, x, y, z);
            }
        }

        return null;
    }

    private static int resolveY(World world, ConfigManager config, int x, int z) {
        if (config.isYAuto()) {
            return world.getHighestBlockYAt(x, z);
        }
        return config.getFixedY();
    }

    /**
     * Verifies the footprint (width x height, single block deep) is safe:
     * solid, non-dangerous ground; no lava/water in the frame's volume;
     * roughly flat terrain (avoids cliffs); and clear headroom above.
     */
    private static boolean isAreaSafe(World world, int x, int y, int z, int width, int height) {
        for (int dx = 0; dx < width; dx++) {
            int gx = x + dx;

            Block ground = world.getBlockAt(gx, y - 1, z);
            Material groundType = ground.getType();
            if (!groundType.isSolid() || isDangerous(groundType)) {
                return false;
            }

            // Reject overly uneven terrain (cliffs / floating edges) under any column of the frame.
            int columnHeight = world.getHighestBlockYAt(gx, z);
            if (Math.abs(columnHeight - y) > 1) {
                return false;
            }

            // Every block the frame/interior will occupy must be free (air-like) and not liquid.
            for (int dy = 0; dy < height; dy++) {
                Block b = world.getBlockAt(gx, y + dy, z);
                Material type = b.getType();
                if (type.isSolid()) {
                    return false;
                }
                if (type == Material.LAVA || type == Material.WATER) {
                    return false;
                }
            }

            // Require open headroom directly above the structure (avoids cramped cave ceilings).
            Block above = world.getBlockAt(gx, y + height, z);
            if (above.getType().isSolid()) {
                return false;
            }
        }

        return true;
    }

    private static boolean isDangerous(Material material) {
        return material == Material.LAVA
                || material == Material.WATER
                || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.AIR;
    }
}
