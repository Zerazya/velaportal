package fr.velaportal;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /vportal create|delete|list|tp|color|target|reload */
public final class PortalCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("create", "delete", "list", "tp", "color", "target", "reload");

    private final VelaPortalPlugin plugin;

    public PortalCommand(VelaPortalPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("velaportal.admin")) {
            plugin.send(s, "no-permission");
            return true;
        }
        if (a.length == 0) {
            plugin.send(s, "help");
            return true;
        }
        PortalManager pm = plugin.portals();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.send(s, "reloaded", "count", pm.all().size());
            }
            case "list" -> {
                if (pm.all().isEmpty()) {
                    plugin.send(s, "list-empty");
                    return true;
                }
                for (Portal p : pm.all()) {
                    s.sendMessage(plugin.msg("list-line", "name", p.name, "world", p.world, "x", p.ox, "y", p.oy,
                            "z", p.oz, "size", p.w + "x" + p.h, "color", p.color.toLowerCase(Locale.ROOT),
                            "target", p.target.isBlank() ? plugin.getConfig().getString("target-world", "construction") : p.target));
                }
            }
            case "create" -> {
                if (!(s instanceof Player pl)) {
                    plugin.send(s, "players-only");
                    return true;
                }
                if (a.length < 2) {
                    plugin.send(s, "usage", "usage", "/vportal create <nom> [largeur] [hauteur]");
                    return true;
                }
                String name = a[1].toLowerCase(Locale.ROOT);
                if (!name.matches("[a-z0-9_-]{1,32}")) {
                    plugin.send(s, "bad-name");
                    return true;
                }
                if (pm.get(name) != null) {
                    plugin.send(s, "exists", "name", name);
                    return true;
                }
                int w = plugin.getConfig().getInt("portal.default-width", 9);
                int h = plugin.getConfig().getInt("portal.default-height", 13);
                try {
                    if (a.length > 2) {
                        w = Integer.parseInt(a[2]);
                    }
                    if (a.length > 3) {
                        h = Integer.parseInt(a[3]);
                    }
                } catch (NumberFormatException ex) {
                    plugin.send(s, "bad-number");
                    return true;
                }
                if (w < 5 || w > 31 || h < 7 || h > 45) {
                    plugin.send(s, "bad-size");
                    return true;
                }
                Portal p = pm.create(pl, name, w, h);
                if (p == null) {
                    plugin.send(s, "too-high");
                    return true;
                }
                plugin.send(s, "created", "name", name, "size", w + "x" + h);
            }
            case "delete", "tp", "color", "target" -> {
                if (a.length < 2) {
                    plugin.send(s, "usage", "usage", "/vportal " + a[0] + " <nom>");
                    return true;
                }
                Portal p = pm.get(a[1]);
                if (p == null) {
                    plugin.send(s, "unknown", "name", a[1]);
                    return true;
                }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "delete" -> {
                        pm.delete(p);
                        plugin.send(s, "deleted", "name", p.name);
                    }
                    case "tp" -> {
                        if (!(s instanceof Player pl)) {
                            plugin.send(s, "players-only");
                            return true;
                        }
                        Location l = p.center();
                        if (l == null) {
                            plugin.send(s, "world-missing", "world", p.world);
                            return true;
                        }
                        Location dest = l.clone();
                        dest.setY(p.oy);
                        if (p.axis == 'X') {
                            dest.add(0, 0, 3);
                        } else {
                            dest.add(3, 0, 0);
                        }
                        pl.teleportAsync(dest);
                    }
                    case "color" -> {
                        if (a.length < 3) {
                            plugin.send(s, "usage", "usage", "/vportal color <nom> <couleur>");
                            return true;
                        }
                        try {
                            DyeColor dc = DyeColor.valueOf(a[2].toUpperCase(Locale.ROOT));
                            p.color = dc.name();
                        } catch (IllegalArgumentException ex) {
                            plugin.send(s, "bad-color");
                            return true;
                        }
                        pm.changed(p);
                        plugin.send(s, "updated", "name", p.name);
                    }
                    default -> {
                        if (a.length < 3) {
                            plugin.send(s, "usage", "usage", "/vportal target <nom> <monde|default>");
                            return true;
                        }
                        if (a[2].equalsIgnoreCase("default")) {
                            p.target = "";
                        } else {
                            World w = Bukkit.getWorld(a[2]);
                            if (w == null) {
                                plugin.send(s, "world-missing", "world", a[2]);
                                return true;
                            }
                            p.target = w.getName();
                        }
                        pm.changed(p);
                        plugin.send(s, "updated", "name", p.name);
                    }
                }
            }
            default -> plugin.send(s, "help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("velaportal.admin")) {
            return out;
        }
        String cur = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            out.addAll(SUBS);
        } else if (a.length == 2 && List.of("delete", "tp", "color", "target").contains(a[0].toLowerCase(Locale.ROOT))) {
            plugin.portals().all().forEach(p -> out.add(p.name));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("color")) {
            for (DyeColor d : DyeColor.values()) {
                out.add(d.name().toLowerCase(Locale.ROOT));
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("target")) {
            out.add("default");
            Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
