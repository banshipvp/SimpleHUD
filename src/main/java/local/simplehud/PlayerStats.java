package local.simplehud;

/**
 * Tracks player stats like K/D ratio
 */
public class PlayerStats {
    private int kills = 0;
    private int deaths = 0;

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public double getKD() {
        if (deaths == 0) {
            return kills; // If no deaths, K/D = kills
        }
        return (double) kills / deaths;
    }

    public void reset() {
        kills = 0;
        deaths = 0;
    }
}
