# Spec: Manual Lot Creation

Operators create manual (manifest-free) lots through Lot Management screen.

## Requirements

1. **Create manual lot via API**
   - Endpoint: POST /api/lots/manual
   - Body: {supplier, receivedOn, amountPaidPaise, allocationMethod}
   - Response: LotSummaryDto with id, isManual=true
   - Validation: supplier not blank, date valid, amount > 0, allocation method valid
   - Status: 201 Created on success, 400 Bad Request on validation error

2. **Lot Management screen exists**
   - Route: /lots
   - Header: "Lot Management"
   - Tabs: "In Progress" and "Complete" (same as Receiving)
   - List: open lots only (or both tabs)
   - Each lot card shows: supplier, date, amount, type (manual indicator), progress bar
   - Type indicator: (M) or "manual" badge for isManual=true

3. **Create Lot button and modal**
   - Button: "Create Lot" (top right)
   - Modal form: supplier (text), date (date picker), amount (number), type (dropdown: Manifest / Manual)
   - When type=Manual: allocation method defaults to RELATIVE_MRP (hidden)
   - Submit: POST /api/lots/manual → close modal, refresh list
   - Error handling: show validation errors in modal, don't close
   - Cancel: close modal without action

4. **Manual lots appear in Receiving overview**
   - Receiving GET /api/lots includes manual lots
   - Manual lots show (M) badge or "manual" label in overview
   - Tappable to open detail (product entry form)

## Test Scenarios

1. **SC-1: Create manual lot with valid data**
   - Given: Lot Management screen open
   - When: Enter supplier "Liquidation Co", date "2026-07-26", amount "₹50,000", type "Manual", submit
   - Then: Modal closes, lot appears in list with (M) badge, isManual=true in API response

2. **SC-2: Validation: supplier required**
   - Given: Modal open
   - When: Leave supplier blank, submit
   - Then: Error "Supplier required" shown in modal, form not submitted

3. **SC-3: Validation: amount > 0**
   - Given: Modal open, supplier filled
   - When: Enter amount "0" or negative, submit
   - Then: Error "Amount must be greater than 0" shown

4. **SC-4: Created lot appears in Receiving**
   - Given: Manual lot created
   - When: Open Receiving screen
   - Then: Lot listed in overview with (M) badge and 0/0 progress

5. **SC-5: Manual lot grouped correctly**
   - Given: Create manual lot with receivingComplete=false
   - When: Open Lot Management, click "In Progress" tab
   - Then: Manual lot shown under In Progress, not Complete

6. **SC-6: Manifest and manual lots coexist**
   - Given: Manifest lot and manual lot both exist
   - When: List lots
   - Then: Both shown, each with correct type indicator

## Edge Cases

- Lot creation during network outage (graceful fail, show error)
- Very long supplier name (truncate in display or allow overflow)
- Date in past vs future (allow both; no validation)
- Large amount (₹999,999,999 paise = ₹9,999,999.99; ensure no overflow)
