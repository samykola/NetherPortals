package com.dynamicportal.util;

import org.bukkit.ChatColor;

/**
 * Helper for translating legacy '&' color codes. Kept intentionally simple and
 * dependency-free; MiniMessage/Adventure Components can be layered on top of this
 * later without changing the rest of the plugin's structure.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
