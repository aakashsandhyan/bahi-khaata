## Context

Suppliers exist today only as a free-typed `text` column on `lot` (migration `V5`,
`NOT NULL`). The string is re-entered by hand at each receipt, so the same vendor
fragments across casing/spacing variants, and there is nowhere to hold GSTIN, phone,
address, or contact. This change promotes suppliers to a first-class entity with a
managed dashboard, links every lot to one, and constrains receipt entry to existing
suppliers. See `proposal.md` for motivation and `specs/supplier-management/spec.md` for
the normative requirements.

Constraints that shape the design:
- SQLite locally; Hibernate schema validation runs at startup and is strict about declared
  column types (`UuidEntity` documents the `CHAR(36)` requirement).
- SQLite cannot add a `NOT NULL` constraint to an existing column without a full table
  rebuild, and has limited `ALTER` support generally.
- The codebase prefers explicit, reviewable code and enforces some invariants in
  application logic rather than DB triggers (see the freeze rule on `Lot`).

## Goals / Non-Goals

**Goals:**
- One canonical `supplier` record per vendor, with the details the store needs.
- Every lot linked to a supplier via FK, with existing data backfilled and no lot left
  unlinked.
- Receipt entry restricted to existing active suppliers (strict pick-from-list).
- A dashboard page to manage suppliers and see their lots.

**Non-Goals:**
- Dropping the legacy `lot.supplier` text column (a later change once the FK is trusted).
- Merging mis-typed backfilled suppliers — a future CRUD-screen action.
- Any supplier-side ledger / payables / khaata balance.
- Inline supplier creation from the receipt screens.

## Decisions

### Entity and identity
`Supplier extends UuidEntity` (UUID stored as `CHAR(36)`), matching every other table.
Fields: `name`, `gstin`, `phone`, `address`, `contactPerson`, `notes`, `active`,
timestamps.

Identity is **hybrid**: normalized name unique, and GSTIN unique when present.
- Normalized name is stored in its own column `name_normalized` and carries a plain
  `UNIQUE` index. Storing the normalized form (rather than a SQL expression index) keeps
  the rule identical between the app write path and the index, and keeps the normalization
  logic in reviewable Java. Normalization = trim, collapse internal whitespace to single
  spaces, lowercase. The display `name` keeps original casing.
- GSTIN uses a **partial unique index**: `CREATE UNIQUE INDEX ... ON supplier(gstin) WHERE
  gstin IS NOT NULL`. SQLite supports partial indexes, so multiple null-GSTIN rows coexist
  while real GSTINs stay unique. *Alternative rejected:* a plain unique index would treat
  every null as distinct in SQLite anyway, but a partial index states the intent and
  guards against a future engine that folds nulls.

GSTIN format is validated in the service layer (15-char Indian pattern) only when present.

### Lot link and the NOT NULL question
`lot` gains `supplier_id CHAR(36)` with a foreign key to `supplier(id)`. The legacy
`lot.supplier` text column is retained and still written (denormalized snapshot of the
chosen supplier's name), so nothing that reads it breaks before the later drop.

The column is left **nullable at the SQLite level** and non-null is enforced by the
Hibernate mapping (`@JoinColumn(nullable = false)`) plus the backfill. Rationale: adding a
real `NOT NULL` constraint to an existing SQLite column requires a 12-step table rebuild
(create-copy-drop-rename), which is risky and noisy against the live `lot` table; the
codebase already enforces comparable invariants in application logic. *Alternative
considered:* full table rebuild to get a hard DB constraint — rejected as
disproportionate for a single-writer local database.

### Backfill (migration V34)
Ordered steps in one migration:
1. `CREATE TABLE supplier (...)` plus the `name_normalized` unique index and the partial
   GSTIN unique index.
2. Insert one supplier per distinct normalized existing `lot.supplier`, using SQLite
   `lower(trim(supplier))` as the group key; `name` = one representative original string,
   `name_normalized` = that key, `gstin` null, `active` = 1.
3. `ALTER TABLE lot ADD COLUMN supplier_id CHAR(36)`.
4. `UPDATE lot SET supplier_id = (SELECT id FROM supplier WHERE name_normalized =
   lower(trim(lot.supplier)))`.
5. Add the FK/index for `supplier_id`.

The SQL-level normalization for the backfill is `lower(trim(...))` — it does not collapse
*internal* double-spaces, because SQLite has no bundled regex. The Java write path does
the fuller collapse for new suppliers. For the small existing dataset this only matters if
two rows differ solely by internal repeated spaces; that lands as two suppliers and is
resolved by the future manual-merge action, not here.

### Receipt path (BREAKING contract change)
`CreateManualLotRequest` and `ImportConsignmentRequest` replace the `supplier` string with
`supplierId`. The service resolves it to an existing supplier and rejects missing /
unknown / inactive ids before creating the lot. On success the lot stores both
`supplier_id` and the denormalized `supplier` name. Frontend receipt inputs become
dropdowns of active suppliers.

### API and frontend
`SupplierController` + `SupplierService` following existing `LotController` structure:
list (active filter + search), get, create, update, deactivate/reactivate, and lots-for-
supplier (query `lot` by `supplier_id`). Supplier DTOs live in the contracts module.
Dashboard gains a Suppliers page; `types.ts` and `api.ts` are extended.

## Risks / Trade-offs

- **Ambiguous backfill grouping** (e.g. "ABC" vs "ABC Traders") → cannot be auto-resolved;
  they remain separate suppliers, cleaned up later via manual merge. Documented as
  out-of-scope, not silently guessed.
- **DB-level nullability of `supplier_id`** → a direct SQL insert bypassing the app could
  create an unlinked lot. Mitigation: all lot creation goes through the service; the invariant
  is covered by tests, consistent with existing app-enforced rules.
- **Breaking receipt contract** → any caller still sending a `supplier` string breaks.
  Mitigation: the callers are in-repo (JavaFX terminal, dashboard, `tools/import_consignment.py`)
  and are updated in the same change.
- **Internal-whitespace normalization mismatch** between SQL backfill and Java writes →
  bounded to pre-existing rows differing only by repeated internal spaces; negligible for
  the current dataset, resolvable by manual merge.

## Migration Plan

- Forward: deploy V34; it creates the table, backfills suppliers, adds and populates
  `supplier_id`. No manual step.
- Rollback: the change is additive at the DB level (legacy `lot.supplier` retained), so
  reverting the application code restores prior behavior; the new table and column can be
  left in place harmlessly or dropped if a clean rollback is required.

## Open Questions

- Search scope on the supplier list — name only, or also GSTIN/phone? (Defaulting to name
  + GSTIN; cheap to widen.)
- Whether `tools/import_consignment.py` should look up a supplier by name and pass its id,
  or require the id directly. (Defaulting to accepting an id, keeping the strict rule.)
