## Why

`Bachat_Bazar_POS_Report.md` settles what the system should be, but no code exists yet, so every customer-facing flow — checkout, goods-in, labelling — is blocked on a data model that does not exist. This change builds that foundation and closes the structural decisions the report deliberately left open, because they are nearly free to make now and expensive to retrofit: primary key strategy, component boundaries, data access, and the invariants that keep inventory costing and GST invoices correct. It then drives a single sale all the way through the resulting stack, because a foundation that has never carried weight is an assumption rather than a result.

## What Changes

- **Repository becomes a Gradle multi-project build** with four modules — `contracts` (shared DTOs only), `backend`, `terminal`, `dashboard`. Declared as real modules from the start so boundaries are enforced by the build rather than by convention, keeping any module cheap to detach into its own repository later.
- **Backend owns the data; the terminal is a client.** The JavaFX terminal reaches SQLite only through the Spring Boot backend over localhost HTTP. Nothing but `backend` opens a database connection. This keeps one source of truth for business logic and makes the eventual move to a real server a configuration change. Offline-first is unaffected — localhost is not the network.
- **Hibernate/JPA** as the data access layer, resolving report §11.1.
- **Automated, versioned schema migrations** replace any manual or hand-applied schema step. Chromis POS stalled precisely because manual migrations let installations drift apart; this is treated as a hard requirement, not tooling preference.
- **Core schema introduced**: products with a hybrid relational + JSON attribute model, batches/lots carrying per-delivery cost, an append-only stock ledger, and invoice records structured to be immutable once issued. UUIDv4 primary keys throughout.
- **Boundary violations fail the build** via ArchUnit tests, so detachability is verified rather than assumed.
- **Project made self-hostable and licensed**: AGPL-3.0 text and headers, a Docker setup for the backend, and `jpackage` packaging for the terminal.
- **One working sale, end to end** — a deliberately thin vertical slice: receive a lot, scan a product, add it to a cart, finalise the sale. Finalising writes an immutable invoice, appends a ledger row, and consumes stock FIFO from the oldest batch.

The slice exists to prove the foundation rather than to be the product. It is the only way to establish that the riskiest decisions actually hold together — Hibernate's community-maintained SQLite dialect, JSON attribute mapping, integer-paise money, the immutability triggers, and FIFO consumption — while changing them is still cheap. A design validated only on paper defers that discovery to the point where real invoice data makes it expensive.

Scope of the slice is held down deliberately: no printing, no payment tender, no label generation, no error-state or edge-case handling, no dashboard, and no visual polish. Barcode scanning is included because USB-HID scanners present as keyboards and therefore cost almost nothing.

Goods-in is the exception, and deliberately so. Because stock is bought as a lot for a single sum, per-product cost has to be allocated rather than entered, and that arithmetic — proportional allocation, pinned costs, exact reconciliation to the amount paid, damaged units — is where the financial risk of the whole change concentrates. It is built properly here and covered by tests. Its screens stay rough; the review queue for margin-flagged products, the tagging decision, and label-print blocking belong to the full intake flow later. The full versions of checkout, goods-in, labelling, payments, and reporting all land in later changes. Nothing here is breaking — the project is greenfield.

## Capabilities

### New Capabilities

- `product-catalog`: What a product *is* and the invariants that hold regardless of how it is created or edited — identity and barcode assignment (including internally generated Code 128 codes that cannot collide with manufacturer codes), the category-varying attribute model, and selling price as a single product-level value that never lives on a batch. Structural rules only; the goods-in and price-edit flows that exercise them come later.
- `stock-ledger`: How stock movement and cost are recorded and derived — batches as the unit of arrival cost, every movement appended and never mutated, FIFO consumption order, backdated entries recalculating forward rather than editing history, and point-in-time stock valuation and cost of goods sold.
- `invoicing`: What makes an invoice legally sound and permanently trustworthy — immutability once issued, corrections as linked reversing entries, the GST fields every invoice must carry, per-financial-year consecutive numbering, and rupee rounding under CGST Act §170. Schema leaves room for IRN and signed QR payloads without building the integration.
- `sales-checkout`: The sale itself, as exercised by the vertical slice — identifying a product by scanned code, building a cart, and finalising it into an invoice with its ledger and stock effects applied atomically. Scoped to the thin path only; tender, returns, discounts, held sales, and error recovery are specified by later changes as they are built.

### Modified Capabilities

None. `openspec/specs/` is empty; this change establishes the first specs.

## Impact

- **Repository layout**: root Gradle build plus four module directories, replacing the current flat structure of a single report file.
- **New dependencies**: Spring Boot, Hibernate/JPA, a migration tool, SQLite JDBC, JavaFX, ArchUnit, and `jpackage` for terminal packaging.
- **New contract surface**: the localhost HTTP interface between `terminal` and `backend`, together with the handful of endpoints the slice needs. Establishing these against a real running terminal, rather than in the abstract, is what makes the boundary trustworthy — a contract nothing has consumed yet is a guess.
- **Downstream**: every later change builds on this schema, so errors in the ledger and invoice invariants are costly to correct once real transaction data exists.
- **Existing code**: none affected.
