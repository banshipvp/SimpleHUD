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

    private static final String TAB_BRAND = "§d§l✦ §b§lＳＩＭＰＬＥ§f§lＦＡＣＴＩＯＮＳ §d§l✦";

    private final JavaPlugin plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private net.milkbowl.vault.economy.Economy economy = null;

    public HUDManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
            
            // Create objective for right-side display with nice title
            Objective objective = scoreboard.registerNewObjective("hud", "dummy", "§b══════════════");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            player.setScoreboard(scoreboard);
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error creating HUD for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Update HUD for a player
     */
    public void updateHUD(Player player) {
        try {
            Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
            if (scoreboard == null) return;
            
            Objective objective = scoreboard.getObjective("hud");
            if (objective == null) return;
            
            // Clear existing scores
            scoreboard.getEntries().forEach(scoreboard::resetScores);
            
            // Add stats with beautiful colors
            int line = 13;
            
            // Header
            objective.getScore("              ").setScore(line--);
            
            // Player name
            objective.getScore("§b▸ §fPlayer").setScore(line--);
            objective.getScore("  §e" + player.getName()).setScore(line--);
            
            // Rank (from LuckPerms)
            String rankText = getRankText(player);
            objective.getScore("§b▸ §fRank").setScore(line--);
            objective.getScore("  " + rankText).setScore(line--);
            
            // Faction info
            String factionText = getFactionText(player);
            objective.getScore("§b▸ §fFaction").setScore(line--);
            objective.getScore("  " + factionText).setScore(line--);
            
            // Balance
            String balanceText = getBalanceText(player);
            objective.getScore("§b▸ §fBalance").setScore(line--);
            objective.getScore("  " + balanceText).setScore(line--);
            
            // XP Level
            String xpText = getXPText(player);
            objective.getScore("§b▸ §fExperience").setScore(line--);
            objective.getScore("  " + xpText).setScore(line--);
            
            // K/D Ratio
            PlayerStats stats = playerStats.get(player.getUniqueId());
            if (stats != null) {
                double kd = stats.getKD();
                String kdColor = kd >= 2.0 ? "§a" : kd >= 1.0 ? "§e" : "§c";
                objective.getScore("§b▸ §fK/D Ratio").setScore(line--);
                objective.getScore("  " + kdColor + String.format("%.2f", kd)).setScore(line--);
            }
            
            // Footer
            objective.getScore("              ").setScore(line);
            
        } catch (Exception e) {
            System.out.println("[SimpleHUD] Error updating HUD: " + e.getMessage());
        }
    }

    public void updateTabForAll() {
        int online = Bukkit.getOnlinePlayers().size();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String viewerFaction = getFactionName(viewer);
            if (viewerFaction == null || viewerFaction.isBlank()) {
            viewerFaction = "No Faction";
            }

            String header = "\n" +
                "§b§lTECHNOCLASH\n" +
                TAB_BRAND + "\n" +
                "§7Faction: §6" + viewerFaction + "\n" +
                "§7Online Players: §f" + online + "\n";

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

    private String formatTabName(Player player) {
        String factionName = getFactionName(player);
        String rankColor = getTabRankColor(player);

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
        playerScoreboards.remove(player.getUniqueId());
        playerStats.remove(player.getUniqueId());
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
