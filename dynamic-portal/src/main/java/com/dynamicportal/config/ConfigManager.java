package com.dynamicportal.config;

import com.dynamicportal.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Provides typed, null-safe access to the plugin's config.yml.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // ---------------- Portal structure ----------------

    public Material getFrameMaterial() {
        String name = config.getString("portal.frame-material", "GOLD_BLOCK");
        Material material = Material.matchMaterial(name);
        return material != null ? material : Material.GOLD_BLOCK;
    }

    public int getWidth() {
        return Math.max(4, config.getInt("portal.width", 4));
    }

    public int getHeight() {
        return Math.max(5, config.getInt("portal.height", 5));
    }

    // ---------------- Relocation ----------------

    public boolean isRelocationEnabled() {
        return config.getBoolean("relocation.enabled", true);
    }

    public long getIntervalDays() {
        return Math.max(1, config.getLong("relocation.interval-days", 3));
    }

    // ---------------- Location ----------------

    public boolean isYAuto() {
        return "auto".equalsIgnoreCase(config.getString("location.y", "auto"));
    }

    public int getFixedY() {
        return config.getInt("location.y", 64);
    }

    public int getMinX() {
        return config.getInt("location.min-x", -5000);
    }

    public int getMaxX() {
        return config.getInt("location.max-x", 5000);
    }

    public int getMinZ() {
        return config.getInt("location.min-z", -5000);
    }

    public int getMaxZ() {
        return config.getInt("location.max-z", 5000);
    }

    public int getMinDistanceFromSpawn() {
        return config.getInt("location.min-distance-from-spawn", 0);
    }

    // ---------------- Worlds ----------------

    public String getDefaultWorld() {
        return config.getString("worlds.default", "world");
    }

    public void setDefaultWorld(String world) {
        config.set("worlds.default", world);
        plugin.saveConfig();
    }

    public List<String> getAllowedWorlds() {
        return config.getStringList("worlds.allowed");
    }

    public boolean isRandomWorldSelection() {
        return config.getBoolean("worlds.random-world-selection", false);
    }

    // ---------------- Permissions ----------------

    public boolean locationRequiresPermission() {
        return config.getBoolean("permissions.location-requires-permission", false);
    }

    public boolean teleportRequiresPermission() {
        return config.getBoolean("permissions.teleport-requires-permission", false);
    }

    // ---------------- Protection ----------------

    public boolean preventBlockBreak() {
        return config.getBoolean("protection.prevent-block-break", true);
    }

    public boolean preventBlockPlace() {
        return config.getBoolean("protection.prevent-block-place", true);
    }

    public boolean preventExplosions() {
        return config.getBoolean("protection.prevent-explosions", true);
    }

    public boolean preventFire() {
        return config.getBoolean("protection.prevent-fire", true);
    }

    public boolean preventPiston() {
        return config.getBoolean("protection.prevent-piston", true);
    }

    public boolean preventLiquidFlow() {
        return config.getBoolean("protection.prevent-liquid-flow", true);
    }

    // ---------------- Teleport / entry experience ----------------

    public boolean isVanillaPortalTravelEnabled() {
        return config.getBoolean("teleport.enable-vanilla-portal-travel", false);
    }

    public boolean isEntryMessageEnabled() {
        return config.getBoolean("teleport.entry-message-enabled", true);
    }

    public int getEntryCooldownSeconds() {
        return config.getInt("teleport.entry-cooldown-seconds", 5);
    }

    // ---------------- Messages ----------------

    public String getPrefix() {
        return MessageUtil.color(config.getString("messages.prefix", "&5&lPortal &8» "));
    }

    /**
     * Returns a fully colorized message with the configured prefix applied.
     */
    public String msg(String key) {
        String raw = config.getString("messages." + key, "");
        return getPrefix() + MessageUtil.color(raw);
    }

    /**
     * Returns a fully colorized message with the prefix applied, with %placeholder% substitutions.
     * placeholders array must be in pairs: key, value, key, value...
     */
    public String msg(String key, String... placeholders) {
        String raw = config.getString("messages." + key, "");
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return getPrefix() + MessageUtil.color(raw);
    }

    /**
     * Returns a colorized message WITHOUT the prefix (used for titles/subtitles).
     */
    public String rawMsg(String key) {
        return MessageUtil.color(config.getString("messages." + key, ""));
    }

    // ---------------- Debug ----------------

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }
}
