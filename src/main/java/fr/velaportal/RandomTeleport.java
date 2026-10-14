package fr.velaportal;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Recherche d'un point sûr aléatoire dans le monde cible, puis téléportation. */
final class RandomTeleport {

    private static final Set<Material> UNSAFE = Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.COBWEB,
            Material.BEDROCK, Material.LAVA, Material.WATER, Material.BUBBLE_COLUMN);

    private final VelaPortalPlugin plugin;

    RandomTeleport(VelaPortalPlugin plugin) {
        this.plugin = plugin;
    }

    void send(Player p, Portal portal) {
        String worldName = portal.target.isBlank() ? plugin.getConfig().getString("target-world", "construction") : portal.target;
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            plugin.send(p, "world-missing", "world", worldName);
            return;
        }
        plugin.send(p, "searching");
        int attempts = Math.max(1, plugin.getConfig().getInt("random.attempts", 15));
        search(w, attempts, loc -> {
            if (!p.isOnline()) {
                plugin.portals().release(p.getUniqueId());
                return;
            }
            if (loc == null) {
                plugin.portals().release(p.getUniqueId());
                plugin.send(p, "no-safe-spot");
                return;
            }
            loc.setYaw(p.getLocation().getYaw());
            loc.setPitch(p.getLocation().getPitch());
            p.teleportAsync(loc).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.portals().release(p.getUniqueId());
                if (ok && p.isOnline()) {
                    arrival(p, w);
                }
            }));
        });
    }

    private void arrival(Player p, World w) {
        if (plugin.getConfig().getBoolean("effects.sound", true)) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }
        if (plugin.getConfig().getBoolean("effects.particles", true)) {
            w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.2);
        }
        Location l = p.getLocation();
        plugin.send(p, "arrived", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ(), "world", w.getName());
    }

    private void search(World w, int tries, Consumer<Location> done) {
        if (tries <= 0) {
            done.accept(null);
            return;
        }
        double cx = plugin.getConfig().getDouble("random.center-x", 0);
        double cz = plugin.getConfig().getDouble("random.center-z", 0);
        double min = Math.max(0, plugin.getConfig().getDouble("random.min-radius", 200));
        double max = Math.max(min + 1, plugin.getConfig().getDouble("random.max-radius", 3000));
        WorldBorder b = w.getWorldBorder();
        if (plugin.getConfig().getBoolean("random.use-world-border", true) && b.getSize() < 5.9e7) {
            cx = b.getCenter().getX();
            cz = b.getCenter().getZ();
            max = Math.min(max, b.getSize() / 2.0 - 8);
            min = Math.min(min, max / 2.0);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = Math.sqrt(r.nextDouble(min * min, max * max));
        final int x = (int) Math.floor(cx + Math.cos(angle) * dist);
        final int z = (int) Math.floor(cz + Math.sin(angle) * dist);

        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
            Location l = safe(w, x, z);
            if (l != null) {
                done.accept(l);
            } else {
                search(w, tries - 1, done);
            }
        }));
    }

    private Location safe(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= w.getMinHeight() || y + 2 >= w.getMaxHeight()) {
            return null;
        }
        Block ground = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        if (ground.isLiquid() || UNSAFE.contains(ground.getType()) || !ground.getType().isSolid()) {
            return null;
        }
        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()
                || UNSAFE.contains(feet.getType()) || UNSAFE.contains(head.getType())) {
            return null;
        }
        Location l = new Location(w, x + 0.5, y + 1, z + 0.5);
        if (!w.getWorldBorder().isInside(l)) {
            return null;
        }
        return l;
    }
}
