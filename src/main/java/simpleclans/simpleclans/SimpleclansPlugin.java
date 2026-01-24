package simpleclans.simpleclans;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import java.sql.*;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleclansPlugin extends JavaPlugin implements Listener {

    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        connectDatabase();
        createTables();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClanPlaceholder(this).register();
            getLogger().info("[Simpleclan-PLUS] PlaceholderAPI Activated!");
        } else {
            getLogger().warning("[Simpleclan-PLUS]NO placeholderAPI found, placeholders won't work.");
        }

        
        getCommand("clan").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§6[Simpleclan-PLUS] §cOnly players can use this command.");
                return true;
            }

            UUID uuid = player.getUniqueId();

            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                player.sendMessage("§6===== §6[Simpleclan-PLUS] §fClan Commands =====");
                player.sendMessage("§e/clan create <name> §7- Create a new clan.");
                player.sendMessage("§e/clan invite <player> §7- Invite a player to your clan.");
                player.sendMessage("§e/clan join <name> §7- Join a clan you were invited to.");
                player.sendMessage("§e/clan leave §7- Leave your current clan.");
                player.sendMessage("§e/clan info [name] §7- View info about a clan.");
                player.sendMessage("§e/clan promote <player> §7- Promote a member.");
                player.sendMessage("§e/clan demote <player> §7- Demote a member.");
                player.sendMessage("§6=======================================");
                return true;
            }

            String sub = args[0].toLowerCase();

            switch (sub) {
                case "create" -> {
                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan create <name>");
                        return true;
                    }
                    if (getClanOf(uuid) != null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are already in a clan!");
                        return true;
                    }
                    String clanName = args[1];
                    createClan(clanName, uuid);
                    player.sendMessage("§6[Simpleclan-PLUS] §aClan §e" + clanName + " §ahas been created!");
                }

                case "invite" -> {
                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan invite <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cOnly leaders or co-leaders can invite players!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                        return true;
                    }

                    UUID targetId = target.getUniqueId();
                    if (getClanOf(targetId) != null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cThat player is already in a clan!");
                        return true;
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO clan_invites(target_uuid, clan_name, inviter_uuid) VALUES(?, ?, ?)")) {
                        ps.setString(1, targetId.toString());
                        ps.setString(2, clan);
                        ps.setString(3, uuid.toString());
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    player.sendMessage("§6[Simpleclan-PLUS] §aYou invited §e" + target.getName() + " §ato join §b" + clan + "§a!");
                    target.sendMessage("§6[Simpleclan-PLUS] §aYou have been invited to join §b" + clan + "§a! Type §e/clan join " + clan + " §ato accept.");
                }

                case "join" -> {
                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan join <name>");
                        return true;
                    }
                    String clanName = args[1];

                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT * FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, clanName);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) {
                            player.sendMessage("§6[Simpleclan-PLUS] §cYou have not been invited to this clan!");
                            return true;
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    addMemberToClan(uuid, clanName, "RECRUIT");
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, clanName);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    player.sendMessage("§6[Simpleclan-PLUS] §aYou have joined §b" + clanName + "§a!");
                }

                case "leave" -> {
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    addMemberToClan(uuid, null, null);
                    player.sendMessage("§6[Simpleclan-PLUS] §7You left the clan §e" + clan);
                }

                case "info" -> {
                    String clan = args.length > 1 ? args[1] : getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    int level = getClanLevel(clan);
                    int kills = getClanKills(clan);

                    player.sendMessage("§6=====[Simpleclan-PLUS] Clan Info =====");
                    player.sendMessage("§eName: §f" + clan);
                    player.sendMessage("§eLevel: §f" + level);
                    player.sendMessage("§eKills: §f" + kills);
                }

                case "promote" -> {
                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan promote <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou do not have permission to promote!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                        return true;
                    }
                    UUID targetId = target.getUniqueId();
                    if (!clan.equals(getClanOf(targetId))) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cThat player is not in your clan!");
                        return true;
                    }

                    String targetRole = getRoleOf(targetId);
                    String newRole = switch (targetRole.toUpperCase()) {
                        case "RECRUIT" -> "MEMBER";
                        case "MEMBER" -> "CO-LEADER";
                        default -> null;
                    };

                    if (newRole == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cThat player cannot be promoted further!");
                        return true;
                    }

                    addMemberToClan(targetId, clan, newRole);
                    player.sendMessage("§6[Simpleclan-PLUS] §aYou promoted §e" + target.getName() + " §ato §b" + newRole);
                    target.sendMessage("§6[Simpleclan-PLUS] §aYou have been promoted to §b" + newRole);
                }
                case "disband" -> {
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }

                    String role = getRoleOf(uuid);
                    if (!"LEADER".equalsIgnoreCase(role)) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cOnly the clan leader can disband the clan!");
                        return true;
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM clans WHERE name = ?")) {
                        ps.setString(1, clan);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage("§6[Simpleclan-PLUS] §cAn error occurred while disbanding the clan!");
                        return true;
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE clan_members SET clan = NULL, role = NULL WHERE clan = ?")) {
                        ps.setString(1, clan);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    player.sendMessage("§6[Simpleclan-PLUS] §aYou have disbanded the clan §e" + clan + "§a!");
                    Bukkit.broadcastMessage("§6[Simpleclan-PLUS] §cThe clan §e" + clan + " §chas been disbanded!");
                }

                case "demote" -> {
                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan demote <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou do not have permission to demote!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                        return true;
                    }
                    UUID targetId = target.getUniqueId();
                    if (!clan.equals(getClanOf(targetId))) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cThat player is not in your clan!");
                        return true;
                    }

                    String targetRole = getRoleOf(targetId);
                    String newRole = switch (targetRole.toUpperCase()) {
                        case "CO-LEADER" -> "MEMBER";
                        case "MEMBER" -> "RECRUIT";
                        default -> null;
                    };

                    if (newRole == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cThat player cannot be demoted further!");
                        return true;
                    }

                    addMemberToClan(targetId, clan, newRole);
                    player.sendMessage("§6[Simpleclan-PLUS] §aYou demoted §e" + target.getName() + " §ato §b" + newRole);
                    target.sendMessage("§6[Simpleclan-PLUS] §cYou have been demoted to §b" + newRole);
                }

                default -> player.sendMessage("§6[Simpleclan-PLUS] §cUnknown subcommand. Use /clan help");
            }

            return true;
        });
        getCommand("clan").setTabCompleter((sender, command, alias, args) -> {
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                completions.addAll(Arrays.asList(
                        "create", "invite", "join", "leave", "info", "promote", "demote", "disband", "help"
                ));

                completions.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
                return completions;
            } else if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("invite") || sub.equals("promote") || sub.equals("demote")) {
                    completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    completions.removeIf(s -> !s.toLowerCase().startsWith(args[1].toLowerCase()));
                } else if (sub.equals("join") || sub.equals("info")) {
                    completions.addAll(getAllClanNames());
                    completions.removeIf(s -> !s.toLowerCase().startsWith(args[1].toLowerCase()));
                }
                return completions;
            }

            return Collections.emptyList();
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("[Simpleclan-PLUS] ClanPlugin is disabled!");
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void connectDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/database.db");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS clans (" +
                    "name TEXT PRIMARY KEY," +
                    "leader TEXT," +
                    "kills INTEGER DEFAULT 0," +
                    "level INTEGER DEFAULT 1)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS clan_members (" +
                    "uuid TEXT PRIMARY KEY," +
                    "clan TEXT," +
                    "role TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS clan_invites (" +
                    "target_uuid TEXT," +
                    "clan_name TEXT," +
                    "inviter_uuid TEXT)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void createClan(String name, UUID leader) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO clans(name, leader, kills, level) VALUES(?, ?, 0, 1)")) {
            ps.setString(1, name);
            ps.setString(2, leader.toString());
            ps.executeUpdate();

            addMemberToClan(leader, name, "LEADER");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addMemberToClan(UUID uuid, String clan, String role) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO clan_members(uuid, clan, role) VALUES(?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, clan);
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getClanOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT clan FROM clan_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("clan");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getRoleOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT role FROM clan_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("role");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateClanKills(String clan, int kills) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE clans SET kills = ?, level = (? / 5) + 1 WHERE name = ?")) {
            ps.setInt(1, kills);
            ps.setInt(2, kills);
            ps.setString(3, clan);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getClanKills(String clan) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT kills FROM clans WHERE name = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("kills");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getClanLevel(String clan) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT level FROM clans WHERE name = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("level");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public List<String> getAllClanNames() {
        List<String> clans = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name FROM clans");
            while (rs.next()) clans.add(rs.getString("name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return clans;
    }

    class ClanPlaceholder extends PlaceholderExpansion {
        private final SimpleclansPlugin plugin;

        public ClanPlaceholder(SimpleclansPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() { return "simpleclans"; }

        @Override
        public @NotNull String getAuthor() { return "[Robin]"; }

        @Override
        public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

        @Override
        public boolean persist() { return true; }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
            if (identifier.equalsIgnoreCase("clan_name")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? clan : "No Clan";
            }
            if (identifier.equalsIgnoreCase("clan_role")) {
                String role = plugin.getRoleOf(player.getUniqueId());
                return role != null ? role : "None";
            }
            if (identifier.equalsIgnoreCase("clan_level")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? String.valueOf(plugin.getClanLevel(clan)) : "0";
            }
            if (identifier.equalsIgnoreCase("clan_kills")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? String.valueOf(plugin.getClanKills(clan)) : "0";
            }
            return null;
        }
    }
}
