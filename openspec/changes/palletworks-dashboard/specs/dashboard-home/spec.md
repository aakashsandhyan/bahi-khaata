## ADDED Requirements

### Requirement: Aggregate dashboard endpoint
The backend SHALL expose a read-only `GET /api/dashboard` endpoint that returns the entire Dashboard screen as one `DashboardView` payload containing the KPI section, the received → priced → sold funnel, the alerts list, and the recent-sales list. The request SHALL perform no writes and SHALL NOT mutate any row; all figures SHALL be derived from existing data via read queries. A single request SHALL return every section together so the tiles can never disagree mid-refresh.

#### Scenario: One request returns the whole screen
- **WHEN** a client issues `GET /api/dashboard`
- **THEN** the response is a single `DashboardView` payload carrying the `kpis`, `funnel`, `alerts`, and `recentSales` sections, and no database row is created or modified

### Requirement: Revenue-today KPI
The KPI section SHALL report revenue for the current IST calendar day as `SUM(sale.total_paise)` over sales whose `created_at` falls in the day window, together with the count of bills in that window and the average bill value. The "today" window SHALL be an IST calendar day (`Asia/Kolkata`) converted to two UTC ISO-8601 instant strings and applied as a half-open text range `created_at >= :startUtc AND created_at < :endUtc`. When there are zero bills in the window the average SHALL be omitted (dashed), never reported as ₹0.

#### Scenario: Sales exist today
- **WHEN** one or more sales fall within today's IST window
- **THEN** the revenue-today tile shows the summed `total_paise`, the bill count, and the average bill value for those sales

#### Scenario: No sales today
- **WHEN** no sale falls within today's IST window
- **THEN** the revenue-today tile shows ₹0 revenue with a zero bill count and a dashed (omitted) average rather than a ₹0 average

### Requirement: Received-vs-priced KPI
The KPI section SHALL report received units against priced units and the unpriced backlog count, so the operator sees how much of what was received has been given a shelf price and how much still waits. Priced units SHALL be counted as on-hand units for products carrying a `selling_price_paise`; the unpriced backlog SHALL be the count of counted units still lacking a `selling_price_paise`.

#### Scenario: Received and priced units reported with backlog
- **WHEN** the dashboard is requested
- **THEN** the received-vs-priced tile shows received units, priced units, and the count of unpriced counted units awaiting a price

### Requirement: Recovery KPI
The KPI section SHALL report all-time recovery as all-time `SUM(sale.total_paise) ÷ SUM(lot.amount_paid_paise)`, with a sub-line showing both the all-time revenue and the total lot amount paid that form the ratio. When there are no lots (total amount paid is zero) the tile SHALL show no ratio rather than dividing by zero.

#### Scenario: Recovery ratio from revenue over lot spend
- **WHEN** at least one lot has a recorded amount paid
- **THEN** the recovery tile shows the ratio of all-time revenue to total lot amount paid, with both figures on the sub-line

#### Scenario: No lots yet
- **WHEN** no lot has any amount paid recorded
- **THEN** the recovery tile shows no ratio and does not divide by zero

### Requirement: GST-collected KPI
The KPI section SHALL report GST collected as `SUM(sale.tax_paise)`. While that value is structurally zero (because GST is not yet computed until the separate gst-inclusive-pricing change ships), the tile MUST carry a "not yet computed" label so the ₹0 is never read as a real revenue-neutral figure.

#### Scenario: Zero GST carries the honesty label
- **WHEN** `sale.tax_paise` sums to zero across all sales
- **THEN** the GST tile shows ₹0 accompanied by a "not yet computed" label rather than a bare ₹0

### Requirement: Received → priced → sold funnel
The funnel section SHALL report three stages computed from single-pass ledger queries: received units = `SUM(quantity)` where `movement_type='PURCHASE_RECEIPT'`; priced/on-floor units = on-hand `SUM(quantity)` for products with `selling_price_paise` set; sold units = `SUM(-quantity)` where `movement_type='SALE'`. Each stage SHALL also carry its MRP value derived from `batch.mrp_paise`. The queries SHALL avoid per-row loading (no N+1) over the append-only ledger.

#### Scenario: Three stages with unit and MRP value
- **WHEN** the dashboard is requested
- **THEN** the funnel shows received, priced-on-floor, and sold unit counts, each with its MRP value, from single-pass ledger queries

### Requirement: Decision alerts from real signals
The alerts section SHALL contain only the five real signals, each computed as a real count and each carrying the `View` it opens: unpriced counted units → Pricing; NEEDS_WORK backlog → Prep; pending product captures → Review; print jobs in `review` status → Review (the label review queue renders on the Review screen, not Reprint); open lots (`receiving_complete=0`) whose `received_on` is older than the staleness constant → Lots. A signal whose count is zero SHALL be omitted from the list, never shown as a "0" row. Clicking an alert row SHALL navigate to that signal's owning screen through the existing view switch.

#### Scenario: Signal present shows a row that navigates
- **WHEN** a signal has a non-zero count
- **THEN** its alert row appears with that count, and clicking it navigates to the owning screen via the existing view switch

#### Scenario: Zero-count signal is omitted
- **WHEN** a signal's count is zero
- **THEN** no row for that signal appears in the alerts list

### Requirement: Recent sales rail
The recent-sales section SHALL list the latest five sales, obtained through the checkout component's public API (`Checkout.recentSales(5)`) rather than by querying another package's repository directly.

#### Scenario: Latest five sales listed
- **WHEN** the dashboard is requested
- **THEN** the recent-sales rail shows the five most recent sales sourced from the checkout component's public API

### Requirement: Dashboard renders the Palletworks language
The `Dashboard.tsx` screen SHALL render the aggregate payload using the existing design-language classes and tokens (KPI strip, funnel bars, alert list, recent-sales rail), and SHALL NOT introduce a new one-off styling system.

#### Scenario: Screen reuses the shared design language
- **WHEN** the Dashboard screen renders
- **THEN** its tiles, bars, and lists use the existing shared design-language classes rather than any new bespoke styling

### Requirement: Dashboard error state stays navigable
When the aggregate request fails, the Dashboard screen SHALL show a plain error state and SHALL keep the app's navigation usable so the operator can move to any other screen; a failing aggregate SHALL NOT wedge the shell.

#### Scenario: Aggregate failure shows a plain error
- **WHEN** the `GET /api/dashboard` request fails
- **THEN** the Dashboard shows a plain error message and the sidebar navigation remains usable to reach other screens
