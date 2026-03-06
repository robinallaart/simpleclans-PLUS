package simpleclans.simpleclans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class ClanMenu implements Listener {
    private final SimpleclansPlugin plugin;

    // Track which menu each player has open
    private final Map<UUID, MenuType> playerMenus = new HashMap<>();

    // Pagination state
    private final Map<UUID, Integer> memberPages      = new HashMap<>();
    private final Map<UUID, Integer> invitePages      = new HashMap<>();
    private final Map<UUID, Integer> adminClanPages   = new HashMap<>();
    private final Map<UUID, Integer> adminPlayerPages = new HashMap<>();

    // Chat-input pending actions: "TAG", "DESC", "ADMIN_PLACE"
    private final Map<UUID, String> pendingInput = new HashMap<>();

    // When an admin is picking a clan for a specific player (manage-players flow)
    private final Map<UUID, UUID> pendingAdminTarget = new HashMap<>();

    public enum MenuType {
        MAIN, MEMBERS, INVITES, ADMIN, INFO, SETTINGS,
        ADMIN_RESET, ADMIN_DISBAND, ADMIN_PLAYERS
    }

    // ─── Constructor ───────────────────────────────────────────────────────────

    public ClanMenu(SimpleclansPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        openMainMenu(player);
    }

    // ─── MAIN MENU ─────────────────────────────────────────────────────────────

    private void openMainMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(plugin.getMessage("not_in_clan", Map.of()));
            return;
        }

        String role  = plugin.getRoleOf(player.getUniqueId());
        int    level = plugin.getClanLevel(clan);
        int    kills = plugin.getClanKills(clan);
        String tag   = plugin.getClanTag(clan);

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ " + clan + " Menu ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.MAIN);

        fillBorders(menu, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));

        // Clan Information — shows live level & kills
        menu.setItem(13, createItem(Material.DIAMOND,
            "§b§lClan Information",
            "§7Level: §e" + level + "  §7Kills: §c" + kills,
            "§7Tag: §f[" + tag + "]",
            "",
            "§e▸ Click for full stats"));

        // Manage Members
        menu.setItem(20, createItem(Material.PLAYER_HEAD,
            "§a§lManage Members",
            "§7View and manage clan members",
            "",
            "§e▸ Left Click: §fView Members"));

        // Invite Players (leader / co-leader only)
        if (role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER")) {
            menu.setItem(22, createItem(Material.WRITABLE_BOOK,
                "§e§lInvite Players",
                "§7Invite new players to your clan",
                "",
                "§7Online (no clan): §f" + countClanlessOnline(),
                "",
                "§e▸ Click to open"));
        }

        // Clan Chat toggle
        menu.setItem(24, createItem(Material.PAPER,
            "§d§lClan Chat",
            "§7Toggle clan chat mode",
            "",
            "§7Current: §f" + (plugin.getClanChatStatus(player.getUniqueId()) ? "§aEnabled" : "§cDisabled"),
            "",
            "§e▸ Click to toggle"));

        // Settings (leader) or Leave (everyone else)
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

        // Admin Panel
        if (player.hasPermission("simpleclans.admin")) {
            menu.setItem(49, createItem(Material.COMMAND_BLOCK,
                "§4§lAdmin Panel",
                "§7Access admin commands",
                "",
                "§e▸ Click to open"));
        }

        // Close
        menu.setItem(45, createItem(Material.RED_STAINED_GLASS_PANE,
            "§c§lClose Menu",
            "§7Click to close this menu"));

        player.openInventory(menu);
    }

    // ─── INFO MENU ─────────────────────────────────────────────────────────────

    private void openInfoMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        int    level       = plugin.getClanLevel(clan);
        int    kills       = plugin.getClanKills(clan);
        int    memberCount = plugin.getClanMemberCount(clan);
        int    online      = plugin.getOnlineClanMembers(clan);
        String leader      = plugin.getClanLeader(clan);
        String tag         = plugin.getClanTag(clan);
        String desc        = plugin.getClanDescription(clan);
        int    killsNeeded = Math.max(0, (level * 5) - kills);

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ " + clan + " Info ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.INFO);

        fillBorders(menu, createItem(Material.CYAN_STAINED_GLASS_PANE, " "));

        menu.setItem(11, createItem(Material.EXPERIENCE_BOTTLE,
            "§b§lClan Level",
            "§7Current Level: §e" + level,
            "§7Next Level: §e" + (level + 1),
            "",
            "§7Kills to next level: §f" + killsNeeded));

        menu.setItem(13, createItem(Material.IRON_SWORD,
            "§c§lTotal Kills",
            "§7Your clan has §c" + kills + " §7kills",
            "",
            "§7Keep fighting to level up!"));

        menu.setItem(15, createItem(Material.PLAYER_HEAD,
            "§a§lMembers",
            "§7Total: §f" + memberCount,
            "§7Online: §a" + online,
            "§7Offline: §c" + (memberCount - online)));

        menu.setItem(20, createHeadItem(leader,
            "§e§lClan Leader",
            "§7Leader: §f" + leader,
            "",
            "§7The founder of " + clan));

        menu.setItem(22, createItem(Material.NAME_TAG,
            "§d§lClan Tag",
            "§7Current tag: §f[" + tag + "]"));

        menu.setItem(24, createItem(Material.BOOK,
            "§d§lStatistics",
            "§7Avg kills/member: §f" + (memberCount > 0 ? kills / memberCount : 0),
            "§7Kills per online: §f" + (online > 0 ? kills / online : 0)));

        // Description
        String[] descLines = splitDescription(desc);
        List<String> descLore = new ArrayList<>();
        descLore.add("§7");
        descLore.addAll(Arrays.asList(descLines));
        menu.setItem(31, createItem(Material.PAPER,
            "§f§lClan Description",
            descLore.toArray(new String[0])));

        menu.setItem(49, createItem(Material.ARROW,
            "§e§lBack to Main Menu",
            "§7Return to the main menu"));

        player.openInventory(menu);
    }

    // ─── MEMBERS MENU ──────────────────────────────────────────────────────────

    private void openMembersMenu(Player player, int page) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String  role      = plugin.getRoleOf(player.getUniqueId());
        boolean canManage = role.equalsIgnoreCase("LEADER") || role.equalsIgnoreCase("CO-LEADER");

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Clan Members (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.MEMBERS);
        memberPages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.PURPLE_STAINED_GLASS_PANE, " "));

        Map<UUID, String> members    = plugin.getClanMembers(clan);
        List<Map.Entry<UUID, String>> memberList = new ArrayList<>(members.entrySet());
        memberList.sort((a, b) -> Integer.compare(getRolePriority(b.getValue()), getRolePriority(a.getValue())));

        int startIndex = page * 28;
        int endIndex   = Math.min(startIndex + 28, memberList.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;

            Map.Entry<UUID, String> entry      = memberList.get(i);
            UUID                    memberUUID = entry.getKey();
            String                  memberRole = entry.getValue();
            OfflinePlayer           member     = Bukkit.getOfflinePlayer(memberUUID);
            boolean                 isOnline   = member.isOnline();
            String                  name       = member.getName() != null ? member.getName() : memberUUID.toString();

            List<String> lore = new ArrayList<>();
            lore.add("§7Role: " + getRoleColor(memberRole) + memberRole);
            lore.add("§7Status: " + (isOnline ? "§aOnline" : "§cOffline"));
            lore.add("");
            if (canManage && !memberUUID.equals(player.getUniqueId())) {
                lore.add("§e▸ Left Click: §fPromote");
                lore.add("§e▸ Right Click: §fDemote");
                lore.add("§c▸ Shift+Right Click: §4Kick");
            }
            // Hidden UUID — used for offline-safe resolution on click
            lore.add("§0UUID:" + memberUUID);

            menu.setItem(slot++, createHeadItem(name,
                (isOnline ? "§a" : "§7") + name,
                lore.toArray(new String[0])));
        }

        if (page > 0)
            menu.setItem(45, createItem(Material.ARROW, "§e§lPrevious Page", "§7Go to page " + page));
        if (endIndex < memberList.size())
            menu.setItem(53, createItem(Material.ARROW, "§e§lNext Page", "§7Go to page " + (page + 2)));

        menu.setItem(49, createItem(Material.BARRIER, "§c§lBack to Main Menu", "§7Return to the main menu"));
        player.openInventory(menu);
    }

    // ─── INVITE MENU ───────────────────────────────────────────────────────────

    private void openInvitesMenu(Player player) { openInvitesMenu(player, 0); }

    private void openInvitesMenu(Player player, int page) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Invite Players (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.INVITES);
        invitePages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.YELLOW_STAINED_GLASS_PANE, " "));

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getClanOf(online.getUniqueId()) != null) continue;
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            eligible.add(online);
        }

        int startIndex = page * 28;
        int endIndex   = Math.min(startIndex + 28, eligible.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            Player online = eligible.get(i);
            menu.setItem(slot++, createHeadItem(online.getName(),
                "§a" + online.getName(),
                "§7Click to invite to your clan",
                "",
                "§e▸ Click to invite"));
        }

        if (eligible.isEmpty()) {
            menu.setItem(22, createItem(Material.BARRIER,
                "§cNo players available",
                "§7All online players are already in a clan."));
        }

        if (page > 0)
            menu.setItem(45, createItem(Material.ARROW, "§e§lPrevious Page", "§7Go to page " + page));
        if (endIndex < eligible.size())
            menu.setItem(53, createItem(Material.ARROW, "§e§lNext Page", "§7Go to page " + (page + 2)));

        menu.setItem(49, createItem(Material.PAPER,
            "§e§lInvite Information",
            "§7Invites expire after 5 minutes",
            "§7Click on a player to invite them",
            "",
            "§7Available players: §f" + eligible.size()));

        menu.setItem(40, createItem(Material.BARRIER, "§c§lBack to Main Menu", "§7Return to the main menu"));
        player.openInventory(menu);
    }

    // ─── SETTINGS MENU ─────────────────────────────────────────────────────────

    private void openSettingsMenu(Player player) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        if (!role.equalsIgnoreCase("LEADER")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        String tag  = plugin.getClanTag(clan);
        String desc = plugin.getClanDescription(clan);

        Inventory menu = Bukkit.createInventory(null, 54, "§6§l⚔ Clan Settings ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.SETTINGS);

        fillBorders(menu, createItem(Material.RED_STAINED_GLASS_PANE, " "));

        // Clan Tag — fully functional
        menu.setItem(20, createItem(Material.NAME_TAG,
            "§e§lClan Tag",
            "§7Change your clan's display tag",
            "",
            "§7Currently: §f[" + tag + "]",
            "§7Max 6 chars, letters & numbers only",
            "",
            "§e▸ Click to change"));

        // Clan Description — fully functional
        menu.setItem(22, createItem(Material.WRITABLE_BOOK,
            "§e§lClan Description",
            "§7Set your clan's description",
            "",
            "§7Current: §f" + truncate(desc, 30),
            "",
            "§e▸ Click to change"));

        // Disband
        menu.setItem(24, createItem(Material.TNT,
            "§c§lDisband Clan",
            "§7Permanently delete your clan",
            "",
            "§c⚠ WARNING ⚠",
            "§cThis action CANNOT be undone!",
            "§cAll members will be removed!",
            "",
            "§c▸ Click to confirm"));

        menu.setItem(49, createItem(Material.ARROW, "§e§lBack to Main Menu", "§7Return to the main menu"));
        player.openInventory(menu);
    }

    // ─── ADMIN MENU ────────────────────────────────────────────────────────────

    private void openAdminMenu(Player player) {
        if (!player.hasPermission("simpleclans.admin")) {
            player.sendMessage(plugin.getMessage("no_permission", Map.of()));
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§4§l⚔ Admin Panel ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.ADMIN);

        fillBorders(menu, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));

        menu.setItem(11, createItem(Material.BOOK,
            "§e§lView All Clans",
            "§7See a list of all clans",
            "",
            "§7Total Clans: §f" + plugin.getAllClanNames().size(),
            "",
            "§e▸ Click to view in chat"));

        menu.setItem(13, createItem(Material.PLAYER_HEAD,
            "§a§lManage Players",
            "§7Add clanless players to a clan",
            "",
            "§7Click a player, then type a clan name",
            "",
            "§e▸ Click to open"));

        menu.setItem(15, createItem(Material.COMPARATOR,
            "§c§lReset Clan Data",
            "§7Reset a clan's kills & levels",
            "§7All members demoted to Recruit",
            "",
            "§c⚠ Use with caution!",
            "",
            "§c▸ Click to select clan"));

        menu.setItem(20, createItem(Material.TNT,
            "§4§lForce Disband",
            "§7Permanently disband any clan",
            "",
            "§c⚠ This is permanent!",
            "",
            "§4▸ Click to select clan"));

        menu.setItem(24, createItem(Material.COMMAND_BLOCK,
            "§b§lReload Config",
            "§7Reload plugin configuration",
            "",
            "§e▸ Click to reload"));

        menu.setItem(49, createItem(Material.ARROW, "§e§lBack to Main Menu", "§7Return to the main menu"));
        player.openInventory(menu);
    }

    // ─── ADMIN SUB: RESET CLAN DATA ────────────────────────────────────────────

    private void openAdminResetMenu(Player player, int page) {
        if (!player.hasPermission("simpleclans.admin")) return;

        List<String> clans = plugin.getAllClanNames();
        Inventory menu = Bukkit.createInventory(null, 54, "§c§l⚔ Reset Clan Data (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.ADMIN_RESET);
        adminClanPages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.RED_STAINED_GLASS_PANE, " "));

        int startIndex = page * 28;
        int endIndex   = Math.min(startIndex + 28, clans.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            String c = clans.get(i);
            menu.setItem(slot++, createItem(Material.CHEST,
                "§c" + c,
                "§7Members: §f" + plugin.getClanMemberCount(c),
                "§7Level: §f"   + plugin.getClanLevel(c),
                "§7Kills: §f"   + plugin.getClanKills(c),
                "",
                "§c▸ Click to reset this clan's data"));
        }

        if (clans.isEmpty())
            menu.setItem(22, createItem(Material.BARRIER, "§cNo clans exist yet.", ""));

        if (page > 0)
            menu.setItem(45, createItem(Material.ARROW, "§e§lPrevious Page", "§7Go to page " + page));
        if (endIndex < clans.size())
            menu.setItem(53, createItem(Material.ARROW, "§e§lNext Page", "§7Go to page " + (page + 2)));

        menu.setItem(49, createItem(Material.BARRIER, "§c§lBack to Admin Panel", "§7Return to admin menu"));
        player.openInventory(menu);
    }

    // ─── ADMIN SUB: FORCE DISBAND ──────────────────────────────────────────────

    private void openAdminDisbandMenu(Player player, int page) {
        if (!player.hasPermission("simpleclans.admin")) return;

        List<String> clans = plugin.getAllClanNames();
        Inventory menu = Bukkit.createInventory(null, 54, "§4§l⚔ Force Disband (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.ADMIN_DISBAND);
        adminClanPages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.BLACK_STAINED_GLASS_PANE, " "));

        int startIndex = page * 28;
        int endIndex   = Math.min(startIndex + 28, clans.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            String c = clans.get(i);
            menu.setItem(slot++, createItem(Material.TNT,
                "§4" + c,
                "§7Members: §f" + plugin.getClanMemberCount(c),
                "§7Level: §f"   + plugin.getClanLevel(c),
                "",
                "§c⚠ This PERMANENTLY disbands the clan!",
                "",
                "§4▸ Click to disband"));
        }

        if (clans.isEmpty())
            menu.setItem(22, createItem(Material.BARRIER, "§cNo clans exist yet.", ""));

        if (page > 0)
            menu.setItem(45, createItem(Material.ARROW, "§e§lPrevious Page", "§7Go to page " + page));
        if (endIndex < clans.size())
            menu.setItem(53, createItem(Material.ARROW, "§e§lNext Page", "§7Go to page " + (page + 2)));

        menu.setItem(49, createItem(Material.BARRIER, "§c§lBack to Admin Panel", "§7Return to admin menu"));
        player.openInventory(menu);
    }

    // ─── ADMIN SUB: MANAGE PLAYERS ─────────────────────────────────────────────

    private void openAdminPlayersMenu(Player player, int page) {
        if (!player.hasPermission("simpleclans.admin")) return;

        List<Player> clanless = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getClanOf(online.getUniqueId()) == null) clanless.add(online);
        }

        Inventory menu = Bukkit.createInventory(null, 54, "§a§l⚔ Manage Players (Page " + (page + 1) + ") ⚔");
        playerMenus.put(player.getUniqueId(), MenuType.ADMIN_PLAYERS);
        adminPlayerPages.put(player.getUniqueId(), page);

        fillBorders(menu, createItem(Material.GREEN_STAINED_GLASS_PANE, " "));

        int startIndex = page * 28;
        int endIndex   = Math.min(startIndex + 28, clanless.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            Player target = clanless.get(i);
            menu.setItem(slot++, createHeadItem(target.getName(),
                "§a" + target.getName(),
                "§7Status: §aOnline",
                "§7Clan: §cNone",
                "",
                "§e▸ Click to add to a clan"));
        }

        if (clanless.isEmpty()) {
            menu.setItem(22, createItem(Material.BARRIER,
                "§cNo clanless players online",
                "§7All online players are in a clan."));
        }

        if (page > 0)
            menu.setItem(45, createItem(Material.ARROW, "§e§lPrevious Page", "§7Go to page " + page));
        if (endIndex < clanless.size())
            menu.setItem(53, createItem(Material.ARROW, "§e§lNext Page", "§7Go to page " + (page + 2)));

        menu.setItem(49, createItem(Material.BARRIER, "§c§lBack to Admin Panel", "§7Return to admin menu"));
        player.openInventory(menu);
    }

    // ─── INVENTORY CLICK HANDLER ───────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!title.contains("Clan") && !title.contains("Menu") && !title.contains("Admin")
                && !title.contains("Members") && !title.contains("Info")
                && !title.contains("Invite") && !title.contains("Settings")
                && !title.contains("Reset") && !title.contains("Disband")
                && !title.contains("Manage Players")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        MenuType currentMenu = playerMenus.get(player.getUniqueId());
        if (currentMenu == null) {
            if      (title.contains("Reset"))          currentMenu = MenuType.ADMIN_RESET;
            else if (title.contains("Force Disband"))  currentMenu = MenuType.ADMIN_DISBAND;
            else if (title.contains("Manage Players")) currentMenu = MenuType.ADMIN_PLAYERS;
            else if (title.contains("Info"))           currentMenu = MenuType.INFO;
            else if (title.contains("Members"))        currentMenu = MenuType.MEMBERS;
            else if (title.contains("Invite"))         currentMenu = MenuType.INVITES;
            else if (title.contains("Settings"))       currentMenu = MenuType.SETTINGS;
            else if (title.contains("Admin"))          currentMenu = MenuType.ADMIN;
            else                                       currentMenu = MenuType.MAIN;
            playerMenus.put(player.getUniqueId(), currentMenu);
        }

        ClickType clickType = event.getClick();
        String    itemName  = clicked.getItemMeta().getDisplayName();

        switch (currentMenu) {
            case MAIN          -> handleMainMenuClick(player, itemName, clickType);
            case INFO          -> handleInfoMenuClick(player, itemName);
            case MEMBERS       -> handleMembersMenuClick(player, clicked, clickType);
            case INVITES       -> handleInvitesMenuClick(player, clicked);
            case SETTINGS      -> handleSettingsMenuClick(player, itemName);
            case ADMIN         -> handleAdminMenuClick(player, itemName);
            case ADMIN_RESET   -> handleAdminResetMenuClick(player, itemName);
            case ADMIN_DISBAND -> handleAdminDisbandMenuClick(player, itemName);
            case ADMIN_PLAYERS -> handleAdminPlayersMenuClick(player, clicked);
        }
    }

    // ─── CLICK HANDLERS ────────────────────────────────────────────────────────

    private void handleMainMenuClick(Player player, String itemName, ClickType clickType) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        switch (itemName) {
            case "§b§lClan Information" -> openInfoMenu(player);
            case "§a§lManage Members" -> { if (clickType.isLeftClick()) openMembersMenu(player, 0); }
            case "§e§lInvite Players"  -> openInvitesMenu(player);
            case "§d§lClan Chat" -> { player.performCommand("clan chat"); player.closeInventory(); }
            case "§c§lLeave Clan"  -> { player.closeInventory(); player.performCommand("clan leave"); }
            case "§c§lClan Settings" -> {
                if (clickType.isLeftClick())       openSettingsMenu(player);
                else if (clickType.isRightClick()) { player.closeInventory(); player.performCommand("clan disband"); }
            }
            case "§4§lAdmin Panel" -> openAdminMenu(player);
            case "§c§lClose Menu"  -> player.closeInventory();
        }
    }

    private void handleInfoMenuClick(Player player, String itemName) {
        if (itemName.equals("§e§lBack to Main Menu")) openMainMenu(player);
    }

    private void handleMembersMenuClick(Player player, ItemStack clicked, ClickType clickType) {
        String clan = plugin.getClanOf(player.getUniqueId());
        if (clan == null) return;

        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.equals("§c§lBack to Main Menu")) { openMainMenu(player); return; }
        if (itemName.equals("§e§lPrevious Page")) {
            openMembersMenu(player, Math.max(0, memberPages.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (itemName.equals("§e§lNext Page")) {
            openMembersMenu(player, memberPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (clicked.getType() != Material.PLAYER_HEAD) return;

        UUID targetUUID = getUUIDFromLore(clicked);
        if (targetUUID == null || targetUUID.equals(player.getUniqueId())) return;

        String role = plugin.getRoleOf(player.getUniqueId());
        if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) return;

        String        targetRole    = plugin.getRoleOf(targetUUID);
        if (targetRole == null) return;
        OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetUUID);
        String        targetName    = targetOffline.getName() != null ? targetOffline.getName() : targetUUID.toString();
        Player        targetOnline  = Bukkit.getPlayer(targetUUID);

        player.closeInventory();

        if (clickType.isShiftClick() && clickType.isRightClick()) {
            if (targetRole.equalsIgnoreCase("LEADER")) {
                player.sendMessage(plugin.getMessage("cannot_kick_leader", Map.of()));
            } else if (role.equalsIgnoreCase("CO-LEADER") && targetRole.equalsIgnoreCase("CO-LEADER")) {
                player.sendMessage(plugin.getMessage("cannot_kick_coleader", Map.of()));
            } else {
                plugin.removeMemberFromClan(targetUUID);
                player.sendMessage(plugin.getMessage("kicked_player", Map.of("player", targetName, "clan", clan)));
                if (targetOnline != null) targetOnline.sendMessage(plugin.getMessage("you_were_kicked", Map.of("clan", clan)));
            }
        } else if (clickType.isLeftClick() && !clickType.isShiftClick()) {
            String newRole = switch (targetRole.toUpperCase()) {
                case "RECRUIT" -> "MEMBER";
                case "MEMBER"  -> "CO-LEADER";
                default        -> null;
            };
            if (newRole == null) {
                player.sendMessage(plugin.getMessage("cannot_promote", Map.of()));
            } else {
                plugin.addMemberToClan(targetUUID, clan, newRole);
                player.sendMessage(plugin.getMessage("promote_success", Map.of("player", targetName, "role", newRole)));
                if (targetOnline != null) targetOnline.sendMessage(plugin.getMessage("promoted_to", Map.of("role", newRole)));
            }
        } else if (clickType.isRightClick() && !clickType.isShiftClick()) {
            String newRole = switch (targetRole.toUpperCase()) {
                case "CO-LEADER" -> "MEMBER";
                case "MEMBER"    -> "RECRUIT";
                default          -> null;
            };
            if (newRole == null) {
                player.sendMessage(plugin.getMessage("cannot_demote", Map.of()));
            } else {
                plugin.addMemberToClan(targetUUID, clan, newRole);
                player.sendMessage(plugin.getMessage("demote_success", Map.of("player", targetName, "role", newRole)));
                if (targetOnline != null) targetOnline.sendMessage(plugin.getMessage("demoted_to", Map.of("role", newRole)));
            }
        }
    }

    private void handleInvitesMenuClick(Player player, ItemStack clicked) {
        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.equals("§c§lBack to Main Menu")) { openMainMenu(player); return; }
        if (itemName.equals("§e§lPrevious Page")) {
            openInvitesMenu(player, Math.max(0, invitePages.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (itemName.equals("§e§lNext Page")) {
            openInvitesMenu(player, invitePages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD) {
            String targetName = itemName.replace("§a", "").trim();
            Player target     = Bukkit.getPlayerExact(targetName);
            if (target == null) return;

            String clan = plugin.getClanOf(player.getUniqueId());
            if (plugin.getClanOf(target.getUniqueId()) != null) {
                player.sendMessage(plugin.getMessage("already_in_clan_player",
                    Map.of("clan", clan != null ? clan : "")));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            player.performCommand("clan invite " + targetName);
        }
    }

    private void handleSettingsMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "§e§lClan Tag" -> {
                player.closeInventory();
                pendingInput.put(player.getUniqueId(), "TAG");
                player.sendMessage("§6[Simpleclan-PLUS] §eType your new clan tag in chat.");
                player.sendMessage("§7Max 6 characters, letters & numbers only. Type §ccancel §7to abort.");
            }
            case "§e§lClan Description" -> {
                player.closeInventory();
                pendingInput.put(player.getUniqueId(), "DESC");
                player.sendMessage("§6[Simpleclan-PLUS] §eType your new clan description in chat.");
                player.sendMessage("§7Max 100 characters. Type §ccancel §7to abort.");
            }
            case "§c§lDisband Clan"        -> { player.closeInventory(); player.performCommand("clan disband"); }
            case "§e§lBack to Main Menu"   -> openMainMenu(player);
        }
    }

    private void handleAdminMenuClick(Player player, String itemName) {
        switch (itemName) {
            case "§e§lView All Clans"  -> { player.closeInventory(); player.performCommand("clan list"); }
            case "§a§lManage Players"  -> openAdminPlayersMenu(player, 0);
            case "§c§lReset Clan Data" -> openAdminResetMenu(player, 0);
            case "§4§lForce Disband"   -> openAdminDisbandMenu(player, 0);
            case "§b§lReload Config"   -> { player.closeInventory(); player.performCommand("clan admin reload"); }
            case "§e§lBack to Main Menu" -> openMainMenu(player);
        }
    }

    private void handleAdminResetMenuClick(Player player, String itemName) {
        if (itemName.equals("§c§lBack to Admin Panel")) { openAdminMenu(player); return; }
        if (itemName.equals("§e§lPrevious Page")) {
            openAdminResetMenu(player, Math.max(0, adminClanPages.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (itemName.equals("§e§lNext Page")) {
            openAdminResetMenu(player, adminClanPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        // Clan items are named §c<clanName>
        String clanName = itemName.replace("§c", "").trim();
        if (!plugin.getAllClanNames().contains(clanName)) return;

        plugin.resetClanData(clanName);
        player.sendMessage("§6[Simpleclan-PLUS] §aReset data for clan §e" + clanName + "§a.");
        openAdminResetMenu(player, adminClanPages.getOrDefault(player.getUniqueId(), 0));
    }

    private void handleAdminDisbandMenuClick(Player player, String itemName) {
        if (itemName.equals("§c§lBack to Admin Panel")) { openAdminMenu(player); return; }
        if (itemName.equals("§e§lPrevious Page")) {
            openAdminDisbandMenu(player, Math.max(0, adminClanPages.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (itemName.equals("§e§lNext Page")) {
            openAdminDisbandMenu(player, adminClanPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        // Clan items are named §4<clanName>
        String clanName = itemName.replace("§4", "").trim();
        if (!plugin.getAllClanNames().contains(clanName)) return;

        plugin.forceDisbandClan(clanName);
        player.sendMessage("§6[Simpleclan-PLUS] §aForce disbanded clan §e" + clanName + "§a.");
        openAdminDisbandMenu(player, 0); // restart at page 0 — list changed
    }

    private void handleAdminPlayersMenuClick(Player player, ItemStack clicked) {
        String itemName = clicked.getItemMeta().getDisplayName();

        if (itemName.equals("§c§lBack to Admin Panel")) { openAdminMenu(player); return; }
        if (itemName.equals("§e§lPrevious Page")) {
            openAdminPlayersMenu(player, Math.max(0, adminPlayerPages.getOrDefault(player.getUniqueId(), 0) - 1));
            return;
        }
        if (itemName.equals("§e§lNext Page")) {
            openAdminPlayersMenu(player, adminPlayerPages.getOrDefault(player.getUniqueId(), 0) + 1);
            return;
        }

        if (clicked.getType() != Material.PLAYER_HEAD) return;

        String targetName = itemName.replace("§a", "").trim();
        Player target     = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§c[!] That player is no longer online.");
            openAdminPlayersMenu(player, 0);
            return;
        }

        player.closeInventory();
        pendingAdminTarget.put(player.getUniqueId(), target.getUniqueId());
        pendingInput.put(player.getUniqueId(), "ADMIN_PLACE");
        player.sendMessage("§6[Simpleclan-PLUS] §eType the clan name to add §f" + targetName + " §eto (or §ccancel§e):");
        player.sendMessage("§7Available clans: §f" + String.join("§7, §f", plugin.getAllClanNames()));
    }

    // ─── CHAT LISTENER (tag / description / admin place) ──────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String action = pendingInput.get(player.getUniqueId());
        if (action == null) return;

        event.setCancelled(true);
        pendingInput.remove(player.getUniqueId());

        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) {
            player.sendMessage("§6[Simpleclan-PLUS] §cAction cancelled.");
            return;
        }

        switch (action) {
            case "TAG" -> {
                String clan = plugin.getClanOf(player.getUniqueId());
                if (clan == null) { player.sendMessage("§c[!] You are not in a clan."); return; }
                if (msg.length() > 6 || !msg.matches("[a-zA-Z0-9]+")) {
                    player.sendMessage("§c[!] Invalid tag. Max 6 chars, letters and numbers only.");
                    return;
                }
                String finalTag = msg.toUpperCase();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.setClanTag(clan, finalTag);
                    player.sendMessage("§6[Simpleclan-PLUS] §aClan tag set to §f[" + finalTag + "]§a.");
                });
            }
            case "DESC" -> {
                String clan = plugin.getClanOf(player.getUniqueId());
                if (clan == null) { player.sendMessage("§c[!] You are not in a clan."); return; }
                if (msg.length() > 100) {
                    player.sendMessage("§c[!] Description too long (max 100 characters).");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.setClanDescription(clan, msg);
                    player.sendMessage("§6[Simpleclan-PLUS] §aClan description updated.");
                });
            }
            case "ADMIN_PLACE" -> {
                UUID targetUUID = pendingAdminTarget.remove(player.getUniqueId());
                if (targetUUID == null) return;

                List<String> allClans = plugin.getAllClanNames();
                String exactClan = allClans.stream()
                    .filter(c -> c.equalsIgnoreCase(msg))
                    .findFirst().orElse(null);

                if (exactClan == null) {
                    player.sendMessage("§c[!] Clan '§f" + msg + "§c' does not exist.");
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.addMemberToClan(targetUUID, exactClan, "MEMBER");
                    OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetUUID);
                    String        targetName    = targetOffline.getName() != null
                        ? targetOffline.getName() : targetUUID.toString();
                    player.sendMessage("§6[Simpleclan-PLUS] §aAdded §f" + targetName
                        + " §ato clan §f" + exactClan + " §aas MEMBER.");
                    Player targetOnline = Bukkit.getPlayer(targetUUID);
                    if (targetOnline != null)
                        targetOnline.sendMessage("§6[Simpleclan-PLUS] §aYou have been added to clan §f"
                            + exactClan + " §aby an admin.");
                });
            }
        }
    }

    // ─── ITEM BUILDERS ─────────────────────────────────────────────────────────

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                if (!line.isEmpty() || !loreList.isEmpty()) loreList.add(line);
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
            if (playerName != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
            meta.setDisplayName(displayName);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                if (!line.isEmpty() || !loreList.isEmpty()) loreList.add(line);
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorders(Inventory inv, ItemStack item) {
        int size = inv.getSize();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, item);
            inv.setItem(size - 9 + i, item);
        }
        for (int i = 9; i < size - 9; i += 9) {
            inv.setItem(i, item);
            inv.setItem(i + 8, item);
        }
    }

    /** Reads the hidden §0UUID:<uuid> tag written into member item lore. */
    private UUID getUUIDFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return null;
        for (String line : lore) {
            if (line.startsWith("§0UUID:")) {
                try { return UUID.fromString(line.substring(7)); }
                catch (IllegalArgumentException ignored) { return null; }
            }
        }
        return null;
    }

    private int getRolePriority(String role) {
        return switch (role.toUpperCase()) {
            case "LEADER"    -> 4;
            case "CO-LEADER" -> 3;
            case "MEMBER"    -> 2;
            case "RECRUIT"   -> 1;
            default          -> 0;
        };
    }

    private String getRoleColor(String role) {
        return switch (role.toUpperCase()) {
            case "LEADER"    -> "§6§l";
            case "CO-LEADER" -> "§e§l";
            case "MEMBER"    -> "§a";
            case "RECRUIT"   -> "§7";
            default          -> "§f";
        };
    }

    private int countClanlessOnline() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers())
            if (plugin.getClanOf(p.getUniqueId()) == null) count++;
        return count;
    }

    /** Wraps a long description string into ≤40-char lore lines. */
    private String[] splitDescription(String desc) {
        if (desc.length() <= 40) return new String[]{ "§7" + desc };
        List<String> lines = new ArrayList<>();
        StringBuilder sb   = new StringBuilder();
        for (String word : desc.split(" ")) {
            if (sb.length() + word.length() + 1 > 40) {
                lines.add("§7" + sb.toString().trim());
                sb = new StringBuilder();
            }
            sb.append(word).append(" ");
        }
        if (!sb.isEmpty()) lines.add("§7" + sb.toString().trim());
        return lines.toArray(new String[0]);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}