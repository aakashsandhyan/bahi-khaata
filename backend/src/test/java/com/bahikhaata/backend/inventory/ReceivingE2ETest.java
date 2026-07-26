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

import static org.assertj.core.api.Assertions.*;

import com.bahikhaata.contracts.AllocationMethod;
import com.bahikhaata.contracts.BoxState;
import com.bahikhaata.contracts.LotState;
import com.bahikhaata.contracts.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReceivingE2ETest {

  @Autowired private BoxReceiptRepository boxReceiptRepository;
  @Autowired private LotRepository lotRepository;
  @Autowired private ReceivingService receivingService;

  @Test
  void receiveBoxTransitionsFromExpectedToReceived() {
    Lot lot = lotRepository.save(
        new Lot("Test Supplier", LocalDate.now(), Money.ofRupees(1000), Money.ZERO, AllocationMethod.RELATIVE_MRP));
    UUID lotId = lot.getId();

    BoxReceipt box = new BoxReceipt(lotId, "BOX-001");
    boxReceiptRepository.save(box);

    assertThat(box.getState()).isEqualTo(BoxState.EXPECTED);

    receivingService.receiveBox(lotId, "BOX-001", java.time.Instant.now());

    BoxReceipt received = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, "BOX-001").orElseThrow();
    assertThat(received.getState()).isEqualTo(BoxState.RECEIVED);
    assertThat(received.getReceivedAt()).isNotNull();
  }

  @Test
  void rejectBoxMarksAsNotReceived() {
    Lot lot = lotRepository.save(
        new Lot("Test Supplier", LocalDate.now(), Money.ofRupees(1000), Money.ZERO, AllocationMethod.RELATIVE_MRP));
    UUID lotId = lot.getId();

    BoxReceipt box = new BoxReceipt(lotId, "BOX-002");
    boxReceiptRepository.save(box);

    receivingService.markNotReceived(lotId, "BOX-002");

    BoxReceipt rejected = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, "BOX-002").orElseThrow();
    assertThat(rejected.getState()).isEqualTo(BoxState.NOT_RECEIVED);
  }

  @Test
  void allBoxesTerminalGatesLotClose() {
    Lot lot = lotRepository.save(
        new Lot("Test Supplier", LocalDate.now(), Money.ofRupees(1000), Money.ZERO, AllocationMethod.RELATIVE_MRP));
    UUID lotId = lot.getId();

    BoxReceipt box1 = new BoxReceipt(lotId, "BOX-001");
    BoxReceipt box2 = new BoxReceipt(lotId, "BOX-002");
    boxReceiptRepository.saveAll(List.of(box1, box2));

    // Not all terminal yet
    assertThatThrownBy(() -> receivingService.validateLotCanClose(lotId))
        .isInstanceOf(IllegalStateException.class);

    // Mark all terminal
    receivingService.markNotReceived(lotId, "BOX-001");
    receivingService.receiveBox(lotId, "BOX-002", java.time.Instant.now());
    receivingService.markBoxUnpacking(lotId, "BOX-002");
    receivingService.markBoxUnpacked(lotId, "BOX-002");

    // Should not throw
    assertThatCode(() -> receivingService.validateLotCanClose(lotId)).doesNotThrowAnyException();
  }
}
