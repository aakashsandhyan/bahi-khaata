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

import com.bahikhaata.contracts.CountOutcome;
import com.bahikhaata.contracts.UnpackingCarton;
import com.bahikhaata.contracts.UnpackingLine;
import java.util.List;
import java.util.UUID;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Opening cartons and saying what was inside them.
 *
 * <p>The first screen a non-technical person uses all day, which decides almost everything
 * about it.
 *
 * <p><strong>No word from the data model appears here.</strong> Not batch, not lot, not
 * allocation, FIFO or ledger. The vocabulary is box, delivery, item, count, and the price
 * printed on the pack. MRP stays, because it is on every Indian pack and every shopkeeper
 * already knows it.
 *
 * <p><strong>The scanner drives.</strong> Focus returns to the scan field after every action,
 * because a barcode scanner is a keyboard that types and presses Enter — if focus is anywhere
 * else, the scan lands in the wrong box or nowhere at all. Typing is only for a count that is
 * not one, and for the MRP.
 *
 * <p><strong>Built for the common carton, not the impressive one.</strong> In the first real
 * consignment 311 of 533 cartons held exactly one line. So the ordinary path is: scan the box,
 * scan the item, type the MRP, done — and anything that would add a step to that was rejected
 * however much it helped the sixty-line carton.
 *
 * <p><strong>Stopping is normal.</strong> A half-counted carton at closing time is not an error
 * state to be cleared; it is what the end of a shift looks like. Every count is recorded as it
 * is taken, so walking away loses nothing.
 */
public class UnpackingScreen {

    private static final Font HEADING = Font.font("System", FontWeight.BOLD, 26);
    private static final Font BIG = Font.font("System", FontWeight.BOLD, 40);
    private static final Font BODY = Font.font("System", 18);
    private static final Font SMALL = Font.font("System", 14);

    /** Loud enough to read across a room, which is where the operator usually is. */
    private static final String OK = "-fx-background-color:#1b5e20;-fx-text-fill:white;"
            + "-fx-padding:14;-fx-background-radius:6;";
    private static final String WARN = "-fx-background-color:#e65100;-fx-text-fill:white;"
            + "-fx-padding:14;-fx-background-radius:6;";
    private static final String STOP = "-fx-background-color:#b71c1c;-fx-text-fill:white;"
            + "-fx-padding:14;-fx-background-radius:6;";

    private final BackendClient backend;
    private final BorderPane root = new BorderPane();

    private final TextField scanField = new TextField();
    private final Label cartonLabel = new Label("Scan a box to start");
    private final Label message = new Label();
    private final VBox lineList = new VBox(8);
    private final Button finishButton = new Button("Box is done");
    private final Button leaveButton = new Button("Leave this box");

    private final Label mrpPrompt = new Label();
    private final TextField mrpField = new TextField();
    private final VBox mrpBox = new VBox(6);

    private UnpackingCarton carton;
    private List<UnpackingLine> lines = List.of();

    /** The item whose printed price is being asked for, or null when nothing is waiting. */
    private UnpackingLine awaitingMrpFor;

    /**
     * A code scanned off a pack that matched nothing, waiting for someone to say which line it
     * belongs to. Null when no such question is open.
     */
    private String untaggedCode;

    /** A code chosen for tagging whose price is still being asked for. */
    private String pendingTagCode;

    public UnpackingScreen(BackendClient backend) {
        this.backend = backend;
        build();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void build() {
        cartonLabel.setFont(HEADING);

        scanField.setFont(BIG);
        scanField.setPromptText("Scan here");
        scanField.setOnAction(event -> onScan(scanField.getText().trim()));

        message.setFont(BODY);
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setVisible(false);

        finishButton.setFont(BODY);
        finishButton.setDisable(true);
        finishButton.setOnAction(event -> finishCarton());

        // Scanning the wrong box must not trap anyone. Counts are saved as they are taken, so
        // leaving keeps every one of them and the box simply stays part-counted — which is a
        // normal state, not an unfinished job needing explanation.
        leaveButton.setFont(BODY);
        leaveButton.setDisable(true);
        leaveButton.setOnAction(event -> leaveCarton());

        Label hint = new Label("Scan the number on the box, then scan each item inside it.");
        hint.setFont(SMALL);

        mrpPrompt.setFont(BODY);
        mrpPrompt.setWrapText(true);
        mrpField.setFont(BIG);
        mrpField.setPromptText("₹ printed on the pack");
        mrpField.setOnAction(event -> onMrpEntered());
        mrpBox.getChildren().addAll(mrpPrompt, mrpField);
        mrpBox.setVisible(false);
        mrpBox.setManaged(false);

        VBox header = new VBox(6, cartonLabel, hint, scanField, mrpBox, message);
        header.setPadding(new Insets(16));

        lineList.setPadding(new Insets(16));

        HBox footer = new HBox(12, leaveButton, finishButton);
        footer.setPadding(new Insets(16));
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.setTop(header);
        root.setCenter(lineList);
        root.setBottom(footer);

        // The scanner is a keyboard. If focus drifts, its output goes nowhere.
        Platform.runLater(scanField::requestFocus);
    }

    private void onScan(String scanned) {
        scanField.clear();
        if (scanned.isBlank()) {
            return;
        }
        try {
            if (carton == null) {
                openCarton(scanned);
            } else {
                countItem(scanned);
            }
        } catch (BackendClient.RefusedException e) {
            // The backend's own sentence, written for a person. Paraphrasing it here would
            // drift out of step with the rule it describes.
            say(e.getMessage(), STOP);
        } catch (BackendUnavailableException e) {
            say("Cannot reach the system. Fetch someone before carrying on — nothing you scan"
                    + " now is being saved.", STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    private void openCarton(String trackingNumber) {
        List<UnpackingCarton> found = backend.cartonsByTracking(trackingNumber);
        if (found.isEmpty()) {
            say("That box is not part of any delivery here. Check the number, or set it aside.",
                    WARN);
            return;
        }
        carton = found.get(0);
        refreshLines();
        if (carton.finished()) {
            say("This box was already marked done. Scanning more will add to it.", WARN);
        } else {
            hideMessage();
        }
    }

    private void countItem(String code) {
        UnpackingLine match =
                lines.stream()
                        .filter(line -> line.code().equalsIgnoreCase(code))
                        .findFirst()
                        .orElse(null);

        if (match == null) {
            // Almost always this is not a stranger — it is one of the items on the sheet,
            // wearing the code its maker printed rather than the one the supplier's list uses.
            // The two never match, so someone holding the item has to say which line it is.
            // Once. After that the real code resolves by itself, here and in every later
            // delivery.
            askWhichItem(code);
            return;
        }

        if (match.outstanding() == 0) {
            say("All " + match.expected() + " of these are already counted. Anything more is"
                    + " extra — set it aside.", WARN);
            return;
        }

        if (match.needsMrp()) {
            // Nothing is recorded until the price is in. Counting first and asking after would
            // leave goods on the shelf with no printed price behind them the moment anyone
            // walked away mid-question — and that is the state this whole job exists to clear.
            askForMrp(match);
            return;
        }

        record(match, null);
    }

    /**
     * Asks for the price printed on the pack, once per item per delivery.
     *
     * <p>The only typing in the ordinary path. It is asked here, with the goods in hand, because
     * this is the one moment someone is holding the pack and can read it — afterwards it means
     * finding the item again.
     */
    private void askForMrp(UnpackingLine line) {
        awaitingMrpFor = line;
        mrpPrompt.setText("What is the MRP printed on " + shortName(line) + "?");
        mrpField.clear();
        mrpBox.setVisible(true);
        mrpBox.setManaged(true);
        hideMessage();
        Platform.runLater(mrpField::requestFocus);
    }

    private void onMrpEntered() {
        UnpackingLine line = awaitingMrpFor;
        if (line == null) {
            return;
        }
        Long paise = parseRupees(mrpField.getText());
        if (paise == null) {
            say("That does not look like a price. Type the number printed on the pack, like"
                    + " 249 or 249.50.", WARN);
            Platform.runLater(mrpField::requestFocus);
            return;
        }
        closeMrpPrompt();
        try {
            record(line, paise);
        } catch (BackendClient.RefusedException e) {
            say(e.getMessage(), STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    private void closeMrpPrompt() {
        awaitingMrpFor = null;
        mrpBox.setVisible(false);
        mrpBox.setManaged(false);
        mrpField.clear();
    }

    /**
     * Rupees as a person writes them, to paise. Returns null for anything that is not a price,
     * rather than guessing — a wrong MRP is worse than a missing one, because selling above the
     * printed price is unlawful.
     */
    private Long parseRupees(String text) {
        String cleaned = text == null ? "" : text.trim().replace(",", "").replace("\u20b9", "");
        if (!cleaned.matches("\\d+(\\.\\d{1,2})?")) {
            return null;
        }
        java.math.BigDecimal rupees = new java.math.BigDecimal(cleaned);
        if (rupees.signum() <= 0) {
            return null;
        }
        return rupees.movePointRight(2).longValueExact();
    }

    private void record(UnpackingLine match, Long mrpPaise) {
        // A code waiting to be tagged rides along with the count, so the mapping and the first
        // unit are recorded together rather than in two steps that could half-fail.
        String code = pendingTagCode;
        pendingTagCode = null;
        CountOutcome outcome =
                code == null
                        ? backend.count(match.lineId(), 1, mrpPaise)
                        : backend.tag(match.lineId(), code, 1, mrpPaise);
        refreshLines();
        sayCounted(match, outcome);
    }

    private void sayCounted(UnpackingLine match, CountOutcome outcome) {
        long left = Math.max(0, outcome.quantityExpected() - outcome.quantityCounted());
        if (left == 0) {
            say(shortName(match) + " — all " + outcome.quantityCounted() + " found.", OK);
        } else {
            say(shortName(match) + " — " + outcome.quantityCounted() + " of "
                    + outcome.quantityExpected() + ", " + left + " still to find.", OK);
        }
    }

    /**
     * Asks which of the outstanding items the scanned code belongs to.
     *
     * <p>Offered as the lines still to find in this box, largest first, because the thing in
     * someone's hand is far likelier to be one of many than the last of one.
     */
    private void askWhichItem(String code) {
        untaggedCode = code;
        lineList.getChildren().clear();

        Label prompt = new Label("Which of these is it? Code on the item: " + code);
        prompt.setFont(HEADING);
        prompt.setWrapText(true);
        lineList.getChildren().add(prompt);

        Label why = new Label(
                "The list uses the supplier's own reference, not the code printed on the item."
                        + " Tell it which one this is and it will remember.");
        why.setFont(SMALL);
        why.setWrapText(true);
        lineList.getChildren().add(why);

        lines.stream()
                .filter(line -> line.outstanding() > 0)
                .sorted((a, b) -> Long.compare(b.outstanding(), a.outstanding()))
                .forEach(line -> lineList.getChildren().add(choiceFor(line)));

        Button none = new Button("None of these — not on the sheet");
        none.setFont(BODY);
        none.setOnAction(event -> {
            untaggedCode = null;
            refreshLines();
            say("Put it to one side and tell the manager. Do not put it on the shelf.", WARN);
            Platform.runLater(scanField::requestFocus);
        });
        lineList.getChildren().add(none);
    }

    private Button choiceFor(UnpackingLine line) {
        Button choice = new Button(shortName(line) + "   (" + line.outstanding() + " to find)");
        choice.setFont(BODY);
        choice.setWrapText(true);
        choice.setMaxWidth(Double.MAX_VALUE);
        choice.setStyle("-fx-padding:12;-fx-background-radius:6;");
        choice.setOnAction(event -> tagChosen(line));
        return choice;
    }

    private void tagChosen(UnpackingLine line) {
        String code = untaggedCode;
        untaggedCode = null;
        if (code == null) {
            return;
        }
        try {
            if (line.needsMrp()) {
                pendingTagCode = code;
                askForMrp(line);
                return;
            }
            CountOutcome outcome = backend.tag(line.lineId(), code, 1, null);
            refreshLines();
            sayCounted(line, outcome);
        } catch (BackendClient.RefusedException e) {
            refreshLines();
            say(e.getMessage(), STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    private void leaveCarton() {
        carton = null;
        lines = List.of();
        untaggedCode = null;
        pendingTagCode = null;
        closeMrpPrompt();
        lineList.getChildren().clear();
        cartonLabel.setText("Scan a box to start");
        finishButton.setDisable(true);
        leaveButton.setDisable(true);
        say("Left the box as it is. Everything counted so far is saved. Scan another box.", OK);
        Platform.runLater(scanField::requestFocus);
    }

    private void finishCarton() {
        if (carton == null) {
            return;
        }
        try {
            backend.finishCarton(carton.boxId());
            long missing = lines.stream().mapToLong(UnpackingLine::outstanding).sum();
            carton = null;
            lines = List.of();
            untaggedCode = null;
            pendingTagCode = null;
            closeMrpPrompt();
            lineList.getChildren().clear();
            cartonLabel.setText("Scan a box to start");
            finishButton.setDisable(true);
            leaveButton.setDisable(true);
            if (missing > 0) {
                say("Box done, with " + missing + " item(s) not found. That has been recorded."
                        + " Scan the next box.", WARN);
            } else {
                say("Box done, everything found. Scan the next box.", OK);
            }
        } catch (BackendClient.RefusedException e) {
            say(e.getMessage(), STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    private void refreshLines() {
        lines = backend.linesIn(carton.boxId());
        cartonLabel.setText("Box " + carton.trackingNumber());
        finishButton.setDisable(false);
        leaveButton.setDisable(false);

        lineList.getChildren().clear();
        for (UnpackingLine line : lines) {
            lineList.getChildren().add(rowFor(line));
        }
    }

    private HBox rowFor(UnpackingLine line) {
        Label name = new Label(shortName(line));
        name.setFont(BODY);
        name.setWrapText(true);
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);

        Label count = new Label(line.counted() + " / " + line.expected());
        count.setFont(Font.font("System", FontWeight.BOLD, 22));

        // Done reads green from across the room; still-to-find stays plain. Nothing is red,
        // because nothing here is yet a mistake — a box in progress is just a box in progress.
        HBox row = new HBox(12, name, count);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setStyle(
                line.outstanding() == 0
                        ? "-fx-background-color:#e8f5e9;-fx-background-radius:6;"
                        : "-fx-background-color:#f5f5f5;-fx-background-radius:6;");
        return row;
    }

    /** Manifest names run to two hundred characters. A person needs the first few words. */
    private String shortName(UnpackingLine line) {
        String name = line.name();
        return name.length() <= 60 ? name : name.substring(0, 57) + "…";
    }

    private void say(String text, String style) {
        message.setText(text);
        message.setStyle(style);
        message.setVisible(true);
    }

    private void hideMessage() {
        message.setVisible(false);
    }
}
