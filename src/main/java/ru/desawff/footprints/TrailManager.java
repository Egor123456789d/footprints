package ru.desawff.footprints;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one trail per player: a bounded queue of TextDisplay entities that fade out and die.
 *
 * <p>TextDisplay rather than ItemDisplay because of {@code setTextOpacity}. Fading costs a
 * single metadata packet instead of a stack of half transparent textures, and since the glyph
 * itself is white, one pair of textures covers every surface colour.
 */
final class TrailManager {

    private static final class Print {
        final TextDisplay display;
        final int lifetime;
        final int opacity;
        int age;
        int lastAlpha = -1;

        Print(TextDisplay display, Settings.Surface surface) {
            this.display = display;
            this.lifetime = surface.lifetime();
            this.opacity = surface.opacity();
        }
    }

    /**
     * Touched from the player's region thread only: move events and the entity scheduler
     * both run there, so a plain ArrayDeque is enough.
     */
    private static final class Trail {
        final Deque<Print> prints = new ArrayDeque<>();
        ScheduledTask task;
        double walked;
        double lastX;
        double lastZ;
        boolean hasLast;
        boolean rightFoot;
    }

    private final Plugin plugin;
    private final NamespacedKey optOutKey;
    private final Map<UUID, Trail> trails = new ConcurrentHashMap<>();
    private volatile Settings settings;

    TrailManager(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.optOutKey = new NamespacedKey(plugin, "opted_out");
    }

    Settings settings() {
        return settings;
    }

    void replaceSettings(Settings updated) {
        for (Trail trail : trails.values()) {
            wipe(trail);
        }
        settings = updated;
    }

    void track(Player player) {
        if (optedOut(player) || trails.containsKey(player.getUniqueId())) {
            return;
        }
        Trail trail = new Trail();
        int period = settings.tickPeriod;
        trail.task = player.getScheduler().runAtFixedRate(plugin, task -> fade(player), null, period, period);
        if (trail.task == null) {
            return; // player is already on the way out
        }
        trails.put(player.getUniqueId(), trail);
    }

    void forget(Player player) {
        Trail trail = trails.remove(player.getUniqueId());
        if (trail == null) {
            return;
        }
        if (trail.task != null) {
            trail.task.cancel();
        }
        wipe(trail);
    }

    void forgetEveryone() {
        for (UUID id : trails.keySet()) {
            Trail trail = trails.remove(id);
            if (trail != null) {
                if (trail.task != null) {
                    trail.task.cancel();
                }
                wipe(trail);
            }
        }
    }

    /** Erases the prints but keeps following the player: teleports, world changes, /footprints clear. */
    void erase(Player player) {
        Trail trail = trails.get(player.getUniqueId());
        if (trail == null) {
            return;
        }
        wipe(trail);
        trail.hasLast = false;
        trail.walked = 0;
    }

    boolean optedOut(Player player) {
        Byte flag = player.getPersistentDataContainer().get(optOutKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    /** @return true if the player now leaves footprints. */
    boolean toggle(Player player) {
        if (optedOut(player)) {
            player.getPersistentDataContainer().remove(optOutKey);
            track(player);
            return true;
        }
        player.getPersistentDataContainer().set(optOutKey, PersistentDataType.BYTE, (byte) 1);
        forget(player);
        return false;
    }

    int liveCount() {
        int total = 0;
        for (Trail trail : trails.values()) {
            total += trail.prints.size();
        }
        return total;
    }

    void onMove(Player player, Location to) {
        Trail trail = trails.get(player.getUniqueId());
        if (trail == null) {
            return;
        }
        if (!trail.hasLast) {
            trail.lastX = to.getX();
            trail.lastZ = to.getZ();
            trail.hasLast = true;
            return;
        }

        double dx = to.getX() - trail.lastX;
        double dz = to.getZ() - trail.lastZ;
        trail.lastX = to.getX();
        trail.lastZ = to.getZ();

        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 1.5) {
            return; // teleport, elytra, horse: don't let one jump fill the meter
        }
        trail.walked += step;
        if (trail.walked < settings.stepDistance) {
            return;
        }
        trail.walked = 0;

        if (!leavesPrints(player, to)) {
            return;
        }
        Settings.Surface surface = surfaceUnder(to);
        if (surface != null) {
            stamp(trail, to, surface);
        }
    }

    /** Drops one print where the player stands, whatever the ground is. Used to tune offsets. */
    void stampHere(Player player) {
        Trail trail = trails.get(player.getUniqueId());
        if (trail == null) {
            return;
        }
        Location where = player.getLocation();
        Settings.Surface surface = surfaceUnder(where);
        if (surface == null) {
            surface = new Settings.Surface("debug", 0xFFFFFF, 255, 400);
        }
        stamp(trail, where, surface);
    }

    // isOnGround() comes from the client, and a client that lies about it only earns
    // itself a footprint, so the cheap check wins over ray casting the block below.
    @SuppressWarnings("deprecation")
    private boolean leavesPrints(Player player, Location at) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying() || player.isGliding()) {
            return false;
        }
        if (player.isInsideVehicle() || player.isSwimming() || at.getBlock().isLiquid()) {
            return false;
        }
        if (settings.skipSneaking && player.isSneaking()) {
            return false;
        }
        return !settings.requireOnGround || player.isOnGround();
    }

    private Settings.Surface surfaceUnder(Location at) {
        Block below = at.getWorld().getBlockAt(
                at.getBlockX(), (int) Math.floor(at.getY() - 0.1), at.getBlockZ());
        Settings.Surface surface = settings.surfaceFor(below.getType());
        if (surface == null && below.getType().isAir()) {
            // Standing on a slab or a stair puts the sample in air, so look one block deeper.
            surface = settings.surfaceFor(below.getRelative(BlockFace.DOWN).getType());
        }
        return surface;
    }

    private void stamp(Trail trail, Location at, Settings.Surface surface) {
        float yaw = at.getYaw();
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);

        trail.rightFoot = !trail.rightFoot;
        double side = trail.rightFoot ? settings.sideOffset : -settings.sideOffset;

        Location spot = new Location(at.getWorld(),
                at.getX() + rightX * side + forwardX * settings.forwardOffset,
                at.getY() + settings.lift,
                at.getZ() + rightZ * side + forwardZ * settings.forwardOffset,
                yaw, 0f);

        char glyph = trail.rightFoot ? settings.rightGlyph : settings.leftGlyph;
        Component text = Component.text(String.valueOf(glyph))
                .font(settings.font)
                .color(TextColor.color(surface.rgb()));

        // The text quad stands upright facing +Z. Rotating -90 around X lays it on the ground;
        // +90 lays it down too, but the normal then points into the block and text renders on
        // one side only, so the print turns invisible from above while the entity is still there.
        // After that the top of the line points north, hence the 180 - yaw spin.
        float pitch = settings.faceUp ? -90f : 90f;
        float spin = settings.faceUp ? 180f - yaw : -yaw;
        Quaternionf rotation = new Quaternionf(new AxisAngle4f((float) Math.toRadians(spin), 0f, 1f, 0f))
                .mul(new Quaternionf(new AxisAngle4f((float) Math.toRadians(pitch), 1f, 0f, 0f)));
        float size = settings.scale;

        TextDisplay display = spot.getWorld().spawn(spot, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(false);
            entity.setSeeThrough(false);
            entity.setLineWidth(200);
            entity.setTextOpacity((byte) surface.opacity());
            entity.setViewRange(settings.viewRange);
            entity.setPersistent(false);
            if (settings.blockLight >= 0 || settings.skyLight >= 0) {
                entity.setBrightness(new Display.Brightness(
                        Math.max(0, settings.blockLight), Math.max(0, settings.skyLight)));
            }
            entity.setTransformation(new Transformation(
                    new Vector3f(), rotation, new Vector3f(size, size, size), new Quaternionf()));
        });

        trail.prints.addLast(new Print(display, surface));
        while (trail.prints.size() > settings.maxPerPlayer) {
            kill(trail.prints.pollFirst());
        }
    }

    private void fade(Player player) {
        Trail trail = trails.get(player.getUniqueId());
        if (trail == null) {
            return;
        }
        int period = settings.tickPeriod;
        double fadeStart = settings.fadeStart;

        trail.prints.removeIf(print -> {
            print.age += period;
            if (print.age >= print.lifetime || !print.display.isValid()) {
                kill(print);
                return true;
            }
            double lived = (double) print.age / print.lifetime;
            if (lived < fadeStart) {
                return false;
            }
            int alpha = (int) Math.round(print.opacity * (1.0 - (lived - fadeStart) / (1.0 - fadeStart)));
            // One packet per visible step, not per point of alpha.
            if (alpha <= 0 || Math.abs(alpha - print.lastAlpha) >= 4) {
                print.lastAlpha = alpha;
                print.display.setTextOpacity((byte) Math.max(0, alpha));
            }
            return false;
        });
    }

    private void wipe(Trail trail) {
        Print print;
        while ((print = trail.prints.pollFirst()) != null) {
            kill(print);
        }
    }

    private void kill(Print print) {
        if (print != null && print.display.isValid()) {
            print.display.remove();
        }
    }
}
