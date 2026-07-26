# Design: Redesign Receiving Workflow

## Context

The receiving workflow was added in the prior commit but never functioned end-to-end because consignment import doesn't create `BoxReceipt` rows — `ReceivingService` methods expect them to exist via `findByLotIdAndManifestCartonId(...).orElseThrow()`. The "Not Received" button routes to `/reject-box`, which sets state `REJECTED`, not the semantically distinct `NOT_RECEIVED` state that `markNotReceived()` produces.

The web UI was a quick stub: raw `fetch` calls (not `api.ts`), private inline `<style>` block (not shared `styles.css`), no progress counter, no box list, no lot picker, manual UUID entry.

Terminal UI (`ReceivingScreen.java`) has a lot selector and progress display, but `TerminalApplication` passes the lot externally — it doesn't compose a picker into the screen itself.

## Goals / Non-Goals

**Goals:**
- Fix the import gap: make `ConsignmentImporter` create `BoxReceipt` rows in EXPECTED state, one per distinct `trackingNumber` in the lot's lines.
- Fix the endpoint gap: expose `markNotReceived()` as `POST /api/lots/{lotId}/mark-not-received`.
- Add lot picker: provide `GET /api/lots` to list open lots with box counts for the web UI to render as an overview + detail flow.
- Redesign Receiving.tsx: adopt the dashboard's patterns (shared styles/api, overview-to-detail navigation like Unpacking.tsx), add progress counter, box-state list, lot picker, Done button.
- Match terminal UX: progress counter and done-button pattern match `ReceivingScreen.java`.

**Non-Goals:**
- Real-time scanning parity with terminal (terminal still has lower latency; web is async and page-reload based).
- Damage categorization (already scoped to `ReceivingController.rejectBox`; we're only surfacing the existing endpoint).
- MRP modal (unpacking's problem, not receiving's).
- Lot-close workflow (lot closes when all boxes reach terminal state; the UI just returns to overview).

## Decisions

### 1. BoxReceipt creation in ConsignmentImporter (not deferred)
**Decision:** Create `BoxReceipt` rows inside `ConsignmentImporter.importConsignment()` alongside existing `Box` creation.

**Rationale:** 
- `BoxReceipt` is the receipt tracking layer; it's a manifest concern, not an unpacking concern.
- The same transaction that creates the expectation should create the receipt tracker in EXPECTED state.
- No deferred initialization means receiving can begin immediately after import.

**Alternatives considered:**
- Deferred in ReceivingController: "lazy-create on first receive attempt" — adds HTTP-layer complexity and leaves a gap where a box is on a manifest but untrackable.
- Migration backfill for old lots: only works for new imports; doesn't help existing imported-but-not-yet-received lots.
- Separate `POST /api/lots/{id}/initialize-receiving`: explicit second step — adds cognitive burden; import and receiving should feel like one flow.

### 2. Separate mark-not-received endpoint (not merge with reject)
**Decision:** Add distinct `POST /api/lots/{lotId}/mark-not-received` endpoint calling `ReceivingService.markNotReceived()`.

**Rationale:**
- State semantics matter: `REJECTED` (functional damage found) and `NOT_RECEIVED` (never arrived) are different — cost allocation treats both as zero-contribution, but the operator's action and the business meaning differ.
- API clarity: callers know exactly which state they're requesting.
- Future extensibility: `NOT_RECEIVED` may warrant different audit/notification logic later.

**Alternatives considered:**
- Merge under `/reject-box` with a `reason` enum: "NOT_RECEIVED" vs "REJECTED" — breaks the distinction and makes the state visible only in the reason field, not the `box_state` column.
- UI-only workaround: POST to `/reject-box` but UI reads it back and displays it as NOT_RECEIVED — wrong semantics in the database; corrupts queries and reports.

### 3. Lot list endpoint in LotController (not Unpacking's deliveries)
**Decision:** Add new `GET /api/lots` to `LotController`, returning `List<LotSummaryDto>` filtered to open lots, with box counts.

**Rationale:**
- `UnpackingController.deliveries()` exists for unpacking progress (units counted, items with MRP, etc.). Reusing it would couple receiving to unpacking concerns.
- LotController owns the lot lifecycle; receiving is a lot-level entry point.
- Filtering is simple: `lot.isOpen()` — lots already have `state` and `receivingComplete` fields.

**Alternatives considered:**
- Reuse `unpacking.deliveries()`: filters it in the frontend — wrong separation of concerns; backend should enforce the filter.
- Single large list with all lots: unfiltered `GET /api/lots` — fine, but sorting (incomplete first) makes sense for a picker.

### 4. Frontend: overview-to-detail navigation (like Unpacking)
**Decision:** Receiving.tsx renders an overview of open lots (cards with progress bars) when no lot is selected; tapping opens the detail view (scan input, box list, done button).

**Rationale:**
- Matches Unpacking.tsx structure: users already know the pattern.
- Scales: if lots pile up, the overview lets you see all at once before picking; detail is focused.
- Progress bars on the overview give quick sense of which lots are nearly done.

**Alternatives considered:**
- Dropdown picker in the detail view: simpler code but no overview. Means you can't see progress across lots without switching.
- Persistent picker (always visible beside the scan area): wider layout, more visual noise, not mobile-friendly.

### 5. Shared styles.css and api.ts (not private inline)
**Decision:** Move from inline `<style>` block to shared `styles.css` (add `.receiving` class), use `api.ts` `receiving` export instead of raw `fetch`.

**Rationale:**
- Consistency: every other screen (Unpacking, Pricing, Catalog) uses shared styles and api.
- Maintainability: color/spacing changes in one place, not scattered across components.
- Smaller bundle: shared CSS isn't duplicated per-component.

**Alternatives considered:**
- Keep private style block: faster iteration during development — but we're shipping this, so consistency wins.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| BoxReceipt creation during import adds latency to import. | Import is not latency-critical (manual once per delivery). Keeps receiving unblocked and data consistent. |
| Lot list endpoint exposes all open lots to any client. | Same as existing GET endpoints; no auth layer exists in v1. Acceptable within the local-only architecture. |
| Frontend rewrite could regress usability. | Rewrite is UI-only; service layer and API are unchanged. Unpacking.tsx is the pattern, so UX is proven. Testing happens against real dev backend with live import. |
| Sorting/filtering on LotController side vs frontend side. | Server-side reduces client logic and ensures sort order is stable. Trade: server returns all open lots (no pagination). Acceptable for single outlet (dozens of lots max at once). |

## Migration Plan

1. **Commit 1:** Add `BoxReceiptRepository` injection and `BoxReceipt` creation to `ConsignmentImporter`. Add `mark-not-received` endpoint to `ReceivingController`. Add `GET /api/lots` to `LotController`. Run existing `ReceivingE2ETest` (tests pass, no behavior change to service layer).
2. **Commit 2:** Add types to `types.ts`, `receiving` export to `api.ts`, `.receiving` and `.flag.neutral` to `styles.css`.
3. **Commit 3:** Rewrite `Receiving.tsx` with new architecture. Test live against running backend.

**Rollback:** Receiving hasn't shipped yet (it's new in this cycle). Rollback is a git revert.

## Open Questions

None. Proposal, design decisions, and spec-level requirements are clear.
