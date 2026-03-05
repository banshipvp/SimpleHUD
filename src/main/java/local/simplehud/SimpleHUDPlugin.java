package local.simplehud;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
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
        
        // Create HUD for all currently online players (skip those in hub)
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isHubWorld(player)) {
                hudManager.createHUD(player);
            }
        }
        
        // Update HUD for all online players periodically (skip hub world)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            hudManager.updateTabForAll();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isHubWorld(player)) {
                    hudManager.updateHUD(player);
                }
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
        // Don't create HUD immediately - wait to see which world they're in
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isHubWorld(player)) {
                hudManager.createHUD(player);
            }
            hudManager.updateTabForAll();
        }, 10L);
    }

    // LOWEST fires before WorldEdit's compass navigation handler, cancelling it
    // before it can call player.teleport().
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCompassInteractEarly(PlayerInteractEvent event) {
        if (!isCompassClick(event)) return;
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    // HIGHEST mops up anything that slipped through (e.g. Essentials at NORMAL).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassInteract(PlayerInteractEvent event) {
        if (!isCompassClick(event)) return;
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        Player player = event.getPlayer();
        hudManager.createHUD(player);
        hudManager.updateHUD(player);
    }

    private boolean isCompassClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return false;
        ItemStack item = event.getItem();
        return item != null && item.getType() == Material.COMPASS;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hudManager.removeHUD(player);
        Bukkit.getScheduler().runTaskLater(this, () -> hudManager.updateTabForAll(), 1L);
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // If changing to hub, remove scoreboard; if leaving hub, create scoreboard
        if (isHubWorld(player)) {
            hudManager.removeHUD(player);
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } else {
            hudManager.createHUD(player);
        }

        // Refresh tab immediately so faction tag appears/disappears right away
        Bukkit.getScheduler().runTaskLater(this, hudManager::updateTabForAll, 1L);
    }

    private boolean isHubWorld(Player player) {
        String worldName = player.getWorld().getName();
        return worldName.equalsIgnoreCase("hub");
    }
}
