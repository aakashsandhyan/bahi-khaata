## 1. Contracts

- [ ] 1.1 Add supplier DTOs to the contracts module: `SupplierResponse`, `CreateSupplierRequest`, `UpdateSupplierRequest` (name required; gstin/phone/address/contactPerson/notes optional; active on response).
- [ ] 1.2 Add a `GstinFormat` validation constant/helper (15-char Indian GSTIN pattern) usable by both DTO validation and service.
- [ ] 1.3 **BREAKING**: replace the `supplier` string field with `supplierId` in `CreateManualLotRequest` and `ImportConsignmentRequest`; update `DeliveryProgress`/lot response contracts to also expose `supplierId`.

## 2. Persistence — entity and migration

- [ ] 2.1 Create `Supplier` entity extending `UuidEntity`: fields name, `name_normalized`, gstin, phone, address, contactPerson, notes, active (default true), created/updated timestamps; annotations match existing entities (CHAR(36) id, text columns).
- [ ] 2.2 Add a normalization helper (trim + collapse internal whitespace + lowercase) and populate `name_normalized` on every write.
- [ ] 2.3 Add `supplier_id` association on `Lot` (`@JoinColumn(name = "supplier_id", nullable = false)`), keeping the legacy `supplier` text field written as the denormalized name.
- [ ] 2.4 Write migration `V34__supplier_and_lot_link.sql`: create `supplier` table; unique index on `name_normalized`; partial unique index on `gstin WHERE gstin IS NOT NULL`.
- [ ] 2.5 In V34, backfill one supplier per distinct `lower(trim(lot.supplier))`; add `lot.supplier_id CHAR(36)`; `UPDATE` it by matching `name_normalized`; add FK + index for `supplier_id`.
- [ ] 2.6 Verify Hibernate schema validation passes at startup (column types line up with entity declarations).

## 3. Repository and service

- [ ] 3.1 Create `SupplierRepository` with lookups by id and by `name_normalized`, and a lots-for-supplier query on `lot.supplier_id`.
- [ ] 3.2 Create `SupplierService`: create (normalize name, dedupe on normalized name, validate GSTIN + GSTIN uniqueness when present), update, deactivate/reactivate, list (active filter + search), get.
- [ ] 3.3 Add `resolveActiveSupplier(supplierId)` in the service that rejects missing/unknown/inactive ids, for the receipt path to call.

## 4. Controllers / API

- [ ] 4.1 Create `SupplierController`: list (active + search), get, create, update, deactivate, reactivate, lots-for-supplier — following `LotController` conventions.
- [ ] 4.2 Update `LotController` manual-lot create and `GoodsInService`/`ConsignmentImporter` to resolve `supplierId` via the service, reject invalid suppliers, and store both `supplier_id` and the denormalized name.

## 5. Frontend (dashboard/web)

- [ ] 5.1 Extend `types.ts` with `Supplier` types and add supplier API calls to `api.ts`.
- [ ] 5.2 Build the Suppliers page: list/search + active filter, create/edit form (with client-side GSTIN validation), deactivate/reactivate, and a detail view listing that supplier's lots.
- [ ] 5.3 Replace the free-text supplier inputs in `Receiving.tsx`, the manual-lot create flow, and `ReviewQueue.tsx` with a pick-from-list dropdown of active suppliers (no inline create).

## 6. Terminal and tools

- [ ] 6.1 Update the JavaFX terminal, if it sends a supplier at receipt, to pass `supplierId`.
- [ ] 6.2 Update `tools/import_consignment.py` to accept/pass a `supplierId` instead of a supplier string.

## 7. Tests and verification

- [ ] 7.1 Backend unit tests: supplier create/dedupe on normalized name, GSTIN format + uniqueness (present and null), soft-delete/reactivate, lots-for-supplier.
- [ ] 7.2 Backend test: receipt rejects missing/unknown/inactive `supplierId`; accepts valid; stores both fields.
- [ ] 7.3 Migration test: seed messy supplier strings on lots, run V34, assert distinct normalized values collapse correctly and no lot has a null `supplier_id`.
- [ ] 7.4 Frontend tests: supplier CRUD flows, receipt dropdown selection, client-side GSTIN validation.
- [ ] 7.5 Run backend build + tests and frontend build + tests; confirm app starts (schema validation passes).
