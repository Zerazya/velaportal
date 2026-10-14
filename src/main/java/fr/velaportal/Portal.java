package fr.velaportal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Un portail : une forme arrondie dans un plan vertical, avec son cadre. */
public final class Portal {

    public final String name;
    public String world;
    /** Coin (cellule 0,0) du portail. */
    public int ox, oy, oz;
    /** 'X' : le portail s'étend le long de l'axe X (normale Z) ; 'Z' : le long de l'axe Z (normale X). */
    public char axis = 'X';
    public int w = 9;
    public int h = 13;
    public double roundness = 4.0;
    public String color = "PURPLE";
    public String target = "";

    private boolean[][] grid;

    public Portal(String name) {
        this.name = name;
    }

    // ---------------------------------------------------------------- géométrie

    private boolean[][] grid() {
        if (grid == null) {
            grid = new boolean[w][h];
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    double u = (i + 0.5 - w / 2.0) / (w / 2.0);
                    double v = (j + 0.5 - h / 2.0) / (h / 2.0);
                    grid[i][j] = Math.pow(Math.abs(u), roundness) + Math.pow(Math.abs(v), roundness) <= 1.0;
                }
            }
        }
        return grid;
    }

    public boolean inside(int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < h && grid()[i][j];
    }

    public int worldX(int i) {
        return axis == 'X' ? ox + i : ox;
    }

    public int worldZ(int i) {
        return axis == 'X' ? oz : oz + i;
    }

    /** Cellules du cadre {i, j} (indices pouvant valoir -1 ou w/h). */
    public List<int[]> frameCells() {
        List<int[]> out = new ArrayList<>();
        for (int i = -1; i <= w; i++) {
            for (int j = -1; j <= h; j++) {
                if (inside(i, j)) {
                    continue;
                }
                boolean near = false;
                for (int di = -1; di <= 1 && !near; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        if (inside(i + di, j + dj)) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) {
                    out.add(new int[]{i, j});
                }
            }
        }
        return out;
    }

    /** Segments horizontaux continus de l'intérieur : {j, i0, i1}. */
    public List<int[]> spans() {
        List<int[]> out = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            int start = -1;
            for (int i = 0; i <= w; i++) {
                boolean in = inside(i, j);
                if (in && start < 0) {
                    start = i;
                } else if (!in && start >= 0) {
                    out.add(new int[]{j, start, i - 1});
                    start = -1;
                }
            }
        }
        return out;
    }

    public Location center() {
        World wd = Bukkit.getWorld(world);
        if (wd == null) {
            return null;
        }
        double cx = axis == 'X' ? ox + w / 2.0 : ox + 0.5;
        double cz = axis == 'X' ? oz + 0.5 : oz + w / 2.0;
        return new Location(wd, cx, oy + h / 2.0, cz);
    }

    /** Vrai si la position (pieds du joueur) traverse la surface du portail. */
    public boolean contains(Location l) {
        double u = axis == 'X' ? l.getX() - ox : l.getZ() - oz;
        double d = axis == 'X' ? l.getZ() - (oz + 0.5) : l.getX() - (ox + 0.5);
        if (Math.abs(d) > 0.75 || u < -0.3 || u > w + 0.3) {
            return false;
        }
        int i = (int) Math.floor(u);
        int j0 = (int) Math.floor(l.getY() - oy);
        int j1 = (int) Math.floor(l.getY() + 1.0 - oy);
        return inside(i, j0) || inside(i, j1) || inside(i - 1, j0) || inside(i + 1, j0);
    }

    // ---------------------------------------------------------------- sauvegarde

    public void save(YamlConfiguration y) {
        String p = "portals." + name + ".";
        y.set(p + "world", world);
        y.set(p + "ox", ox);
        y.set(p + "oy", oy);
        y.set(p + "oz", oz);
        y.set(p + "axis", String.valueOf(axis));
        y.set(p + "width", w);
        y.set(p + "height", h);
        y.set(p + "roundness", roundness);
        y.set(p + "color", color);
        y.set(p + "target", target);
    }

    public static Portal load(String name, ConfigurationSection s) {
        Portal p = new Portal(name);
        p.world = s.getString("world", "world");
        p.ox = s.getInt("ox");
        p.oy = s.getInt("oy");
        p.oz = s.getInt("oz");
        p.axis = "Z".equalsIgnoreCase(s.getString("axis", "X")) ? 'Z' : 'X';
        p.w = Math.max(3, s.getInt("width", 9));
        p.h = Math.max(4, s.getInt("height", 13));
        p.roundness = Math.max(2.0, s.getDouble("roundness", 4.0));
        p.color = s.getString("color", "PURPLE");
        p.target = s.getString("target", "");
        return p;
    }
}
