package local.simplehud;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;

/**
 * SimpleHUD - Player stats display on scoreboard
 */
public class SimpleHUDPlugin extends JavaPlugin implements Listener {

    private HUDManager hudManager;
    private ServerStatusManager serverStatus;

    @Override
    public void onEnable() {
        serverStatus = new ServerStatusManager();
        hudManager = new HUDManager(this, serverStatus);

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

    // ── /server <open|close> ────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("server")) return false;

        if (!sender.hasPermission("simplehud.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6Usage: §f/server <open|close>");
            sender.sendMessage("§7Current status: " + serverStatus.getStatusDisplay());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "close" -> {
                if (!serverStatus.isOpen()) {
                    sender.sendMessage("§cThe server is already closed.");
                    return true;
                }
                serverStatus.setOpen(false);
                Bukkit.broadcastMessage("§8[§cServer§8] §cThe server has been §l§cCLOSED§r§c. New players will be queued.");
                getLogger().info(sender.getName() + " closed the server.");
            }
            case "open" -> {
                if (serverStatus.isOpen()) {
                    sender.sendMessage("§aThe server is already open.");
                    return true;
                }
                serverStatus.setOpen(true);
                Bukkit.broadcastMessage("§8[§aServer§8] §aThe server is now §l§aOPEN§r§a. Players may join freely.");
                getLogger().info(sender.getName() + " opened the server.");
            }
            default -> sender.sendMessage("§6Usage: §f/server <open|close>");
        }
        return true;
    }

    // ── Block/queue joining players when server is closed ─────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (serverStatus.isOpen()) return;
        // Allow admins / staff through
        if (event.getPlayer().hasPermission("simplehud.admin")) return;
        // Op bypass
        if (event.getPlayer().isOp()) return;

        event.disallow(
            PlayerLoginEvent.Result.KICK_OTHER,
            "§7You are joining the queue for server §6Factions§7.\n"
            + "§cThis server is currently closed.\n"
            + "§7You will be let in once the server reopens."
        );
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
        // Never show the HUD while in the hub world
        if (isHubWorld(player)) return;
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
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        hudManager.recordDeath(victim);
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            hudManager.recordKill(killer);
        }
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
            // Use a fresh blank scoreboard so no other plugin's objectives bleed through
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
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
