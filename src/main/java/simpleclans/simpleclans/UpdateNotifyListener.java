package simpleclans.simpleclans;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class UpdateNotifyListener implements Listener {
    private final ModrinthUpdater updater;

    public UpdateNotifyListener(ModrinthUpdater updater) {
        this.updater = updater;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && updater.isUpdateAvailable()) {
            updater.sendUpdateMessage(player);
        }
    }
}
