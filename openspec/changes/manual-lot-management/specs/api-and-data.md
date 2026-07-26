# Spec: API and Data Model Changes

Backend changes to support manual lot creation and product entry.

## Requirements

1. **Database schema: Add is_manual column**
   - Table: lot
   - Column: is_manual (BOOLEAN, NOT NULL, DEFAULT false)
   - Migration: adds column, backwards compatible (existing lots false)

2. **POST /api/lots/manual endpoint**
   - Request body:
     ```json
     {
       "supplier": "string",
       "receivedOn": "YYYY-MM-DD",
       "amountPaidPaise": "long",
       "allocationMethod": "enum (RELATIVE_MRP | ...)"
     }
     ```
   - Response: LotSummaryDto (same as GET /api/lots response)
   - Validation:
     - supplier: required, non-blank
     - receivedOn: required, valid date
     - amountPaidPaise: required, > 0
     - allocationMethod: required, valid enum
   - Status: 201 Created
   - Creates: Lot entity with isManual=true, freight=0
   - Returns: LotSummaryDto with isManual=true, expected=0 (no boxes)

3. **POST /api/lots/{lotId}/add-product endpoint**
   - Precondition: Lot exists and isManual=true
   - Request body:
     ```json
     {
       "code": "string | null",
       "name": "string",
       "quantity": "long",
       "categoryCode": "string",
       "estimatedCostPaise": "long | null"
     }
     ```
   - Response:
     ```json
     {
       "success": true,
       "totalProducts": "number",
       "totalQuantity": "number",
       "allocationPerUnit": "number (paise)"
     }
     ```
   - Validation:
     - name: required, non-blank
     - quantity: required, > 0
     - categoryCode: required, valid Category enum
     - code: optional
     - estimatedCostPaise: optional, ≥ 0 if provided
   - Status: 201 Created on success, 400 Bad Request on validation, 404 Not Found if lot missing
   - Action: Creates ExpectedLine with code/name/quantity, category lookup/create as needed
   - Idempotent: No (each call adds a new line)

4. **GET /api/lots response updated**
   - Added field: isManual (boolean)
   - Existing: id, supplier, receivedOn, receivingComplete, expected, received, unpacked, rejected, notReceived
   - Expected value for manual lots: 0 (no boxes, so expected=0; received/unpacked/etc. track product counts instead)

5. **Lot.isManual property**
   - JPA entity: add `boolean isManual` field with column annotation
   - Getter: isManual()
   - Used in:
     - LotController.listLots() to populate isManual in response
     - ReceivingController.getBoxesForLot() to return empty boxes list (or special response for manual)
     - Receiving UI (conditional logic: if manual, show product form)

6. **ExpectedLine creation for manual products**
   - Existing entity, reused for manual products
   - Fields used: id (UUID), lot (FK), code (nullable, set if provided), name (required), quantity, receivedQuantity (updated when product entered)
   - Fields NOT used for manual: tracking number (null), box (null for manual lots)

7. **Product lookup and creation**
   - When adding product with categoryCode not yet seen, auto-create category (if not exists)
   - Product creation: if code provided and matches existing product, link; otherwise create new internal product

## Test Scenarios

1. **SC-1: Create manual lot via API**
   - POST /api/lots/manual with {supplier, receivedOn, amountPaidPaise, allocationMethod}
   - Response: 201, LotSummaryDto with isManual=true, expected=0

2. **SC-2: Add product to manual lot**
   - POST /api/lots/{id}/add-product with {name, categoryCode, quantity}
   - Response: 201, {success, totalProducts, totalQuantity}

3. **SC-3: Multiple products, allocation updated**
   - Add 2 products: qty 100 and 50 (total 150)
   - Response shows allocationPerUnit = amountPaid / 150 paise per unit

4. **SC-4: Manual lot in lot list**
   - GET /api/lots returns lot with isManual=true, expected=0

5. **SC-5: Validation: manual lot requires amountPaidPaise > 0**
   - POST with amountPaidPaise=0 → 400 "Amount must be greater than 0"

6. **SC-6: Validation: add-product requires name**
   - POST /add-product with name="" → 400 "Name required"

7. **SC-7: Validation: add-product requires categoryCode**
   - POST /add-product without categoryCode → 400 "Category required"

8. **SC-8: Manifest lot unaffected**
   - POST /api/lots (manifest endpoint) still works (not changed)
   - Response: LotSummaryDto with isManual=false

9. **SC-9: Category auto-creation**
   - Add product with categoryCode="NEW_CATEGORY" (not in enum)
   - System handles gracefully: either rejects or auto-creates (decision per implementation)

10. **SC-10: Product code optional**
    - Add product without code → accepted
    - ExpectedLine.code = null

## Edge Cases

- Adding product to non-existent lot → 404
- Adding product to manifest lot (isManual=false) → should be rejected (validate in endpoint)
- Negative or zero quantity → 400
- Very large quantity (₹999,999) → ensure no overflow (use long, not int)
- Duplicate product names → allowed (no uniqueness constraint per lot)
- Code conflicts with existing product → link to existing product or create new (decision TBD)
