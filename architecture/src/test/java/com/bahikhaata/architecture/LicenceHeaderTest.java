/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Java source carries the AGPL notice.
 *
 * <p>Headers are added once and then rot: a file created next week has no reason to
 * acquire one, and nobody notices until a licence audit or a contribution dispute. Task
 * 1.11 put headers on the codebase while it was 26 files precisely because retrofitting
 * them across hundreds is a chore that gets skipped — this keeps that from being needed
 * twice.
 *
 * <p>The check is textual rather than structural because that is what a licence audit
 * looks at. It does not care where the notice sits, only that it is present.
 */
class LicenceHeaderTest {

    /** Distinctive enough that a stray mention in prose will not satisfy it. */
    private static final String NOTICE = "GNU Affero General Public License";

    private static final String COPYRIGHT = "Copyright (C)";

    @Test
    @DisplayName("Every Java source carries the AGPL notice and a copyright line")
    void everySourceFileIsLicensed() throws IOException {
        List<Path> sources = javaSources();

        assertThat(sources)
                .as("no Java sources found — the scan is looking in the wrong place and "
                        + "would pass having checked nothing")
                .isNotEmpty();

        List<String> unlicensed = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            if (!text.contains(NOTICE) || !text.contains(COPYRIGHT)) {
                unlicensed.add(source.toString());
            }
        }

        assertThat(unlicensed)
                .as("AGPL notice missing — add the header block from any existing source")
                .isEmpty();
    }

    @Test
    @DisplayName("The licence text itself is present and is actually AGPL v3")
    void licenceFileIsPresent() throws IOException {
        Path licence = repositoryRoot().resolve("LICENSE");

        assertThat(Files.exists(licence)).as("LICENSE at the repository root").isTrue();

        String text = Files.readString(licence);
        assertThat(text).contains("GNU AFFERO GENERAL PUBLIC LICENSE");
        assertThat(text).contains("Version 3, 19 November 2007");
        // The clause that distinguishes AGPL from plain GPL: interaction over a network
        // triggers the obligation to publish modifications. That closure is the reason
        // this licence was chosen, so verify the file actually contains it.
        assertThat(text).contains("Remote Network Interaction");
    }

    /** Gradle runs tests with the module directory as working directory. */
    private static Path repositoryRoot() {
        return Paths.get("..").toAbsolutePath().normalize();
    }

    private static List<Path> javaSources() throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repositoryRoot())) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    // Generated output is not source and is not committed.
                    .filter(path -> !path.toString().contains("/build/"))
                    .forEach(found::add);
        }
        return found;
    }
}
