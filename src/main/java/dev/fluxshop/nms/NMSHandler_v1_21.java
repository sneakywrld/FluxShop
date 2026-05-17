package dev.fluxshop.nms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

/**
 * NMS handler for Minecraft 1.21+.
 *
 * <p>Uses the full Paper Adventure Component API for title sending and
 * the 1.21 {@link PlayerProfile} API for skull textures.
 */
public class NMSHandler_v1_21 extends NMSHandler_Modern {

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Component titleComp    = LegacyComponentSerializer.legacyAmpersand().deserialize(title);
        Component subtitleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle);
        player.showTitle(net.kyori.adventure.title.Title.title(
            titleComp,
            subtitleComp,
            net.kyori.adventure.title.Title.Times.times(
                net.kyori.adventure.util.Ticks.duration(fadeIn),
                net.kyori.adventure.util.Ticks.duration(stay),
                net.kyori.adventure.util.Ticks.duration(fadeOut)
            )
        ));
    }

    @Override
    public ItemStack createSkullFromBase64(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        try {
            // Decode the base64 to get the texture URL
            String decoded    = new String(Base64.getDecoder().decode(base64));
            // Expected format: {"textures":{"SKIN":{"url":"http://..."}}}
            String urlString  = decoded.split("\"url\":\"")[1].split("\"")[0];
            URL    textureUrl = new URL(urlString);

            PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(UUID.randomUUID(), "FluxShopSkull");
            profile.getTextures().setSkin(textureUrl);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException | ArrayIndexOutOfBoundsException e) {
            // Silently ignore malformed base64
        }
        skull.setItemMeta(meta);
        return skull;
    }

    @Override
    public ItemStack createSkullFromPlayer(String playerName, UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(uuid, playerName);
            meta.setOwnerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    @Override
    public boolean supportsAdventureInventoryTitle() {
        return true;
    }
}
