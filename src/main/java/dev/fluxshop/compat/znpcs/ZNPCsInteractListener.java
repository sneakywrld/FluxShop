package dev.fluxshop.compat.znpcs;

import dev.fluxshop.FluxShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Listens for ZNPCsPlus NPC interact events and triggers the FluxShop action
 * when an NPC carries an action string of the form {@code FLUXSHOP:<shopid>}.
 *
 * <p>The event class and all NPC accessors are invoked via reflection so there
 * is no compile-time dependency on ZNPCsPlus.
 *
 * <p>ZNPCsPlus stores NPC actions as plain strings in a list accessible via
 * {@code npc.getNpcPojo().getInteractActions()} (v3) or
 * {@code npc.getActions()} (v4+). Both are tried in order.
 */
class ZNPCsInteractListener implements Listener {

    private static final String ACTION_PREFIX = "FLUXSHOP:";

    private final FluxShopPlugin plugin;

    ZNPCsInteractListener(FluxShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Generic event handler — catches any event whose simple class name is
     * "NPCInteractEvent" and processes it via reflection.
     *
     * <p>We cannot reference the event class directly (no compile-time dep), so we
     * register against {@link org.bukkit.event.Event} and filter by class name.
     * Bukkit's event bus still routes correctly because the event is a subclass of Event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNPCInteract(org.bukkit.event.Event event) {
        String className = event.getClass().getSimpleName();
        if (!className.equals("NPCInteractEvent")) return;

        try {
            // Extract the interacting player
            Player player = (Player) event.getClass()
                .getMethod("getPlayer").invoke(event);
            if (player == null) return;

            // Extract the NPC object
            Object npc = event.getClass().getMethod("getNpc").invoke(event);
            if (npc == null) return;

            // Collect action strings — try v4 API first, then v3 pojo API
            Collection<?> actions = getActions(npc);
            if (actions == null || actions.isEmpty()) return;

            for (Object actionObj : actions) {
                String action = actionObj.toString().trim();
                if (action.toUpperCase().startsWith(ACTION_PREFIX)) {
                    String shopId = action.substring(ACTION_PREFIX.length()).trim();
                    ZNPCsShopAction.execute(plugin, player, shopId);
                    return; // only fire once per interaction even if duplicate entries
                }
            }
        } catch (Exception ignored) {
            // Silently ignore — ZNPCsPlus API mismatch should not produce console spam
        }
    }

    /**
     * Attempts to read NPC action strings from both the ZNPCsPlus v3 pojo API
     * and the v4 direct API.
     *
     * @param npc the NPC object from the event
     * @return a collection of action strings, or null if not readable
     */
    @SuppressWarnings("unchecked")
    private Collection<?> getActions(Object npc) {
        // v4: npc.getActions() returns List<String>
        try {
            Method getActions = npc.getClass().getMethod("getActions");
            Object result = getActions.invoke(npc);
            if (result instanceof Collection<?> col) return col;
        } catch (NoSuchMethodException ignored) {}
        catch (Exception ignored) {}

        // v3: npc.getNpcPojo().getInteractActions() returns List<String>
        try {
            Object pojo = npc.getClass().getMethod("getNpcPojo").invoke(npc);
            if (pojo == null) return null;
            Object result = pojo.getClass().getMethod("getInteractActions").invoke(pojo);
            if (result instanceof Collection<?> col) return col;
        } catch (Exception ignored) {}

        return null;
    }
}
