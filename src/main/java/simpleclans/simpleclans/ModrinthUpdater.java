package simpleclans.simpleclans;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ModrinthUpdater {
    private final JavaPlugin plugin;
    private final String projectSlug;

    public ModrinthUpdater(JavaPlugin plugin, String projectSlug) {
        this.plugin = plugin;
        this.projectSlug = projectSlug;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectSlug + "/version");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                Scanner scanner = new Scanner(conn.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNext()) response.append(scanner.nextLine());
                scanner.close();

                JSONArray versions = new JSONArray(response.toString());
                if (versions.length() == 0) {
                    plugin.getLogger().warning("[Updater] No versions found on Modrinth!");
                    return;
                }

                JSONObject latest = versions.getJSONObject(0);
                String latestVersion = latest.getString("version_number");
                String currentVersion = plugin.getDescription().getVersion();

                if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                    plugin.getLogger().info("===========================================");
                    plugin.getLogger().info("[SimpleClans-PLUS] Update available!");
                    plugin.getLogger().info("Current version: v" + currentVersion);
                    plugin.getLogger().info("Latest version:  v" + latestVersion);
                    plugin.getLogger().info("===========================================");

                    String downloadUrl = latest.getJSONArray("files")
                            .getJSONObject(0)
                            .getString("url");

                    plugin.getLogger().info("[SimpleClans-PLUS] Downloading update...");
                    downloadUpdate(downloadUrl);
                } else {
                    plugin.getLogger().info("[SimpleClans-PLUS] You are running the latest version (v" + currentVersion + ")");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Failed to check for updates: " + e.getMessage());
            }
        });
    }

    private void downloadUpdate(String downloadUrl) {
        try {
            File updateFolder = new File("plugins/update");
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                plugin.getLogger().warning("[Updater] Could not create update folder!");
                return;
            }

            File outFile = new File(updateFolder, plugin.getName() + ".jar");

            try (InputStream in = new URL(downloadUrl).openStream();
                 FileOutputStream out = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }

            plugin.getLogger().info("[SimpleClans-PLUS] Update downloaded successfully!");
            plugin.getLogger().info("[SimpleClans-PLUS] The new version will be installed on the next server restart.");
        } catch (Exception e) {
            plugin.getLogger().warning("[Updater] Failed to download update: " + e.getMessage());
        }
    }
}
