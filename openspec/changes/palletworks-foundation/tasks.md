## 1. Visual foundation

- [x] 1.1 Download Archivo 400/600/800 latin woff2 subsets into `dashboard/web/src/assets/fonts/` and add `@font-face` declarations (`font-display: swap`, system-ui fallback); remove any external font references
- [x] 1.2 Rewrite `styles.css` `:root` with modernist tokens (bg/surface/ink/accent, neutral + accent ramps 100–900, spacing scale, radii 0, shadows); delete retired tokens (`--ink*`, `--brand*`, `--s1..s6`, `--r1..r3`, old status colors)
- [x] 1.3 Port design-system component classes into `styles.css`: base/reset, headings, `hr`, `btn` family, `field`/`input`/`radio`/`seg`, `card`, `tag` variants, `table`, dialog
- [x] 1.4 Re-express all existing screen-specific classes (`.till*`, `.unpack*`, `.prep*`, `.cat*`, `.pcc*`, `.banner`, `.choice`, `.cond*`, `.issue*`, `.flag*`, …) in the new tokens; grep-verify zero occurrences of retired palette values (`#4338ca`, till teal/coral) and token names
- [x] 1.5 Build passes; commit foundation

## 2. App shell

- [x] 2.1 Create `Sidebar.tsx` + nav config (groups: Operations — Receiving, Unpacking, Prep, Pricing, Lots, Review; Selling — Till, Reprint; Back office — Catalog, Suppliers, Printer config; kicker + title per screen); active-entry treatment (inverted bg, accent left bar); operator footer from localStorage
- [x] 2.2 Restructure `App.tsx` to `236px 1fr` grid with sticky sidebar and per-screen sticky header (kicker + title from nav config); view-switching state, screen props, and default screen unchanged; Capture excluded from sidebar
- [x] 2.3 Responsive: ≤760px sidebar hidden behind hamburger overlay drawer; verify Capture phone flow and ≤900px Unpacking stack still work
- [x] 2.4 Build passes; manual click-through of all 12 screens via sidebar; commit shell

## 3. Screen sweep — Selling

- [x] 3.1 Checkout (Till): shared accent replaces teal/coral, `btn`/`table`/`input` classes, tabular-nums cart and totals, scan field styling per design
- [x] 3.2 Reprint: design-system form + queue styling
- [x] 3.3 Commit per screen as completed

## 4. Screen sweep — Operations

- [x] 4.1 Receiving: lot picker, box list, state badges as `tag` variants, progress counter styling
- [x] 4.2 Unpacking: two-column layout with dividers, recent rail as bordered list, condition/issue grids on `seg`/`tag`, floating pane restyle
- [x] 4.3 Prep: backlog/review/extras modes, state mover buttons, tags
- [x] 4.4 Pricing + PricingWorkbench: category rules, MRP confirm, per-item flow; KPI-strip treatment where counters already exist
- [x] 4.5 Lots (+ LotReconcile): lot list, create form, reconcile table
- [x] 4.6 Review queue: capture cards, assign controls, awaiting-labels section
- [x] 4.7 Capture (phone): tokens + form classes on existing stacked layout

## 5. Screen sweep — Back office

- [x] 5.1 Catalog (+ ProductCountPane): dense table with tag chips, detail panel, counting grid
- [x] 5.2 Suppliers: table + CRUD form + lot history
- [x] 5.3 Printer config: form + test-print section
- [x] 5.4 Shared components (`QtyInput`, `BulkPrint`): design-system controls; commit remaining sweep

## 6. e2e harness

- [x] 6.1 Backend `e2e` Spring profile: scratch SQLite under `backend/target/e2e/`, port 8081, Flyway migrates, label-print poller and printer sends disabled
- [x] 6.2 Deterministic seed (fixed UUIDs/barcodes): supplier, lot with boxes, products in received/counted/priced/needs-work states, one completed sale, printer config at unroutable address; loads after migration, fails loudly if schema drifts
- [x] 6.3 Add `@playwright/test`; `playwright.config.ts` with `webServer` array (backend jar + Vite with `VITE_BACKEND=http://localhost:8081`, backend timeout ≥120s, workers=1); `npm run e2e` script; `e2e/seed.ts` constants mirroring fixture
- [x] 6.4 Verify one-command run from clean state boots both servers, runs a trivial probe spec, and shuts down; commit harness

## 7. Smoke tests per screen

- [x] 7.1 Till: loads; keying seeded barcode adds cart line with right price
- [x] 7.2 Receiving: loads; opening seeded lot lists its boxes with state badges
- [x] 7.3 Unpacking: loads; seeded box opens and a line renders
- [x] 7.4 Prep: loads; needs-work backlog shows seeded item
- [x] 7.5 Pricing + workbench: loads; seeded lot appears; product lookup by seeded barcode resolves
- [x] 7.6 Lots: loads; seeded lot row renders; create-lot form opens
- [x] 7.7 Review: loads; seeded capture appears in queue
- [x] 7.8 Reprint: loads; seeded barcode lookup renders label preview/queue row
- [x] 7.9 Catalog: loads; seeded products listed; detail panel opens
- [x] 7.10 Suppliers: loads; seeded supplier row renders; lot history opens
- [x] 7.11 Printer config: loads; saved config displayed (no test print sent)
- [x] 7.12 Capture (phone viewport): loads; form submits into review queue
- [x] 7.13 Shell: sidebar navigation reaches every screen; active state and header follow; ≤760px drawer opens/closes
- [x] 7.14 Full suite green twice in a row (determinism check); commit tests

## 8. Verify & finish

- [x] 8.1 Grep-audit spec scenarios: no retired palette/tokens anywhere; no external font request; radii zero
- [x] 8.2 Manual walk-through of all screens at desktop + 760px + phone width; fix visual fallout
- [x] 8.3 `npm run e2e` green + backend `mvn test` green; update change tasks checkboxes; ready for review/PR
