package simpleclans.simpleclans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class ClanMenu implements Listener {
    private final SimpleclansPlugin plugin;
    private final Map<UUID, MenuType> playerMenus = new HashMap<>();
    private final Map<UUID, Integer> memberPages = new HashMap<>();

    public enum MenuType {
        MAIN, MEMBERS, INVITES, ADMIN, INFO, SETTINGS
    }

    public ClanMenu(SimpleclansPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        openMainMenu(player);
    }

  
    private void openMainMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("not_in_clan", Map.of()));
            return;
        }

        String role = plugin.getRoleOf(player.getUniqueId());
        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ " + clan + " Menu ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.MAIN);

     
        fillBorders(menu, createItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));

        
        menu.setItem(13, createItem(Material.DIAMOND, 
            "§b§lClan Information",
            "§7Click to view detailed",
            "§7clan statistics and info",
            "",
            "§e▸ Click to open"));

  
        menu.setItem(20, createItem(Material.PLAYER_HEAD, 
            "§a§lManage Members",
            "§7View and manage clan members",
            "",
            "§e▸ Left Click: §fView Members",
            role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER") 
                ? "§e▸ Right Click: §fManage Roles" 
                : ""));

        
        if (role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER")) {
            menu.setItem(22, createItem(Material.WRITABLE_BOOK, 
                "§e§lInvite Players",
                "§7Invite new players to your clan",
                "",
                "§7Online Players: §f" + Bukkit.getOnlinePlayers().size(),
                "",
                "§e▸ Click to open"));
        }

  
        menu.setItem(24, createItem(Material.PAPER, 
            "§d§lClan Chat",
            "§7Toggle clan chat mode",
            "",
            "§7Current: §f" + (getChatStatus(player) ? "§aEnabled" : "§cDisabled"),
            "",
            "§e▸ Click to toggle"));

   
        if (role.equalsIgnoreCase("LEADER")) {
            menu.setItem(31, createItem(Material.REDSTONE_BLOCK, 
                "§c§lClan Settings",
                "§7Manage clan settings",
                "",
                "§e▸ Left Click: §fSettings",
                "§c▸ Right Click: §4Disband Clan"));
        } else {
            menu.setItem(31, createItem(Material.BARRIER, 
                "§c§lLeave Clan",
                "§7Leave your current clan",
                "",
                "§c⚠ This action cannot be undone!",
                "",
                "§e▸ Click to confirm"));
        }

      
        if (player.hasPermission("simpleclans.admin")) {
            menu.setItem(49, createItem(Material.COMMAND_BLOCK, 
                "§4§lAdmin Panel",
                "§7Access admin commands",
                "",
                "§e▸ Click to open"));
        }

     
        menu.setItem(45, createItem(Material.RED_STAINED_GLASS_PANE, 
            "§c§lClose Menu", 
            "§7Click to close this menu"));

        player.openInventory(menu);
    }

 
    private void openInfoMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ " + clan + " Info ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.INFO);

        fillBorders(menu, createItem(Material.CYAN_STAINED_GLASS_PANE, " ", ""));

        int level = plugin.getClanLevel(clan);
        int kills = plugin.getClanKills(clan);
        int memberCount = plugin.getClanMemberCount(clan);
        int onlineMembers = plugin.getOnlineClanMembers(clan);
        String leader = plugin.getClanLeader(clan);

      
        menu.setItem(11, createItem(Material.EXPERIENCE_BOTTLE, 
            "§b§lClan Level",
            "§7Current Level: §e" + level,
            "§7Next Level: §e" + (level + 1),
            "",
            "§7Kills needed: §f" + ((level * 5) - kills) + " more"));

      
        menu.setItem(13, createItem(Material.IRON_SWORD, 
            "§c§lTotal Kills",
            "§7Your clan has §c" + kills + " §7kills",
            "",
            "§7Keep fighting to level up!"));

        
        menu.setItem(15, createItem(Material.PLAYER_HEAD, 
            "§a§lMembers",
            "§7Total Members: §f" + memberCount,
            "§7Online: §a" + onlineMembers,
            "§7Offline: §c" + (memberCount - onlineMembers)));

   
        menu.setItem(20, createHeadItem(leader, 
            "§e§lClan Leader",
            "§7Leader: §f" + leader,
            "",
            "§7The founder of " + clan));

    
        menu.setItem(24, createItem(Material.BOOK, 
            "§d§lStatistics",
            "§7Detailed clan statistics",
            "",
            "§7Average Kills: §f" + (memberCount > 0 ? kills / memberCount : 0),
            "§7Kills per Online Member: §f" + (onlineMembers > 0 ? kills / onlineMembers : 0)));

        
        menu.setItem(49, createItem(Material.ARROW, 
            "§e§lBack to Main Menu", 
            "§7Return to the main menu"));

        player.openInventory(menu);
    }

  
    private void openMembersMenu(Player player, int page) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        boolean canManage = role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER");

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Clan Members (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.MEMBERS);
        memberPages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.PURPLE_STAINED_GLASS_PANE, " ", ""));

        Map<UUID, String> members = plugin.getClanMembers(clan);
        List<Map.Entry<UUID, String>> memberList = new ArrayList<>(members.entrySet());

       
        memberList.sort((a, b) -> {
            int priorityA = getRolePriority(a.getValue());
            int priorityB = getRolePriority(b.getValue());
            return Integer.compare(priorityB, priorityA);
        });

        int startIndex = page * 28;
        int endIndex = Math.min(startIndex + 28, memberList.size());

      
        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2; 

            Map.Entry<UUID, String> entry = memberList.get(i);
            UUID memberUUID = entry.getKey();
            String memberRole = entry.getValue();
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUUID);
            boolean isOnline = member.isOnline();

            List<String> lore = new ArrayList<>();
            lore.add("§7Role: " + getRoleColor(memberRole) + memberRole);
            lore.add("§7Status: " + (isOnline ? "§aOnline" : "§cOffline"));
            lore.add("");

            if (canManage && !member.getUniqueId().equals(player.getUniqueId())) {
                lore.add("§e▸ Left Click: §fPromote");
                lore.add("§e▸ Right Click: §fDemote");
                lore.add("§c▸ Shift+Right Click: §4Kick");
            }

            menu.setItem(slot, createHeadItem(member.getName(), 
                (isOnline ? "§a" : "§7") + member.getName(),
                lore.toArray(new String[0])));

            slot++;
        }

     
        if (page > 0) {
            menu.setItem(45, createItem(Material.ARROW, 
                "§e§lPrevious Page", 
                "§7Go to page " + page));
        }

        if (endIndex < memberList.size()) {
            menu.setItem(53, createItem(Material.ARROW, 
                "§e§lNext Page", 
                "§7Go to page " + (page + 2)));
        }

     
        menu.setItem(49, createItem(Material.BARRIER, 
            "§c§lBack to Main Menu", 
            "§7Return to the main menu"));

        player.openInventory(menu);
    }


    private void openInvitesMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Invite Players ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.INVITES);

        fillBorders(menu, createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", ""));

        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) break;
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;


            if (plugin.getClanOf(online.getUniqueId()) != null) continue;
            if (online.getUniqueId().equals(player.getUniqueId())) continue;

            menu.setItem(slot, createHeadItem(online.getName(), 
                "§a" + online.getName(),
                "§7Click to invite to your clan",
                "",
                "§e▸ Click to invite"));

            slot++;
        }


        menu.setItem(49, createItem(Material.PAPER, 
            "§e§lInvite Information",
            "§7Invites expire after 5 minutes",
            "§7Click on a player to invite them"));

   
        menu.setItem(45, createItem(Material.ARROW, 
            "§c§lBack to Main Menu", 
            "§7Return to the main menu"));

        player.openInventory(menu);
    }


    private void openSettingsMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        if (!role.equalsIgnoreCase("LEADER")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Clan Settings ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.SETTINGS);

        fillBorders(menu, createItem(Material.RED_STAINED_GLASS_PANE, " ", ""));

       
        menu.setItem(22, createItem(Material.TNT, 
            "§c§lDisband Clan",
            "§7Permanently delete your clan",
            "",
            "§c⚠ WARNING ⚠",
            "§cThis action CANNOT be undone!",
            "§cAll members will be removed!",
            "",
            "§c▸ Click to confirm"));

      
        menu.setItem(20, createItem(Material.NAME_TAG, 
            "§e§lClan Tag §7(Coming Soon)",
            "§7Change your clan's tag",
            "",
            "§7Currently: §f[" + clan + "]"));

       
        menu.setItem(24, createItem(Material.BOOK, 
            "§e§lClan Description §7(Coming Soon)",
            "§7Set your clan's description",
            "",
            "§7Let others know what",
            "§7your clan is about!"));

        
        menu.setItem(49, createItem(Material.ARROW, 
            "§e§lBack to Main Menu", 
            "§7Return to the main menu"));

        player.openInventory(menu);
    }

    
    private void openAdminMenu(Player player) {
        if (!player.hasPermission("simpleclans.admin")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§4§l⚔ Admin Panel ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.ADMIN);

        fillBorders(menu, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", ""));

        
        menu.setItem(11, createItem(Material.BOOK, 
            "§e§lView All Clans",
            "§7See a list of all clans",
            "",
            "§7Total Clans: §f" + plugin.getAllClanNames().size(),
            "",
            "§e▸ Click to view"));

       
        menu.setItem(13, createItem(Material.PLAYER_HEAD, 
            "§a§lManage Players",
            "§7Add/Remove players from clans",
            "",
            "§e▸ Click to open"));

    
        menu.setItem(15, createItem(Material.BARRIER, 
            "§c§lReset Clan Data",
            "§7Reset a clan's stats",
            "",
            "§c⚠ Use with caution!",
            "",
            "§c▸ Click to select clan"));

      
        menu.setItem(20, createItem(Material.TNT, 
            "§4§lForce Disband",
            "§7Disband any clan",
            "",
            "§c⚠ This is permanent!",
            "",
            "§4▸ Click to select clan"));

   
        menu.setItem(24, createItem(Material.COMMAND_BLOCK, 
            "§b§lReload Config",
            "§7Reload plugin configuration",
            "",
            "§e▸ Click to reload"));

    
        menu.setItem(49, createItem(Material.ARROW, 
            "§e§lBack to Main Menu", 
            "§7Return to the main menu"));

        player.openInventory(menu);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("⚔") && !title.contains("Clan")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        MenuType currentMenu = playerMenus.get(player.getUniqueId());
        if (currentMenu == null) return;

        ClickType clickType = event.getClick();
        String itemName = clicked.getItemMeta().getDisplayName();

        switch (currentMenu) {
            case MAIN -> handleMainMenuClick(player, itemName, clickType);
            case INFO -> handleInfoMenuClick(player, itemName);
            case MEMBERS -> handleMembersMenuClick(player, clicked, clickType);
            case INVITES -> handleInvitesMenuClick(player, clicked);
            case SETTINGS -> handleSettingsMenuClick(player, itemName);
            case ADMIN -> handleAdminMenuClick(player, itemName);
        }
    }

    private void handleMainMenuClick(Player player, String itemName, ClickType clickType) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        switch (itemName) {
            case "§b§lClan Information" -> openInfoMenu(player);
            case "§a§lManage Members" -> {
                if (clickType.isLeftClick()) {
                    openMembersMenu(player, 0);
                }
            }
            case "§e§lInvite Players" -> openInvitesMenu(player);
            case "§d§lClan Chat" -> {
                player.performCommand("clan chat");
                player.closeInventory();
            }
            case "§c§lLeave Clan" -> {
                player.closeInventory();
                player.performCommand("clan leave");
            }
            case "§c§lClan Settings" -> {
                if (clickType.isLeftClick()) {
                    openSettingsMenu(player);
                } else if (clickType.isRightClick()) {
                    player.closeInventory();
                    player.performCommand("clan disband");
                }
            }
            case "§4§lAdmin Panel" -> openAdminMenu(player);
            case "§c§lClose Menu" -> player.closeInventory();
        }
    }

    private void handleInfoMenuClick(Player player, String itemName) {
        if (itemName.equals("§e§lBack to Main Menu")) {
            openMainMenu(player);
        }
    }

    private void handleMembersMenuClick(Player player, ItemStack clicked, ClickType clickType) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.equals("§c§lBack to Main Menu")) {
            openMainMenu(player);
            return;
        }

        if (itemName.equals("§e§lPrevious Page")) {
            int page = memberPages.getOrDefault(player.getUniqueId(), 0);
            openMembersMenu(player, Math.max(0, page - 1));
            return;
        }

        if (itemName.equals("§e§lNext Page")) {
            int page = memberPages.getOrDefault(player.getUniqueId(), 0);
            openMembersMenu(player, page + 1);
            return;
        }

    
        if (clicked.getType() == Material.PLAYER_HEAD) {
            String targetName = itemName.replace("§a", "").replace("§7", "");
            Player target = Bukkit.getPlayerExact(targetName);

            if (target != null && !target.getUniqueId().equals(player.getUniqueId())) {
                String role = plugin.getRoleOf(player.getUniqueId());
                if (role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER")) {
                    if (clickType.isLeftClick()) {
                        player.closeInventory();
                        player.performCommand("clan promote " + targetName);
                    } else if (clickType.isRightClick() && !clickType.isShiftClick()) {
                        player.closeInventory();
                        player.performCommand("clan demote " + targetName);
                    } else if (clickType.isShiftClick() && clickType.isRightClick()) {
                        player.closeInventory();
                    
                        player.sendMessage("§c§l[!] §cKick feature coming soon! Use §e/clan admin kick " + targetName + " " + clan);
                    }
                }
            }
        }
    }

    private void handleInvitesMenuClick(Player player, ItemStack clicked) {
        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.equals("§c§lBack to Main Menu")) {
            openMainMenu(player);
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            String targetName = itemName.replace("§a", "");
            player.closeInventory();
            player.performCommand("clan invite " + targetName);
        }
    }

    private void handleSettingsMenuClick(Player player, String itemName) {
        if (itemName.equals("§c§lDisband Clan")) {
            player.closeInventory();
            player.performCommand("clan disband");
        } else if (itemName.equals("§e§lBack to Main Menu")) {
            openMainMenu(player);
        }
    }

    private void handleAdminMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "§e§lView All Clans" -> {
                player.closeInventory();
                player.performCommand("clan list");
            }
            case "§b§lReload Config" -> {
                player.closeInventory();
                player.performCommand("clan admin reload");
            }
            case "§e§lBack to Main Menu" -> openMainMenu(player);
            default -> player.sendMessage("§c§l[!] §cThis feature is coming soon!");
        }
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                if (!line.isEmpty() || loreList.size() > 0) {
                    loreList.add(line);
                }
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHeadItem(String playerName, String displayName, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            meta.setDisplayName(displayName);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                if (!line.isEmpty() || loreList.size() > 0) {
                    loreList.add(line);
                }
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorders(Inventory inv, ItemStack item) {
        int size = inv.getSize();
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, item);
            inv.setItem(size - 9 + i, item);
        }
        // Left and right columns
        for (int i = 9; i < size - 9; i += 9) {
            inv.setItem(i, item);
            inv.setItem(i + 8, item);
        }
    }

    private int getRolePriority(String role) {
        return switch (role.toUpperCase()) {
            case "LEADER" -> 4;
            case "CO-LEADER" -> 3;
            case "MEMBER" -> 2;
            case "RECRUIT" -> 1;
            default -> 0;
        };
    }

    private String getRoleColor(String role) {
        return switch (role.toUpperCase()) {
            case "LEADER" -> "§6§l";
            case "CO-LEADER" -> "§e§l";
            case "MEMBER" -> "§a";
            case "RECRUIT" -> "§7";
            default -> "§f";
        };
    }

    private boolean getChatStatus(Player player) {
        return false;
    }
}
