package com.bahikhaata.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hibernate must never modify the schema. Flyway owns it.
 *
 * <p>Task 1.4 asks us to confirm no profile sets {@code update} or {@code create-drop}.
 * Confirming it once by reading the files would be true today and unenforced tomorrow,
 * so this scans every configuration file in the module instead — including any profile
 * added later, which is the case a one-time check cannot cover.
 *
 * <p>{@code create-drop} in a system holding financial records would silently destroy an
 * invoice ledger on restart. {@code update} is the subtler hazard: it cannot drop or
 * rename columns, cannot transform data, and leaves no record of what any install
 * actually applied — the drift that killed Chromis POS, per design decision 4.
 */
class SchemaManagementPolicyTest {

    private static final List<String> PERMITTED = List.of("validate", "none");

    private static final Pattern DDL_AUTO = Pattern.compile(
            "(?m)^[^#!]*ddl-auto\\s*[:=]\\s*([A-Za-z-]+)");

    @Test
    @DisplayName("No configuration file permits Hibernate to modify the schema")
    void noProfileAllowsSchemaModification() throws IOException {
        List<Path> configFiles = configurationFiles();

        assertThat(configFiles)
                .as("expected at least the base application.properties to be found; "
                        + "if this is empty the scan is looking in the wrong place and "
                        + "would pass vacuously")
                .isNotEmpty();

        List<String> offences = new ArrayList<>();
        for (Path file : configFiles) {
            String contents = Files.readString(file);
            Matcher matcher = DDL_AUTO.matcher(contents);
            while (matcher.find()) {
                String value = matcher.group(1).toLowerCase(Locale.ROOT);
                if (!PERMITTED.contains(value)) {
                    offences.add(file + " sets ddl-auto=" + value);
                }
            }
        }

        assertThat(offences)
                .as("Hibernate must never modify the schema; Flyway owns it")
                .isEmpty();
    }

    private static List<Path> configurationFiles() throws IOException {
        List<Path> roots = Stream.of("src/main/resources", "src/test/resources")
                .map(Paths::get)
                .filter(Files::isDirectory)
                .toList();

        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(SchemaManagementPolicyTest::isSpringConfigurationFile)
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static boolean isSpringConfigurationFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("application")
                && (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml"));
    }
}
