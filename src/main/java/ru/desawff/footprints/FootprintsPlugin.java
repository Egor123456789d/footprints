package ru.desawff.footprints;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FootprintsPlugin extends JavaPlugin {

    private TrailManager trails;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        trails = new TrailManager(this, Settings.read(getConfig(), getLogger()));
        getServer().getPluginManager().registerEvents(new PlayerListener(trails), this);

        FootprintsCommand command = new FootprintsCommand(this);
        PluginCommand footprints = getCommand("footprints");
        footprints.setExecutor(command);
        footprints.setTabCompleter(command);

        // /reload or a hot install: nobody is going to send us a join event.
        getServer().getOnlinePlayers().forEach(trails::track);
    }

    @Override
    public void onDisable() {
        if (trails != null) {
            trails.forgetEveryone();
        }
    }

    TrailManager trails() {
        return trails;
    }

    void reloadSettings() {
        reloadConfig();
        trails.replaceSettings(Settings.read(getConfig(), getLogger()));
    }
}
