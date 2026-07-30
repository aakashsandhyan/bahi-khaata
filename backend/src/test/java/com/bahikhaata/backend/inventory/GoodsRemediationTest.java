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
package com.bahikhaata.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.IssueTypeOption;
import com.bahikhaata.contracts.ImportConsignmentRequest;
import com.bahikhaata.contracts.ImportLine;
import com.bahikhaata.contracts.ImportLot;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.StockCondition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goods arrive imperfect but often fixable. A needs-work unit is owned and costed but not yet on
 * the shelf; it is prepared into sellable stock, or found damaged, or turns out scrap — and every
 * such move is recorded without rewriting the ledger.
 */
@SpringBootTest(properties = "bahikhaata.db.path=build/test-remediation.db")
@Transactional
class GoodsRemediationTest {

    private static final Instant AT = Instant.parse("2026-07-24T09:00:00Z");

    @Autowired private ConsignmentImporter importer;
    @Autowired private GoodsInCounting counting;
    @Autowired private GoodsRemediation remediation;
    @Autowired private LotClosing closing;
    @Autowired private ReceivingService receiving;
    @Autowired private ExpectedLineRepository expectedLines;
    @Autowired private BatchRepository batches;
    @Autowired private BoxRepository boxes;
    @Autowired private LotRepository lots;
    @Autowired private StockLevels stock;

    private UUID lotId;

    @BeforeEach
    void importAManifest() {
        importer.importConsignment(
                new ImportConsignmentRequest(
                        "Sushil", "2026-07-17",
                        List.of(new ImportLot("KITCHEN", 100_000, AllocationMethod.RELATIVE_MRP,
                                List.of(new ImportLine("KETTLE", "Kettle", 10, 10_000, null,
                                        "BOX-A", null, null))))));
        lotId = lots.findAll().stream().filter(Lot::isOpen).reduce((a, b) -> b).orElseThrow().getId();
    }

    private ExpectedLine line() {
        return expectedLines.findByLotIdOrderByCode(lotId).get(0);
    }

    private UUID productId() {
        return line().getProduct().getId();
    }

    private Batch batch(StockCondition condition) {
        return batches.findByLotIdAndProductIdAndCondition(lotId, productId(), condition).orElseThrow();
    }

    private Batch needsWork(String issueType) {
        return batches
                .findByLotIdAndProductIdAndConditionAndIssueType(
                        lotId, productId(), StockCondition.NEEDS_WORK, issueType)
                .orElseThrow();
    }

    /** Drives every carton receipt to a terminal state so the receiving guard permits a close. */
    private void finishReceiving() {
        for (Box box : boxes.findByLotIdOrderByTrackingNumber(lotId)) {
            String carton = box.getTrackingNumber();
            receiving.receiveBox(lotId, carton, AT);
            receiving.markBoxUnpacking(lotId, carton);
            receiving.markBoxUnpacked(lotId, carton);
        }
    }

    @Test
    @DisplayName("Needs-work goods are counted and costed but never reach the ledger")
    void needsWorkIsOffLedger() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 5, Money.ofPaise(50_000), false, AT);
        counting.countExpected(
                line().getId(), StockCondition.NEEDS_WORK, 3, Money.ofPaise(50_000), false, null,
                "CLEAN", AT);

        assertThat(needsWork("CLEAN").getQuantityReceived()).isEqualTo(3);
        assertThat(needsWork("CLEAN").getIssueType()).isEqualTo("CLEAN");
        assertThat(stock.onHand(productId()))
                .as("needs-work is owned but not sellable, so it is not on hand — only the five sound ones")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Needs-work carries ordinary cost, pinned at receipt, since it will sell at full price")
    void needsWorkCarriesCost() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 6, null, false, AT);
        counting.countExpected(
                line().getId(), StockCondition.NEEDS_WORK, 4, null, false, null, "CLEAN", AT);

        // Pinned at receipt from the line's stated value, just like the sound goods; no close needed.
        assertThat(needsWork("CLEAN").getAllocatedUnitCost())
                .as("it will sell at full price, so it costs what a sound unit costs")
                .isEqualTo(batch(StockCondition.GOOD).getAllocatedUnitCost());
        assertThat(batch(StockCondition.GOOD).getAllocatedTotal().paise()
                        + needsWork("CLEAN").getAllocatedTotal().paise())
                .as("ten units at their stated 10,000 each — the whole amount paid for the lot")
                .isEqualTo(100_000);
    }

    @Test
    @DisplayName("Preparing needs-work into ready stock raises on-hand and carries the MRP over")
    void preparingRaisesOnHand() {
        counting.countExpected(
                line().getId(), StockCondition.NEEDS_WORK, 3, Money.ofPaise(50_000), false, null,
                "CLEAN", AT);
        assertThat(stock.onHand(productId())).isZero();

        remediation.changeState(
                productId(), lotId, StockCondition.NEEDS_WORK, "CLEAN", StockCondition.GOOD, null, 2, AT);

        assertThat(stock.onHand(productId()))
                .as("two cleaned units become sellable and appear on hand")
                .isEqualTo(2);
        assertThat(needsWork("CLEAN").getQuantityReceived()).isEqualTo(1);
        assertThat(batch(StockCondition.GOOD).getQuantityReceived()).isEqualTo(2);
        assertThat(batch(StockCondition.GOOD).getMrp())
                .as("the rescued unit carries the product's MRP without being asked again")
                .isEqualTo(Money.ofPaise(50_000));
    }

    @Test
    @DisplayName("Moving between two stock-bearing states leaves on-hand unchanged")
    void goodToDamagedHoldsOnHand() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 5, Money.ofPaise(50_000), false, AT);

        remediation.changeState(
                productId(), lotId, StockCondition.GOOD, null, StockCondition.DAMAGED, null, 2, AT);

        assertThat(stock.onHand(productId())).as("both piles are stock; nothing left the shelf").isEqualTo(5);
        assertThat(batch(StockCondition.GOOD).getQuantityReceived()).isEqualTo(3);
        assertThat(batch(StockCondition.DAMAGED).getQuantityReceived()).isEqualTo(2);
    }

    @Test
    @DisplayName("Scrapping sellable stock lowers on-hand")
    void goodToScrapLowersOnHand() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 5, null, false, AT);

        remediation.changeState(
                productId(), lotId, StockCondition.GOOD, null, StockCondition.UNUSABLE, null, 2, AT);

        assertThat(stock.onHand(productId())).isEqualTo(3);
    }

    @Test
    @DisplayName("Moving more than the source holds is refused")
    void overMoveRefused() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 2, null, false, AT);

        assertThatThrownBy(() ->
                        remediation.changeState(
                                productId(), lotId, StockCondition.GOOD, null, StockCondition.DAMAGED,
                                null, 5, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 2");
        assertThat(batch(StockCondition.GOOD).getQuantityReceived())
                .as("nothing changed on the refusal")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A state change against a closed lot is refused")
    void closedLotRefused() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 5, Money.ofPaise(50_000), false, AT);
        finishReceiving();
        closing.close(lotId, false, AT);

        assertThatThrownBy(() ->
                        remediation.changeState(
                                productId(), lotId, StockCondition.GOOD, null, StockCondition.DAMAGED,
                                null, 1, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    private UUID recordExtra(String code, long quantity) {
        counting.countUnlisted(
                line().getBox().getId(), code, "Mystery extra", "KITCHEN", quantity,
                Money.ofPaise(50_000), false, AT);
        return remediation.extras().stream()
                .filter(e -> code.equals(e.code()))
                .findFirst()
                .orElseThrow()
                .productId();
    }

    @Test
    @DisplayName("Linking an extra to a short line fills it, moving attribution not stock")
    void linkingExtraFillsTheShort() {
        // The line expects 10; count 8, leaving it two short. Then two turn up as an extra.
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        UUID extraId = recordExtra("LPNEXTRA0001", 2);

        assertThat(stock.onHand(productId())).as("eight against the line").isEqualTo(8);
        assertThat(stock.onHand(extraId)).as("two held as the extra product").isEqualTo(2);

        remediation.linkExtra(extraId, line().getId(), AT);

        assertThat(line().getQuantityCounted()).as("the shortfall is filled").isEqualTo(10);
        assertThat(line().getDiscrepancy()).isZero();
        assertThat(stock.onHand(productId()))
                .as("the target now holds all ten — the two were reattributed, not created")
                .isEqualTo(10);
        assertThat(stock.onHand(extraId)).as("the phantom extra is emptied").isZero();
        assertThat(remediation.extras()).as("and no longer lists as an extra").isEmpty();
    }

    @Test
    @DisplayName("Linking merges the extra: its code then names the real product")
    void linkingReassignsTheCode() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        UUID extraId = recordExtra("LPNEXTRA0002", 2);

        remediation.linkExtra(extraId, line().getId(), AT);

        assertThat(remediation.statesByCode("LPNEXTRA0002").productId())
                .as("the extra's sticker now scans as the real product")
                .isEqualTo(productId());
    }

    @Test
    @DisplayName("Linking into a closed lot is refused")
    void linkIntoClosedLotRefused() {
        counting.countExpected(line().getId(), StockCondition.GOOD, 8, Money.ofPaise(50_000), false, AT);
        UUID extraId = recordExtra("LPNEXTRA0003", 2);
        finishReceiving();
        closing.close(lotId, false, AT);
        assertThatThrownBy(() -> remediation.linkExtra(extraId, line().getId(), AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("The issue-type menu is scoped to the department")
    void issueTypesAreScopedToCategory() {
        assertThat(remediation.issueTypesFor("KITCHEN"))
                .extracting(IssueTypeOption::code)
                .contains("REBUILD", "REPAIR")
                .doesNotContain("DRYCLEAN");
        assertThat(remediation.issueTypesFor("FASHION"))
                .extracting(IssueTypeOption::code)
                .contains("DRYCLEAN", "IRON")
                .doesNotContain("REBUILD");
    }

    @Test
    @DisplayName("One product may hold units under different kinds of work at once")
    void twoIssueTypesCoexist() {
        counting.countExpected(
                line().getId(), StockCondition.NEEDS_WORK, 2, null, false, null, "CLEAN", AT);
        counting.countExpected(
                line().getId(), StockCondition.NEEDS_WORK, 1, null, false, null, "REPAIR", AT);

        assertThat(needsWork("CLEAN").getQuantityReceived()).isEqualTo(2);
        assertThat(needsWork("REPAIR").getQuantityReceived()).isEqualTo(1);
        assertThat(batches.findByLotIdAndProductId(lotId, productId()))
                .as("two needs-work batches, held apart by the work each needs")
                .hasSize(2);
    }
}
