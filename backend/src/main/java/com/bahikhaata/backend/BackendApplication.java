/*
 * bahi-khaata — point of sale for Bachat Bazaar
 * Copyright (C) 2026 Aakash Sandhyan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.bahikhaata.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling makes the @Scheduled label-print poller actually run. Without it the poll never
// fired, so held labels only ever printed when someone pressed "Print pending now" (a manual flush).
@SpringBootApplication
@EnableScheduling
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
