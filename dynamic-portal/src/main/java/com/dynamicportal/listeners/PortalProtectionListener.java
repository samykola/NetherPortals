package com.dynamicportal.listeners;

import com.dynamicportal.config.ConfigManager;
import com.dynamicportal.portal.PortalManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Protects the portal's exact blocks (frame + interior) from every mechanic
 * that could destroy or alter them. Only blocks belonging to the plugin's
 * tracked portal bounding box are protected - nothing is disabled globally.
 */
public class PortalProtectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PortalManager portalManager;
    private final ConfigManager config;

    public PortalProtectionListener(JavaPlugin plugin, PortalManager portalManager, ConfigManager config) {
        this.plugin = plugin;
        this.portalManager = portalManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.preventBlockBreak()) {
            return;
        }
        if (portalManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.preventBlockPlace()) {
            return;
        }
        if (portalManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.preventExplosions()) {
            return;
        }
        event.blockList().removeIf(portalManager::isProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!config.preventExplosions()) {
            return;
        }
        event.blockList().removeIf(portalManager::isProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!config.preventPiston()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (portalManager.isProtectedBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
        if (portalManager.isProtectedBlock(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!config.preventPiston()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (portalManager.isProtectedBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!config.preventFire()) {
            return;
        }
        if (portalManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!config.preventFire()) {
            return;
        }
        if (portalManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!config.preventLiquidFlow()) {
            return;
        }
        if (portalManager.isProtectedBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (portalManager.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void notifyProtected(Player player) {
        if (player != null) {
            player.sendMessage(config.msg("protected"));
        }
    }
}
