package dev.fluxshop.config;

import dev.fluxshop.FluxShopPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and sends plugin messages.
 *
 * <p>Supports:
 * <ul>
 *   <li>Legacy colour codes (&amp;a, &amp;b, … &amp;l, &amp;r)</li>
 *   <li>Hex colour codes (#RRGGBB — 1.16+ servers; silently stripped on older)</li>
 *   <li>MiniMessage-style tags (&lt;red&gt;, &lt;bold&gt;, &lt;gradient:…&gt;, etc.) converted
 *       to legacy equivalents where possible</li>
 *   <li>Placeholder replacement via a simple {key} → value map</li>
 *   <li>PlaceholderAPI expansion when available</li>
 * </ul>
 */
public class MessageManager {

    private static final Pattern HEX_PATTERN     = Pattern.compile("<color:#([A-Fa-f0-9]{6})>|&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern TAG_PATTERN      = Pattern.compile("</?([a-z_]+(?::[^>]*)?)>");

    /** Map of MiniMessage colour/format tags → legacy codes */
    private static final Map<String, String> MINI_TO_LEGACY = new HashMap<>();
    static {
        MINI_TO_LEGACY.put("black",        "&0");
        MINI_TO_LEGACY.put("dark_blue",    "&1");
        MINI_TO_LEGACY.put("dark_green",   "&2");
        MINI_TO_LEGACY.put("dark_aqua",    "&3");
        MINI_TO_LEGACY.put("dark_red",     "&4");
        MINI_TO_LEGACY.put("dark_purple",  "&5");
        MINI_TO_LEGACY.put("gold",         "&6");
        MINI_TO_LEGACY.put("gray",         "&7");
        MINI_TO_LEGACY.put("dark_gray",    "&8");
        MINI_TO_LEGACY.put("blue",         "&9");
        MINI_TO_LEGACY.put("green",        "&a");
        MINI_TO_LEGACY.put("aqua",         "&b");
        MINI_TO_LEGACY.put("red",          "&c");
        MINI_TO_LEGACY.put("light_purple", "&d");
        MINI_TO_LEGACY.put("yellow",       "&e");
        MINI_TO_LEGACY.put("white",        "&f");
        MINI_TO_LEGACY.put("bold",         "&l");
        MINI_TO_LEGACY.put("italic",       "&o");
        MINI_TO_LEGACY.put("underlined",   "&n");
        MINI_TO_LEGACY.put("strikethrough","&m");
        MINI_TO_LEGACY.put("obfuscated",   "&k");
        MINI_TO_LEGACY.put("reset",        "&r");
        // Closing tags → reset
        MINI_TO_LEGACY.put("/bold",         "&r");
        MINI_TO_LEGACY.put("/italic",       "&r");
        MINI_TO_LEGACY.put("/underlined",   "&r");
        MINI_TO_LEGACY.put("/color",        "&r");
    }

    private final FluxShopPlugin plugin;
    private final ConfigManager  configManager;
    private FileConfiguration    messages;
    private String               prefix;

    /** Whether PlaceholderAPI is available */
    private boolean papiAvailable = false;

    public MessageManager(FluxShopPlugin plugin, ConfigManager configManager) {
        this.plugin        = plugin;
        this.configManager = configManager;
    }

    public void load() {
        messages = configManager.getMessages();
        prefix   = format(messages.getString("prefix", "&8[&6FluxShop&8] &r"));
        papiAvailable = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    /**
     * Sends a message from messages.yml to the sender.
     *
     * @param sender   recipient
     * @param path     YAML path in messages.yml (e.g. "general.no-permission")
     * @param prefixed whether to prepend the plugin prefix
     * @param vars     key-value pairs for {key} → value replacement
     */
    public void send(CommandSender sender, String path, boolean prefixed, Object... vars) {
        String raw = messages.getString(path);
        if (raw == null) {
            plugin.getLogger().warning("Missing message: " + path);
            return;
        }
        String formatted = format(raw, sender instanceof Player p ? p : null, vars);
        if (prefixed) formatted = prefix + formatted;
        sender.sendMessage(formatted);
    }

    /** Sends a prefixed message. Shorthand for {@link #send(CommandSender, String, boolean, Object...)}. */
    public void send(CommandSender sender, String path, Object... vars) {
        send(sender, path, true, vars);
    }

    /** Sends a raw string (not from messages.yml) after colour + placeholder resolution. */
    public void sendRaw(CommandSender sender, String raw, Object... vars) {
        sender.sendMessage(format(raw, sender instanceof Player p ? p : null, vars));
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    /**
     * Applies full formatting pipeline to a raw string:
     * gradient → hex → legacy colour codes → placeholder replacement.
     */
    public String format(String raw, Player player, Object... vars) {
        if (raw == null) return "";
        String s = raw;
        s = applyGradients(s);
        s = applyHexColors(s);
        s = applyVars(s, vars);
        s = colorize(s);
        if (papiAvailable && player != null) {
            s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, s);
        }
        return s;
    }

    /** Format without a player (no PAPI expansion). */
    public String format(String raw, Object... vars) {
        return format(raw, null, vars);
    }

    /**
     * Applies legacy {@code &} colour codes.
     */
    public String colorize(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Gets a raw (unformatted) message from messages.yml.
     */
    public String getRaw(String path) {
        return messages.getString(path, "&cMissing message: " + path);
    }

    // ── Placeholder / variable replacement ────────────────────────────────────

    /**
     * Replaces {key} placeholders from varargs (key, value, key, value, …).
     */
    private String applyVars(String s, Object[] vars) {
        for (int i = 0; i + 1 < vars.length; i += 2) {
            String key   = "{" + vars[i] + "}";
            String value = vars[i + 1] == null ? "" : vars[i + 1].toString();
            s = s.replace(key, value);
        }
        return s;
    }

    // ── MiniMessage to legacy ─────────────────────────────────────────────────

    private String applyMiniMessageTags(String s) {
        Matcher m = TAG_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String full = m.group(0);                        // e.g. "<red>" or "</bold>"
            String tag  = m.group(1).toLowerCase();          // e.g. "red" or "bold"
            // For closing tags (</foo>), look up "/foo" first; if not found, strip the tag.
            String lookupKey  = full.startsWith("</") ? "/" + tag : tag;
            String replacement = MINI_TO_LEGACY.getOrDefault(lookupKey, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Hex colour support (1.16+) ────────────────────────────────────────────

    private String applyHexColors(String s) {
        Matcher m = HEX_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                // On 1.16+ BungeeCord ChatColor supports hex; on older versions silently drop
                String bungeeHex = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
                m.appendReplacement(sb, Matcher.quoteReplacement(bungeeHex));
            } catch (Exception e) {
                // Older server — strip the hex tag
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Gradient support ──────────────────────────────────────────────────────

    /**
     * Converts {@code <gradient:#RRGGBB:#RRGGBB>text</gradient>} into per-character hex colours.
     * Falls back to start colour on pre-1.16 servers.
     */
    private String applyGradients(String s) {
        Matcher m = GRADIENT_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String startHex = m.group(1);
            String endHex   = m.group(2);
            String text     = m.group(3);
            m.appendReplacement(sb, Matcher.quoteReplacement(buildGradient(startHex, endHex, text)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String buildGradient(String startHex, String endHex, String text) {
        if (text.isEmpty()) return text;
        int[] start = hexToRgb(startHex);
        int[] end   = hexToRgb(endHex);
        StringBuilder result = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float ratio = len == 1 ? 0f : (float) i / (len - 1);
            int r = (int) (start[0] + ratio * (end[0] - start[0]));
            int g = (int) (start[1] + ratio * (end[1] - start[1]));
            int b = (int) (start[2] + ratio * (end[2] - start[2]));
            String hex = String.format("%02x%02x%02x", r, g, b);
            try {
                result.append(net.md_5.bungee.api.ChatColor.of("#" + hex));
            } catch (Exception ignored) {
                result.append(net.md_5.bungee.api.ChatColor.of("#" + startHex));
            }
            result.append(text.charAt(i));
        }
        return result.toString();
    }

    private int[] hexToRgb(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new int[]{r, g, b};
    }
}
