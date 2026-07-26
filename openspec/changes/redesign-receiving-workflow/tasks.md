# Tasks: Redesign Receiving Workflow

## 1. Backend: Import & Entity Setup

- [ ] 1.1 Inject BoxReceiptRepository into ConsignmentImporter constructor
- [ ] 1.2 Add BoxReceipt creation inside ConsignmentImporter.importConsignment() within the boxesByTracking.computeIfAbsent() block (save new BoxReceipt(lot.getId(), tracking) on first tracking number encounter)
- [ ] 1.3 Verify ReceivingE2ETest still passes with no service-layer changes

## 2. Backend: API Endpoints

- [ ] 2.1 Add POST /api/lots/{lotId}/mark-not-received endpoint to ReceivingController (call ReceivingService.markNotReceived, return RejectBoxResponse)
- [ ] 2.2 Add GET /api/lots endpoint to LotController (inject BoxReceiptRepository, return List<LotSummaryDto> filtered to open lots, sorted by receivingComplete==false first then createdAt descending)
- [ ] 2.3 Create LotSummaryDto record with fields: id, supplier, receivedOn, receivingComplete, expected, received, unpacked, rejected, notReceived

## 3. Frontend: Types & API

- [ ] 3.1 Add LotSummary, ReceivingBox, ReceivingBoxes types to types.ts (mirror the backend DTOs, use camelCase)
- [ ] 3.2 Add receiving export to api.ts with methods: lots(), boxes(lotId), receiveBox(lotId, manifestCartonId), markNotReceived(lotId, manifestCartonId), rejectBox(lotId, manifestCartonId, reason)

## 4. Frontend: Styles

- [ ] 4.1 Add .receiving class to styles.css (max-width: 560px, margin: 0 auto, padding: var(--s4))
- [ ] 4.2 Add .flag.neutral class to styles.css (background: var(--line-soft), color: var(--ink-faint))

## 5. Frontend: UI Rewrite

- [ ] 5.1 Rewrite Receiving.tsx overview mode: fetch and render lots from api.receiving.lots() as .ov cards with progress bars (reuse Unpacking.tsx style classes)
- [ ] 5.2 Implement detail mode: lot selector state, autofocused .scan input for carton ID, "Not Received" and "Damaged" buttons
- [ ] 5.3 Add box list below scan input showing all boxes with state badges (.flag ok/stop/neutral) and receivedAt
- [ ] 5.4 Add progress counter header (X / Y boxes from counts)
- [ ] 5.5 Add Done button (.btn-primary) in .actions footer that clears selected lot and returns to overview
- [ ] 5.6 Wire button actions to api.receiving methods: Receive → receiveBox, Not Received → markNotReceived, Damaged → rejectBox(..., 'Damaged at dock')
- [ ] 5.7 Add feedback banner (.banner ok/warn/stop) for success/error messages

## 6. Testing & Verification

- [ ] 6.1 Run ./gradlew :backend:test --tests ReceivingE2ETest (verify green)
- [ ] 6.2 Run npm run build in dashboard/web (catch type errors)
- [ ] 6.3 Manually test end-to-end: import a test consignment, open Receiving tab, select lot from picker, scan cartons, mark some as not-received/damaged, verify box list updates and progress counter changes, press Done
- [ ] 6.4 Verify GET /api/lots returns correct counts (curl against dev backend)
- [ ] 6.5 Verify POST /api/lots/{id}/mark-not-received transitions box to NOT_RECEIVED state (curl against dev backend)

## 7. Cleanup & Commit

- [ ] 7.1 Remove any old stub code or comments from Receiving.tsx
- [ ] 7.2 Commit backend changes
- [ ] 7.3 Commit frontend changes
