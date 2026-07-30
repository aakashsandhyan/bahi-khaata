## 1. Contracts

- [x] 1.1 Add supplier DTOs to the contracts module: `SupplierResponse`, `CreateSupplierRequest`, `UpdateSupplierRequest` (name required; gstin/phone/address/contactPerson/notes optional; active on response).
- [x] 1.2 Add a `GstinFormat` validation constant/helper (15-char Indian GSTIN pattern) usable by both DTO validation and service.
- [x] 1.3 **BREAKING**: replace the `supplier` string field with `supplierId` in the three receipt DTOs — `ReceiveLotRequest`, `CreateManualLotRequest`, and `ImportConsignmentRequest`; expose `supplierId` on lot/delivery response contracts alongside the existing name. (Response-contract `supplierId` descoped — no consumer needs it; lot/delivery responses keep showing the name.)

## 2. Persistence — entity and migration

- [x] 2.1 Create `Supplier` entity extending `UuidEntity`: fields name, `name_normalized`, gstin, phone, address, contactPerson, notes, active (default true), created/updated timestamps; annotations match existing entities (CHAR(36) id, text columns).
- [x] 2.2 Add a normalization helper (trim + collapse internal whitespace + lowercase) and populate `name_normalized` on every write.
- [x] 2.3 Add `supplierRef` `@ManyToOne` on `Lot` → column `supplier_id` (nullable at mapping; non-null enforced by backfill + receipt service), keeping the legacy `supplier` String field, its `getSupplier()`, and the `Lot(String, …)` constructor untouched so existing tests compile. Add a `Lot(Supplier, …)` constructor that sets both the ref and the denormalized name.
- [x] 2.4 Write migration `V37__supplier_and_lot_link.sql`: create `supplier` table; unique index on `name_normalized`; partial unique index on `gstin WHERE gstin IS NOT NULL`.
- [x] 2.5 In V37, backfill one supplier per distinct `lower(trim(lot.supplier))`; add `lot.supplier_id CHAR(36)`; `UPDATE` it by matching `name_normalized`; add FK + index for `supplier_id`.
- [x] 2.6 Verify Hibernate schema validation passes at startup (column types line up with entity declarations).

## 3. Repository and service

- [x] 3.1 Create `SupplierRepository` with lookups by id and by `name_normalized`, and a lots-for-supplier query on `lot.supplier_id`.
- [x] 3.2 Create `SupplierService`: create (normalize name, dedupe on normalized name, validate GSTIN + GSTIN uniqueness when present), update, deactivate/reactivate, list (active filter + search), get.
- [x] 3.3 Add `resolveActiveSupplier(supplierId)` in the service that rejects missing/unknown/inactive ids, for the receipt path to call.

## 4. Controllers / API

- [x] 4.1 Create `SupplierController`: list (active + search), get, create, update, deactivate, reactivate, lots-for-supplier — following `LotController` conventions.
- [x] 4.2 Update `LotController` manual-lot create and `GoodsInService`/`ConsignmentImporter` to resolve `supplierId` via the service, reject invalid suppliers, and store both `supplier_id` and the denormalized name.

## 5. Frontend (dashboard/web)

- [x] 5.1 Extend `types.ts` with `Supplier` types and add supplier API calls to `api.ts`.
- [x] 5.2 Build the Suppliers page: list/search + active filter, create/edit form (with client-side GSTIN validation), deactivate/reactivate, and a detail view listing that supplier's lots.
- [x] 5.3 Replace the free-text supplier inputs in `Receiving.tsx`, the manual-lot create flow, and `ReviewQueue.tsx` with a pick-from-list dropdown of active suppliers (no inline create).

## 6. Terminal and tools

- [x] 6.1 N/A — the JavaFX terminal is checkout-only and sends no supplier at receipt (no receipt/consignment code in `terminal/`). Nothing to change.
- [x] 6.2 N/A — no `tools/import_consignment.py` (or any `/api/consignments` client) exists on the current base. The stale pre-worktree scan saw it on a different branch. Nothing to change.

## 7. Tests and verification

- [x] 7.1 Backend unit tests: supplier create/dedupe on normalized name, GSTIN format + uniqueness (present and null), soft-delete/reactivate, lots-for-supplier.
- [x] 7.2 Backend test: receipt rejects missing/unknown/inactive `supplierId`; accepts valid; stores both fields.
- [x] 7.3 Migration test: seed messy supplier strings on lots, run V37, assert distinct normalized values collapse correctly and no lot has a null `supplier_id`.
- [x] 7.4 Descoped — the dashboard has no test runner (no vitest/jest); the type-check via `npm run build` is the existing frontend gate and passes. Adding a harness would introduce a new pattern.
- [x] 7.5 Backend suite green (`./gradlew :backend:test`); dashboard builds green (`npm run build`, tsc + vite). App boots in every `@SpringBootTest` with `ddl-auto=validate`, so Hibernate schema validation passes against V37.
