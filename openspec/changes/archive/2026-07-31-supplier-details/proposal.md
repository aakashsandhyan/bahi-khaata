## Why

Today a supplier is a free-typed `text` field repeated on every `lot` row. The same
vendor is re-entered by hand at each receipt, so casing and spacing drift ("ABC", "abc ",
"ABC Traders") fragment the same supplier into many, and there is nowhere to hold a
supplier's GSTIN, phone, address, or contact — details the store needs for purchase
records and GST paperwork. Promoting the supplier to a first-class entity gives one
canonical record per vendor and a place for those details.

## What Changes

- Introduce a `supplier` entity (UUID PK, matching every other table) with name, GSTIN,
  phone, address, contact person, notes, and an `active` soft-delete flag.
- `lot` gains a `supplier_id` foreign key. A backfill migration creates one supplier per
  distinct existing `lot.supplier` string (normalized: trim, collapse spaces,
  case-insensitive) and links every lot to its new row.
- The legacy `lot.supplier` text column is **kept as-is** (frozen snapshot) for now.
  Dropping it is a deliberate later change once the FK is trusted — **not** in scope here.
- Suppliers are managed on a new dashboard page: list/search, create, edit, deactivate,
  and view the lots received from a supplier.
- Receipt entry points (manual lot create, manifest import) switch from a free-typed
  supplier string to **strict pick-from-list**: they take a `supplierId` that must
  reference an existing active supplier. New suppliers are created only on the CRUD page
  first — **BREAKING** for the receipt request contracts.
- Uniqueness is hybrid: GSTIN is unique when present (partial index), and normalized name
  is unique — GSTIN is the legal identity, name the fallback for the many small vendors
  with no GSTIN.

## Capabilities

### New Capabilities
- `supplier-management`: the supplier entity, its identity/uniqueness and validation rules
  (including GSTIN format), soft-delete semantics, the backfill of existing lot suppliers,
  the lot→supplier link, and the receipt-time constraint that a lot must reference an
  existing active supplier.

### Modified Capabilities
<!-- None. No existing spec (goods-remediation, product-catalog) governs lot suppliers or receipt supplier entry. -->

## Impact

- **Schema / migrations**: new `supplier` table + indexes; new `lot.supplier_id` column;
  backfill Flyway migration. Legacy `lot.supplier` retained.
- **Backend**: new `Supplier` entity, repository, `SupplierService`, `SupplierController`;
  `Lot` gains the supplier association; `CreateManualLotRequest` and
  `ImportConsignmentRequest` replace the supplier string with `supplierId` and validate it;
  new supplier DTOs in the contracts module.
- **Frontend (dashboard/web)**: new Suppliers page (list/create/edit/deactivate/detail);
  receipt, manual-lot, and review-queue supplier inputs become pick-from-list dropdowns;
  `types.ts` and `api.ts` extended.
- **Out of scope**: dropping `lot.supplier`, merging mis-typed backfilled suppliers
  (a future CRUD-screen action), and any supplier-side ledger/khaata balance.
