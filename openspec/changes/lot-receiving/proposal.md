# Lot Receiving Workflow

## Problem

Currently, goods imported via manifest go straight to unpacking (MRP capture, item counting). But in a warehouse, receiving and unpacking are separate acts:

1. **Receiving**: Verify that all expected boxes for a lot have physically arrived (scan tracking numbers)
2. **Unpacking**: Open boxes, count items, capture MRP, record damage

Merging these gates incoming stock too early — if a box is missing, you've already recorded cost allocation assumptions that may be wrong.

## Proposal

Add a two-stage workflow:

1. **Stage 1: Receive** — Scan box tracking numbers for a lot until manifest count matches. Lot moves from `OPEN` to `FULLY_RECEIVED`.
2. **Stage 2: Unpack** — Select lot, open a box, scan items, capture MRP. Gated by Stage 1.
3. **Close** — All items in lot unpacked → close lot, allocate costs (existing logic).

### User Experience

Quick Flow (minimal navigation):
- **Menu** → Receive Boxes or Unpack Items
- **Receive**: Lot selector → Scan box ID → Done
- **Unpack**: Lot selector → Box selector → Scan items + MRP → Done

### State Machine

```
OPEN (manifest imported, zero on hand)
  ↓
FULLY_RECEIVED (all manifest boxes scanned)
  ↓
IN_UNPACKING (items being counted/MRP captured) [optional; may skip to CLOSED]
  ↓
CLOSED (costs allocated, sellable)
```

## Scope

**In:**
- Box receipt tracking (manifest line → box_id mapping)
- Lot FULLY_RECEIVED gate (unlock unpacking)
- Quick Flow UI (two-screen Receive + Unpack flows)
- API: receive-box endpoint, check lot receipt status

**Out (v2+):**
- Partial lot close (receive some boxes, close early)
- Box-level damage/rejection during receiving
- Multi-outlet sync (lot splitting across outlets)

## Trade-offs

| Choice | Pro | Con |
|--------|-----|-----|
| **Separate stages (Receive → Unpack)** | Clear warehouse semantics; gates cost assumptions; easy audit trail | Extra step; adds state to track |
| **Quick Flow UI** | Minimal scanning cognitive load; fast for high-volume | Less context on screen (lot details, progress) |
| **Scan box ID at receiving** | Exact physical verification | Requires manifest to include box tracking numbers |

## Terminology

- **Carton** = **Box** (same thing). Manifest tracking unit with an ID. Each carton holds multiple product lines.

## Open Questions

1. **Box rejection workflow:** If carton arrives damaged, should operator mark it REJECTED during receiving or unpacking? Gate it out completely, or allow unpacking with damage notes?
