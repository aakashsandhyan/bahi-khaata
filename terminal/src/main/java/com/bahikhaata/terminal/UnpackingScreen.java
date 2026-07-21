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
import com.bahikhaata.contracts.LearntCode;
import com.bahikhaata.contracts.SuggestedMrp;
import com.bahikhaata.contracts.UnpackingCarton;
import com.bahikhaata.contracts.UnpackingLine;
import java.util.List;
import java.util.UUID;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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

    /**
     * Ordinary text, stated rather than inherited.
     *
     * <p>JavaFX derives its default text colour from the background through a ladder, so a
     * container styled transparent hands its children white text. Saying the colour outright
     * costs nothing and cannot be undone by a parent's styling.
     */
    private static final String INK = "-fx-text-fill:#212121;";

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
    private final Button undoButton = new Button("Undo that");

    /**
     * What condition scanned items are being recorded in.
     *
     * <p>Three buttons rather than a switch, because a switch cannot say three things and a
     * question after every scan would slow the ordinary case to spare the rare one. Damaged
     * goods arrive in runs — a crushed carton is rarely one item — so the setting stays until
     * changed, and every confirmation repeats it back, since the failure that matters is
     * forgetting it is set.
     */
    private final ToggleGroup conditionChoice = new ToggleGroup();

    private final ToggleButton goodButton = new ToggleButton("Fine");
    private final ToggleButton damagedButton = new ToggleButton("Damaged — sells cheaper");
    private final ToggleButton unusableButton = new ToggleButton("Broken — cannot sell");

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
     * The last thing counted, so it can be taken back.
     *
     * <p>A wrong scan is noticed within seconds — the name on screen is not the thing in your
     * hand. Without this the mistake stands forever and the only remedy is remembering it,
     * which is not a remedy. Kept for the last scan only, because that is when it is caught;
     * anything older is a job for a manager and a fuller screen.
     */
    private LastCount lastCount;

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

        undoButton.setFont(BODY);
        undoButton.setDisable(true);
        undoButton.setOnAction(event -> undoLastCount());

        for (ToggleButton button : List.of(goodButton, damagedButton, unusableButton)) {
            button.setFont(BODY);
            button.setToggleGroup(conditionChoice);
            button.setDisable(true);
        }
        goodButton.setSelected(true);
        conditionChoice.selectedToggleProperty().addListener(
                (observable, was, now) -> {
                    // Nothing selected would silently mean "fine", which is the one thing that
                    // must never be assumed.
                    if (now == null) {
                        goodButton.setSelected(true);
                        return;
                    }
                    damagedButton.setStyle(damagedButton.isSelected() ? WARN : "");
                    unusableButton.setStyle(unusableButton.isSelected() ? STOP : "");
                    if (damagedButton.isSelected()) {
                        say("Marking items damaged. They are counted in and sold cheaper; the"
                                + " manager decides for how much.", WARN);
                    } else if (unusableButton.isSelected()) {
                        say("Marking items broken. They are recorded as having arrived, and"
                                + " cannot be sold at any price.", STOP);
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
        setConditionButtonsDisabled(true);

        HBox footer =
                new HBox(12, goodButton, damagedButton, unusableButton, undoButton, leaveButton,
                        finishButton);
        footer.setPadding(new Insets(16));
        footer.setAlignment(Pos.CENTER_RIGHT);

        // Scrolled, because a carton can hold sixty lines and an unscrolled list simply grows
        // past the bottom of the window — taking the footer with it, so there was no way to
        // finish or leave a box once the list was long enough.
        ScrollPane scroll = new ScrollPane(lineList);
        scroll.setFitToWidth(true);
        // A concrete colour, never "transparent". JavaFX derives text colour from -fx-background
        // through a ladder(), so a transparent background resolves to white text — which turned
        // the whole delivery overview invisible on a light page.
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");
        // Without this the pane insists on being as tall as its contents, which is the whole
        // problem restated.
        scroll.setMinHeight(0);
        // The scanner is a keyboard: a scroll pane that takes focus swallows the next scan.
        scroll.setFocusTraversable(false);

        root.setTop(header);
        root.setCenter(scroll);
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
        heading.setStyle(INK);
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
        nameLabel.setStyle(INK);

        Label boxesLabel = new Label(boxes);
        boxesLabel.setFont(font);
        boxesLabel.setMinWidth(140);
        boxesLabel.setStyle(INK);

        Label itemsLabel = new Label(items);
        itemsLabel.setFont(font);
        itemsLabel.setMinWidth(160);
        itemsLabel.setStyle(INK);

        Label waitingLabel = new Label(waiting);
        waitingLabel.setFont(font);
        // Orange, because an item with no price is stock that cannot be sold — a queue, not a
        // statistic.
        waitingLabel.setStyle(waiting.equals("—") || bold ? INK : "-fx-text-fill:#e65100;");

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
        // Asked of the backend rather than compared against the manifest's reference, because a
        // product gathers codes: the supplier's reference, the maker's printed barcode, and a
        // returns sticker per unit. Matching only the first would send someone back to "which
        // item is this" for a code already known.
        UnpackingLine match =
                backend.resolveInCarton(carton.boxId(), code)
                        .orElseGet(
                                () ->
                                        lines.stream()
                                                .filter(line -> line.code().equalsIgnoreCase(code))
                                                .findFirst()
                                                .orElse(null));

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
            // Two very different situations reach here. Either this really is another of
            // something already complete — genuinely extra — or the code is on the wrong item
            // and keeps landing here, which is a dead end unless the code can be given up.
            say("The sheet said " + match.expected() + " of these, and that many are counted: "
                    + shortName(match), WARN);
            offerToReleaseCode(code, match);
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
        offerLookedUpPrice(line);
    }

    /**
     * Asks what the goods are listed at, and offers it.
     *
     * <p>Off the JavaFX thread and never waited for. The pack is in someone's hand and they can
     * simply read it — this is for the ones where the figure has rubbed off, or was never
     * printed. If the answer arrives after they have typed, it is dropped rather than shown,
     * because by then it is an answer to a question nobody is asking any more.
     */
    private void offerLookedUpPrice(UnpackingLine line) {
        javafx.concurrent.Task<SuggestedMrp> ask =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected SuggestedMrp call() {
                        return backend.suggestedMrp(line.lineId());
                    }
                };
        ask.setOnSucceeded(
                event -> {
                    SuggestedMrp suggestion = ask.getValue();
                    if (!suggestion.found() || awaitingMrpFor != line) {
                        return;
                    }
                    lineList.getChildren().add(useSuggestionButton(line, suggestion));
                });
        // A failed lookup says nothing at all. Someone is holding the goods; the network is not
        // their problem and must never become their problem.
        ask.setOnFailed(event -> { });

        Thread worker = new Thread(ask, "mrp-suggestion");
        worker.setDaemon(true);
        worker.start();
    }

    private Button useSuggestionButton(UnpackingLine line, SuggestedMrp suggestion) {
        Button use =
                new Button(
                        "Listed at ₹" + suggestion.pricePaise() / 100
                                + " — use that, if the pack agrees");
        use.setFont(BODY);
        use.setMaxWidth(Double.MAX_VALUE);
        use.setStyle("-fx-padding:12;-fx-background-radius:6;-fx-background-color:#e3f2fd;");
        use.setOnAction(
                event -> {
                    if (awaitingMrpFor != line) {
                        return;
                    }
                    closeMrpPrompt();
                    try {
                        record(line, suggestion.pricePaise(), true);
                        say("Used the listed price. It is recorded as an estimate — if the pack"
                                + " says otherwise, the pack is right.", WARN);
                    } catch (BackendClient.RefusedException e) {
                        say(e.getMessage(), STOP);
                    } finally {
                        Platform.runLater(scanField::requestFocus);
                    }
                });
        return use;
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
        record(match, mrpPaise, false);
    }

    /**
     * @param mrpIsEstimate true when the price came from a lookup rather than from the pack. It
     *     travels with the count so the distinction survives — a label resting on a guess should
     *     still be recognisable as one months later.
     */
    private void record(UnpackingLine match, Long mrpPaise, boolean mrpIsEstimate) {
        // A code waiting to be tagged rides along with the count, so the mapping and the first
        // unit are recorded together rather than in two steps that could half-fail.
        String code = pendingTagCode;
        pendingTagCode = null;
        String condition = chosenCondition();
        CountOutcome outcome =
                code == null
                        ? backend.count(match.lineId(), 1, mrpPaise, condition, mrpIsEstimate)
                        : backend.tag(
                                match.lineId(), code, 1, mrpPaise, condition, mrpIsEstimate);
        // Remembered before the list is rebuilt, since that is what makes it undoable.
        lastCount = new LastCount(match.lineId(), shortName(match), condition, code);
        refreshLines();
        undoButton.setDisable(false);
        sayCounted(match, outcome);
    }

    private String chosenCondition() {
        if (unusableButton.isSelected()) {
            return "UNUSABLE";
        }
        return damagedButton.isSelected() ? "DAMAGED" : "GOOD";
    }

    private void sayCounted(UnpackingLine match, CountOutcome outcome) {
        // Repeated on every confirmation, not only when it changes, so a setting left on is
        // seen at each scan rather than remembered.
        String mark =
                unusableButton.isSelected()
                        ? " (broken)"
                        : damagedButton.isSelected() ? " (damaged)" : "";
        String style =
                unusableButton.isSelected() ? STOP : damagedButton.isSelected() ? WARN : OK;
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
        setNextStep("Tap the item you are holding, or type part of its name to find it.");
        showChoices(code, "");

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

    /**
     * The choices, narrowed by whatever has been typed.
     *
     * <p>A carton can hold sixty lines and picking one by eye is slow. Typing a few letters of
     * the name — or any part of a code — cuts it down, which is faster than reading.
     *
     * <p>Rebuilt on every keystroke rather than filtered in place: the list is short, this is
     * plainly correct, and cleverness here would buy nothing anyone could feel.
     */
    private void showChoices(String scannedCode, String filter) {
        lineList.getChildren().clear();

        Label prompt = new Label("Which of these is it?");
        prompt.setFont(HEADING);
        prompt.setStyle(INK);
        lineList.getChildren().add(prompt);
        lineList.getChildren().add(selectable("Code on the item: " + scannedCode, BODY));

        Label why = new Label(
                "The list uses the supplier's own reference, not the code printed on the item."
                        + " Tell it which one this is and it will remember.");
        why.setFont(SMALL);
        why.setWrapText(true);
        why.setStyle(INK);
        lineList.getChildren().add(why);

        TextField search = new TextField(filter);
        search.setFont(BODY);
        search.setPromptText("Type part of the name to narrow this down");
        search.textProperty().addListener(
                (observable, was, now) -> showChoices(scannedCode, now));
        lineList.getChildren().add(search);

        String needle = filter.trim().toLowerCase(java.util.Locale.ROOT);
        List<UnpackingLine> matching =
                lines.stream()
                        .filter(line -> line.outstanding() > 0)
                        .filter(
                                line ->
                                        needle.isEmpty()
                                                || line.name().toLowerCase(java.util.Locale.ROOT)
                                                        .contains(needle)
                                                || line.code().toLowerCase(java.util.Locale.ROOT)
                                                        .contains(needle))
                        // The item just tagged comes first: units of one product arrive together,
                        // so the next scan is likelier to be another of the same than anything
                        // else in the carton.
                        .sorted(
                                java.util.Comparator
                                        .comparing((UnpackingLine line) ->
                                                !line.lineId().equals(lastTaggedLineId))
                                        .thenComparing(line -> -line.outstanding()))
                        .toList();

        if (matching.isEmpty()) {
            Label nothing = new Label("Nothing in this box matches that.");
            nothing.setFont(BODY);
            nothing.setStyle(INK);
            lineList.getChildren().add(nothing);
        }
        matching.forEach(line -> lineList.getChildren().add(choiceFor(line)));

        // Typing means the keyboard is wanted here, not at the scan field.
        if (!filter.isEmpty()) {
            Platform.runLater(() -> {
                search.requestFocus();
                search.positionCaret(filter.length());
            });
        }
    }

    /**
     * The whole name, on hover.
     *
     * <p>Manifest names run past two hundred characters and are cut to sixty so a row stays a
     * row. The tail is often where the difference is — a size, a colour, a pack count — so two
     * items can read identically until you see the rest of it.
     *
     * <p>Shown quickly, because someone holding an item and hovering has already decided they
     * need it, and a second of stillness is a second of standing there.
     */
    private void showFullNameOnHover(javafx.scene.control.Control control, UnpackingLine line) {
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(line.name());
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(560);
        tooltip.setFont(BODY);
        tooltip.setShowDelay(javafx.util.Duration.millis(250));
        tooltip.setShowDuration(javafx.util.Duration.seconds(30));
        control.setTooltip(tooltip);
    }

    private Button choiceFor(UnpackingLine line) {
        // The same figures the ordinary rows carry. This is the moment someone is deciding
        // which of two near-identical names they are holding, so withholding what each one cost
        // here — and showing it only after the choice is made — helps at exactly the wrong time.
        Button choice =
                new Button(
                        shortName(line)
                                + "   ("
                                + line.outstanding()
                                + " to find)\n"
                                + sheetSays(line));
        choice.setFont(BODY);
        choice.setWrapText(true);
        choice.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
        choice.setAlignment(Pos.CENTER_LEFT);
        choice.setMaxWidth(Double.MAX_VALUE);
        choice.setStyle("-fx-padding:12;-fx-background-radius:6;");
        choice.setOnAction(event -> tagChosen(line));
        showFullNameOnHover(choice, line);
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
                    backend.tag(line.lineId(), code, 1, null, chosenCondition(), false);
            refreshLines();
            sayCounted(line, outcome);
        } catch (BackendClient.RefusedException e) {
            refreshLines();
            say(e.getMessage(), STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    /**
     * Takes back the last count.
     *
     * <p>Removes the code mapping too, where that scan created it — a code pointing at the
     * wrong goods is worse than no code, since it will keep resolving confidently.
     */
    private void undoLastCount() {
        LastCount last = lastCount;
        if (last == null) {
            return;
        }
        try {
            backend.undo(last.lineId(), 1, last.condition(), last.taggedCode());
            lastCount = null;
            undoButton.setDisable(true);
            refreshLines();
            say("Took that back" + (last.taggedCode() == null
                            ? ""
                            : " and forgot the code, so it can be scanned to the right item")
                    + ": " + last.itemName(), WARN);
        } catch (BackendClient.RefusedException e) {
            say(e.getMessage(), STOP);
        } catch (BackendUnavailableException e) {
            say("Cannot reach the system, so that has not been taken back yet.", STOP);
        } finally {
            Platform.runLater(scanField::requestFocus);
        }
    }

    /**
     * Offers to forget a code that keeps landing on the wrong goods.
     *
     * <p>Shown at the moment the mistake bites: the sticker resolves to something already
     * counted, so there is nothing to add and no way forward. Taking the count back does not
     * help on its own — the sticker still points where it did, and every rescan lands here
     * again.
     */
    private void offerToReleaseCode(String code, UnpackingLine wrongly) {
        lineList.getChildren().clear();

        Label heading = new Label("More of these than the sheet expected");
        heading.setFont(HEADING);
        heading.setStyle(INK);
        lineList.getChildren().add(heading);
        lineList.getChildren().add(selectable("Code on the item: " + code, BODY));

        Label explain =
                new Label(
                        "This code is on \"" + wrongly.name() + "\". If another one really did"
                                + " arrive, count it — extra goods are worth recording. If the"
                                + " item in your hand is something else, the code was put on the"
                                + " wrong thing.");
        explain.setFont(SMALL);
        explain.setWrapText(true);
        explain.setStyle(INK);
        lineList.getChildren().add(explain);

        Button release = new Button("Wrong item — forget this code and let me scan it again");
        release.setFont(BODY);
        release.setWrapText(true);
        release.setMaxWidth(Double.MAX_VALUE);
        release.setStyle("-fx-padding:12;-fx-background-radius:6;-fx-background-color:#ffe0b2;");
        release.setOnAction(
                event -> {
                    try {
                        backend.releaseCode(code);
                        forgetLastCount();
                        refreshLines();
                        say("Forgotten. Scan it again and say which item it really is."
                                + " If you already counted one against the wrong item, take"
                                + " that back too.", WARN);
                    } catch (BackendClient.RefusedException e) {
                        say(e.getMessage(), STOP);
                    } catch (BackendUnavailableException e) {
                        say("Cannot reach the system, so nothing has changed.", STOP);
                    } finally {
                        Platform.runLater(scanField::requestFocus);
                    }
                });
        lineList.getChildren().add(release);

        Button extra = new Button("Yes — another one really did arrive, count it");
        extra.setFont(BODY);
        extra.setWrapText(true);
        extra.setMaxWidth(Double.MAX_VALUE);
        extra.setStyle("-fx-padding:12;-fx-background-radius:6;");
        extra.setOnAction(
                event -> {
                    // More arriving than the sheet promised is a fact about the delivery, not
                    // an error to refuse. Setting it aside would leave real stock off the books
                    // and the surplus invisible — the opposite of what counting is for.
                    try {
                        record(wrongly, null);
                        say("Counted as extra. The sheet said " + wrongly.expected()
                                + "; more than that arrived, and that is now recorded.", WARN);
                    } catch (BackendClient.RefusedException e) {
                        say(e.getMessage(), STOP);
                    } catch (BackendUnavailableException e) {
                        say("Cannot reach the system, so that has not been counted.", STOP);
                    } finally {
                        Platform.runLater(scanField::requestFocus);
                    }
                });
        lineList.getChildren().add(extra);
    }

    private void leaveCarton() {
        goodButton.setSelected(true);
        forgetLastCount();
        carton = null;
        lines = List.of();
        untaggedCode = null;
        pendingTagCode = null;
        closeMrpPrompt();
        lineList.getChildren().clear();
        cartonLabel.setText("Scan a box to start");
        finishButton.setDisable(true);
        leaveButton.setDisable(true);
        setConditionButtonsDisabled(true);
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
            goodButton.setSelected(true);
            forgetLastCount();
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
            setConditionButtonsDisabled(true);
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
        setConditionButtonsDisabled(false);
        setNextStep("Scan the next item, or press \"Box is done\".");

        lineList.getChildren().clear();
        // Still to find first. In a sixty-line carton the done ones are just scenery, and
        // hunting past them for the next thing is the work this screen exists to remove.
        lines.stream()
                .sorted(java.util.Comparator.comparing((UnpackingLine line) -> line.outstanding() == 0))
                .forEach(line -> lineList.getChildren().add(rowFor(line)));
        refreshDelivery();
    }

    private HBox rowFor(UnpackingLine line) {
        javafx.scene.control.TextField nameField = selectable(shortName(line), BODY);
        showFullNameOnHover(nameField, line);

        VBox detail = new VBox(0, nameField, selectable(sheetSays(line), SMALL));
        HBox.setHgrow(detail, Priority.ALWAYS);
        detail.setMaxWidth(Double.MAX_VALUE);

        Label count = new Label(line.counted() + " / " + line.expected());
        count.setFont(Font.font("System", FontWeight.BOLD, 22));

        // Done reads green from across the room; still-to-find stays plain. Nothing is red,
        // because nothing here is yet a mistake — a box in progress is just a box in progress.
        HBox row = new HBox(12, detail, count);
        // Anything counted can be taken back, not only the last thing scanned — a mistake is
        // often found ten items later, and by then the undo button is about something else.
        if (line.counted() > 0) {
            row.getChildren().add(takeBackButton(line));
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 10));
        row.setStyle(
                line.outstanding() == 0
                        ? "-fx-background-color:#e8f5e9;-fx-background-radius:6;"
                        : "-fx-background-color:#f5f5f5;-fx-background-radius:6;");
        return row;
    }

    /**
     * Text a person can select and copy.
     *
     * <p>A JavaFX {@link Label} cannot be selected at all, which is fine for a caption and
     * useless for a product code someone needs to paste into a search. A read-only text field
     * stripped of its chrome reads as a label and behaves as text.
     */
    private javafx.scene.control.TextField selectable(String text, Font font) {
        javafx.scene.control.TextField field = new javafx.scene.control.TextField(text);
        field.setEditable(false);
        field.setFont(font);
        field.setStyle(
                "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
                        + " -fx-border-width: 0;");
        // maxWidth and Hgrow, never prefWidth. A preferred width of MAX_VALUE is not "fill the
        // space" — it is a real number the layout tries to honour, and it collapsed this column
        // to nothing while the row beside it still rendered, so the screen looked half-built
        // rather than broken.
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        // Long enough to be visible before layout stretches it; the manifest names are trimmed
        // to sixty characters already.
        field.setPrefColumnCount(40);
        return field;
    }

    /**
     * What the supplier's sheet said, and what the goods fetched online.
     *
     * <p>Shown beside the item because the person holding it is about to type an MRP, and a
     * printed price nowhere near what the thing sells for online is worth looking at twice
     * before it is entered.
     */
    private String sheetSays(UnpackingLine line) {
        StringBuilder said = new StringBuilder(line.code());
        if (line.indicativeCostPaise() != null) {
            // "about", because it is: the real figure is settled when the delivery closes and
            // is spread over what actually arrived, so a short line ends up costing more.
            said.append("   ·   cost about ₹").append(line.indicativeCostPaise() / 100);
        }
        if (line.onlinePricePaise() != null) {
            said.append("   ·   online ₹").append(line.onlinePricePaise() / 100);
        }
        return said.toString();
    }

    /**
     * What was counted last, and what it taught.
     *
     * @param taggedCode the code that scan mapped, or null where the code was already known —
     *     undoing must unmap the first and leave the second alone
     */
    private record LastCount(
            UUID lineId, String itemName, String condition, String taggedCode) {}

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

    /**
     * Takes one unit back off a line that was counted earlier.
     *
     * <p>Deliberately one at a time. Taking back a count is undoing a real record, and a button
     * that empties a line in a single press is one slip away from a bigger mistake than the one
     * being corrected.
     *
     * <p>It does not unmap any code. This is a correction to a count made some time ago, and
     * which scan taught which code is no longer known — a wrong mapping is a separate job, on a
     * screen that can show what a product's codes are.
     */
    private Button takeBackButton(UnpackingLine line) {
        Button back = new Button("Take one back");
        back.setFont(SMALL);
        back.setStyle("-fx-padding:6 10;-fx-background-radius:6;");
        back.setOnAction(
                event -> {
                    try {
                        backend.undo(line.lineId(), 1, null, null);
                        forgetLastCount();
                        refreshLines();
                        say("Took one back: " + shortName(line), WARN);
                        // Taking the count back is only half of it. If a scan put a code on
                        // these goods, that code still points here — and the next time it is
                        // scanned it lands on an item it does not belong to, with no clue why.
                        offerToReleaseCodesFor(line);
                    } catch (BackendClient.RefusedException e) {
                        say(e.getMessage(), STOP);
                    } catch (BackendUnavailableException e) {
                        say("Cannot reach the system, so nothing has been taken back.", STOP);
                    } finally {
                        Platform.runLater(scanField::requestFocus);
                    }
                });
        return back;
    }


    /**
     * Offers to give up any code that was put on these goods.
     *
     * <p>Shown straight after a count is taken back, because that is when the mistake is in
     * mind. Undoing a count leaves the mapping behind — deliberately, since which scan taught
     * which code is not known — and the mapping is the half that causes trouble later: the
     * sticker keeps resolving to goods it does not belong to, and the person holding it a week
     * later has no idea why.
     *
     * <p>The supplier's own reference is listed but cannot be given up. Losing it would break
     * the line's link to the manifest, which is a worse problem than the one being fixed.
     */
    private void offerToReleaseCodesFor(UnpackingLine line) {
        List<LearntCode> codes;
        try {
            codes = backend.codesFor(line.lineId());
        } catch (RuntimeException e) {
            return;
        }
        List<LearntCode> releasable = codes.stream().filter(LearntCode::releasable).toList();
        if (releasable.isEmpty()) {
            return;
        }

        Label heading = new Label("Was a code put on this by mistake?");
        heading.setFont(BODY);
        heading.setWrapText(true);
        heading.setStyle(INK);
        lineList.getChildren().add(0, heading);

        int at = 1;
        for (LearntCode code : releasable) {
            Button forget = new Button("Forget " + code.code());
            forget.setFont(SMALL);
            forget.setMaxWidth(Double.MAX_VALUE);
            forget.setStyle("-fx-padding:8 10;-fx-background-radius:6;"
                    + "-fx-background-color:#ffe0b2;");
            forget.setOnAction(
                    event -> {
                        try {
                            backend.releaseCode(code.code());
                            refreshLines();
                            say("Forgotten " + code.code()
                                    + ". Scan it again and say which item it really is.", WARN);
                        } catch (BackendClient.RefusedException e) {
                            say(e.getMessage(), STOP);
                        } catch (BackendUnavailableException e) {
                            say("Cannot reach the system, so nothing has changed.", STOP);
                        } finally {
                            Platform.runLater(scanField::requestFocus);
                        }
                    });
            lineList.getChildren().add(at++, forget);
        }
    }

    /** Nothing to take back once a carton is put down; the offer would be a lie. */
    private void forgetLastCount() {
        lastCount = null;
        undoButton.setDisable(true);
    }

    private void setConditionButtonsDisabled(boolean disabled) {
        goodButton.setDisable(disabled);
        damagedButton.setDisable(disabled);
        unusableButton.setDisable(disabled);
    }

    private void setNextStep(String text) {
        nextStep.setText("→ " + text);
    }
}
