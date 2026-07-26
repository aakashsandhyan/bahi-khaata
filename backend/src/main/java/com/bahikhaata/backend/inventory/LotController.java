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

import com.bahikhaata.contracts.LotLineResponse;
import com.bahikhaata.contracts.LotResponse;
import com.bahikhaata.contracts.ReceiveLotRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receiving deliveries.
 *
 * <p>The response returns what each line was costed at, so the operator can see the allocation
 * rather than take it on trust — a figure nobody looked at is a figure nobody catches.
 */
@RestController
@RequestMapping("/api/lots")
class LotController {

    private final GoodsInService goodsIn;
    private final LotRepository lotRepository;
    private final BoxReceiptRepository boxReceiptRepository;

    LotController(GoodsInService goodsIn, LotRepository lotRepository, BoxReceiptRepository boxReceiptRepository) {
        this.goodsIn = goodsIn;
        this.lotRepository = lotRepository;
        this.boxReceiptRepository = boxReceiptRepository;
    }

    @GetMapping
    ResponseEntity<List<LotSummaryDto>> listLots() {
        List<LotSummaryDto> results = lotRepository.findAll().stream()
            .filter(Lot::isOpen)
            .map(lot -> {
                List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lot.getId());
                long expected = boxes.size();
                long received = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.RECEIVED);
                long unpacked = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.UNPACKED);
                long rejected = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.REJECTED);
                long notReceived = boxReceiptRepository.countByLotIdAndState(lot.getId(), com.bahikhaata.contracts.BoxState.NOT_RECEIVED);
                return new LotSummaryDto(lot.getId(), lot.getSupplier(), lot.getReceivedOn(), lot.isReceivingComplete(),
                    expected, received, unpacked, rejected, notReceived);
            })
            .sorted(Comparator
                .comparing((LotSummaryDto l) -> l.receivingComplete())
                .thenComparing(Comparator.comparing((LotSummaryDto l) -> l.id()).reversed()))
            .toList();
        return ResponseEntity.ok(results);
    }

    @PostMapping
    ResponseEntity<LotResponse> receive(@RequestBody ReceiveLotRequest request) {
        GoodsInService.ReceivedLot received = goodsIn.receive(request);

        List<LotLineResponse> lines = new ArrayList<>(received.batches().size());
        for (Batch batch : received.batches()) {
            lines.add(
                    new LotLineResponse(
                            batch.getId().toString(),
                            batch.getProduct().getId().toString(),
                            batch.getQuantityReceived(),
                            batch.getQuantityDamaged(),
                            batch.getAllocatedTotal().paise(),
                            batch.getAllocatedUnitCost().paise(),
                            batch.getCostBasis()));
        }

        Lot lot = received.lot();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new LotResponse(
                                lot.getId().toString(),
                                lot.getSupplier(),
                                lot.getReceivedOn().toString(),
                                lot.getAmountPaid().paise(),
                                lot.getFreight().paise(),
                                received.allocation().totalAllocated().paise(),
                                lot.getAllocationMethod(),
                                lines));
    }

    /**
     * A delivery that cannot be allocated is the operator's to fix — an unknown product, a
     * pinned total that overshoots. The message says which, because "could not receive" leaves
     * someone holding a pallet with nothing to act on.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

record LotSummaryDto(
    UUID id,
    String supplier,
    LocalDate receivedOn,
    boolean receivingComplete,
    long expected,
    long received,
    long unpacked,
    long rejected,
    long notReceived) {}
