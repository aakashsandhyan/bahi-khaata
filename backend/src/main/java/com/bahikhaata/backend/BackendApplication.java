package com.bahikhaata.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        ensureDatabaseDirectoryExists();
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * The SQLite driver creates a missing database file but not a missing parent
     * directory — it fails instead. Creating it here keeps a first run on a fresh
     * machine from needing a manual step, which task 1.13 treats as a defect.
     */
    private static void ensureDatabaseDirectoryExists() {
        String configured = System.getProperty("bahikhaata.db.path", "data/bahi-khaata.db");
        Path parent = Paths.get(configured).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create database directory " + parent + ". "
                            + "The backend cannot start without somewhere to put the database.",
                    e);
        }
    }
}
