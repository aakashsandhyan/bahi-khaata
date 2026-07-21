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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No word from the data model may reach the operator.
 *
 * <p>The unpacking screen is run all day by people who do not work in software, and the design
 * makes one promise about it: the words on it are the shop's, not the schema's. Someone holding
 * a kettle needs to be told it is not on the sheet for this box — not that no expected line
 * matches, and certainly not that a batch has no allocation.
 *
 * <p>This is a test rather than a review note because the wording is exactly the thing that
 * erodes. A developer debugging a real problem reaches for the word they have in their head,
 * and it ships.
 *
 * <p>Only user-facing string literals are examined. Comments explain the code to developers and
 * may name whatever they like; identifiers are the code's own business.
 */
class OperatorVocabularyTest {

    /** Words that mean something precise to this codebase and nothing to a person unpacking. */
    private static final List<String> SCHEMA_WORDS =
            List.of(
                    "batch", "lot", "ledger", "allocation", "allocated", "fifo", "cogs",
                    "expected line", "sku", "entity", "uuid", "null", "transaction",
                    "repository", "endpoint", "http", "json");

    @Test
    @DisplayName("The unpacking screen speaks the shop's language, not the schema's")
    void screenUsesNoSchemaVocabulary() throws IOException {
        List<String> offences = new ArrayList<>();

        for (String literal : userFacingStringsIn(
                Path.of("src/main/java/com/bahikhaata/terminal/UnpackingScreen.java"))) {
            String lower = literal.toLowerCase(Locale.ROOT);
            for (String word : SCHEMA_WORDS) {
                if (lower.matches(".*\\b" + Pattern.quote(word) + "\\b.*")) {
                    offences.add("\"" + literal + "\" contains \"" + word + "\"");
                }
            }
        }

        assertThat(offences)
                .as(
                        "these strings reach someone unpacking cartons, who has no idea what"
                                + " these words mean; say it in the words they would use")
                .isEmpty();
    }

    @Test
    @DisplayName("Nothing tells an operator to set real goods aside instead of counting them")
    void goodsAreCountedNotSetAside() throws IOException {
        // More arriving than the sheet promised is a fact about the delivery. Telling someone
        // to put it in a corner leaves real stock off the books and the surplus invisible,
        // which is the opposite of what counting is for — and it is what this screen said until
        // a real carton produced a second tiffin box.
        // Two things genuinely belong in a corner: goods nobody can put a name to, and a
        // carton that is not part of any delivery here. Everything else that arrived should be
        // counted.
        List<String> unidentifiable =
                List.of("not on the sheet", "not part of any delivery");

        List<String> offences =
                userFacingStringsIn(
                                Path.of("src/main/java/com/bahikhaata/terminal/UnpackingScreen.java"))
                        .stream()
                        .filter(line -> line.toLowerCase(Locale.ROOT).contains("set it aside"))
                        .filter(
                                line ->
                                        unidentifiable.stream()
                                                .noneMatch(allowed ->
                                                        line.toLowerCase(Locale.ROOT)
                                                                .contains(allowed)))
                        .toList();

        assertThat(offences)
                .as("goods that arrived should be recorded; only things nobody can identify"
                        + " belong in a corner")
                .isEmpty();
    }

    @Test
    @DisplayName("MRP survives the vocabulary rule, because every Indian pack carries it")
    void mrpIsAllowed() throws IOException {
        assertThat(userFacingStringsIn(
                        Path.of("src/main/java/com/bahikhaata/terminal/UnpackingScreen.java")))
                .as("MRP is not jargon in an Indian shop; it is printed on the goods")
                .anyMatch(s -> s.contains("MRP"));
    }

    /**
     * String literals excluding CSS, which is styling rather than speech, and excluding short
     * fragments that carry no sentence.
     */
    private List<String> userFacingStringsIn(Path source) throws IOException {
        String code = Files.readString(source, StandardCharsets.UTF_8);
        // Strip comments first: they are for developers and may say "batch" freely.
        code = code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

        List<String> literals = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").matcher(code);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.startsWith("-fx-") || value.length() < 4 || !value.contains(" ")) {
                continue;
            }
            literals.add(value);
        }
        return literals;
    }
}
