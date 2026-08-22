package ru.desawff.footprints;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * config.yml, parsed once. Never mutated after {@link #read}; a reload builds a new instance.
 */
final class Settings {

    record Surface(String name, int rgb, int opacity, int lifetime) {}

    final double stepDistance;
    final double sideOffset;
    final double lift;
    final double forwardOffset;
    final float scale;
    final float viewRange;
    final int maxPerPlayer;
    final int tickPeriod;
    final double fadeStart;
    final boolean requireOnGround;
    final boolean skipSneaking;
    final boolean faceUp;
    final int blockLight;
    final int skyLight;
    final char leftGlyph;
    final char rightGlyph;
    final Key font;

    private final Map<Material, Surface> surfaces;

    private Settings(FileConfiguration cfg, Map<Material, Surface> surfaces) {
        this.surfaces = surfaces;
        stepDistance = cfg.getDouble("step-distance", 0.62);
        sideOffset = cfg.getDouble("side-offset", 0.13);
        lift = cfg.getDouble("lift", 0.015);
        forwardOffset = cfg.getDouble("forward-offset", 0.18);
        scale = (float) cfg.getDouble("scale", 1.0);
        viewRange = (float) cfg.getDouble("view-range", 0.35);
        maxPerPlayer = Math.max(1, cfg.getInt("max-per-player", 12));
        tickPeriod = Math.max(1, cfg.getInt("tick-period", 4));
        fadeStart = cfg.getDouble("fade-start", 0.55);
        requireOnGround = cfg.getBoolean("require-on-ground", true);
        skipSneaking = cfg.getBoolean("skip-sneaking", false);
        faceUp = cfg.getBoolean("face-up", true);
        blockLight = cfg.getInt("brightness.block", -1);
        skyLight = cfg.getInt("brightness.sky", -1);
        leftGlyph = firstChar(cfg.getString("glyph.left"), '\uEB00');
        rightGlyph = firstChar(cfg.getString("glyph.right"), '\uEB01');
        font = Key.key(cfg.getString("glyph.font", "minecraft:default"));
    }

    static Settings read(FileConfiguration cfg, Logger log) {
        Map<Material, Surface> surfaces = new EnumMap<>(Material.class);
        ConfigurationSection root = cfg.getConfigurationSection("surfaces");
        if (root == null) {
            log.warning("config.yml has no 'surfaces' section, no footprints will show up");
            return new Settings(cfg, surfaces);
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Surface surface = new Surface(
                    name,
                    parseColor(section.getString("color", "#4A3A28")),
                    clamp(section.getInt("opacity", 190)),
                    Math.max(20, section.getInt("lifetime", 160)));
            for (String block : section.getStringList("blocks")) {
                Material material = Material.matchMaterial(block.toUpperCase(Locale.ROOT));
                if (material == null) {
                    log.warning("unknown block in surfaces." + name + ": " + block);
                } else {
                    surfaces.put(material, surface);
                }
            }
        }
        return new Settings(cfg, surfaces);
    }

    Surface surfaceFor(Material material) {
        return surfaces.get(material);
    }

    int blockCount() {
        return surfaces.size();
    }

    private static char firstChar(String value, char fallback) {
        return value == null || value.isEmpty() ? fallback : value.charAt(0);
    }

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0x4A3A28;
        }
    }

    private static int clamp(int opacity) {
        return Math.max(0, Math.min(255, opacity));
    }
}
