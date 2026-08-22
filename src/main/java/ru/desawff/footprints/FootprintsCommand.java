package ru.desawff.footprints;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class FootprintsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("toggle", "clear", "stamp", "reload", "status");
    private static final String ADMIN = "footprints.admin";

    private final FootprintsPlugin plugin;

    FootprintsCommand(FootprintsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TrailManager trails = plugin.trails();
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "toggle" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                boolean on = trails.toggle(player);
                reply(player, on ? "Footprints on." : "Footprints off.",
                        on ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            }
            case "clear" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                trails.erase(player);
                reply(player, "Your footprints are gone.", NamedTextColor.GRAY);
            }
            case "stamp" -> {
                Player player = asPlayer(sender);
                if (player == null || !allowed(player)) {
                    return true;
                }
                trails.stampHere(player);
                reply(player, "Stamped one print.", NamedTextColor.GRAY);
            }
            case "reload" -> {
                if (!allowed(sender)) {
                    return true;
                }
                plugin.reloadSettings();
                reply(sender, "config.yml reloaded.", NamedTextColor.GREEN);
            }
            case "status" -> reply(sender, trails.liveCount() + " prints alive, "
                    + trails.settings().blockCount() + " blocks mapped", NamedTextColor.GRAY);
            default -> reply(sender, "/footprints " + String.join("|", SUBCOMMANDS), NamedTextColor.GRAY);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(name -> name.startsWith(typed)).toList();
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        reply(sender, "Players only.", NamedTextColor.RED);
        return null;
    }

    private boolean allowed(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        reply(sender, "You are missing " + ADMIN + ".", NamedTextColor.RED);
        return false;
    }

    private void reply(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }
}
