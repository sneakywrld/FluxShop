package dev.fluxshop.nms;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.Bukkit;

/**
 * Detects the server's Minecraft version and instantiates the correct {@link NMSHandler}.
 *
 * <p>Supports 1.8.x through 1.21.x. For 1.8–1.12 a reflection-based legacy handler is used;
 * 1.13–1.20 use the modern Bukkit API; 1.21+ use Paper's Component API where available.
 */
public final class VersionUtil {

    private VersionUtil() {}

    /** e.g. "1.20.4" */
    private static String versionString;
    /** Numeric: major=1, minor=20, patch=4 */
    private static int major, minor, patch;

    static {
        // Bukkit.getBukkitVersion() returns something like "1.20.4-R0.1-SNAPSHOT"
        String full = Bukkit.getBukkitVersion().split("-")[0]; // "1.20.4"
        versionString = full;
        String[] parts = full.split("\\.");
        major = parts.length > 0 ? parseInt(parts[0]) : 1;
        minor = parts.length > 1 ? parseInt(parts[1]) : 0;
        patch = parts.length > 2 ? parseInt(parts[2]) : 0;
    }

    /** Returns the detected Minecraft version string, e.g. "1.20.4". */
    public static String getVersionString() { return versionString; }

    public static int getMajor() { return major; }
    public static int getMinor() { return minor; }
    public static int getPatch() { return patch; }

    /**
     * Returns {@code true} if the server is running at least the given minor version.
     * E.g. {@code isAtLeast(1, 17)} returns true on 1.17, 1.18, 1.20, 1.21, etc.
     */
    public static boolean isAtLeast(int majorVer, int minorVer) {
        if (major != majorVer) return major > majorVer;
        return minor >= minorVer;
    }

    /** Returns {@code true} if the server is exactly the given minor version (any patch). */
    public static boolean is(int majorVer, int minorVer) {
        return major == majorVer && minor == minorVer;
    }

    /**
     * Loads and returns the correct {@link NMSHandler} for the running server version.
     * Returns {@code null} if the version is completely unsupported.
     */
    public static NMSHandler loadHandler(FluxShopPlugin plugin) {
        plugin.getLogger().info("Detected Minecraft version: " + versionString);

        try {
            if (isAtLeast(1, 21)) {
                // 1.21+ — Paper Component API available
                return new NMSHandler_v1_21();
            } else if (isAtLeast(1, 13)) {
                // 1.13–1.20 — Modern Material / ItemMeta API
                return new NMSHandler_Modern();
            } else if (isAtLeast(1, 8)) {
                // 1.8–1.12 — Legacy NMS via reflection
                return new NMSHandler_Legacy();
            } else {
                plugin.getLogger().severe("Unsupported version: " + versionString + ". FluxShop requires 1.8.8+.");
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load NMS handler for " + versionString + ": " + e.getMessage());
            return null;
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
