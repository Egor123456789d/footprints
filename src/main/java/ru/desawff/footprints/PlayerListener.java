package ru.desawff.footprints;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class PlayerListener implements Listener {

    private final TrailManager trails;

    PlayerListener(TrailManager trails) {
        this.trails = trails;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            return; // looking around is the most common move event of them all
        }
        trails.onMove(event.getPlayer(), to);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trails.track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trails.forget(event.getPlayer());
    }

    /**
     * Prints left behind after a teleport belong to a region this thread no longer owns,
     * which Folia will not let us touch. Clear them while we still can.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        trails.erase(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        trails.erase(event.getPlayer());
    }
}
