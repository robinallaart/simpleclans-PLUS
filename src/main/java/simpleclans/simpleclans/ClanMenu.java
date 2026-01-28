package simpleclans.simpleclans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ClanMenu implements Listener {

    private final SimpleclansPlugin plugin;

    public ClanMenu(SimpleclansPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("not_in_clan", java.util.Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§4Clan Menu - " + clan);

       
        menu.setItem(10, createItem(Material.GOLDEN_SWORD, "§ePromote Member", "Click to promote a member"));

       
        menu.setItem(12, createItem(Material.IRON_SWORD, "§eDemote Member", "Click to demote a member"));

        
        menu.setItem(14, createItem(Material.PLAYER_HEAD, "§eInvite Player", "Click to invite a player"));

       
        menu.setItem(16, createItem(Material.BARRIER, "§cLeave Clan", "Click to leave your clan"));

        player.openInventory(menu);
    }

    private ItemStack createItem(Material material, String name, String loreText) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add(loreText);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("§4Clan Menu")) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player p)) return;

            String clan = plugin.getClanOf(p.getUniqueId());
            if (clan == null) {
                p.closeInventory();
                p.sendMessage(plugin.getMessage("not_in_clan", java.util.Map.of()));
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;

            String name = clicked.getItemMeta().getDisplayName();

            switch (name) {
                case "§ePromote Member" -> p.performCommand("clan promote " + p.getName());
                case "§eDemote Member" -> p.performCommand("clan demote " + p.getName());
                case "§eInvite Player" -> p.performCommand("clan invite " + p.getName());
                case "§cLeave Clan" -> p.performCommand("clan leave");
            }
        }
    }
}
