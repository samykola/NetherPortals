package com.dynamicportal.commands;

import com.dynamicportal.config.ConfigManager;
import com.dynamicportal.data.PortalData;
import com.dynamicportal.portal.PortalManager;
import com.dynamicportal.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements every /portal subcommand described in the plugin spec.
 */
public class PortalCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "location", "time", "teleport", "move", "setworld",
            "randomworld", "reload", "remove", "create"
    );

    private final JavaPlugin plugin;
    private final PortalManager portalManager;
    private final ConfigManager config;

    public PortalCommand(JavaPlugin plugin, PortalManager portalManager, ConfigManager config) {
        this.plugin = plugin;
        this.portalManager = portalManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "location" -> handleLocation(sender);
            case "time" -> handleTime(sender);
            case "teleport", "tp" -> handleTeleport(sender);
            case "move" -> handleMove(sender, args);
            case "setworld" -> handleSetWorld(sender, args);
            case "randomworld" -> handleRandomWorld(sender);
            case "reload" -> handleReload(sender);
            case "remove" -> handleRemove(sender);
            case "create" -> handleCreate(sender);
            default -> sender.sendMessage(config.msg("unknown-command"));
        }
        return true;
    }

    // ---------------- Subcommand handlers ----------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "DynamicPortal Commands");
        sender.sendMessage(ChatColor.GRAY + "/portal help " + ChatColor.DARK_GRAY + "- Show this help menu");
        sender.sendMessage(ChatColor.GRAY + "/portal location " + ChatColor.DARK_GRAY + "- Show the portal's current location");
        sender.sendMessage(ChatColor.GRAY + "/portal time " + ChatColor.DARK_GRAY + "- Show time until next relocation");
        sender.sendMessage(ChatColor.GRAY + "/portal teleport " + ChatColor.DARK_GRAY + "- Teleport to the portal");
        if (sender.hasPermission("dynamicportal.admin")) {
            sender.sendMessage(ChatColor.GRAY + "/portal move [world] " + ChatColor.DARK_GRAY + "- Relocate the portal now");
            sender.sendMessage(ChatColor.GRAY + "/portal setworld <world> " + ChatColor.DARK_GRAY + "- Set the default portal world");
            sender.sendMessage(ChatColor.GRAY + "/portal randomworld " + ChatColor.DARK_GRAY + "- Pick a random allowed world");
            sender.sendMessage(ChatColor.GRAY + "/portal create " + ChatColor.DARK_GRAY + "- Create the portal");
            sender.sendMessage(ChatColor.GRAY + "/portal remove " + ChatColor.DARK_GRAY + "- Remove the portal");
            sender.sendMessage(ChatColor.GRAY + "/portal reload " + ChatColor.DARK_GRAY + "- Reload the configuration");
        }
    }

    private void handleLocation(CommandSender sender) {
        if (config.locationRequiresPermission() && !hasAny(sender, "dynamicportal.location", "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }

        PortalData data = portalManager.getDataManager().getData();
        if (!data.isExists()) {
            sender.sendMessage(config.msg("portal-not-found"));
            return;
        }

        long remaining = data.getNextRelocation() - System.currentTimeMillis();
        sender.sendMessage(config.rawMsg("location-header"));
        sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + data.getWorld());
        sender.sendMessage(ChatColor.GRAY + "X: " + ChatColor.WHITE + data.getX());
        sender.sendMessage(ChatColor.GRAY + "Y: " + ChatColor.WHITE + data.getY());
        sender.sendMessage(ChatColor.GRAY + "Z: " + ChatColor.WHITE + data.getZ());
        sender.sendMessage(config.msg("next-relocation", "time", TimeUtil.formatDuration(remaining)));
    }

    private void handleTime(CommandSender sender) {
        if (config.locationRequiresPermission() && !hasAny(sender, "dynamicportal.location", "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }

        PortalData data = portalManager.getDataManager().getData();
        if (!data.isExists()) {
            sender.sendMessage(config.msg("portal-not-found"));
            return;
        }

        if (!config.isRelocationEnabled()) {
            sender.sendMessage(config.getPrefix() + ChatColor.GRAY + "Automatic relocation is disabled.");
            return;
        }

        long remaining = data.getNextRelocation() - System.currentTimeMillis();
        sender.sendMessage(config.msg("next-relocation", "time", TimeUtil.formatDuration(remaining)));
    }

    private void handleTeleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }
        if (config.teleportRequiresPermission() && !hasAny(sender, "dynamicportal.teleport", "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }

        Location destination = portalManager.getTeleportLocation();
        if (destination == null) {
            sender.sendMessage(config.msg("portal-not-found"));
            return;
        }

        player.teleport(destination);
        player.sendMessage(config.msg("teleported"));
    }

    private void handleMove(CommandSender sender, String[] args) {
        if (!hasAny(sender, "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }

        World targetWorld = null;
        if (args.length >= 2) {
            targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                sender.sendMessage(config.msg("world-invalid"));
                return;
            }
            if (!config.getAllowedWorlds().isEmpty() && !config.getAllowedWorlds().contains(targetWorld.getName())) {
                sender.sendMessage(config.msg("world-not-allowed"));
                return;
            }
        }

        boolean success = portalManager.relocate(targetWorld, sender);
        if (success) {
            PortalData data = portalManager.getDataManager().getData();
            sender.sendMessage(config.msg("portal-created",
                    "x", String.valueOf(data.getX()),
                    "y", String.valueOf(data.getY()),
                    "z", String.valueOf(data.getZ()),
                    "world", data.getWorld()));
        }
    }

    private void handleSetWorld(CommandSender sender, String[] args) {
        if (!hasAny(sender, "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(config.getPrefix() + ChatColor.RED + "Usage: /portal setworld <world>");
            return;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(config.msg("world-invalid"));
            return;
        }

        portalManager.setDefaultWorld(world.getName());
        sender.sendMessage(config.msg("world-set", "world", world.getName()));
    }

    private void handleRandomWorld(CommandSender sender) {
        if (!hasAny(sender, "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }

        String world = portalManager.pickRandomAllowedWorld();
        if (world == null) {
            sender.sendMessage(config.getPrefix() + ChatColor.RED + "No allowed worlds are configured.");
            return;
        }

        sender.sendMessage(config.msg("random-world-selected", "world", world));
    }

    private void handleReload(CommandSender sender) {
        if (!hasAny(sender, "dynamicportal.admin", "dynamicportal.reload")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        config.reload();
        sender.sendMessage(config.msg("reloaded"));
    }

    private void handleRemove(CommandSender sender) {
        if (!hasAny(sender, "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        portalManager.removePortal(sender);
    }

    private void handleCreate(CommandSender sender) {
        if (!hasAny(sender, "dynamicportal.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        portalManager.createPortal(sender);
    }

    // ---------------- Helpers ----------------

    private boolean hasAny(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // ---------------- Tab completion ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("move") || args[0].equalsIgnoreCase("setworld"))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
