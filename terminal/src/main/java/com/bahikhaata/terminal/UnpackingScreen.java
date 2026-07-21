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
import com.bahikhaata.contracts.DeliveryProgress;
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

    /**
     * A ceiling on what can be typed as an MRP: ₹5,00,000.
     *
     * <p>Generous — nothing this shop sells comes close — but low enough that a scanned barcode
     * or a slipped keystroke cannot pass as a price. MRP is the legal maximum a customer may be
     * charged, so a junk figure here is worse than none at all.
     */
    private static final long MOST_A_PRICE_CAN_BE = 500_000_00L;

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

    /**
     * What to do right now, in one line, always on screen.
     *
     * <p>Added after someone got stranded mid-carton: the screen had moved on to asking for a
     * price, and nothing said so. A person who has lost the thread cannot be expected to infer
     * the state from which controls happen to be visible.
     */
    private final Label nextStep = new Label();

    /**
     * How the whole delivery is going, kept on screen while a carton is open.
     *
     * <p>Someone working through five hundred cartons cannot tell how far they have got from
     * the carton in front of them, and asking is not something a screen should make them do.
     * Cartons and items rather than a percentage, because those are the things they can see on
     * the pallet.
     */
    private final Label deliveryLine = new Label();
    private final Label message = new Label();
    private final VBox lineList = new VBox(8);
    private final Button finishButton = new Button("Box is done");
    private final Button leaveButton = new Button("Leave this box");

    /**
     * Held down, in effect, while damaged items are being counted.
     *
     * <p>A toggle rather than a question after each scan, because damaged goods arrive in runs —
     * a crushed carton is rarely one item — and asking every time would make the common case
     * slower to spare the rare one. It stays on until switched off, and says so loudly, because
     * the failure that matters is forgetting it is on.
     */
    private final javafx.scene.control.ToggleButton damagedToggle =
            new javafx.scene.control.ToggleButton("Marking damaged");

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

    /**
     * The line tagged most recently, offered first next time.
     *
     * <p>Units of one product come out of a carton together, so the last answer is the best
     * guess at the next one.
     */
    private UUID lastTaggedLineId;

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

        damagedToggle.setFont(BODY);
        damagedToggle.selectedProperty().addListener((observable, was, isDamaged) -> {
            damagedToggle.setStyle(isDamaged ? WARN : "");
            if (isDamaged) {
                say("Marking items as damaged. They will be counted in, and priced lower"
                        + " later. Switch this off for undamaged items.", WARN);
            } else {
                hideMessage();
            }
            Platform.runLater(scanField::requestFocus);
        });

        nextStep.setFont(BODY);
        nextStep.setWrapText(true);
        nextStep.setStyle("-fx-text-fill:#1565c0;");

        deliveryLine.setFont(SMALL);
        deliveryLine.setWrapText(true);
        deliveryLine.setStyle("-fx-text-fill:#555;");

        mrpPrompt.setFont(BODY);
        mrpPrompt.setWrapText(true);
        mrpField.setFont(BIG);
        mrpField.setPromptText("₹ printed on the pack");
        mrpField.setOnAction(event -> onMrpEntered());
        mrpBox.getChildren().addAll(mrpPrompt, mrpField);
        mrpBox.setVisible(false);
        mrpBox.setManaged(false);

        VBox header =
                new VBox(6, cartonLabel, nextStep, deliveryLine, scanField, mrpBox, message);
        header.setPadding(new Insets(16));

        lineList.setPadding(new Insets(16));

        // Nothing to mark damaged, leave, or finish until a carton is open. Enabled controls
        // that do nothing are a way of asking someone to guess.
        damagedToggle.setDisable(true);

        HBox footer = new HBox(12, damagedToggle, leaveButton, finishButton);
        footer.setPadding(new Insets(16));
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.setTop(header);
        root.setCenter(lineList);
        root.setBottom(footer);

        setNextStep("Scan the number printed on the box.");
        showOverview();

        // The scanner is a keyboard. If focus drifts, its output goes nowhere.
        Platform.runLater(scanField::requestFocus);
    }

    /**
     * How every delivery stands, shown whenever no carton is open.
     *
     * <p>That is where someone stands between cartons, and it was blank. The question it answers
     * — how far have we got — is one people ask constantly and had no way to ask here.
     *
     * <p>The last column is the one that matters. Empty cartons are not the finish line: nothing
     * can be priced, labelled or sold until an MRP has been read off it, so that queue is what
     * actually stands between a pallet and the shelf.
     */
    private void showOverview() {
        lineList.getChildren().clear();

        List<DeliveryProgress> deliveries;
        try {
            deliveries = backend.deliveries();
        } catch (RuntimeException e) {
            // Not worth an error screen. Someone is here to scan a box, and they still can.
            return;
        }
        if (deliveries.isEmpty()) {
            return;
        }

        Label heading = new Label("Deliveries waiting to be unpacked");
        heading.setFont(HEADING);
        lineList.getChildren().add(heading);
        lineList.getChildren().add(overviewRow(
                "Delivery", "Boxes", "Items", "Waiting on a price", true, false));

        long boxesDone = 0;
        long boxesTotal = 0;
        long itemsFound = 0;
        long itemsTotal = 0;
        long waiting = 0;
        for (DeliveryProgress delivery : deliveries) {
            boolean finished = delivery.cartonsFinished() == delivery.cartonsTotal();
            lineList.getChildren().add(
                    overviewRow(
                            delivery.category(),
                            delivery.cartonsFinished() + " of " + delivery.cartonsTotal(),
                            delivery.unitsCounted() + " of " + delivery.unitsExpected(),
                            delivery.itemsWithoutMrp() == 0 ? "—"
                                    : String.valueOf(delivery.itemsWithoutMrp()),
                            false,
                            finished));
            boxesDone += delivery.cartonsFinished();
            boxesTotal += delivery.cartonsTotal();
            itemsFound += delivery.unitsCounted();
            itemsTotal += delivery.unitsExpected();
            waiting += delivery.itemsWithoutMrp();
        }

        lineList.getChildren().add(
                overviewRow(
                        "Everything",
                        boxesDone + " of " + boxesTotal,
                        itemsFound + " of " + itemsTotal,
                        waiting == 0 ? "—" : String.valueOf(waiting),
                        true,
                        false));
    }

    private HBox overviewRow(
            String name, String boxes, String items, String waiting, boolean bold,
            boolean finished) {
        Font font = bold ? Font.font("System", FontWeight.BOLD, 16) : Font.font("System", 16);

        Label nameLabel = new Label(name);
        nameLabel.setFont(font);
        nameLabel.setMinWidth(200);

        Label boxesLabel = new Label(boxes);
        boxesLabel.setFont(font);
        boxesLabel.setMinWidth(140);

        Label itemsLabel = new Label(items);
        itemsLabel.setFont(font);
        itemsLabel.setMinWidth(160);

        Label waitingLabel = new Label(waiting);
        waitingLabel.setFont(font);
        // Orange, because an item with no price is stock that cannot be sold — a queue, not a
        // statistic.
        if (!waiting.equals("—") && !bold) {
            waitingLabel.setStyle("-fx-text-fill:#e65100;");
        }

        HBox row = new HBox(12, nameLabel, boxesLabel, itemsLabel, waitingLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 10));
        if (finished) {
            row.setStyle("-fx-background-color:#e8f5e9;-fx-background-radius:6;");
        }
        return row;
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
        setNextStep("Scan each item in this box. Press \"Box is done\" when it is empty.");
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
        mrpPrompt.setText("MRP printed on " + shortName(line) + "?");
        mrpField.clear();
        mrpBox.setVisible(true);
        mrpBox.setManaged(true);
        hideMessage();

        // The list is cleared so nothing stale is left looking like it still wants a decision.
        // Only the item being asked about, and the way past it if the price is not there.
        lineList.getChildren().clear();
        Label asking = new Label(line.name());
        asking.setFont(BODY);
        asking.setWrapText(true);
        lineList.getChildren().add(asking);
        lineList.getChildren().add(noMrpButton());

        say("Type the price and press Enter.", OK);
        setNextStep("Type the MRP printed on the pack, then press Enter.");
        Platform.runLater(mrpField::requestFocus);
    }

    /**
     * The way past a missing price.
     *
     * <p>Liquidation goods often carry no printed MRP at all, and originally the count simply
     * waited for one — which stranded whoever was holding an item that would never have a price
     * on it. The unit is counted either way; it just cannot be sold until someone supplies a
     * figure, which is already how an unpriced product behaves.
     */
    private Button noMrpButton() {
        Button skip = new Button("No MRP printed on it — count it anyway");
        skip.setFont(BODY);
        skip.setMaxWidth(Double.MAX_VALUE);
        skip.setStyle("-fx-padding:12;-fx-background-radius:6;");
        skip.setOnAction(event -> {
            UnpackingLine line = awaitingMrpFor;
            if (line == null) {
                return;
            }
            closeMrpPrompt();
            try {
                record(line, null);
                say("Counted without a price. It cannot go on the shelf until someone finds"
                        + " one — the manager will see it on the list.", WARN);
            } catch (BackendClient.RefusedException e) {
                say(e.getMessage(), STOP);
            } finally {
                Platform.runLater(scanField::requestFocus);
            }
        });
        return skip;
    }

    private void onMrpEntered() {
        UnpackingLine line = awaitingMrpFor;
        if (line == null) {
            return;
        }
        String typed = mrpField.getText();
        if (looksLikeAScannedCode(typed)) {
            mrpField.clear();
            say("That is the barcode, not the price. Type the MRP printed on the pack — the"
                    + " rupee amount, like 249 or 249.50.", STOP);
            Platform.runLater(mrpField::requestFocus);
            return;
        }
        Long paise = parseRupees(typed);
        if (paise == null) {
            say("That does not look like a price. Type the number printed on the pack, like"
                    + " 249 or 249.50.", WARN);
            Platform.runLater(mrpField::requestFocus);
            return;
        }
        if (paise > MOST_A_PRICE_CAN_BE) {
            mrpField.clear();
            say("₹" + (paise / 100) + " is too large to be an MRP here. Check the pack and type"
                    + " the printed amount.", STOP);
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

    /**
     * Whether the typed figure is a barcode rather than a price.
     *
     * <p>It happens within minutes of real use: the field is focused, someone pulls the trigger
     * out of habit, and the scanner types thirteen digits and presses Enter. An LED batten was
     * recorded at an MRP of ₹6,295,047,541 that way.
     *
     * <p>Caught by shape rather than size alone. Retail barcodes are 8, 12, 13 or 14 digits with
     * no decimal point, and no price in this shop looks remotely like that — so the shape says
     * plainly what happened, and the message can too.
     */
    private boolean looksLikeAScannedCode(String text) {
        String digits = text == null ? "" : text.trim().replace(",", "");
        return digits.matches("\\d{8}|\\d{12,14}");
    }

    private void record(UnpackingLine match, Long mrpPaise) {
        // A code waiting to be tagged rides along with the count, so the mapping and the first
        // unit are recorded together rather than in two steps that could half-fail.
        String code = pendingTagCode;
        pendingTagCode = null;
        boolean damaged = damagedToggle.isSelected();
        CountOutcome outcome =
                code == null
                        ? backend.count(match.lineId(), 1, mrpPaise, damaged)
                        : backend.tag(match.lineId(), code, 1, mrpPaise, damaged);
        refreshLines();
        sayCounted(match, outcome);
    }

    private void sayCounted(UnpackingLine match, CountOutcome outcome) {
        // Damaged counts keep the warning colour, so a toggle left on is visible on every
        // single scan rather than only when it was switched.
        String mark = damagedToggle.isSelected() ? " (damaged)" : "";
        String style = damagedToggle.isSelected() ? WARN : OK;
        long left = Math.max(0, outcome.quantityExpected() - outcome.quantityCounted());
        if (left == 0) {
            say(shortName(match) + mark + " — all " + outcome.quantityCounted() + " found.",
                    style);
        } else {
            say(shortName(match) + mark + " — " + outcome.quantityCounted() + " of "
                    + outcome.quantityExpected() + ", " + left + " still to find.", style);
        }
    }

    /**
     * Asks which of the outstanding items the scanned code belongs to.
     *
     * <p>Offered as the lines still to find in this box, largest first, because the thing in
     * someone's hand is far likelier to be one of many than the last of one.
     */
    private void askWhichItem(String code) {
        List<UnpackingLine> outstanding =
                lines.stream().filter(line -> line.outstanding() > 0).toList();

        // On returns the sticker covers the printed barcode, so every unit scans as a code
        // nothing has seen before and this question would otherwise be asked on every single
        // item. Where one thing is left to find, there is only one answer, and asking for it is
        // just a tap standing between someone and the next item. 310 of 531 unfinished cartons
        // in this consignment are in exactly that state.
        if (outstanding.size() == 1) {
            untaggedCode = code;
            tagChosen(outstanding.get(0));
            return;
        }

        untaggedCode = code;
        setNextStep("Tap the item you are holding.");
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

        // The item just tagged comes first: units of one product arrive together, so the next
        // scan is far likelier to be another of the same than anything else in the carton.
        outstanding.stream()
                .sorted(
                        java.util.Comparator
                                .comparing((UnpackingLine line) ->
                                        !line.lineId().equals(lastTaggedLineId))
                                .thenComparing(line -> -line.outstanding()))
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
        lastTaggedLineId = line.lineId();
        if (code == null) {
            return;
        }
        try {
            if (line.needsMrp()) {
                pendingTagCode = code;
                askForMrp(line);
                return;
            }
            CountOutcome outcome =
                    backend.tag(line.lineId(), code, 1, null, damagedToggle.isSelected());
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
        damagedToggle.setSelected(false);
        carton = null;
        lines = List.of();
        untaggedCode = null;
        pendingTagCode = null;
        closeMrpPrompt();
        lineList.getChildren().clear();
        cartonLabel.setText("Scan a box to start");
        finishButton.setDisable(true);
        leaveButton.setDisable(true);
        damagedToggle.setDisable(true);
        setNextStep("Scan the number printed on the next box.");
        showOverview();
        say("Left the box as it is. Everything counted so far is saved. Scan another box.", OK);
        Platform.runLater(scanField::requestFocus);
    }

    private void finishCarton() {
        if (carton == null) {
            return;
        }
        try {
            backend.finishCarton(carton.boxId());
            damagedToggle.setSelected(false);
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
            damagedToggle.setDisable(true);
            setNextStep("Scan the number printed on the next box.");
            showOverview();
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

    private void refreshDelivery() {
        if (carton == null) {
            deliveryLine.setText("");
            return;
        }
        try {
            var progress = backend.deliveryProgress(carton.lotId());
            String waiting =
                    progress.itemsWithoutMrp() == 0
                            ? ""
                            : "   ·   " + progress.itemsWithoutMrp() + " waiting on a price";
            deliveryLine.setText(
                    progress.category()
                            + "   ·   boxes "
                            + progress.cartonsFinished()
                            + " done, "
                            + progress.cartonsStarted()
                            + " open, "
                            + progress.cartonsNotStarted()
                            + " untouched   ·   items "
                            + progress.unitsCounted()
                            + " of "
                            + progress.unitsExpected()
                            + waiting);
        } catch (RuntimeException e) {
            // Never worth interrupting someone's counting over. The number simply goes blank.
            deliveryLine.setText("");
        }
    }

    private void refreshLines() {
        lines = backend.linesIn(carton.boxId());
        cartonLabel.setText("Box " + carton.trackingNumber());
        finishButton.setDisable(false);
        leaveButton.setDisable(false);
        damagedToggle.setDisable(false);
        setNextStep("Scan the next item, or press \"Box is done\".");

        lineList.getChildren().clear();
        for (UnpackingLine line : lines) {
            lineList.getChildren().add(rowFor(line));
        }
        refreshDelivery();
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

    private void setNextStep(String text) {
        nextStep.setText("→ " + text);
    }
}
