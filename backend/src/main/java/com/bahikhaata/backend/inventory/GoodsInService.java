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
package com.bahikhaata.backend.inventory;

import com.bahikhaata.backend.catalog.Product;
import com.bahikhaata.backend.catalog.ProductRepository;
import com.bahikhaata.backend.inventory.allocation.AllocatedLine;
import com.bahikhaata.backend.inventory.allocation.Allocation;
import com.bahikhaata.backend.inventory.allocation.AllocationLine;
import com.bahikhaata.backend.inventory.allocation.CostAllocator;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.ReceiveLotLine;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receives a delivery: allocates what was paid across the products in it, creates the lot and
 * its batches, and brings the stock on hand.
 *
 * <p>All of it in one transaction. A lot whose batches exist but whose stock never arrived, or
 * batches carrying costs that do not sum to what was paid, are both worse than a failed
 * receipt — the first is invisible stock, the second is a book that does not balance.
 * <p><strong>Not the path a consignment takes.</strong> This receives a complete, known
 * delivery in one transaction — you already know what arrived, at what cost, and record it.
 * That still fits a small hand-entered delivery from a local supplier.
 *
 * <p>A supplier's manifest does not work that way. It states what is <em>coming</em>, the
 * cartons arrive later, and what is inside them routinely differs. Those go through
 * {@link ConsignmentImporter} to record the expectation, {@link GoodsInCounting} to record what
 * was actually found, and {@link LotClosing} to settle the cost across it. Using this method for
 * one would assert quantities nobody has counted.
 *
  */
@Service
public class GoodsInService {

    private final ProductRepository products;
    private final LotRepository lots;
    private final BatchRepository batches;
    private final StockLedgerRepository ledger;
    private final CostAllocator allocator;
    private final SupplierService suppliers;

    GoodsInService(
            ProductRepository products,
            LotRepository lots,
            BatchRepository batches,
            StockLedgerRepository ledger,
            CostAllocator allocator,
            SupplierService suppliers) {
        this.products = products;
        this.lots = lots;
        this.batches = batches;
        this.ledger = ledger;
        this.allocator = allocator;
        this.suppliers = suppliers;
    }

    /**
     * Receives a delivery.
     *
     * @throws IllegalArgumentException if the request is malformed or cannot be allocated
     */
    @Transactional
    public ReceivedLot receive(ReceiveLotRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("a delivery must have at least one line");
        }

        // Resolved first: an unknown or retired supplier fails before any allocation work.
        Supplier supplier = suppliers.resolveActiveSupplier(request.supplierId());

        // Resolved up front so an unknown product fails before anything is written, and so a
        // product appearing twice is caught rather than silently splitting its allocation.
        Map<String, Product> byId = new LinkedHashMap<>();
        for (ReceiveLotLine line : request.lines()) {
            if (byId.containsKey(line.productId())) {
                throw new IllegalArgumentException(
                        "product " + line.productId() + " appears on more than one line; "
                                + "combine them into a single line");
            }
            Product product =
                    products.findById(UUID.fromString(line.productId()))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "no such product: " + line.productId()));
            byId.put(line.productId(), product);
        }

        Allocation allocation =
                allocator.allocate(
                        Money.ofPaise(request.amountPaidPaise()),
                        Money.ofPaise(request.freightPaise()),
                        request.lines().stream().map(GoodsInService::toAllocationLine).toList());

        Lot lot =
                lots.save(
                        new Lot(
                                supplier,
                                LocalDate.parse(request.receivedOn()),
                                Money.ofPaise(request.amountPaidPaise()),
                                Money.ofPaise(request.freightPaise()),
                                allocation.method()));

        List<Batch> created = new ArrayList<>(request.lines().size());
        for (int i = 0; i < request.lines().size(); i++) {
            ReceiveLotLine line = request.lines().get(i);
            AllocatedLine allocated = allocation.lines().get(i);

            Batch batch =
                    batches.save(
                            new Batch(
                                    byId.get(line.productId()),
                                    lot,
                                    allocated.allocatedTotal(),
                                    allocated.allocatedUnitCost(),
                                    allocated.basis(),
                                    line.quantityReceived(),
                                    line.quantityDamaged(),
                                    Money.ofPaise(line.mrpPaise()),
                                    line.mrpIsEstimate()));

            // The stock arrives in the same transaction that records what it cost. Effective
            // at the delivery date, not now, so a late-entered delivery sits where it belongs
            // and FIFO consumes it in true order.
            ledger.save(
                    StockLedgerEntry.receiptOf(
                            batch, lot.getReceivedOn().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));

            created.add(batch);
        }

        return new ReceivedLot(lot, created, allocation);
    }

    private static AllocationLine toAllocationLine(ReceiveLotLine line) {
        return new AllocationLine(
                line.productId(),
                line.quantityReceived(),
                line.quantityDamaged(),
                Money.ofPaise(line.mrpPaise()),
                line.pinnedUnitCostPaise() == null
                        ? null
                        : Money.ofPaise(line.pinnedUnitCostPaise()));
    }

    /** A received lot with the batches created for it and the allocation that costed them. */
    public record ReceivedLot(Lot lot, List<Batch> batches, Allocation allocation) {}
}
