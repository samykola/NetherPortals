package com.dynamicportal.listeners;

import com.dynamicportal.config.ConfigManager;
import com.dynamicportal.portal.PortalManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives the portal a "living" feel: shows a title/message when a player walks
 * into it, and (unless explicitly enabled in config) prevents the block from
 * triggering vanilla Nether dimension travel, since this portal's purpose is
 * the dynamic relocation gimmick rather than actual dimension transport.
 */
public class PortalEntryListener implements Listener {

    private final JavaPlugin plugin;
    private final PortalManager portalManager;
    private final ConfigManager config;
    private final Map<UUID, Long> lastNotified = new ConcurrentHashMap<>();

    public PortalEntryListener(JavaPlugin plugin, PortalManager portalManager, ConfigManager config) {
        this.plugin = plugin;
        this.portalManager = portalManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isEntryMessageEnabled()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (to.getBlock().getType() != Material.NETHER_PORTAL) {
            return;
        }
        if (!portalManager.isProtectedLocation(to)) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0, config.getEntryCooldownSeconds()) * 1000L;
        Long last = lastNotified.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownMillis) {
            return;
        }
        lastNotified.put(player.getUniqueId(), now);

        Location portalLoc = portalManager.getPortalLocation();
        String world = portalLoc != null && portalLoc.getWorld() != null ? portalLoc.getWorld().getName() : "?";
        String x = portalLoc != null ? String.valueOf(portalLoc.getBlockX()) : "?";
        String y = portalLoc != null ? String.valueOf(portalLoc.getBlockY()) : "?";
        String z = portalLoc != null ? String.valueOf(portalLoc.getBlockZ()) : "?";

        player.sendTitle(config.rawMsg("entry-title"), config.rawMsg("entry-subtitle"), 5, 40, 10);
        player.sendMessage(config.msg("entry-subtitle"));
        player.sendMessage(config.getPrefix() + "§7Destination: §f" + world);
        player.sendMessage(config.getPrefix() + "§7Coordinates: §f" + x + " " + y + " " + z);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (config.isVanillaPortalTravelEnabled()) {
            return;
        }
        if (portalManager.isProtectedLocation(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPortal(EntityPortalEvent event) {
        if (config.isVanillaPortalTravelEnabled()) {
            return;
        }
        if (portalManager.isProtectedLocation(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Prevent unbounded growth of the cooldown map over long-running servers.
        lastNotified.remove(event.getPlayer().getUniqueId());
    }
}
