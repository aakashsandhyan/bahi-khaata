/*
 * bahi-khaata — point of sale for Bachat Baazar
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
package com.bahikhaata.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every control the screen builds must actually reach the layout.
 *
 * <p>Exists because two did not. A field was declared, styled and updated on every action, and
 * never added to any container — so the code read as though it worked, compiled, passed, and
 * showed nothing. The screenshot that revealed it came from a person using the app, which is
 * far too late for a fault a compiler cannot see and a reader will not.
 *
 * <p>Crude on purpose: it checks a name appears somewhere inside a container's construction. That
 * catches the mistake that actually happened — declaring a control and forgetting it — without
 * pretending to be a layout test, which would need a display and a robot.
 */
class ScreenWiringTest {

    private static final Path SCREEN =
            Path.of("src/main/java/com/bahikhaata/terminal/UnpackingScreen.java");

    @Test
    @DisplayName("Every Label, TextField and Button field is placed in the layout")
    void everyControlReachesTheLayout() throws IOException {
        String code = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String containers = containerArguments(code);

        Matcher declarations =
                Pattern.compile(
                                "private final (?:javafx\\.scene\\.control\\.)?"
                                        + "(Label|TextField|Button|ToggleButton|VBox|HBox)"
                                        + "\\s+(\\w+)\\s*=")
                        .matcher(code);

        while (declarations.find()) {
            String name = declarations.group(2);
            assertThat(containers)
                    .as(
                            "%s is built and updated but never added to a container, so it exists"
                                    + " everywhere except on screen",
                            name)
                    .contains(name);
        }
    }

    /** Everything passed to a layout container or added to one, as one blob of text. */
    private String containerArguments(String code) {
        StringBuilder placed = new StringBuilder();
        Matcher constructed =
                Pattern.compile("new (?:VBox|HBox|BorderPane|StackPane)\\(([^;]*?)\\)")
                        .matcher(code);
        while (constructed.find()) {
            placed.append(constructed.group(1)).append(' ');
        }
        Matcher added = Pattern.compile("getChildren\\(\\)\\.(?:add|addAll|setAll)\\(([^;]*?)\\)")
                .matcher(code);
        while (added.find()) {
            placed.append(added.group(1)).append(' ');
        }
        Matcher assigned = Pattern.compile("set(?:Top|Bottom|Center|Left|Right|Graphic)\\((\\w+)\\)")
                .matcher(code);
        while (assigned.find()) {
            placed.append(assigned.group(1)).append(' ');
        }
        return placed.toString();
    }
}
