## Purpose

The Intake hub: one screen for receiving boxes, watching a lot's count, reconciling shorts and overs, and closing the delivery — with lot math computed honestly from what has actually been counted and priced. Established by the palletworks-intake change.

## Requirements

### Requirement: Lot rail lists open lots and drives every section
The Intake screen SHALL render a lot rail of open lots only (from `receiving.lots()` / `GET /api/lots`, which already filters to `Lot::isOpen`), each entry showing the lot's supplier, received date, and a state badge reflecting its inferred step. Exactly one lot SHALL be selected at a time, and the selected lot SHALL drive the header stats, the step strip, all three tabs, and the lot-math rail — no section SHALL show data for any lot other than the selected one. A lot that closes SHALL drop off the rail on its own, because the source query returns open lots only.

#### Scenario: Selecting a lot drives every section
- **WHEN** the operator selects a lot in the rail
- **THEN** the header stats, step strip, active tab, and lot-math rail all render that lot's data, and no other lot's data is shown

#### Scenario: The rail lists only open lots
- **WHEN** the rail renders
- **THEN** only open lots appear, each with its supplier, received date, and a state badge, and no closed lot is listed

#### Scenario: A closed lot leaves the rail
- **WHEN** a lot is closed
- **THEN** it is no longer listed in the rail on the next load, because the rail's source returns open lots only

### Requirement: Header stats come from the lot stats endpoint and never divide by zero
The Intake header SHALL show, for the selected lot, four figures sourced from `GET /api/lots/{lotId}/stats`: amount paid, MRP found (cumulative MRP discovered over counted units, not a manifest total, per D6), cost-of-MRP percent (paid divided by MRP found, labelled provisional), and counted x of y. When nothing has been counted yet — MRP found is zero or no units are counted — the header SHALL render honest dashes for the figures that would otherwise require division, and SHALL NOT emit any divide-by-zero, infinity, or NaN output. The counted x of y figure SHALL always render from expected and counted counts.

#### Scenario: Fully-populated header shows the four figures
- **WHEN** the selected lot has counted units and discovered MRP
- **THEN** the header shows amount paid, MRP found, a provisional cost-of-MRP percent, and counted x of y

#### Scenario: Nothing counted yet shows honest dashes, not divide-by-zero
- **WHEN** the selected lot has no counted units and no MRP found
- **THEN** the cost-of-MRP percent renders as a dash rather than a computed value, and no divide-by-zero, infinity, or NaN is displayed

#### Scenario: Counted progress always renders
- **WHEN** the selected lot's stats are shown
- **THEN** counted x of y is displayed from the expected and counted counts regardless of whether any money figure is available

### Requirement: The step strip is inferred frontend-side with no schema change
Intake SHALL display a four-step strip — (1) Manifest in / Manual, (2) Counting, (3) Reconcile, (4) Close — inferred on the frontend from `LotSummary` and `DeliveryProgress`, with no new persisted state column. Step 1 SHALL be past once the lot exists. Counting SHALL be the active step while a manifest lot has non-terminal boxes or `unitsCounted < unitsExpected`; for a manual lot, which has no boxes to reach a terminal state, `receivingComplete` SHALL be the gate out of Counting. Reconcile SHALL be reached when counting is done but the lot is still open. Close SHALL be the terminal action, not a resting listed state — a closed lot has already left the rail, so no closed step is shown for a rail lot.

#### Scenario: A manifest lot mid-count sits on Counting
- **WHEN** a selected manifest lot has non-terminal boxes or fewer units counted than expected
- **THEN** the step strip marks Counting as the active step

#### Scenario: A manual lot advances on receiving-complete, not boxes
- **WHEN** a selected manual lot has no boxes and its receiving is not yet complete
- **THEN** Counting is the active step, and it advances only when `receivingComplete` is set, not by any box reaching a terminal state

#### Scenario: A fully-counted open lot reaches Reconcile
- **WHEN** a selected lot's counting is done and the lot is still open
- **THEN** the step strip marks Reconcile as the active step and Close as the remaining action

### Requirement: Boxes tab preserves the existing receive flow behavior
The Boxes tab SHALL present the existing door-receiving flow for the selected lot — the box list with receive, not-received, and reject (damaged) actions — extracted from the retired Receiving screen with behavior identical to before this change. It SHALL call the same `receiving.*` endpoints, and no receiving behavior, box state transition, or write path SHALL change as a result of relocating the flow into Intake.

#### Scenario: Box actions behave exactly as before
- **WHEN** the operator receives, marks not-received, or rejects a box in the Boxes tab
- **THEN** the same `receiving.*` endpoint is called and the box transitions exactly as it did on the retired Receiving screen, with no behavior change

#### Scenario: The box list renders for the selected lot
- **WHEN** a manifest lot is selected and the Boxes tab is active
- **THEN** the tab lists that lot's boxes with their state badges and the receive / not-received / damaged actions

### Requirement: Lines tab shows manifest reconciliation columns only
For a manifest lot, the Lines tab SHALL render from `catalog.browse(…, lot)` exactly four columns: Product, Expected (manifest), Counted, and Δ (counted minus expected). It SHALL NOT show a grade or condition column (condition is a per-batch fact absent from `CatalogEntry`, and there is no grade at intake), and it SHALL NOT show an MRP or list-price column (neither is on `CatalogEntry`, and there is no list-price-at-intake), per D8.

#### Scenario: Lines tab renders the four reconciliation columns
- **WHEN** a manifest lot is selected and the Lines tab is active
- **THEN** each line shows Product, Expected, Counted, and Δ computed as counted minus expected

#### Scenario: No grade, MRP, or price columns appear
- **WHEN** the Lines tab renders
- **THEN** no grade or condition column, no MRP column, and no list-price column is present

### Requirement: Manual lots use counting-is-the-manifest framing
For a manual lot, Intake SHALL replace the manifest table with discovered lines — the products found so far — and SHALL offer an add-product entry so counting itself builds the record. The provisional cost per unit SHALL be shown as paid divided by counted, framed as provisional, and SHALL fall as counting continues. Intake SHALL make clear that for a manual lot counting is the manifest — there is no expected line to reconcile against until a product is discovered.

#### Scenario: Discovered lines replace the manifest table
- **WHEN** a manual lot is selected
- **THEN** Intake shows the discovered lines and an add-product entry rather than a manifest expected/counted table

#### Scenario: Provisional cost per unit falls as counting continues
- **WHEN** more units are counted into a manual lot
- **THEN** the provisional cost per unit (paid divided by counted) is recomputed lower and is labelled provisional

### Requirement: Reconcile & close tab surfaces the close gate honestly
The Reconcile & close tab SHALL show the lot's shorts and overs and its paid-versus-pinned cross-check (from the stats endpoint), the list of unopened cartons (from `GET /api/unpacking/lots/{lotId}/unopened`), and a Close action wiring `POST /api/unpacking/lots/{lotId}/close`. When cartons remain unopened, the Close action SHALL surface that list and SHALL require a deliberate confirm (`confirm=true`); closing SHALL NOT be blocked, because goods that never arrive would otherwise hold a lot open forever. For a manual lot the tab SHALL offer the receiving-finished action (`markReceivingComplete`) instead of a box-based close gate. The tab SHALL provide a hand-off link into Pricing for counted-awaiting-pricing goods. `LotReconcile` (the pricing-time phantom write-off) SHALL NOT be embedded here — at intake it would report zero.

#### Scenario: Closing over unopened cartons surfaces the list and requires confirm
- **WHEN** the operator invokes Close while cartons remain unopened
- **THEN** the unopened carton list is surfaced and the close proceeds only on a deliberate confirm (`confirm=true`), and closing is not otherwise blocked

#### Scenario: A clean lot closes without a confirm gate
- **WHEN** the operator invokes Close on a lot with no unopened cartons
- **THEN** the lot closes without requiring the extra unopened-cartons confirmation

#### Scenario: A manual lot offers receiving-finished
- **WHEN** the Reconcile & close tab is shown for a manual lot
- **THEN** it offers the receiving-finished action rather than a box-based close gate, and this does not close the lot or alter its stock

#### Scenario: Counted-awaiting-pricing hands off to Pricing
- **WHEN** the operator follows the counted-awaiting-pricing link
- **THEN** Intake hands off to the Pricing screen, and the phantom write-off tool is not embedded in the Intake tab

### Requirement: Right rail shows lot math over counted and priced units only
The lot-math right rail SHALL show, for the selected lot: effective cost per unit computed as amount paid divided by counted units, short and over totals, MRP found, and projected retail computed over priced units only (priced units times price). The projected retail SHALL NOT be extrapolated over unpriced units. When counted units are zero, the effective cost per unit SHALL render as a dash rather than a divide-by-zero. The rail's primary action SHALL be Close for a fully-counted lot or Receiving-finished for a lot still receiving.

#### Scenario: Effective cost per unit is paid over counted
- **WHEN** the selected lot has counted units
- **THEN** the rail shows effective cost per unit as amount paid divided by counted units, plus short and over totals and MRP found

#### Scenario: Projected retail covers priced units only
- **WHEN** only some units of the lot are priced
- **THEN** projected retail is computed over the priced units only and is not extrapolated across unpriced units

#### Scenario: Zero counted shows a dash, not divide-by-zero
- **WHEN** the selected lot has no counted units
- **THEN** the effective cost per unit renders as a dash and no divide-by-zero is displayed

### Requirement: Lot creation is manual-only; manifest import is not a dashboard flow
The rail header SHALL provide a "+ New lot" control opening a create-lot modal that offers manual lots only. Manifest import SHALL NOT be a dashboard flow: manifest lots arrive pre-imported via the backend importer, and Intake SHALL NOT expose a manifest-import path or a manifest option in the create-lot modal. This boundary SHALL hold — the modal SHALL NOT carry a "Manifest-based" option that only ever reports "not yet supported here".

#### Scenario: Manual lot creation from the rail header
- **WHEN** the operator uses "+ New lot" in the rail header
- **THEN** a create-lot modal opens offering manual lots only, and creating one adds it to the rail

#### Scenario: No manifest-import path is exposed
- **WHEN** the create-lot modal is open
- **THEN** it presents no manifest-import option, and Intake exposes no dashboard path to import a manifest

### Requirement: Loading and error states follow the house pattern and degrade the stats endpoint gracefully
Intake SHALL render loading and error states consistent with the other dashboard screens' shared pattern. A failure of the `GET /api/lots/{lotId}/stats` endpoint SHALL degrade only the header stats and the money figures in the lot-math rail — the affected figures SHALL show a non-fatal fallback — while the rail, step strip, Boxes tab, and Lines tab continue to render from their own sources. A stats failure SHALL NOT blank the whole screen.

#### Scenario: Stats failure degrades the header, not the screen
- **WHEN** the stats endpoint fails for the selected lot
- **THEN** the header and rail money figures show a non-fatal fallback while the rail, step strip, and tabs still render from their own sources

#### Scenario: House loading and error states are used
- **WHEN** Intake is loading or a non-stats request errors
- **THEN** it shows the shared loading and error treatment used across the dashboard rather than a bespoke one
