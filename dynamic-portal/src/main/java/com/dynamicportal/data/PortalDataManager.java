package com.dynamicportal.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Handles reading and writing portal-data.yml, which stores the portal's
 * current location and the timestamp for its next relocation. This is kept
 * separate from config.yml so that reloading the config never touches the
 * relocation timer, and so the timer never resets on restart.
 */
public class PortalDataManager {

    private final JavaPlugin plugin;
    private final File file;
    private PortalData data = new PortalData();

    public PortalDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portal-data.yml");
    }

    public void load() {
        if (!file.exists()) {
            data = new PortalData();
            save();
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        PortalData loaded = new PortalData();
        loaded.setExists(yaml.getBoolean("exists", false));
        loaded.setWorld(yaml.getString("world", "world"));
        loaded.setX(yaml.getInt("x", 0));
        loaded.setY(yaml.getInt("y", 64));
        loaded.setZ(yaml.getInt("z", 0));
        loaded.setWidth(yaml.getInt("width", 4));
        loaded.setHeight(yaml.getInt("height", 5));
        loaded.setCreatedAt(yaml.getLong("created-at", 0L));
        loaded.setNextRelocation(yaml.getLong("next-relocation", 0L));
        loaded.setMinX(yaml.getInt("bounds.min-x", 0));
        loaded.setMinY(yaml.getInt("bounds.min-y", 0));
        loaded.setMinZ(yaml.getInt("bounds.min-z", 0));
        loaded.setMaxX(yaml.getInt("bounds.max-x", 0));
        loaded.setMaxY(yaml.getInt("bounds.max-y", 0));
        loaded.setMaxZ(yaml.getInt("bounds.max-z", 0));

        this.data = loaded;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("exists", data.isExists());
        yaml.set("world", data.getWorld());
        yaml.set("x", data.getX());
        yaml.set("y", data.getY());
        yaml.set("z", data.getZ());
        yaml.set("width", data.getWidth());
        yaml.set("height", data.getHeight());
        yaml.set("created-at", data.getCreatedAt());
        yaml.set("next-relocation", data.getNextRelocation());
        yaml.set("bounds.min-x", data.getMinX());
        yaml.set("bounds.min-y", data.getMinY());
        yaml.set("bounds.min-z", data.getMinZ());
        yaml.set("bounds.max-x", data.getMaxX());
        yaml.set("bounds.max-y", data.getMaxY());
        yaml.set("bounds.max-z", data.getMaxZ());

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save portal-data.yml: " + e.getMessage());
        }
    }

    public PortalData getData() {
        return data;
    }
}
