package dev.fluxshop.util;

import dev.fluxshop.FluxShopPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fluent builder for {@link ItemStack} objects.
 *
 * <p>Handles all version differences via the plugin's {@link dev.fluxshop.nms.NMSHandler}.
 * Supports legacy colour codes and hex colours in names/lore.
 *
 * <pre>{@code
 * ItemStack item = new ItemBuilder(Material.DIAMOND)
 *     .name("&b&lShiny Diamond")
 *     .lore("&7A very shiny diamond.", "", "&6Buy: $500")
 *     .amount(1)
 *     .glow(true)
 *     .build();
 * }</pre>
 */
public class ItemBuilder {

    private ItemStack item;
    private ItemMeta  meta;
    private final FluxShopPlugin plugin;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ItemBuilder(Material material) {
        this(material, 1, FluxShopPlugin.getInstance());
    }

    public ItemBuilder(Material material, int amount) {
        this(material, amount, FluxShopPlugin.getInstance());
    }

    public ItemBuilder(Material material, int amount, FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.item   = new ItemStack(material, amount);
        this.meta   = item.getItemMeta();
        if (this.meta == null) {
            this.meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(material);
        }
    }

    /** Wraps an existing ItemStack for modification. Makes a clone. */
    public ItemBuilder(ItemStack base) {
        this.plugin = FluxShopPlugin.getInstance();
        this.item   = base.clone();
        this.meta   = this.item.getItemMeta();
    }

    // ── Builder methods ───────────────────────────────────────────────────────

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta == null) return this;
        List<String> colored = Arrays.stream(lines)
            .map(this::color)
            .collect(Collectors.toList());
        meta.setLore(colored);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta == null) return this;
        meta.setLore(lines.stream().map(this::color).collect(Collectors.toList()));
        return this;
    }

    public ItemBuilder appendLore(String... lines) {
        if (meta == null) return this;
        List<String> existing = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
        Arrays.stream(lines).map(this::color).forEach(existing::add);
        meta.setLore(existing);
        return this;
    }

    public ItemBuilder appendLore(List<String> lines) {
        if (meta == null) return this;
        List<String> existing = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
        lines.stream().map(this::color).forEach(existing::add);
        meta.setLore(existing);
        return this;
    }

    /** Adds an enchantment glow effect unconditionally. */
    public ItemBuilder glow() {
        return glow(true);
    }

    /** Adds an enchantment glow effect (Unbreaking I hidden). */
    public ItemBuilder glow(boolean glow) {
        if (!glow || meta == null) return this;
        if (meta instanceof EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchant, int level) {
        if (meta == null) return this;
        if (meta instanceof EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(enchant, level, true);
        } else {
            meta.addEnchant(enchant, level, true);
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        if (meta != null) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    public ItemBuilder hideAll() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (meta == null) return this;
        try {
            meta.setCustomModelData(data);
        } catch (Exception ignored) {
            // 1.13 doesn't have custom model data — ignore
        }
        return this;
    }

    public ItemBuilder nbt(String key, String value) {
        ItemStack built = build();
        return new ItemBuilder(plugin.getNMSHandler().setNBTString(built, key, value));
    }

    public ItemBuilder skull(String base64) {
        ItemStack skull = plugin.getNMSHandler().createSkullFromBase64(base64);
        ItemMeta  skullMeta = skull.getItemMeta();
        if (skullMeta != null && meta != null) {
            // Copy over name, lore etc.
            skullMeta.setDisplayName(meta.getDisplayName());
            skullMeta.setLore(meta.getLore());
        }
        this.item = skull;
        this.meta = skullMeta;
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }

    // ── Static factory helpers ────────────────────────────────────────────────

    /** Creates a simple named pane for GUI borders / backgrounds. */
    public static ItemStack pane(Material material, String name) {
        return new ItemBuilder(material).name(name).hideAll().build();
    }

    /** Creates a transparent "air" placeholder. */
    public static ItemStack air() {
        return new ItemStack(Material.AIR);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
