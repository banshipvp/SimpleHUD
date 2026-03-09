package local.simplehud;

/**
 * Tracks whether the server is open or closed.
 * When closed, joining non-staff players are denied entry.
 */
public class ServerStatusManager {

    private boolean open = true;

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    /** §a-coloured status string for the HUD sidebar. */
    public String getStatusDisplay() {
        return open ? "§a§lSERVER OPEN" : "§c§lSERVER CLOSED";
    }
}
