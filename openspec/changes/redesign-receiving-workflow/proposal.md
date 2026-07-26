# Redesign: Receiving Workflow

## Why

The receiving workflow was built as a frontend stub that never worked end-to-end against real imported consignments. Two critical gaps block it: (1) consignment import never creates `BoxReceipt` rows, so `ReceivingService` has nothing to transition; (2) the "Not Received" button routes to the wrong state endpoint, making state semantics inaccurate. The redesign fixes both gaps while rewriting the web UI to match the dashboard's design patterns and add missing UX (progress counter, box list, lot picker, done button). Needed to enable the two-stage receiving flow to work with real data.

## What Changes

- **Backend**: Fix `ConsignmentImporter` to create `BoxReceipt` rows (in `EXPECTED` state) alongside `Box` rows on import. Add missing `POST /api/lots/{lotId}/mark-not-received` endpoint. Add `GET /api/lots` list endpoint for lot picker.
- **Frontend**: Rewrite `Receiving.tsx` to use shared `styles.css` and `api.ts` (not private inline styles + raw `fetch`). Add lot picker, progress counter, box-state list view, and Done button. Structure like `Unpacking.tsx` (overview ↔ detail pattern).
- **Types**: Add `LotSummary`, `ReceivingBox`, `ReceivingBoxes` to `types.ts`. Add `receiving` export to `api.ts`.
- **Styles**: Two small additions to `styles.css` (`.receiving` layout class, `.flag.neutral` badge for EXPECTED state).

## Capabilities

### New Capabilities
- `lot-picker`: User selects an open lot from a dropdown/list instead of typing a UUID.
- `receiving-progress`: Real-time progress counter showing boxes received/rejected/not-received versus total expected.
- `box-state-list`: View all boxes for a lot with their individual states and timestamps.
- `receiving-lot-list`: Backend endpoint to list all open lots with box counts and progress bars.
- `mark-not-received-endpoint`: Dedicated HTTP endpoint to transition a box to NOT_RECEIVED state (distinct from REJECTED).

### Modified Capabilities
- `box-receipt-creation`: Consignment import now creates `BoxReceipt` rows in EXPECTED state alongside Box rows (previously these were created only manually in tests).
- `receiving-workflow`: UI redesigned to use shared patterns (styles.css, api.ts) and full scanner flow with exception handling for non-received and damaged boxes.

## Impact

**Backend files**: `ConsignmentImporter.java`, `ReceivingController.java`, `LotController.java`, `types.ts`, `api.ts`, `styles.css`, `Receiving.tsx`.

**Breaking changes**: None. New endpoints are additive. `ConsignmentImporter` change is transparent to existing code.

**APIs**: `POST /api/lots/{lotId}/mark-not-received`, `GET /api/lots` (new). Existing endpoints unchanged.

**Tests**: `ReceivingE2ETest` remains green (service layer behavior unchanged).
