package local.simplehud;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages HUD display for players
 */
public class HUDManager {

    private static final String TAB_BRAND = "§d§l✦ §b§lSimple Factions §d§l✦";

    private final JavaPlugin plugin;
    private final ServerStatusManager serverStatus;
    private final Map<UUID, Scoreboard>  playerScoreboards = new HashMap<>();
    private final Map<UUID, PlayerStats> playerStats       = new HashMap<>();
    private net.milkbowl.vault.economy.Economy economy = null;

    // ── Caches to avoid expensive reflection every second ─────────────────────
    private final Map<UUID, String> factionCache   = new HashMap<>();
    private final Map<UUID, String> rankCache      = new HashMap<>();
    private final Map<UUID, Long>   cacheTimestamp = new HashMap<>();
    /** Refresh rank/faction cache at most this often (ms). */
    private static final long CACHE_TTL_MS = 5_000;

    public HUDManager(JavaPlugin plugin, ServerStatusManager serverStatus) {
        this.plugin       = plugin;
        this.serverStatus = serverStatus;
        setupEconomy();
    }

    private void setupEconomy() {
        try {
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> economyProvider =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (economyProvider != null) {
                economy = economyProvider.getProvider();
            }
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Economy not available");
        }
    }

    /**
     * Create HUD for a player
     */
    public void createHUD(Player player) {
        try {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), scoreboard);
            playerStats.put(player.getUniqueId(), new PlayerStats());

            Objective objective = scoreboard.registerNewObjective(
                    "hud", "dummy", "§6§lSimple Factions");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            player.setScoreboard(scoreboard);
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error creating HUD for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Update HUD for a player (called every 20 ticks).
     * Expensive lookups (faction, rank) are cached for CACHE_TTL_MS.
     */
    public void updateHUD(Player player) {
        try {
            Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
            if (scoreboard == null) return;

            Objective objective = scoreboard.getObjective("hud");
            if (objective == null) return;

            // ── Refresh expensive caches if stale ──────────────────────────────
            UUID uid = player.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - cacheTimestamp.getOrDefault(uid, 0L) > CACHE_TTL_MS) {
                factionCache.put(uid, getFactionName(player));
                rankCache.put(uid, getRankText(player));
                cacheTimestamp.put(uid, now);
            }
            String factionName = factionCache.getOrDefault(uid, null);
            String rankText    = rankCache.getOrDefault(uid, "§7N/A");

            // Update scoreboard title to faction name (or "No Faction")
            String title = (factionName != null && !factionName.isBlank())
                    ? "§6§l" + factionName : "§6§lNo Faction";
            objective.setDisplayName(title);

            // ── Cheap real-time values ─────────────────────────────────────────
            String balanceText = getBalanceText(player);
            int totalXP = player.getTotalExperience();
            String xpText = "§b" + formatNumber(totalXP) + " XP";

            PlayerStats stats = playerStats.get(uid);
            int kills  = stats != null ? stats.getKills()  : 0;
            int deaths = stats != null ? stats.getDeaths() : 0;
            double ratio = deaths == 0 ? kills : (double) kills / deaths;
            String ratioStr = String.format("%.2f", ratio);

            // ── Rebuild scoreboard ─────────────────────────────────────────────
            scoreboard.getEntries().forEach(scoreboard::resetScores);

            int line = 11;

            // Separator 1
            objective.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);

            // Player / Rank
            objective.getScore("§e◆ §7Player  §f" + player.getName()).setScore(line--);
            objective.getScore("§e◆ §7Rank    " + rankText).setScore(line--);

            // Separator 2 (different length)
            objective.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);

            // Balance / XP
            objective.getScore("§e◆ §7Balance " + balanceText).setScore(line--);
            objective.getScore("§e◆ §7XP      " + xpText).setScore(line--);

            // Separator 3
            objective.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);

            // K/D in Cosmic style: K/D: ratio [Kills;Deaths]
            objective.getScore("§e◆ §7K/D: §f" + ratioStr
                    + " §8[§a" + kills + "§8;§c" + deaths + "§8]").setScore(line--);

            // Separator 4
            objective.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line);

        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error updating HUD: " + e.getMessage());
        }
    }

    // ── Hub-world scoreboard ───────────────────────────────────────────────────

    /**
     * Creates (or replaces) a hub-world sidebar scoreboard for {@code player}.
     * Shows the Factions server status and live downtime.
     */
    public void createHubHUD(Player player) {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), board);
            Objective obj = board.registerNewObjective("hubhud", "dummy", "§6§lSimple Factions");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(board);
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error creating hub HUD: " + e.getMessage());
        }
    }

    /**
     * Refreshes the hub-world sidebar for {@code player} (called every second).
     */
    public void updateHubHUD(Player player) {
        try {
            Scoreboard board = playerScoreboards.get(player.getUniqueId());
            if (board == null) return;
            Objective obj = board.getObjective("hubhud");
            if (obj == null) return;

            // Query Factions server status from SimpleFactionsRaiding at runtime
            local.simplefactionsraiding.ServerStatusManager sfrStatus = getFactionServerStatus();

            boolean closed     = sfrStatus != null && sfrStatus.isServerClosed();
            boolean rebooting  = sfrStatus != null && sfrStatus.isRebooting();

            String statusLine;
            if (rebooting)     statusLine = "§c§lREBOOTING";
            else if (closed)   statusLine = "§c§lCLOSED";
            else               statusLine = "§a§lOPEN";

            board.getEntries().forEach(board::resetScores);

            int line = 9;
            obj.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);
            obj.getScore("§e◆ §7Factions Server").setScore(line--);
            obj.getScore("§e◆ §7Status: " + statusLine).setScore(line--);

            if (rebooting && sfrStatus != null) {
                obj.getScore("§e◆ §7Downtime: " + sfrStatus.getDowntimeDisplay()).setScore(line--);
            } else {
                obj.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);
            }

            obj.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line--);
            obj.getScore("§e◆ §7Players: §f" + Bukkit.getOnlinePlayers().size()).setScore(line--);
            obj.getScore("§8§m▬▬▬▬▬▬▬▬▬▬▬▬▬").setScore(line);
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error updating hub HUD: " + e.getMessage());
        }
    }

    /** Looks up the SimpleFactionsRaiding ServerStatusManager at runtime. Null-safe. */
    private local.simplefactionsraiding.ServerStatusManager getFactionServerStatus() {
        try {
            org.bukkit.plugin.Plugin sfr = Bukkit.getPluginManager().getPlugin("SimpleFactionsRaiding");
            if (sfr instanceof local.simplefactionsraiding.SimpleFactionsRaidingPlugin sfrPlugin) {
                return sfrPlugin.getServerStatusManager();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void updateTabForAll() {
        int online = Bukkit.getOnlinePlayers().size();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String header;
            if (isHubWorld(viewer)) {
                header = "\n" +
                    TAB_BRAND + "\n" +
                    "§7Online Players: §f" + online + "\n";
            } else {
                String viewerFaction = getFactionName(viewer);
                if (viewerFaction == null || viewerFaction.isBlank()) {
                    viewerFaction = "No Faction";
                }
                header = "\n" +
                    TAB_BRAND + "\n" +
                    "§7Faction: §6" + viewerFaction + "\n" +
                    "§7Online Players: §f" + online + "\n";
            }

            String footer = "\n" +
                "§f§lDISCORD §8| §f§lSTORE §8| §f§lHELP\n" +
                "§7Need help? Ask a staff member\n" +
                "§b/onlinestaff\n";

            viewer.setPlayerListHeaderFooter(header, footer);
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            target.setPlayerListName(formatTabName(target));
        }
    }

    private boolean isHubWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase("hub");
    }

    private String formatTabName(Player player) {
        String rankColor = getTabRankColor(player);

        // No faction tag shown for hub world players
        if (isHubWorld(player)) {
            return limitTabName(rankColor + player.getName());
        }

        String factionName = getFactionName(player);
        if (factionName == null || factionName.isBlank()) {
            return limitTabName(rankColor + player.getName());
        }

        String shortFaction = factionName.length() > 10 ? factionName.substring(0, 10) : factionName;
        return limitTabName("§8[§6" + shortFaction + "§8] " + rankColor + player.getName());
    }

    private String getTabRankColor(Player player) {
        try {
            var luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPermsPlugin == null) return "§f";

            var luckPerms = net.luckperms.api.LuckPermsProvider.get();
            var user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "§f";

            String rank = user.getPrimaryGroup().toLowerCase();
            if (rank.contains("owner")) return "§5";
            if (rank.contains("admin")) return "§4";
            if (rank.contains("dev")) return "§1";
            if (rank.contains("mod")) return "§b";
            if (rank.contains("helper")) return "§d";
            if (rank.contains("sovereign")) return "§c";
            if (rank.contains("warlord")) return "§5";
            if (rank.contains("tactician")) return "§6";
            if (rank.contains("militant")) return "§e";
            if (rank.contains("scout")) return "§a";
        } catch (Exception ignored) {
        }
        return "§f";
    }

    private String limitTabName(String text) {
        String stripped = ChatColor.stripColor(text);
        if (stripped == null || stripped.length() <= 48) {
            return text;
        }
        String truncatedPlain = stripped.substring(0, 48);
        return "§f" + truncatedPlain;
    }

    /**
     * Remove HUD for a player
     */
    public void removeHUD(Player player) {
        UUID uid = player.getUniqueId();
        playerScoreboards.remove(uid);
        playerStats.remove(uid);
        factionCache.remove(uid);
        rankCache.remove(uid);
        cacheTimestamp.remove(uid);
    }

    /**
     * Get rank text from LuckPerms
     */
    private String getRankText(Player player) {
        try {
            var luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPermsPlugin == null) return "§7N/A";
            
            var luckPerms = net.luckperms.api.LuckPermsProvider.get();
            var user = luckPerms.getUserManager().getUser(player.getUniqueId());
            
            if (user != null) {
                String rank = user.getPrimaryGroup();
                String rankLower = rank.toLowerCase();
                
                // Color code by rank
                if (rankLower.contains("owner")) return "§5✦ Owner";
                if (rankLower.contains("admin")) return "§4✦ Admin";
                if (rankLower.contains("dev")) return "§1✦ Dev";
                if (rankLower.contains("mod")) return "§b✦ Mod";
                if (rankLower.contains("helper")) return "§d✦ Helper";
                if (rankLower.contains("sovereign")) return "§c✦ Sovereign";
                if (rankLower.contains("warlord")) return "§5✦ Warlord";
                if (rankLower.contains("tactician")) return "§6✦ Tactician";
                if (rankLower.contains("militant")) return "§e✦ Militant";
                if (rankLower.contains("scout")) return "§a✦ Scout";
                
                return "§e" + capitalize(rank);
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return "§7N/A";
    }

    /**
     * Get faction text from SimpleFactions
     */
    private String getFactionText(Player player) {
        String factionName = getFactionName(player);
        if (factionName != null && !factionName.isBlank()) {
            return "§6" + factionName;
        }
        return "§7No Faction";
    }

    private String getFactionName(Player player) {
        try {
            var factionPlugin = Bukkit.getPluginManager().getPlugin("SimpleFactions");
            if (factionPlugin == null) return null;
            
            // Use reflection to get SimpleFactions plugin instance
            var getFactionManagerMethod = factionPlugin.getClass().getMethod("getFactionManager");
            var factionManager = getFactionManagerMethod.invoke(factionPlugin);
            
            // Get faction for player
            var getFactionMethod = factionManager.getClass().getMethod("getFaction", java.util.UUID.class);
            var faction = getFactionMethod.invoke(factionManager, player.getUniqueId());
            
            if (faction != null) {
                var getNameMethod = faction.getClass().getMethod("getName");
                return (String) getNameMethod.invoke(faction);
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return null;
    }

    /**
     * Get balance text - Show both player and faction balance
     */
    private String getBalanceText(Player player) {
        try {
            // Try to get player balance from economy
            if (economy != null) {
                double playerBalance = economy.getBalance(player);
                return "§a$" + formatNumber((long) playerBalance);
            }
        } catch (Exception e) {
            // Silently ignore
        }
        return "§7N/A";
    }

    /**
     * Get XP/Level text
     */
    private String getXPText(Player player) {
        try {
            int level = player.getLevel();
            int totalXP = player.getTotalExperience();
            return "§bLvl §e" + level + " §b(§e" + formatNumber(totalXP) + "§b XP)";
        } catch (Exception e) {
            return "§7N/A";
        }
    }

    /**
     * Track player kill/death
     */
    public void recordKill(Player killer) {
        PlayerStats stats = playerStats.get(killer.getUniqueId());
        if (stats != null) {
            stats.addKill();
        }
    }

    public void recordDeath(Player victim) {
        PlayerStats stats = playerStats.get(victim.getUniqueId());
        if (stats != null) {
            stats.addDeath();
        }
    }

    /**
     * Capitalize a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Format number with K, M, B suffix
     */
    private String formatNumber(long num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1_000_000) return (num / 1000) + "K";
        if (num < 1_000_000_000) return (num / 1_000_000) + "M";
        return (num / 1_000_000_000) + "B";
    }
}
