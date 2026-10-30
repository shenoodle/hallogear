package dev.halloweengear;

import org.bukkit.plugin.java.JavaPlugin;

public final class HalloweenGear extends JavaPlugin {

    private Gear gear;
    private GearListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        gear = new Gear(this);
        listener = new GearListener(this, gear);
        getServer().getPluginManager().registerEvents(listener, this);

        HalloweenCommand command = new HalloweenCommand(this, gear);
        if (getCommand("halloween") != null) {
            getCommand("halloween").setExecutor(command);
            getCommand("halloween").setTabCompleter(command);
        }

        // Armor set bonuses refresh once per second.
        getServer().getScheduler().runTaskTimer(this, listener::tickArmor, 20L, 20L);

        getLogger().info("HalloweenGear enabled.");
    }

    @Override
    public void onDisable() {
        if (listener != null) listener.shutdown();
    }
}
