# Goods-in Reconciliation

## MODIFIED Requirements

### Requirement: Completeness is reportable by box

The system SHALL report, for a lot, which boxes are untouched, which are part counted, and which are finished. The Intake screen's Reconcile & close tab SHALL surface this per-box completeness for the selected lot; surfacing it there changes no reporting behavior.

#### Scenario: Untouched boxes are identifiable

- **WHEN** the state of a lot is requested
- **THEN** boxes with no counts recorded are listed as not started

#### Scenario: A finished box is marked as such

- **WHEN** every expected line in a box has been counted and the box is marked finished
- **THEN** it is reported as finished
- **AND** any shortfall or surplus within it remains visible

#### Scenario: Intake surfaces per-box completeness for the selected lot

- **WHEN** a lot is selected in the Intake screen
- **THEN** its Reconcile & close tab shows which of the lot's boxes are not started, part counted, and finished, reading the same report with no change to how completeness is computed

### Requirement: A lot cannot be closed silently over unopened boxes

Closing a lot while boxes remain uncounted SHALL require explicit confirmation, and the boxes concerned SHALL be reported. Closing SHALL NOT be prevented, because goods that never arrive would otherwise hold a lot open forever. The Intake screen's Reconcile & close tab is the dashboard surface for this gate: its Close action SHALL surface the unopened-carton list (from `GET /api/unpacking/lots/{lotId}/unopened`) and SHALL require the operator's deliberate confirmation (`confirm=true` on `POST /api/unpacking/lots/{lotId}/close`) before closing over them; the underlying close behavior is unchanged.

#### Scenario: Closing over unopened boxes reports them

- **WHEN** a lot is closed while some boxes have no counts
- **THEN** those boxes are reported before the lot closes
- **AND** closing proceeds only on explicit confirmation

#### Scenario: An uncounted expected line has no stock and no cost

- **WHEN** a lot is closed with expected lines never counted
- **THEN** those lines have no batch and no cost, having brought in no stock

#### Scenario: The Intake close action surfaces the unopened list and requires a deliberate confirm

- **WHEN** the operator invokes Close in the Intake Reconcile & close tab while cartons remain unopened
- **THEN** the unopened-carton list is surfaced and the close proceeds only after a deliberate `confirm=true`, and closing is not otherwise blocked
