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

import com.bahikhaata.contracts.BoxState;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivingService {

  private final BoxReceiptRepository boxReceiptRepository;
  private final LotRepository lotRepository;

  public ReceivingService(BoxReceiptRepository boxReceiptRepository, LotRepository lotRepository) {
    this.boxReceiptRepository = boxReceiptRepository;
    this.lotRepository = lotRepository;
  }

  @Transactional
  public BoxReceipt receiveBox(UUID lotId, String manifestCartonId, Instant at) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));

    if (!lot.isOpen()) {
      throw new IllegalStateException(
          "cannot receive boxes for lot " + lotId + " in state " + lot.getState());
    }

    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    box.markReceived(at);
    boxReceiptRepository.save(box);

    updateReceivingComplete(lot);
    return box;
  }

  @Transactional
  public BoxReceipt rejectBox(UUID lotId, String manifestCartonId, String reason) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));

    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    box.reject(reason);
    boxReceiptRepository.save(box);

    updateReceivingComplete(lot);
    return box;
  }

  @Transactional
  public BoxReceipt markNotReceived(UUID lotId, String manifestCartonId) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));

    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    box.markNotReceived();
    boxReceiptRepository.save(box);

    updateReceivingComplete(lot);
    return box;
  }

  @Transactional
  public BoxReceipt markBoxUnpacking(UUID lotId, String manifestCartonId) {
    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    box.markUnpacking();
    boxReceiptRepository.save(box);
    return box;
  }

  @Transactional
  public BoxReceipt markBoxUnpacked(UUID lotId, String manifestCartonId) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));

    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    box.markUnpacked();
    boxReceiptRepository.save(box);

    // Check if all boxes are now terminal
    if (areAllBoxesTerminal(lot.getId())) {
      lot.setReceivingComplete(true);
      lotRepository.save(lot);
    }

    return box;
  }

  @Transactional
  public BoxReceipt rejectBoxDuringUnpack(UUID lotId, String manifestCartonId, String reason) {
    BoxReceipt box = boxReceiptRepository.findByLotIdAndManifestCartonId(lotId, manifestCartonId)
        .orElseThrow(() -> new IllegalArgumentException(
            "box " + manifestCartonId + " not in manifest for lot " + lotId));

    if (box.getState() != BoxState.UNPACKING) {
      throw new IllegalStateException(
          "cannot reject box during unpacking if state is " + box.getState());
    }

    box.reject(reason);
    boxReceiptRepository.save(box);

    // Items already scanned in this box remain recorded
    return box;
  }

  public boolean isReceivingComplete(UUID lotId) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));
    return lot.isReceivingComplete();
  }

  public boolean areAllBoxesTerminal(UUID lotId) {
    List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lotId);
    return boxes.stream().allMatch(b -> b.getState().isTerminal());
  }

  public void validateLotCanClose(UUID lotId) {
    Lot lot = lotRepository.findById(lotId)
        .orElseThrow(() -> new NoSuchElementException("lot " + lotId));

    List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lotId);
    List<BoxReceipt> nonTerminal = boxes.stream()
        .filter(b -> !b.getState().isTerminal())
        .toList();

    if (!nonTerminal.isEmpty()) {
      String pending = nonTerminal.stream()
          .map(BoxReceipt::getManifestCartonId)
          .toList()
          .toString();
      throw new IllegalStateException(
          "cannot close lot " + lotId + " — boxes pending: " + pending);
    }
  }

  private void updateReceivingComplete(Lot lot) {
    if (areAllBoxesTerminal(lot.getId())) {
      lot.setReceivingComplete(true);
      lotRepository.save(lot);
    }
  }
}
