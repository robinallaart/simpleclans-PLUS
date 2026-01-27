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
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.sql.*;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class SimpleclansPlugin extends JavaPlugin implements Listener {
    private FileConfiguration languageConfig;
    private String languageCode;
    private Connection connection;
    private ModrinthUpdater updater;

    private final Map<UUID, Boolean> clanChatToggled = new HashMap<>();
    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        languageCode = getConfig().getString("Language.default", "EN").toUpperCase();
        loadLanguageFile(languageCode);
        getLogger().info("SimpleClans started with language: " + languageCode);
        Bukkit.getPluginManager().registerEvents(this, this);
        connectDatabase();
        createTables();
        updater = new ModrinthUpdater(this, "simpleclans-plus");
        updater.checkForUpdates();
        Bukkit.getPluginManager().registerEvents(new UpdateNotifyListener(updater), this);

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
                player.sendMessage("§e/clan chatmsg §7- Send a single message in clanchat.");
                player.sendMessage("§e/clan chat §7- Toggle clan chat.");
                player.sendMessage("§e/clan list §7- shows a clan list.");
                player.sendMessage("§e/clan update §7- Update to newest version.");
                player.sendMessage("§6=======================================");
                return true;
            }

            String sub = args[0].toLowerCase();

            switch (sub) {
                case "create" -> {
                        if (!player.hasPermission("simpleclans.create")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                                return true;
                        }
                        if (args.length < 2) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan create <name>");
                                return true;
                        }
                        if (getClanOf(uuid) != null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou are already in a clan!");
                                return true;
                        }
                        String clanName = args[1];

                        if (getAllClanNames().stream().anyMatch(name -> name.equalsIgnoreCase(clanName))) {
                            player.sendMessage("§6[Simpleclan-PLUS] §cA clan with that name already exists!");
                            return true;
                        }
                        
                        createClan(clanName, uuid);
                        player.sendMessage("§6[Simpleclan-PLUS] §aClan §e" + clanName + " §ahas been created!");
                }

                case "update" -> {
                    if (!player.hasPermission("simpleclans.admin")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission to use this command.");
                        return true;
                    }

                    if (updater.isUpdateAvailable()) {
                        player.sendMessage("§6[Simpleclan-PLUS] §eDownloading latest update...");
                        updater.downloadLatestUpdate();
                        player.sendMessage("§6[Simpleclan-PLUS] §aUpdate downloaded! It will be installed on the next restart.");
                    } else {
                        player.sendMessage("§6[Simpleclan-PLUS] §aYou are already running the latest version!");
                    }
                }

                case "invite" -> {
                        if (!player.hasPermission("simpleclans.invite")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
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
                        if (args.length < 2) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan invite <player>");
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
                                        "INSERT INTO clan_invites(target_uuid, clan_name, inviter_uuid, timestamp) VALUES(?, ?, ?, ?)")) {
                                ps.setString(1, targetId.toString());
                                ps.setString(2, clan);
                                ps.setString(3, uuid.toString());
                                ps.setLong(4, System.currentTimeMillis());
                                ps.executeUpdate();
                        } catch (Exception e) {
                                e.printStackTrace();
                        }

                        player.sendMessage("§6[Simpleclan-PLUS] §aYou invited §e" + target.getName() + " §ato join §b" + clan + "§a!");
                        target.sendMessage("§6[Simpleclan-PLUS] §aYou have been invited to join §b" + clan + "§a! Type §e/clan join " + clan + " §ato accept.");
                }


                case "join" -> {
                        if (!player.hasPermission("simpleclans.join")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                                return true;
                        }
                        if (args.length < 2) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan join <name>");
                                return true;
                        }
                        String clanName = args[1];

                        try (PreparedStatement ps = connection.prepareStatement(
                                        "SELECT * FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                                ps.setString(1, uuid.toString());
                                ps.setString(2, clanName);
                                var rs = ps.executeQuery();
                                if (!rs.next()) {
                                        player.sendMessage("§6[Simpleclan-PLUS] §cYou have not been invited to this clan!");
                                        return true;
                                }
                                long timestamp = rs.getLong("timestamp");
                                if (System.currentTimeMillis() - timestamp > 5 * 60 * 1000) {
                                        player.sendMessage("§6[Simpleclan-PLUS] §cYour invitation has expired!");
                                        try (PreparedStatement del = connection.prepareStatement(
                                                        "DELETE FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                                                del.setString(1, uuid.toString());
                                                del.setString(2, clanName);
                                                del.executeUpdate();
                                        }
                                        return true;
                                }
                        } catch (Exception e) {
                                e.printStackTrace();
                        }

                        addMemberToClan(uuid, clanName, "RECRUIT");

                        try (PreparedStatement ps = connection.prepareStatement(
                                        "DELETE FROM clan_invites WHERE target_uuid = ? AND clan_name = ?")) {
                                ps.setString(1, uuid.toString());
                                ps.setString(2, clanName);
                                ps.executeUpdate();
                        } catch (Exception e) {
                                e.printStackTrace();
                        }

                        player.sendMessage("§6[Simpleclan-PLUS] §aYou have joined §b" + clanName + "§a!");
                }


                case "leave" -> {
                        if (!player.hasPermission("simpleclans.leave")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                                return true;
                        }
                        String clan = getClanOf(uuid);
                        if (clan == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                                return true;
                        }
                        addMemberToClan(uuid, null, null);
                        player.sendMessage("§6[Simpleclan-PLUS] §7You left the clan §e" + clan);
                }

                case "info" -> {
                    if (!player.hasPermission("simpleclans.info")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                        return true;
                    }
                    
                    String clan = args.length > 1 ? args[1] : getClanOf(uuid);
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }

                    int level = getClanLevel(clan);
                    int kills = getClanKills(clan);
                    Map<UUID, String> members = getClanMembers(clan);

                    player.sendMessage("§6===== §eClan Info §6===== ");
                    player.sendMessage("§eClan Name: §f" + clan);
                    player.sendMessage("§eLevel: §f" + level);
                    player.sendMessage("§eKills: §f" + kills);
                    player.sendMessage("§eMembers: ");

                    for (Map.Entry<UUID, String> entry : members.entrySet()) {
                        UUID memberUUID = entry.getKey();
                        String role = entry.getValue();
                        OfflinePlayer member = Bukkit.getOfflinePlayer(memberUUID);

                        boolean online = member.isOnline();
                        String color = online ? "§a" : "§c";
                        String status = online ? "§7(Online)" : "§7(Offline)";

                        player.sendMessage(color + " - " + member.getName() + " §8[" + role + "] " + status);
                    }

                    player.sendMessage("§6=========================");
                    return true;
                }


                case "promote" -> {
                    if (!player.hasPermission("simpleclans.promote")) {
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
                    if (!player.hasPermission("simpleclans.disband")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                        return true;
                    }
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
                case "list" -> {
                    if (!player.hasPermission("simpleclans.list")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission!");
                        return true;
                    }
                    player.sendMessage("§6===== Online Players & Clans =====");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        String clan = getClanOf(p.getUniqueId());
                        player.sendMessage("§e" + p.getName() + " §7- Clan: " + (clan != null ? clan : "None"));
                    }
                }

                case "chat" -> {
                    UUID playerUUID = player.getUniqueId();
                    boolean toggled = clanChatToggled.getOrDefault(playerUUID, false);
                    clanChatToggled.put(playerUUID, !toggled);
                    player.sendMessage("§6[Simpleclan-PLUS] §aClan chat " + (!toggled ? "enabled" : "disabled"));
                }

                case "chatmsg" -> {
                    if (!player.hasPermission("simpleclans.chatmsg")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan chatmsg <message>");
                        return true;
                    }
                    String clan = getClanOf(player.getUniqueId());
                    if (clan == null) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
                        return true;
                    }
                    String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    for (Map.Entry<UUID, String> member : getClanMembers(clan).entrySet()) {
                        Player target = Bukkit.getPlayer(member.getKey());
                        if (target != null) {
                            target.sendMessage("§6[Clan] §e" + player.getName() + "§f: " + msg);
                        }
                    }
                }
                case "demote" -> {
                    if (!player.hasPermission("simpleclans.demote")) {
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
                case "admin" -> {
                    if (!player.hasPermission("simpleclans.admin")) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cYou don't have permission to use admin commands!");
                        return true;
                    }

                    if (args.length < 2) {
                        player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin <subcommand> [args]");
                        return true;
                    }

                    String adminCommand = args[1].toLowerCase();

                    switch (adminCommand) {
                        case "promote" -> {
                            if (!player.hasPermission("simpleclans.admin.promote")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 4) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin promote <player> <clan>");
                                return true;
                            }
                            Player target = Bukkit.getPlayerExact(args[2]);
                            String clan = args[3];

                            if (target == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                return true;
                            }

                            String targetRole = getRoleOf(target.getUniqueId());
                            String newRole = switch (targetRole.toUpperCase()) {
                                case "RECRUIT" -> "MEMBER";
                                case "MEMBER" -> "CO-LEADER";
                                default -> null;
                            };

                            if (newRole == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cThat player cannot be promoted further!");
                                return true;
                            }

                            addMemberToClan(target.getUniqueId(), clan, newRole);
                            player.sendMessage("§6[Simpleclan-PLUS] §aYou promoted §e" + target.getName() + " §ato §b" + newRole);
                            target.sendMessage("§6[Simpleclan-PLUS] §aYou have been promoted to §b" + newRole);
                        }
                        case "purge" -> {
                            if (!player.hasPermission("simpleclans.admin.purge")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 3) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin purge <player>");
                                return true;
                            }
                            Player target = Bukkit.getPlayerExact(args[2]);
                            if (target == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                return true;
                            }
                            addMemberToClan(target.getUniqueId(), null, null);
                            player.sendMessage("§6[Simpleclan-PLUS] §aPurged data for §e" + target.getName());
                            target.sendMessage("§6[Simpleclan-PLUS] §cYour clan data has been purged!");
                        }

                        case "reset" -> {
                            if (!player.hasPermission("simpleclans.admin.reset")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 3) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin reset <clan>");
                                return true;
                            }
                            String clan = args[2];

                          
                            updateClanKills(clan, 0);

                           
                            for (UUID memberUUID : getClanMembers(clan).keySet()) {
                                addMemberToClan(memberUUID, clan, "RECRUIT");
                            }

                            player.sendMessage("§6[Simpleclan-PLUS] §aClan §e" + clan + " §ahas been reset!");
                        }

                        case "place" -> {
                                if (!player.hasPermission("simpleclans.admin.place")) {
                                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                                return true;
                                }

                                if (args.length < 4) {
                                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin place <player> <clan> [role]");
                                                return true;
                                }

                                Player target = Bukkit.getPlayerExact(args[2]);
                                String clan = args[3];
                                String role = args.length >= 5 ? args[4].toUpperCase() : "MEMBER";

                                if (target == null) {
                                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                                return true;
                                }

                               
                                if (!getAllClanNames().contains(clan)) {
                                                player.sendMessage("§6[Simpleclan-PLUS] §cClan §e" + clan + " §cbestaat niet!");
                                                return true;
                                }

                               
                                addMemberToClan(target.getUniqueId(), clan, role);

                                player.sendMessage("§6[Simpleclan-PLUS] §aYou placed §e" + target.getName() + " §ainto clan §b" + clan + " §7as §f" + role);
                                target.sendMessage("§6[Simpleclan-PLUS] §aYou have been placed into clan §b" + clan + " §7as §f" + role + " §aby an admin.");
                        }

                        case "invite" -> {
                            if (!player.hasPermission("simpleclans.admin.invite")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 4) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin invite <player> <clan>");
                                return true;
                            }
                            Player target = Bukkit.getPlayerExact(args[2]);
                            String clan = args[3];

                            if (target == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                return true;
                            }

                            try (PreparedStatement ps = connection.prepareStatement(
                                    "INSERT INTO clan_invites(target_uuid, clan_name, inviter_uuid, timestamp) VALUES(?, ?, ?, ?)")) {
                                ps.setString(1, target.getUniqueId().toString());
                                ps.setString(2, clan);
                                ps.setString(3, player.getUniqueId().toString());
                                ps.setLong(4, System.currentTimeMillis());
                                ps.executeUpdate();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }

                            player.sendMessage("§6[Simpleclan-PLUS] §aYou invited §e" + target.getName() + " §ato join §b" + clan + "§a!");
                            target.sendMessage("§6[Simpleclan-PLUS] §aYou have been invited to join §b" + clan + "§a! Type §e/clan join " + clan + " §ato accept.");
                        }

                        case "reload" -> {
                            if (!player.hasPermission("simpleclans.admin.reload")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            reloadConfig();
                            languageCode = getConfig().getString("Language.default", "EN").toUpperCase();
                            loadLanguageFile(languageCode);
                            player.sendMessage("§6[Simpleclan-PLUS] §aConfiguration and language reloaded! (Current: " + languageCode + ")");
                        }

                        case "demote" -> {
                            if (!player.hasPermission("simpleclans.admin.demote")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 4) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin demote <player> <clan>");
                                return true;
                            }
                            Player target = Bukkit.getPlayerExact(args[2]);
                            String clan = args[3];

                            if (target == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                return true;
                            }

                            String targetRole = getRoleOf(target.getUniqueId());
                            String newRole = switch (targetRole.toUpperCase()) {
                                case "CO-LEADER" -> "MEMBER";
                                case "MEMBER" -> "RECRUIT";
                                default -> null;
                            };

                            if (newRole == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cThat player cannot be demoted further!");
                                return true;
                            }

                            addMemberToClan(target.getUniqueId(), clan, newRole);
                            player.sendMessage("§6[Simpleclan-PLUS] §aYou demoted §e" + target.getName() + " §ato §b" + newRole);
                            target.sendMessage("§6[Simpleclan-PLUS] §cYou have been demoted to §b" + newRole);
                        }

                        case "kick" -> {
                            if (!player.hasPermission("simpleclans.admin.kick")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 4) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin kick <player> <clan>");
                                return true;
                            }
                            Player target = Bukkit.getPlayerExact(args[2]);
                            String clan = args[3];

                            if (target == null) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cPlayer not found!");
                                return true;
                            }

                            if (!clan.equals(getClanOf(target.getUniqueId()))) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cThat player is not in the specified clan!");
                                return true;
                            }

                            addMemberToClan(target.getUniqueId(), null, null);
                            player.sendMessage("§6[Simpleclan-PLUS] §aYou kicked §e" + target.getName() + " §afrom §b" + clan);
                            target.sendMessage("§6[Simpleclan-PLUS] §cYou have been kicked from §b" + clan);
                        }
                        case "help" -> {
                            if (!player.hasPermission("simpleclans.admin")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }

                            player.sendMessage("§6===== §eAdmin Commands §6=====");
                            player.sendMessage("§e/clan admin promote <player> <clan> §7- Promote a player in a clan");
                            player.sendMessage("§e/clan admin demote <player> <clan> §7- Demote a player in a clan");
                            player.sendMessage("§e/clan admin kick <player> <clan> §7- Kick a player from a clan");
                            player.sendMessage("§e/clan admin disband <clan> §7- Disband any clan");
                            player.sendMessage("§e/clan admin purge <player> §7- Reset a player's clan data");
                            player.sendMessage("§e/clan admin reset <clan> §7- Reset a clan's data");
                            player.sendMessage("§e/clan admin place §7- Place clan items/blocks");
                            player.sendMessage("§e/clan admin invite <player> <clan> §7- Invite a player to any clan");
                            player.sendMessage("§e/clan admin reload §7- Reload the plugin configuration");
                            player.sendMessage("§6==============================");
                        }


                        case "disband" -> {
                            if (!player.hasPermission("simpleclans.admin.disband")) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cNo permission!");
                                return true;
                            }
                            if (args.length < 3) {
                                player.sendMessage("§6[Simpleclan-PLUS] §cUsage: /clan admin disband <clan>");
                                return true;
                            }
                            String clan = args[2];

                            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM clans WHERE name = ?")) {
                                ps.setString(1, clan);
                                ps.executeUpdate();
                            } catch (SQLException e) {
                                e.printStackTrace();
                                player.sendMessage("§6[Simpleclan-PLUS] §cAn error occurred while disbanding the clan!");
                                return true;
                            }

                            try (PreparedStatement ps = connection.prepareStatement("UPDATE clan_members SET clan = NULL, role = NULL WHERE clan = ?")) {
                                ps.setString(1, clan);
                                ps.executeUpdate();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }

                            player.sendMessage("§6[Simpleclan-PLUS] §aYou have disbanded the clan §e" + clan);
                            Bukkit.broadcastMessage("§6[Simpleclan-PLUS] §cThe clan §e" + clan + " §chas been disbanded!");
                        }

                        
                        default -> player.sendMessage("§6[Simpleclan-PLUS] §cUnknown admin command.");
                    }
                }
                default -> player.sendMessage("§6[Simpleclan-PLUS] §cUnknown subcommand. Use /clan help");
            }

            return true;
        });
        getCommand("clan").setTabCompleter((sender, command, alias, args) -> {
                List<String> completions = new ArrayList<>();

                if (!(sender instanceof Player)) return completions;

                if (args.length == 1) {
                        completions.addAll(Arrays.asList(
                                "create", "invite", "join", "leave", "info", "promote", "demote", "disband",
                                "help", "update", "list", "chat", "chatmsg", "admin"
                        ));
                }

                else if (args.length == 2) {
                        String sub = args[0].toLowerCase();
                        switch (sub) {
                                case "invite", "promote", "demote" ->
                                        completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                                case "join", "info", "disband" ->
                                        completions.addAll(getAllClanNames());
                                case "admin" ->
                                        completions.addAll(Arrays.asList(
                                                "promote", "demote", "kick", "invite", "disband", "reset", "purge",
                                                "place", "reload", "help"
                                        ));
                        }
                }

                else if (args.length == 3) {
                        String sub = args[0].toLowerCase();

                        if (sub.equals("admin")) {
                                String adminSub = args[1].toLowerCase();
                                switch (adminSub) {
                                        case "promote", "demote", "kick", "invite", "purge", "place" ->
                                                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                                        case "disband", "reset" ->
                                                completions.addAll(getAllClanNames());
                                }
                        }
                }

                else if (args.length == 4) {
                        String sub = args[0].toLowerCase();

                        if (sub.equals("admin")) {
                                String adminSub = args[1].toLowerCase();
                                switch (adminSub) {
                                        case "promote", "demote", "kick", "invite", "place", "reset" ->
                                                completions.addAll(getAllClanNames());
                                }
                        }
                }

                else if (args.length == 5) {
                        String sub = args[0].toLowerCase();

                        if (sub.equals("admin")) {
                                String adminSub = args[1].toLowerCase();
                                if (adminSub.equals("place")) {
                                        completions.addAll(Arrays.asList("LEADER", "CO-LEADER", "MEMBER", "RECRUIT"));
                                }
                        }
                }

           
                String lastArg = args[args.length - 1].toLowerCase();
                completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));

                return completions;
        });
    }
    private void loadLanguageFile(String lang) {
        File langFolder = new File(getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, lang + ".yml");
        if (!langFile.exists()) {
            getLogger().warning("Language file " + lang + ".yml not found! Falling back to EN.yml.");
            lang = "EN";
            saveResource("languages/EN.yml", false);
            langFile = new File(langFolder, "EN.yml");
        }

        languageConfig = YamlConfiguration.loadConfiguration(langFile);

        InputStream defaultStream = getResource("languages/" + lang + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            languageConfig.setDefaults(defConfig);
        }
    }

    public String getMessage(String path) {
        return languageConfig.getString("messages." + path, "Message not found: " + path);
    }
    public String getLanguage() {
        return languageCode;
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
                    "inviter_uuid TEXT," +
                    "timestamp INTEGER DEFAULT (strftime('%s','now')))");
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
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        
        if (!clanChatToggled.getOrDefault(uuid, false)) {
            return; 
        }

        event.setCancelled(true);

        String clan = getClanOf(uuid);
        if (clan == null) {
            player.sendMessage("§6[Simpleclan-PLUS] §cYou are not in a clan!");
            return;
        }

        String msg = event.getMessage();

        for (Map.Entry<UUID, String> member : getClanMembers(clan).entrySet()) {
            Player target = Bukkit.getPlayer(member.getKey());
            if (target != null && target.isOnline()) {
                target.sendMessage("§6[Clan] §e" + player.getName() + "§f: " + msg);
            }
        }
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
    public int getClanMemberCount(String clan) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) AS total FROM clan_members WHERE clan = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("total");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    public int getOnlineClanMembers(String clan) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (clan.equals(getClanOf(player.getUniqueId()))) count++;
        }
        return count;
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
    public Map<UUID, String> getClanMembers(String clan) {
        Map<UUID, String> members = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT uuid, role FROM clan_members WHERE clan = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.put(UUID.fromString(rs.getString("uuid")), rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }
    public String getClanLeader(String clan) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT leader FROM clans WHERE name = ?")) {
            ps.setString(1, clan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UUID leaderUUID = UUID.fromString(rs.getString("leader"));
                OfflinePlayer leaderPlayer = Bukkit.getOfflinePlayer(leaderUUID);
                return leaderPlayer.getName();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "None";
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
            if (identifier.equalsIgnoreCase("member_count")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? String.valueOf(plugin.getClanMemberCount(clan)) : "0";
            }

            if (identifier.equalsIgnoreCase("online_members")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? String.valueOf(plugin.getOnlineClanMembers(clan)) : "0";
            }

            if (identifier.equalsIgnoreCase("clan_leader")) {
                String clan = plugin.getClanOf(player.getUniqueId());
                return clan != null ? plugin.getClanLeader(clan) : "None";
            }

            return null;
        }
    }
}
