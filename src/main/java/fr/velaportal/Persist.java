package fr.velaportal;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Écriture de fichiers hors du thread principal, de façon atomique (fichier temporaire puis déplacement). */
final class Persist {

    private final JavaPlugin plugin;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VelaPortal-IO");
        t.setDaemon(true);
        return t;
    });

    Persist(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void write(File file, String data) {
        io.execute(() -> {
            Path target = file.toPath();
            Path tmp = target.resolveSibling(file.getName() + ".tmp");
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicFailed) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Impossible d'écrire " + file.getName() + " : " + ex.getMessage());
            }
        });
    }

    void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
