# Spec: Manual Product Entry in Receiving

Operators enter products one by one for manual lots in Receiving detail screen.

## Requirements

1. **Manual lot detail shows product-entry form (not box scanner)**
   - When lot.isManual=true, detail view shows product entry form instead of box scanner
   - Form fields: code (optional), name (required), category (required, dropdown), quantity (required, > 0), estimated cost (optional)
   - "Add Product" button below form
   - Product list shows all entered products with running totals
   - "Done" button (enabled when ≥1 product entered, visible when all products received)

2. **Add Product via API**
   - Endpoint: POST /api/lots/{lotId}/add-product
   - Body: {code, name, quantity, categoryCode, estimatedCostPaise}
   - Response: {success, totalProducts, totalQuantity, allocationStatus}
   - Validation: name not blank, quantity > 0, category valid
   - Status: 201 Created on success, 400 Bad Request on validation error

3. **Products accumulate in list**
   - Each entry shows: code (if provided), name, category, quantity, (estimated cost if provided)
   - Running totals: "N products, M total units" shown above list
   - Products marked received immediately (no pending state)

4. **Cost allocation display**
   - Show "Cost per unit: ₹X.XX" (total amount / total quantity, updated as products added)
   - Optional: show per-product estimated cost if provided
   - Note: exact allocation happens at lot close (may re-weight by actual final counts)

5. **Done button closes lot**
   - When: operator clicks Done
   - Then: POST to close lot (or mark receivingComplete), detail closes to overview
   - List refreshes showing lot moved to Complete tab (receivingComplete=true)

## Test Scenarios

1. **SC-1: Add first product**
   - Given: Manual lot opened in Receiving detail, form empty
   - When: Enter name "Face Cream", category "Personal Care", qty "50", submit
   - Then: Product appears in list, "1 product, 50 units" shown, form clears for next entry

2. **SC-2: Multiple products accumulate**
   - Given: One product entered
   - When: Add another "Body Lotion", qty "30", submit
   - Then: Both products shown, "2 products, 80 units" displayed

3. **SC-3: Cost per unit updates**
   - Given: Lot total ₹50,000, entered 1 product qty 100
   - When: View cost display
   - Then: Shows "Cost per unit: ₹500"
   - When: Add second product qty 50
   - Then: Updates to "Cost per unit: ₹333.33" (50000/150)

4. **SC-4: Code optional**
   - Given: Product entry form
   - When: Leave code blank, enter name + category + qty, submit
   - Then: Product added successfully (code remains null)

5. **SC-5: Category from dropdown**
   - Given: Category dropdown open
   - When: Select "KITCHEN"
   - Then: Category set, product can be added with KITCHEN category

6. **SC-6: Validation: name required**
   - Given: Form with empty name
   - When: Submit
   - Then: Error "Name required" shown, product not added

7. **SC-7: Validation: qty > 0**
   - Given: Form with qty "0"
   - When: Submit
   - Then: Error "Quantity must be greater than 0", product not added

8. **SC-8: Done button visible after products added**
   - Given: Manual lot detail, no products
   - When: View Done button
   - Then: Button disabled (greyed out) or hidden
   - When: Add one product
   - Then: Done button enabled and visible

9. **SC-9: Done button closes lot**
   - Given: Manual lot with products entered
   - When: Click Done
   - Then: Lot closed (receivingComplete=true), detail closes, lot moves to Complete tab

10. **SC-10: Done button not visible for manifest lots**
    - Given: Manifest lot opened in Receiving
    - When: View detail
    - Then: Done button not visible (manifest lots close when all boxes terminal)

## Edge Cases

- Product name with special characters (allow)
- Very large quantity (₹999,999 units; ensure no overflow)
- Same product added twice (allow; operator responsible for dedup)
- Add product, then navigate away (data saved per POST; no pending state)
- Estimated cost provided (include in response, used at lot close)
