package dev.arctic.goob;

import dev.arctic.goob.command.GoobCommand;
import dev.arctic.goob.listener.BlockBreakListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Goob extends JavaPlugin {

    private static Goob instance;
    private MainConfig config;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("[Goob] Enabling!");

        config = new MainConfig(getDataFolder().toPath(), getLogger());
        if (!config.isValid()) {
            getLogger().severe("Config failed to load — disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Loaded " + config.getExtraSilkDrops().size() + " extra-silk entries.");

        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);

        var goobCommand = new GoobCommand();
        var cmd = getCommand("goob");
        if (cmd != null) {
            cmd.setExecutor(goobCommand);
            cmd.setTabCompleter(goobCommand);
        }
        getLogger().info("[Goob] Enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Goob] Disabled!");
    }

    public static Goob get() {
        return instance;
    }

    public MainConfig getMainConfig() {
        return config;
    }
}
