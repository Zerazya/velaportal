package fr.velaportal;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Création, affichage (voile coloré + particules) et détection des portails. */
public final class PortalManager {

    private final VelaPortalPlugin plugin;
    private final Persist persist;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<String, List<BlockDisplay>> veils = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final RandomTeleport teleporter;
    private long tick;

    public PortalManager(VelaPortalPlugin plugin, Persist persist) {
        this.plugin = plugin;
        this.persist = persist;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
        this.teleporter = new RandomTeleport(plugin);
    }

    // ---------------------------------------------------------------- données

    public void load() {
        removeAllVeils();
        portals.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("portals");
        if (s == null) {
            return;
        }
        for (String n : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(n);
            if (c != null) {
                portals.put(n, Portal.load(n, c));
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Portal p : portals.values()) {
            p.save(y);
        }
        persist.write(file, y.saveToString());
    }

    public Portal get(String name) {
        return portals.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Portal> all() {
        return portals.values();
    }

    public void release(UUID id) {
        busy.remove(id);
    }

    // ---------------------------------------------------------------- création / suppression

    private List<Material> frameMaterials() {
        List<Material> list = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("portal.frame-materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null && m.isBlock() && !m.isAir()) {
                list.add(m);
            }
        }
        if (list.isEmpty()) {
            list.add(Material.OBSIDIAN);
        }
        return list;
    }

    /** Construit le portail devant le joueur. Retourne null si le monde est trop limité en hauteur. */
    public Portal create(Player p, String name, int w, int h) {
        Portal portal = new Portal(name);
        Location l = p.getLocation();
        World world = l.getWorld();
        float yaw = l.getYaw();
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // le plan du portail est perpendiculaire à la direction du regard
        portal.axis = Math.abs(fx) > Math.abs(fz) ? 'Z' : 'X';
        portal.w = w;
        portal.h = h;
        portal.roundness = Math.max(2.0, plugin.getConfig().getDouble("portal.roundness", 3.0));
        portal.color = plugin.getConfig().getString("portal.color", "PURPLE").toUpperCase(Locale.ROOT);
        portal.world = world.getName();
        int dist = 3 + 0;
        int cx = (int) Math.floor(l.getX() + fx * dist);
        int cz = (int) Math.floor(l.getZ() + fz * dist);
        portal.oy = l.getBlockY();
        if (portal.oy - 1 < world.getMinHeight() || portal.oy + h + 1 >= world.getMaxHeight()) {
            return null;
        }
        if (portal.axis == 'X') {
            portal.ox = cx - w / 2;
            portal.oz = cz;
        } else {
            portal.ox = cx;
            portal.oz = cz - w / 2;
        }
        List<Material> mats = frameMaterials();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int[] c : portal.frameCells()) {
            world.getBlockAt(portal.worldX(c[0]), portal.oy + c[1], portal.worldZ(c[0]))
                    .setType(mats.get(r.nextInt(mats.size())), false);
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (portal.inside(i, j)) {
                    world.getBlockAt(portal.worldX(i), portal.oy + j, portal.worldZ(i)).setType(Material.AIR, false);
                }
            }
        }
        portals.put(name, portal);
        save();
        return portal;
    }

    public void delete(Portal portal) {
        removeVeil(portal.name);
        portals.remove(portal.name);
        World world = Bukkit.getWorld(portal.world);
        if (world != null) {
            for (int[] c : portal.frameCells()) {
                world.getBlockAt(portal.worldX(c[0]), portal.oy + c[1], portal.worldZ(c[0])).setType(Material.AIR, false);
            }
        }
        save();
    }

    /** Recrée le voile après une modification (couleur…). */
    public void changed(Portal portal) {
        removeVeil(portal.name);
        save();
    }

    // ---------------------------------------------------------------- boucle

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::run, 20L, 2L);
    }

    private void run() {
        tick += 2;
        boolean ensure = tick % 20 == 0;
        boolean fx = tick % 6 == 0;
        for (Portal p : portals.values()) {
            try {
                Location c = p.center();
                if (c == null) {
                    continue;
                }
                World w = c.getWorld();
                if (ensure && chunksLoaded(p, w)) {
                    ensureVeil(p, w);
                }
                if (fx) {
                    effects(p, c);
                }
                trigger(p, c);
            } catch (Throwable t) {
                plugin.getLogger().warning("Portail " + p.name + " : " + t);
            }
        }
    }

    private boolean chunksLoaded(Portal p, World w) {
        int x1 = p.worldX(0);
        int z1 = p.worldZ(0);
        int x2 = p.worldX(p.w - 1);
        int z2 = p.worldZ(p.w - 1);
        return w.isChunkLoaded(x1 >> 4, z1 >> 4) && w.isChunkLoaded(x2 >> 4, z2 >> 4);
    }

    private void trigger(Portal p, Location c) {
        double reach = Math.max(p.w, p.h) / 2.0 + 3;
        double reachSq = reach * reach;
        long now = System.currentTimeMillis();
        for (Player pl : c.getWorld().getPlayers()) {
            if (pl.getGameMode() == org.bukkit.GameMode.SPECTATOR || pl.isDead()) {
                continue;
            }
            Location l = pl.getLocation();
            if (l.distanceSquared(c) > reachSq || !p.contains(l)) {
                continue;
            }
            UUID id = pl.getUniqueId();
            if (busy.contains(id)) {
                continue;
            }
            Long until = cooldown.get(id);
            if (until != null && now < until && !pl.hasPermission("velaportal.bypass.cooldown")) {
                continue;
            }
            if (!pl.hasPermission("velaportal.use")) {
                if (until == null || now >= until) {
                    cooldown.put(id, now + 2000);
                    plugin.send(pl, "no-permission-use");
                }
                continue;
            }
            busy.add(id);
            cooldown.put(id, now + Math.max(0, plugin.getConfig().getLong("cooldown-ms", 3000)));
            if (plugin.getConfig().getBoolean("effects.sound", true)) {
                pl.playSound(l, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.2f);
            }
            teleporter.send(pl, p);
        }
    }

    // ---------------------------------------------------------------- voile coloré

    static DyeColor dye(String name) {
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return DyeColor.PURPLE;
        }
    }

    private void ensureVeil(Portal p, World w) {
        List<int[]> spans = p.spans();
        List<BlockDisplay> list = veils.get(p.name);
        boolean ok = list != null && list.size() == spans.size();
        if (ok) {
            for (BlockDisplay d : list) {
                if (!d.isValid()) {
                    ok = false;
                    break;
                }
            }
        }
        if (ok) {
            return;
        }
        removeVeil(p.name);
        Material glass = Material.matchMaterial(dye(p.color).name() + "_STAINED_GLASS");
        if (glass == null) {
            glass = Material.PURPLE_STAINED_GLASS;
        }
        final Material g = glass;
        List<BlockDisplay> fresh = new ArrayList<>();
        for (int[] s : spans) {
            int j = s[0];
            int len = s[2] - s[1] + 1;
            Location at = new Location(w, p.worldX(s[1]), p.oy + j, p.worldZ(s[1]));
            final boolean alongX = p.axis == 'X';
            BlockDisplay d = w.spawn(at, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(g.createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(1.5f);
                Vector3f scale = alongX ? new Vector3f(len, 1f, 0.12f) : new Vector3f(0.12f, 1f, len);
                Vector3f shift = alongX ? new Vector3f(0f, 0f, 0.44f) : new Vector3f(0.44f, 0f, 0f);
                e.setTransformation(new Transformation(shift, new AxisAngle4f(), scale, new AxisAngle4f()));
            });
            fresh.add(d);
        }
        veils.put(p.name, fresh);
    }

    private void removeVeil(String name) {
        List<BlockDisplay> list = veils.remove(name);
        if (list != null) {
            for (BlockDisplay d : list) {
                d.remove();
            }
        }
    }

    public void removeAllVeils() {
        for (String n : new ArrayList<>(veils.keySet())) {
            removeVeil(n);
        }
    }

    // ---------------------------------------------------------------- effets

    private void effects(Portal p, Location c) {
        World w = c.getWorld();
        boolean near = false;
        for (Player pl : w.getPlayers()) {
            if (pl.getLocation().distanceSquared(c) <= 40 * 40) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        if (plugin.getConfig().getBoolean("effects.particles", true)) {
            DyeColor dc = dye(p.color);
            Color col = dc.getColor();
            Particle.DustOptions dust = new Particle.DustOptions(col, 1.4f);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int k = 0; k < 10; k++) {
                int i = r.nextInt(p.w);
                int j = r.nextInt(p.h);
                if (!p.inside(i, j)) {
                    continue;
                }
                double x = p.worldX(i) + (p.axis == 'X' ? r.nextDouble() : 0.5);
                double z = p.worldZ(i) + (p.axis == 'X' ? 0.5 : r.nextDouble());
                w.spawnParticle(Particle.DUST, x, p.oy + j + r.nextDouble(), z, 1, 0.05, 0.05, 0.05, 0, dust);
            }
        }
        if (plugin.getConfig().getBoolean("effects.ambient-sound", true) && tick % 120 == 0) {
            w.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.35f, 1f);
        }
    }
}
