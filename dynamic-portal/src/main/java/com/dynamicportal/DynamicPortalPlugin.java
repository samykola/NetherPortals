package com.dynamicportal;

import com.dynamicportal.commands.PortalCommand;
import com.dynamicportal.config.ConfigManager;
import com.dynamicportal.data.PortalDataManager;
import com.dynamicportal.listeners.PortalEntryListener;
import com.dynamicportal.listeners.PortalProtectionListener;
import com.dynamicportal.portal.PortalManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DynamicPortalPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private PortalDataManager portalDataManager;
    private PortalManager portalManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.portalDataManager = new PortalDataManager(this);
        this.portalDataManager.load();
        this.portalManager = new PortalManager(this, configManager, portalDataManager);

        getServer().getPluginManager().registerEvents(
                new PortalProtectionListener(this, portalManager, configManager), this);
        getServer().getPluginManager().registerEvents(
                new PortalEntryListener(this, portalManager, configManager), this);

        PortalCommand portalCommand = new PortalCommand(this, portalManager, configManager);
        PluginCommand command = getCommand("portal");
        if (command != null) {
            command.setExecutor(portalCommand);
            command.setTabCompleter(portalCommand);
        } else {
            getLogger().severe("Failed to register /portal command - check plugin.yml.");
        }

        // Run after the server finishes its enable sequence so all worlds are guaranteed loaded.
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                portalManager.initialize();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize the portal: " + e.getMessage());
                if (configManager.isDebug()) {
                    e.printStackTrace();
                }
            }
        });

        getLogger().info("DynamicPortal has been enabled.");
    }

    @Override
    public void onDisable() {
        if (portalManager != null) {
            portalManager.shutdown();
        }
        if (portalDataManager != null) {
            portalDataManager.save();
        }
        getLogger().info("DynamicPortal has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PortalDataManager getPortalDataManager() {
        return portalDataManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }
}
