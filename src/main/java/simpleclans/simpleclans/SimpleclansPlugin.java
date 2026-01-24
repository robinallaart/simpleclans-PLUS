package Gekko.Gekko;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


public class BorderPlugin extends JavaPlugin implements Listener {

    private final HashMap<UUID, Long> playTimes = new HashMap<>();
    private final HashMap<UUID, Long> lastCheck = new HashMap<>();
    private final HashMap<UUID, Long> borderExpansions = new HashMap<>();
    private double maxBorderSize;
    private double expandAmount;
    private long intervalMs;
    private boolean friendlyFireEnabled;


    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        FileConfiguration config = getConfig();
        reloadConfig();
        maxBorderSize = config.getDouble("max-border-size", 10000.0);
        expandAmount = config.getDouble("expand-amount", 8.0);
        int intervalMinutes = config.getInt("expand-interval-minutes", 60);
        friendlyFireEnabled = getConfig().getBoolean("friendly-fire", false);


        intervalMs = intervalMinutes * 60 * 1000L;




        connectDatabase();
        createTable();


        Bukkit.getPluginManager().registerEvents(this, this);


        Bukkit.getScheduler().runTaskTimer(this, this::updatePlaytime, 20L, 20L);


        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BorderPlaceholder(this).register();
            getLogger().info("§1[GekkoGames] PlaceholderAPI Activated!");
        } else {
            getLogger().warning("§1[GekkoGames] NO placeholderAPI found, placeholders won't work.");
        }


        getCommand("GekkoGamesReload").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("borderplugin.admin")) {
                sender.sendMessage("§1[GekkoGames] §cYou do not have permission to use this command!");
                return true;
            }


            reloadConfig();
            saveDefaultConfig();


            FileConfiguration config = getConfig();
            maxBorderSize = config.getDouble("max-border-size", 10000.0);
            expandAmount = config.getDouble("expand-amount", 8.0);
            int intervalMinutes = config.getInt("expand-interval-minutes", 60);
            intervalMs = intervalMinutes * 60 * 1000L;
            friendlyFireEnabled = config.getBoolean("friendly-fire", false);

            sender.sendMessage("§1[GekkoGames] §aconfiguration reloaded successfully!");
            getLogger().info(sender.getName() + " reloaded the BorderPlugin configuration.");
            return true;
        });




        getCommand("playtime").setExecutor((sender, command, label, args) -> {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    long liveTime = getLivePlaytime(player.getUniqueId());
                    sender.sendMessage("§1[GekkoGames] §aYour playtime: §e" + formatTime(liveTime));
                    checkBorderExpansion(player);
                } else {
                    sender.sendMessage("§1[GekkoGames] §cSpecify a player!");
                }
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§1[GekkoGames] §cPlayer not found!");
                return true;
            }

            long liveTime = getLivePlaytime(target.getUniqueId());
            sender.sendMessage("§1[GekkoGames] §a" + target.getName() + "'s playtime: §e" + formatTime(liveTime));
            checkBorderExpansion(target);
            return true;
        });

        getCommand("kills").setExecutor((sender, command, label, args) -> {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    int kills = getKills(player.getUniqueId());
                    sender.sendMessage("§1[GekkoGames] §aYour kills: §e" + kills);
                } else {
                    sender.sendMessage("§1[GekkoGames] §cYou must specify a player!");
                }
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§1[GekkoGames] §cPlayer not found!");
                return true;
            }

            int kills = getKills(target.getUniqueId());
            sender.sendMessage("§1[GekkoGames] §a" + target.getName() + "'s kills: §e" + kills);
            return true;
        });

        getCommand("killleaderboard").setExecutor((sender, command, label, args) -> {
            sender.sendMessage("§6===== Kill Leaderboard =====");

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT uuid, kills FROM kills ORDER BY kills DESC LIMIT 10"
                );

                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int kills = rs.getInt("kills");

                    Player online = Bukkit.getPlayer(uuid);
                    String name = (online != null) ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();

                    sender.sendMessage("§e#" + rank + " §a" + name + " §7- §b" + kills + " kills");
                    rank++;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§1[GekkoGames] §cAn error occurred while fetching the leaderboard!");
            }

            return true;
        });

        getCommand("deaths").setExecutor((sender, command, label, args) -> {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    int deaths = getDeaths(player.getUniqueId());
                    sender.sendMessage("§1[GekkoGames] §aYour deaths: §e" + deaths);
                } else {
                    sender.sendMessage("§1[GekkoGames] §cYou must specify a player!");
                }
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§1[GekkoGames] §cPlayer not found!");
                return true;
            }

            int deaths = getDeaths(target.getUniqueId());
            sender.sendMessage("§1[GekkoGames] §a" + target.getName() + "'s deaths: §e" + deaths);
            return true;
        });

        
        getCommand("clan").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            UUID uuid = player.getUniqueId();

            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                player.sendMessage("§6===== §1[GekkoGames] §fClan Commands =====");
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
                        player.sendMessage("§cUsage: /clan create <name>");
                        return true;
                    }
                    if (getClanOf(uuid) != null) {
                        player.sendMessage("§cYou are already in a clan!");
                        return true;
                    }
                    String clanName = args[1];
                    createClan(clanName, uuid);
                    player.sendMessage("§1[GekkoGames] §aClan §e" + clanName + " §ahas been created!");
                }

                case "invite" -> {
                    if (args.length < 2) {
                        player.sendMessage("§cUsage: /clan invite <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§cOnly leaders or co-leaders can invite players!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found!");
                        return true;
                    }

                    UUID targetId = target.getUniqueId();
                    if (getClanOf(targetId) != null) {
                        player.sendMessage("§cThat player is already in a clan!");
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

                    player.sendMessage("§aYou invited §e" + target.getName() + " §ato join §b" + clan + "§a!");
                    target.sendMessage("§1[GekkoGames] §aYou have been invited to join §b" + clan + "§a! Type §e/clan join " + clan + " §ato accept.");
                }

                case "join" -> {
                    if (args.length < 2) {
                        player.sendMessage("§cUsage: /clan join <name>");
                        return true;
                    }
                    String clanName = args[1];

                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT * FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, clanName);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) {
                            player.sendMessage("§cYou have not been invited to this clan!");
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

                    player.sendMessage("§1[GekkoGames] §aYou have joined §b" + clanName + "§a!");
                }

                case "leave" -> {
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }
                    addMemberToClan(uuid, null, null);
                    player.sendMessage("§1[GekkoGames] §7You left the clan §e" + clan);
                }

                case "info" -> {
                    String clan = args.length > 1 ? args[1] : getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }
                    int level = getClanLevel(clan);
                    int kills = getClanKills(clan);

                    player.sendMessage("§6===== Clan Info =====");
                    player.sendMessage("§eName: §f" + clan);
                    player.sendMessage("§eLevel: §f" + level);
                    player.sendMessage("§eKills: §f" + kills);
                }

                case "promote" -> {
                    if (args.length < 2) {
                        player.sendMessage("§cUsage: /clan promote <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§cYou do not have permission to promote!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found!");
                        return true;
                    }
                    UUID targetId = target.getUniqueId();
                    if (!clan.equals(getClanOf(targetId))) {
                        player.sendMessage("§cThat player is not in your clan!");
                        return true;
                    }

                    String targetRole = getRoleOf(targetId);
                    String newRole = switch (targetRole.toUpperCase()) {
                        case "RECRUIT" -> "MEMBER";
                        case "MEMBER" -> "CO-LEADER";
                        default -> null;
                    };

                    if (newRole == null) {
                        player.sendMessage("§cThat player cannot be promoted further!");
                        return true;
                    }

                    addMemberToClan(targetId, clan, newRole);
                    player.sendMessage("§aYou promoted §e" + target.getName() + " §ato §b" + newRole);
                    target.sendMessage("§1[GekkoGames] §aYou have been promoted to §b" + newRole);
                }
                case "disband" -> {
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }

                    String role = getRoleOf(uuid);
                    if (!"LEADER".equalsIgnoreCase(role)) {
                        player.sendMessage("§cOnly the clan leader can disband the clan!");
                        return true;
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM clans WHERE name = ?")) {
                        ps.setString(1, clan);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage("§cAn error occurred while disbanding the clan!");
                        return true;
                    }

                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE clan_members SET clan = NULL, role = NULL WHERE clan = ?")) {
                        ps.setString(1, clan);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    player.sendMessage("§1[GekkoGames] §aYou have disbanded the clan §e" + clan + "§a!");
                    Bukkit.broadcastMessage("§1[GekkoGames] §cThe clan §e" + clan + " §chas been disbanded!");
                }

                case "demote" -> {
                    if (args.length < 2) {
                        player.sendMessage("§cUsage: /clan demote <player>");
                        return true;
                    }
                    String clan = getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§cYou are not in a clan!");
                        return true;
                    }
                    String role = getRoleOf(uuid);
                    if (!role.equalsIgnoreCase("LEADER") && !role.equalsIgnoreCase("CO-LEADER")) {
                        player.sendMessage("§cYou do not have permission to demote!");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found!");
                        return true;
                    }
                    UUID targetId = target.getUniqueId();
                    if (!clan.equals(getClanOf(targetId))) {
                        player.sendMessage("§cThat player is not in your clan!");
                        return true;
                    }

                    String targetRole = getRoleOf(targetId);
                    String newRole = switch (targetRole.toUpperCase()) {
                        case "CO-LEADER" -> "MEMBER";
                        case "MEMBER" -> "RECRUIT";
                        default -> null;
                    };

                    if (newRole == null) {
                        player.sendMessage("§cThat player cannot be demoted further!");
                        return true;
                    }

                    addMemberToClan(targetId, clan, newRole);
                    player.sendMessage("§aYou demoted §e" + target.getName() + " §ato §b" + newRole);
                    target.sendMessage("§1[GekkoGames] §cYou have been demoted to §b" + newRole);
                }

                default -> player.sendMessage("§cUnknown subcommand. Use /clan help");
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



        getCommand("deathleaderboard").setExecutor((sender, command, label, args) -> {
            sender.sendMessage("§6===== Death Leaderboard =====");

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT uuid, deaths FROM deaths ORDER BY deaths DESC LIMIT 10"
                );

                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int deaths = rs.getInt("deaths");

                    Player online = Bukkit.getPlayer(uuid);
                    String name = (online != null) ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();

                    sender.sendMessage("§e#" + rank + " §a" + name + " §7- §c" + deaths + " deaths");
                    rank++;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§1[GekkoGames] §cAn error occurred while fetching the leaderboard!");
            }

            return true;
        });

        getCommand("testplaceholders").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command!");
                return true;
            }

            sender.sendMessage("§6===== Placeholder Test =====");
            sender.sendMessage("§eBorder size: §f" + String.format("%.0f", getCurrentBorderSize()));

            String clan = getClanOf(player.getUniqueId());
            sender.sendMessage("§eClan: §f" + (clan != null ? clan : "No Clan"));
            sender.sendMessage("§eClan Level: §f" + (clan != null ? getClanLevel(clan) : 0));
            sender.sendMessage("§eClan Role: §f" + (getRoleOf(player.getUniqueId()) != null ? getRoleOf(player.getUniqueId()) : "None"));
            sender.sendMessage("§eClan Kills: §f" + (clan != null ? getClanKills(clan) : 0));
            sender.sendMessage("§6============================");
            return true;
        });

        
        getCommand("playtimeleaderboard").setExecutor((sender, command, label, args) -> {
            sender.sendMessage("§6===== Playtime Leaderboard =====");

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT uuid, playtime FROM playtimes ORDER BY playtime DESC LIMIT 10"
                );

                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long storedPlaytime = rs.getLong("playtime");

                    long livePlaytime = storedPlaytime;
                    if (lastCheck.containsKey(uuid)) {
                        livePlaytime += System.currentTimeMillis() - lastCheck.get(uuid);
                    }

                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    String name = onlinePlayer != null ? onlinePlayer.getName() :
                            Bukkit.getOfflinePlayer(uuid).getName();

                    sender.sendMessage("§e#" + rank + " §a" + name + " §7- §b" + formatTime(livePlaytime));
                    rank++;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§1[GekkoGames] §cAn error occurred while fetching the leaderboard!");
            }

            return true;
        });

        getLogger().info("§1[GekkoGames] BorderPlugin is enabled!");
    }

    @Override
    public void onDisable() {
        for (UUID uuid : playTimes.keySet()) {
            savePlaytime(uuid, getLivePlaytime(uuid));
        }
        getLogger().info("§1[GekkoGames] BorderPlugin is disabled!");
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long dbTime = getPlaytime(uuid);
        playTimes.put(uuid, dbTime);
        lastCheck.put(uuid, System.currentTimeMillis());


        long expansionsDone = dbTime / intervalMs;
        borderExpansions.put(uuid, expansionsDone);
    }
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();


        UUID victimId = victim.getUniqueId();
        int deaths = getDeaths(victimId) + 1;
        saveDeaths(victimId, deaths);
        victim.sendMessage("§1[GekkoGames] §cYou died! §7Total deaths: §e" + deaths);

        if (killer != null && killer != victim) {
            String clan = getClanOf(killer.getUniqueId());
            if (clan != null) {
                int clanKills = getClanKills(clan) + 1;
                updateClanKills(clan, clanKills);
                killer.sendMessage("§1[GekkoGames] §aYour clan gained §e1 §akill! (Total: " + clanKills + ")");
            }
        }

        if (killer != null && killer != victim) {
            UUID killerId = killer.getUniqueId();
            int kills = getKills(killerId) + 1;
            saveKills(killerId, kills);
            killer.sendMessage("§1[GekkoGames] §aYou killed §e" + victim.getName() + "§a! Total kills: §e" + kills);
        }
    }
    @EventHandler
    public void onClanFriendlyFire(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Alleen spelers kunnen geraakt worden
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        // Direct melee hit
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        }

        // Projectile (pijl, sneeuwbal, etc.)
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile)
            if (projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        

        if (attacker == null) return;

        // === Clan checks ===
        String victimClan = getClanOf(victim.getUniqueId());
        String attackerClan = getClanOf(attacker.getUniqueId());

        // Als friendly fire uitstaat en spelers in dezelfde clan zitten → cancel
        if (!friendlyFireEnabled && victimClan != null && victimClan.equals(attackerClan)) {
            event.setCancelled(true);
            attacker.sendMessage("§1[GekkoGames] §cYou cannot attack your clan members!");
        }
    }




    private long getLivePlaytime(UUID uuid) {
        long stored = playTimes.getOrDefault(uuid, getPlaytime(uuid));
        long last = lastCheck.getOrDefault(uuid, System.currentTimeMillis());
        return stored + (System.currentTimeMillis() - last);
    }

    private void saveKills(UUID uuid, int kills) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO kills(uuid, kills) VALUES(?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET kills = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, kills);
            ps.setInt(3, kills);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private int getKills(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT kills FROM kills WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("kills");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void checkBorderExpansion(Player player) {
        UUID uuid = player.getUniqueId();

        long liveTime = getLivePlaytime(uuid);
        long expansionsDone = borderExpansions.getOrDefault(uuid, 0L);
        long expectedExpansions = liveTime / intervalMs;

        if (expectedExpansions > expansionsDone) {
            long toExpand = expectedExpansions - expansionsDone;
            for (int i = 0; i < toExpand; i++) {
                expandBorder(player.getWorld());
            }
            borderExpansions.put(uuid, expectedExpansions);


            lastCheck.put(uuid, System.currentTimeMillis());
            playTimes.put(uuid, liveTime);
            savePlaytime(uuid, liveTime);
        }
    }

    private void updatePlaytime() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            long last = lastCheck.getOrDefault(uuid, now);
            lastCheck.put(uuid, now);

            long stored = playTimes.getOrDefault(uuid, getPlaytime(uuid));
            playTimes.put(uuid, stored + (now - last));

            checkBorderExpansion(player);
            savePlaytime(uuid, playTimes.get(uuid));
        }
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }


    private void expandBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        double newSize = Math.min(border.getSize() + expandAmount, maxBorderSize);

        if (border.getSize() < maxBorderSize) {
            border.setSize(newSize);
            Bukkit.broadcastMessage("§1[GekkoGames] §aThe worldborder expanded to §e" + (int) newSize + " §ablocks!");
        } else {
            border.setSize(maxBorderSize);
            Bukkit.broadcastMessage("§1[GekkoGames] §eThe worldborder reached the max: §c" + (int) maxBorderSize + " blocks!");
        }
    }

    public double getCurrentBorderSize() {
        World world = Bukkit.getWorlds().get(0);
        return world.getWorldBorder().getSize();
    }


    private void connectDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/playtime.db");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS playtimes (" +
                    "uuid TEXT PRIMARY KEY," +
                    "playtime LONG)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS kills (" +
                    "uuid TEXT PRIMARY KEY," +
                    "kills INTEGER)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS deaths (" +
                    "uuid TEXT PRIMARY KEY," +
                    "deaths INTEGER)");
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


    private void createClan(String name, UUID leader) {
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

    private void addMemberToClan(UUID uuid, String clan, String role) {
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

    private String getClanOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT clan FROM clan_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("clan");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getRoleOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT role FROM clan_members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("role");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateClanKills(String clan, int kills) {
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

    private int getClanKills(String clan) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT kills FROM clans WHERE name = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("kills");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int getClanLevel(String clan) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT level FROM clans WHERE name = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("level");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private void saveDeaths(UUID uuid, int deaths) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO deaths(uuid, deaths) VALUES(?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET deaths = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, deaths);
            ps.setInt(3, deaths);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getDeaths(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT deaths FROM deaths WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("deaths");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void savePlaytime(UUID uuid, long time) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO playtimes(uuid, playtime) VALUES(?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET playtime = ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, time);
            ps.setLong(3, time);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private java.util.List<String> getAllClanNames() {
        java.util.List<String> clans = new java.util.ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT name FROM clans");
            while (rs.next()) {
                clans.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return clans;
    }

    private long getPlaytime(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT playtime FROM playtimes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("playtime");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0L;
    }


    class BorderPlaceholder extends PlaceholderExpansion {
        private final BorderPlugin plugin;

        public BorderPlaceholder(BorderPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "Gekko";
        }

        @Override
        public @NotNull String getAuthor() {
            return "[Robin]";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onRequest(OfflinePlayer player, @NotNull String identifier) {
            if (identifier.equalsIgnoreCase("current_size")) {
                double size = plugin.getCurrentBorderSize();
                return String.format("%.0f", size);
            }
            if (identifier.equalsIgnoreCase("clan_name")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? clan : "No Clan";
            }
            if (identifier.equalsIgnoreCase("clan_level")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? clan : "No Clan";
            }
            if (identifier.equalsIgnoreCase("clan_role")) {
                String role = plugin.getRoleOf(player.getUniqueId());
                return role != null ? role : "None";
            }
            if (identifier.equalsIgnoreCase("clan_kills")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? clan : "No Clan";
            }

            return null;
        }
    }
}