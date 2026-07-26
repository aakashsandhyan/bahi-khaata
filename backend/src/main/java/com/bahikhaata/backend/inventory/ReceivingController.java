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

import com.bahikhaata.contracts.BoxState;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lots/{lotId}")
public class ReceivingController {

  private final ReceivingService receivingService;
  private final LotRepository lotRepository;
  private final BoxReceiptRepository boxReceiptRepository;

  public ReceivingController(
      ReceivingService receivingService,
      LotRepository lotRepository,
      BoxReceiptRepository boxReceiptRepository) {
    this.receivingService = receivingService;
    this.lotRepository = lotRepository;
    this.boxReceiptRepository = boxReceiptRepository;
  }

  @PostMapping("/receive-box")
  public ResponseEntity<?> receiveBox(
      @PathVariable UUID lotId,
      @RequestBody ReceiveBoxRequest request) {
    try {
      BoxReceipt box = receivingService.receiveBox(lotId, request.manifestCartonId(), Instant.now());
      Lot lot = lotRepository.findById(lotId).orElseThrow();
      long receivedCount = boxReceiptRepository.countByLotIdAndState(lotId, BoxState.RECEIVED)
          + boxReceiptRepository.countByLotIdAndState(lotId, BoxState.UNPACKED)
          + boxReceiptRepository.countByLotIdAndState(lotId, BoxState.REJECTED)
          + boxReceiptRepository.countByLotIdAndState(lotId, BoxState.NOT_RECEIVED);
      long totalExpected = boxReceiptRepository.findByLotId(lotId).size();
      return ResponseEntity.ok(new ReceiveBoxResponse(
          box.getId(),
          box.getState().name(),
          box.getReceivedAt(),
          new LotSummary(lot.getId(), receivedCount, totalExpected, lot.isReceivingComplete())));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("BOX_NOT_IN_MANIFEST", e.getMessage()));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("LOT_NOT_FOUND", e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("INVALID_STATE", e.getMessage()));
    }
  }

  @PostMapping("/reject-box")
  public ResponseEntity<?> rejectBox(
      @PathVariable UUID lotId,
      @RequestBody RejectBoxRequest request) {
    try {
      BoxReceipt box = receivingService.rejectBox(lotId, request.manifestCartonId(), request.reason());
      return ResponseEntity.ok(new RejectBoxResponse(box));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("BOX_NOT_IN_MANIFEST", e.getMessage()));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("LOT_NOT_FOUND", e.getMessage()));
    }
  }

  @GetMapping("/boxes")
  public ResponseEntity<?> getBoxesForLot(@PathVariable UUID lotId) {
    try {
      Lot lot = lotRepository.findById(lotId).orElseThrow();
      List<BoxReceipt> boxes = boxReceiptRepository.findByLotId(lotId);

      long expectedCount = boxes.size();
      long receivedCount = boxReceiptRepository.countByLotIdAndState(lotId, BoxState.RECEIVED);
      long unpackedCount = boxReceiptRepository.countByLotIdAndState(lotId, BoxState.UNPACKED);
      long rejectedCount = boxReceiptRepository.countByLotIdAndState(lotId, BoxState.REJECTED);
      long notReceivedCount = boxReceiptRepository.countByLotIdAndState(lotId, BoxState.NOT_RECEIVED);

      boolean allTerminal = receivingService.areAllBoxesTerminal(lotId);

      return ResponseEntity.ok(new GetBoxesResponse(
          boxes,
          lot.isReceivingComplete(),
          allTerminal,
          expectedCount,
          receivedCount,
          unpackedCount,
          rejectedCount,
          notReceivedCount));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("LOT_NOT_FOUND", e.getMessage()));
    }
  }

  @PostMapping("/mark-box-unpacked")
  public ResponseEntity<?> markBoxUnpacked(
      @PathVariable UUID lotId,
      @RequestBody MarkBoxUnpackedRequest request) {
    try {
      BoxReceipt box = receivingService.markBoxUnpacked(lotId, request.manifestCartonId());

      // Find next unpackable box
      List<BoxReceipt> boxes = boxReceiptRepository.findByLotIdAndStateIn(
          lotId,
          List.of(BoxState.RECEIVED, BoxState.UNPACKING));

      BoxReceipt nextBox = boxes.stream()
          .filter(b -> !b.getManifestCartonId().equals(request.manifestCartonId()))
          .findFirst()
          .orElse(null);

      boolean lotReadyToClose = receivingService.areAllBoxesTerminal(lotId);
      BoxDto nextBoxDto = nextBox != null ? BoxDto.from(nextBox) : null;

      return ResponseEntity.ok(new MarkBoxUnpackedResponse(
          box.getState().name(),
          lotReadyToClose,
          nextBoxDto));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("LOT_NOT_FOUND", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("BOX_NOT_FOUND", e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("INVALID_TRANSITION", e.getMessage()));
    }
  }

  // DTOs

  public record ReceiveBoxRequest(String manifestCartonId) {}

  public record ReceiveBoxResponse(
      UUID boxId,
      String state,
      Instant receivedAt,
      LotSummary lot) {}

  public record LotSummary(
      UUID id,
      long receivedCount,
      long totalExpected,
      boolean receivingComplete) {}

  public record RejectBoxRequest(String manifestCartonId, String reason) {}

  public record RejectBoxResponse(
      String state,
      String rejectedReason) {
    RejectBoxResponse(BoxReceipt box) {
      this(box.getState().name(), box.getRejectedReason());
    }
  }

  public record GetBoxesResponse(
      List<BoxDto> boxes,
      boolean receivingComplete,
      boolean allTerminal,
      Counts counts) {
    GetBoxesResponse(
        List<BoxReceipt> boxes,
        boolean receivingComplete,
        boolean allTerminal,
        long expectedCount,
        long receivedCount,
        long unpackedCount,
        long rejectedCount,
        long notReceivedCount) {
      this(
          boxes.stream().map(BoxDto::from).toList(),
          receivingComplete,
          allTerminal,
          new Counts(expectedCount, receivedCount, unpackedCount, rejectedCount, notReceivedCount));
    }
  }

  public record BoxDto(
      String manifestCartonId,
      String state,
      Instant receivedAt,
      String rejectedReason) {
    static BoxDto from(BoxReceipt box) {
      return new BoxDto(
          box.getManifestCartonId(),
          box.getState().name(),
          box.getReceivedAt(),
          box.getRejectedReason());
    }
  }

  public record Counts(
      long expected,
      long received,
      long unpacked,
      long rejected,
      long notReceived) {}

  public record MarkBoxUnpackedRequest(String manifestCartonId) {}

  public record MarkBoxUnpackedResponse(
      String state,
      boolean lotReadyToClose,
      BoxDto nextBox) {}

  public record ErrorResponse(String code, String message) {}
}
