package fr.velaportal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VelaPortalPlugin extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private Persist persist;
    private PortalManager portals;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        persist = new Persist(this);
        portals = new PortalManager(this, persist);
        portals.load();
        portals.start();
        PluginCommand cmd = getCommand("vportal");
        if (cmd != null) {
            PortalCommand pc = new PortalCommand(this);
            cmd.setExecutor(pc);
            cmd.setTabCompleter(pc);
        }
        getLogger().info(portals.all().size() + " portail(s) chargé(s).");
    }

    @Override
    public void onDisable() {
        if (portals != null) {
            portals.removeAllVeils();
        }
        if (persist != null) {
            persist.shutdown();
        }
    }

    public PortalManager portals() {
        return portals;
    }

    public void reloadAll() {
        reloadConfig();
        portals.load();
    }

    public Component msg(String key, Object... kv) {
        String text = getConfig().getString("messages." + key, "<red>Message manquant : " + key);
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            b.resolver(Placeholder.unparsed(String.valueOf(kv[i]), String.valueOf(kv[i + 1])));
        }
        return mm.deserialize(text, b.build());
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(msg("prefix").append(msg(key, kv)));
    }
}
