package dev.fluxshop.compat;

import dev.fluxshop.FluxShopPlugin;
import dev.fluxshop.compat.spawner.SpawnerProvider;
import dev.fluxshop.compat.spawner.VanillaSpawnerProvider;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Detects and registers all optional plugin integrations.
 *
 * <p>Each integration is tried in isolation so a failure in one never
 * prevents the others from loading.
 */
public class CompatManager {

    private final FluxShopPlugin plugin;
    private final Logger         log;

    // ── Optional integrations ─────────────────────────────────────────────────
    private SpawnerProvider  spawnerProvider;
    private boolean          hasMythicMobs;
    private boolean          hasCitizens;
    private boolean          hasPAPI;
    private boolean          hasDiscordSRV;
    private boolean          hasLuckPerms;
    private boolean          hasWorldGuard;
    private boolean          hasItemsAdder;
    private boolean          hasOraxen;
    private boolean          hasRealisticSeasons;
    private boolean          hasGeyser;
    private boolean          hasZNPCs;

    public CompatManager(FluxShopPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    public void registerAll() {
        spawnerProvider    = detectSpawnerProvider();
        hasMythicMobs      = isLoaded("MythicMobs");
        hasCitizens        = isLoaded("Citizens");
        hasPAPI            = isLoaded("PlaceholderAPI");
        hasDiscordSRV      = isLoaded("DiscordSRV");
        hasLuckPerms       = isLoaded("LuckPerms");
        hasWorldGuard      = isLoaded("WorldGuard");
        hasItemsAdder      = isLoaded("ItemsAdder");
        hasOraxen          = isLoaded("Oraxen");
        hasRealisticSeasons= isLoaded("RealisticSeasons");
        hasGeyser          = isLoaded("GeyserMC") || isLoaded("floodgate");
        hasZNPCs           = isLoaded("ZNPCsPlus");

        if (hasPAPI)             registerPAPI();
        if (hasCitizens)         registerCitizens();
        if (hasZNPCs)            registerZNPCs();
        if (hasMythicMobs)       log.info("MythicMobs integration enabled.");
        if (hasDiscordSRV)       log.info("DiscordSRV integration enabled.");
        if (hasLuckPerms)        log.info("LuckPerms integration enabled.");
        if (hasWorldGuard)       log.info("WorldGuard integration enabled.");
        if (hasItemsAdder)       log.info("ItemsAdder integration enabled.");
        if (hasOraxen)           log.info("Oraxen integration enabled.");
        if (hasRealisticSeasons) registerRealisticSeasons();
        if (hasGeyser)           log.info("GeyserMC/Floodgate integration enabled.");
    }

    // ── Spawner detection ─────────────────────────────────────────────────────

    private SpawnerProvider detectSpawnerProvider() {
        String configured = plugin.getConfigManager().getString("shop.spawner-provider", "AUTO").toUpperCase();

        if (!configured.equals("AUTO")) {
            return tryLoad(configured);
        }

        // Auto-detect in priority order
        String[] order = {"ROSESTACKER","WILDSTACKER","SILKSPAWNERS","ULTIMATESTACKER",
            "SMARTSPAWNER","EPICSPAWNERS","MINEABLESPAWNERS","SPAWNERMETA","VANILLA"};
        for (String id : order) {
            SpawnerProvider p = tryLoad(id);
            if (p != null) { log.info("Spawner provider: " + id); return p; }
        }
        return new VanillaSpawnerProvider();
    }

    private SpawnerProvider tryLoad(String id) {
        try {
            return switch (id) {
                case "SILKSPAWNERS"   -> isLoaded("SilkSpawners")    ? new dev.fluxshop.compat.spawner.SilkSpawnersProvider()    : null;
                case "ROSESTACKER"    -> isLoaded("RoseStacker")     ? new dev.fluxshop.compat.spawner.RoseStackerProvider()     : null;
                case "WILDSTACKER"    -> isLoaded("WildStacker")     ? new dev.fluxshop.compat.spawner.WildStackerProvider()     : null;
                case "ULTIMATESTACKER"-> isLoaded("UltimateStacker") ? new dev.fluxshop.compat.spawner.UltimateStackerProvider() : null;
                case "SMARTSPAWNER"   -> isLoaded("SmartSpawner")    ? new dev.fluxshop.compat.spawner.SmartSpawnerProvider()    : null;
                case "EPICSPAWNERS"   -> isLoaded("EpicSpawners")    ? new dev.fluxshop.compat.spawner.EpicSpawnersProvider()    : null;
                case "MINEABLESPAWNERS"->isLoaded("MineableSpawners")? new dev.fluxshop.compat.spawner.MineableSpawnersProvider(): null;
                case "SPAWNERMETA"    -> isLoaded("SpawnerMeta")     ? new dev.fluxshop.compat.spawner.SpawnerMetaProvider()     : null;
                case "VANILLA"        -> new VanillaSpawnerProvider();
                default               -> null;
            };
        } catch (Throwable e) {
            return null;
        }
    }

    // ── ZNPCsPlus ─────────────────────────────────────────────────────────────

    private void registerZNPCs() {
        try {
            new dev.fluxshop.compat.znpcs.ZNPCsShopAction(plugin).register();
        } catch (Throwable e) {
            log.warning("ZNPCsPlus integration failed: " + e.getMessage());
        }
    }

    // ── Citizens ──────────────────────────────────────────────────────────────

    private void registerCitizens() {
        try {
            dev.fluxshop.compat.citizens.ShopNPCCommand cmd =
                new dev.fluxshop.compat.citizens.ShopNPCCommand(plugin);
            cmd.register();
            log.info("Citizens NPC trait registered.");
        } catch (Throwable e) {
            log.warning("Citizens integration failed: " + e.getMessage());
        }
    }

    // ── PlaceholderAPI ────────────────────────────────────────────────────────

    private void registerPAPI() {
        try {
            new dev.fluxshop.compat.FluxShopExpansion(plugin).register();
            log.info("PlaceholderAPI expansion registered.");
        } catch (Throwable e) {
            log.warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
        }
    }

    // ── RealisticSeasons ─────────────────────────────────────────────────────

    private void registerRealisticSeasons() {
        try {
            plugin.getServer().getPluginManager()
                .registerEvents(new RealisticSeasonsListener(plugin), plugin);
            log.info("RealisticSeasons integration enabled.");
        } catch (Throwable e) {
            log.warning("RealisticSeasons integration failed: " + e.getMessage());
        }
    }

    /** Returns the current season name (e.g. "SUMMER"), or null if unavailable. */
    public String getCurrentSeason() {
        if (!hasRealisticSeasons) return null;
        try {
            Class<?> api    = Class.forName("me.casperge.realisticseasons.api.SeasonsAPI");
            Object   inst   = api.getMethod("getInstance").invoke(null);
            Object   season = inst.getClass().getMethod("getSeason",
                org.bukkit.World.class).invoke(inst, org.bukkit.Bukkit.getWorlds().get(0));
            return season.toString().toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }

    // ── WorldGuard ────────────────────────────────────────────────────────────

    /**
     * Returns {@code false} if WorldGuard is enabled and the player's location
     * is inside a region where the custom {@code ALLOW_SHOP} flag is set to DENY.
     * Returns {@code true} in all other cases (including when WorldGuard is absent).
     */
    public boolean isShopAllowed(org.bukkit.entity.Player player) {
        if (!hasWorldGuard) return true;
        if (!plugin.getConfigManager().getBoolean("worldguard.enabled", true)) return true;
        try {
            Class<?> wgClass  = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object   wg       = wgClass.getMethod("getInstance").invoke(null);
            Object   platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object   regionMgr = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            // Convert Bukkit world → WorldEdit world
            Class<?> bukkit = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object   wgWorld = bukkit.getMethod("adapt", org.bukkit.World.class)
                                     .invoke(null, player.getWorld());
            Object   query   = regionMgr.getClass().getMethod("createQuery").invoke(regionMgr);
            // com.sk89q.worldedit.util.Location
            Class<?> locClass = Class.forName("com.sk89q.worldedit.util.Location");
            Object   loc = locClass.getConstructor(
                    Class.forName("com.sk89q.worldedit.extent.Extent"), double.class, double.class, double.class)
                .newInstance(wgWorld,
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ());
            // Check the ALLOW flag — we just deny if the region explicitly has a flag-deny
            // A simplified check: if WorldGuard is present, defer to it via StateFlag
            Class<?> registryClass = Class.forName("com.sk89q.worldguard.protection.flags.registry.FlagRegistry");
            Object   registry      = wg.getClass().getMethod("getFlagRegistry").invoke(wg);
            Object   flag          = registryClass.getMethod("get", String.class).invoke(registry, "allow-shop");
            if (flag == null) return true; // flag not registered — allow
            Object flagArray = java.lang.reflect.Array.newInstance(flag.getClass(), 1);
            java.lang.reflect.Array.set(flagArray, 0, flag);
            Object state = query.getClass()
                .getMethod("queryState", locClass,
                    Class.forName("com.sk89q.worldguard.LocalPlayer"),
                    Class.forName("com.sk89q.worldguard.protection.flags.StateFlag[]"))
                .invoke(query, loc, null, flagArray);
            if (state == null) return true;
            return !"DENY".equals(state.toString());
        } catch (Exception e) {
            return true; // on any reflection failure, allow
        }
    }

    // ── MythicMobs item helpers ───────────────────────────────────────────────

    /**
     * Returns {@code true} if the given ItemStack was created by MythicMobs
     * (i.e., carries the internal MYTHIC_TYPE NBT tag).
     */
    public boolean isMythicItem(org.bukkit.inventory.ItemStack item) {
        if (!hasMythicMobs || item == null) return false;
        try {
            Class<?> mmAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object   inst  = mmAPI.getMethod("inst").invoke(null);
            Object   im    = inst.getClass().getMethod("getItemManager").invoke(inst);
            return (boolean) im.getClass()
                .getMethod("isMythicItem", org.bukkit.inventory.ItemStack.class)
                .invoke(im, item);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the internal MythicMobs item ID (the {@code MYTHIC_TYPE} NBT value)
     * for the given ItemStack, or {@code null} if it is not a MythicMobs item.
     */
    public String getMythicItemId(org.bukkit.inventory.ItemStack item) {
        if (!hasMythicMobs || item == null) return null;
        try {
            Class<?> mmAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object   inst  = mmAPI.getMethod("inst").invoke(null);
            Object   im    = inst.getClass().getMethod("getItemManager").invoke(inst);
            Object   result = im.getClass()
                .getMethod("getMythicTypeFromItem", org.bukkit.inventory.ItemStack.class)
                .invoke(im, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Bedrock detection ─────────────────────────────────────────────────────

    /** Returns true if the given player is a Bedrock player (via Floodgate). */
    public boolean isBedrockPlayer(org.bukkit.entity.Player player) {
        if (!hasGeyser) return false;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) inst.getClass().getMethod("isFloodgatePlayer",
                java.util.UUID.class).invoke(inst, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public SpawnerProvider getSpawnerProvider()   { return spawnerProvider; }
    public boolean         hasMythicMobs()        { return hasMythicMobs; }
    public boolean         hasCitizens()          { return hasCitizens; }
    public boolean         hasPAPI()              { return hasPAPI; }
    public boolean         hasDiscordSRV()        { return hasDiscordSRV; }
    public boolean         hasLuckPerms()         { return hasLuckPerms; }
    public boolean         hasWorldGuard()        { return hasWorldGuard; }
    public boolean         hasItemsAdder()        { return hasItemsAdder; }
    public boolean         hasOraxen()            { return hasOraxen; }
    public boolean         hasRealisticSeasons()  { return hasRealisticSeasons; }
    public boolean         hasGeyser()            { return hasGeyser; }
    public boolean         hasZNPCs()             { return hasZNPCs; }

    // ── Custom item helpers ───────────────────────────────────────────────────

    /**
     * Creates a MythicMobs item by id. Returns null if unavailable.
     */
    public org.bukkit.inventory.ItemStack getMythicItem(String id) {
        if (!hasMythicMobs || id == null) return null;
        try {
            Class<?> mmAPI = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object   inst  = mmAPI.getMethod("inst").invoke(null);
            Object   itemManager = inst.getClass().getMethod("getItemManager").invoke(inst);
            java.util.Optional<?> item = (java.util.Optional<?>) itemManager.getClass()
                .getMethod("getItem", String.class).invoke(itemManager, id);
            if (item.isEmpty()) return null;
            return (org.bukkit.inventory.ItemStack) item.get().getClass()
                .getMethod("generateItemStack", int.class).invoke(item.get(), 1);
        } catch (Exception e) {
            log.warning("[CompatManager] Failed to get MythicMobs item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates an ItemsAdder custom item by namespace:id. Returns null if unavailable.
     */
    public org.bukkit.inventory.ItemStack getItemsAdderItem(String id) {
        if (!hasItemsAdder || id == null) return null;
        try {
            Class<?> cls = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object instance = cls.getMethod("getInstance", String.class).invoke(null, id);
            if (instance == null) return null;
            return (org.bukkit.inventory.ItemStack) instance.getClass()
                .getMethod("getItemStack").invoke(instance);
        } catch (Exception e) {
            log.warning("[CompatManager] Failed to get ItemsAdder item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if the given ItemStack is an ItemsAdder custom item.
     * Uses {@code CustomStack.byItemStack(item)} via reflection.
     */
    public boolean isItemsAdderItem(org.bukkit.inventory.ItemStack item) {
        if (!hasItemsAdder || item == null) return false;
        try {
            Class<?> cls = Class.forName("dev.lone.itemsadder.api.CustomStack");
            return cls.getMethod("byItemStack", org.bukkit.inventory.ItemStack.class)
                      .invoke(null, item) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the namespace:id (e.g. {@code "mypack:ruby_sword"}) of an ItemsAdder item,
     * or {@code null} if the ItemStack is not an ItemsAdder item.
     *
     * <p>Tries {@code getNamespacedID()} first (IA v3+), then {@code getId()} (IA v2).
     */
    public String getItemsAdderItemId(org.bukkit.inventory.ItemStack item) {
        if (!hasItemsAdder || item == null) return null;
        try {
            Class<?> cls = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cls.getMethod("byItemStack", org.bukkit.inventory.ItemStack.class)
                              .invoke(null, item);
            if (stack == null) return null;
            // IA v3+ uses getNamespacedID(); IA v2 uses getId()
            try {
                return (String) stack.getClass().getMethod("getNamespacedID").invoke(stack);
            } catch (NoSuchMethodException e) {
                return (String) stack.getClass().getMethod("getId").invoke(stack);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates an Oraxen custom item by id. Returns null if unavailable.
     */
    public org.bukkit.inventory.ItemStack getOraxenItem(String id) {
        if (!hasOraxen || id == null) return null;
        try {
            Class<?> cls = Class.forName("io.th0rgal.oraxen.items.OraxenItems");
            Object builder = cls.getMethod("getItemById", String.class).invoke(null, id);
            if (builder == null) return null;
            return (org.bukkit.inventory.ItemStack) builder.getClass()
                .getMethod("build").invoke(builder);
        } catch (Exception e) {
            log.warning("[CompatManager] Failed to get Oraxen item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Applies custom enchantments from supported plugins (EcoEnchants, AdvancedEnchantments,
     * ExcellentEnchants, CrazyEnchantments) to the given ItemStack via reflection.
     *
     * <p>Any enchantment key not recognized by a loaded plugin is silently ignored — vanilla
     * keys are handled earlier in {@code ShopLoader.applyVanillaEnchantments()}.
     *
     * @param stack   the item to enchant (mutated in place)
     * @param enchants map of enchantment key → level (all keys, both vanilla and custom)
     * @param unsafe  whether to bypass level limits
     */
    public void applyCustomEnchantments(org.bukkit.inventory.ItemStack stack,
                                        java.util.Map<String, Integer> enchants,
                                        boolean unsafe) {
        if (enchants == null || enchants.isEmpty()) return;

        boolean hasEco       = isLoaded("EcoEnchants");
        boolean hasAdvanced  = isLoaded("AdvancedEnchantments");
        boolean hasExcellent = isLoaded("ExcellentEnchants");
        boolean hasCrazy     = isLoaded("CrazyEnchantments");

        for (java.util.Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String key   = entry.getKey().toLowerCase();
            int    level = entry.getValue();

            // ── EcoEnchants ───────────────────────────────────────────────────
            if (hasEco) {
                try {
                    Class<?> reg = Class.forName("com.willfp.ecoenchants.enchant.EcoEnchants");
                    Object enc = reg.getMethod("getByKey", String.class).invoke(null, key);
                    if (enc != null) {
                        org.bukkit.enchantments.Enchantment vanilla =
                            (org.bukkit.enchantments.Enchantment) enc;
                        stack.addUnsafeEnchantment(vanilla, level);
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            // ── AdvancedEnchantments ──────────────────────────────────────────
            if (hasAdvanced) {
                try {
                    Class<?> api = Class.forName("me.egg82.ae.api.AdvancedEnchantmentAPI");
                    Object enc = api.getMethod("getEnchantmentByName", String.class).invoke(null, key);
                    if (enc != null) {
                        // AE enchantments are applied via their ItemManager
                        Class<?> itemMgr = Class.forName("me.egg82.ae.api.AdvancedEnchantedItem");
                        itemMgr.getMethod("addEnchantment", org.bukkit.inventory.ItemStack.class,
                            enc.getClass(), int.class).invoke(null, stack, enc, level);
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            // ── ExcellentEnchants ─────────────────────────────────────────────
            if (hasExcellent) {
                try {
                    Class<?> reg = Class.forName("su.nightexpress.excellentenchants.api.EnchantRegistry");
                    Object inst = reg.getMethod("getInstance").invoke(null);
                    Object enc = inst.getClass().getMethod("get", String.class).invoke(inst, key);
                    if (enc != null) {
                        // ExcellentEnchants wraps Bukkit's Enchantment
                        org.bukkit.enchantments.Enchantment vanilla =
                            (org.bukkit.enchantments.Enchantment)
                                enc.getClass().getMethod("getBukkitEnchantment").invoke(enc);
                        stack.addUnsafeEnchantment(vanilla, level);
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            // ── CrazyEnchantments ─────────────────────────────────────────────
            if (hasCrazy) {
                try {
                    Class<?> api = Class.forName("com.badbones69.crazyenchantments.api.CrazyEnchantments");
                    Object inst = api.getMethod("getAPI").invoke(null);
                    Object enc = inst.getClass().getMethod("getEnchantmentByName", String.class)
                        .invoke(inst, key);
                    if (enc != null) {
                        inst.getClass().getMethod("addEnchantment",
                            org.bukkit.inventory.ItemStack.class, enc.getClass(), int.class)
                            .invoke(inst, stack, enc, level);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isLoaded(String name) {
        Plugin p = plugin.getServer().getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }
}
