package local.simplehud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

/**
 * SimpleHUD - Player stats display on scoreboard
 */
public class SimpleHUDPlugin extends JavaPlugin implements Listener {

    private HUDManager hudManager;

    @Override
    public void onEnable() {
        hudManager = new HUDManager(this);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Create HUD for all currently online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            hudManager.createHUD(player);
        }
        
        // Update HUD for all online players periodically
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                hudManager.updateHUD(player);
            }
        }, 0, 20L); // Update every 20 ticks (1 second)
        
        getLogger().info("SimpleHUD enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleHUD disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        hudManager.createHUD(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hudManager.removeHUD(player);
    }
}
