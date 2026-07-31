# goods-in-reconciliation Specification

## Purpose
TBD - created by archiving change goods-in-from-manifest. Update Purpose after archive.
## Requirements
### Requirement: Counting records fact, and fact overrides the manifest

Recording a count SHALL write stock for the quantity actually found, not the quantity expected. Where the two differ, the counted quantity SHALL be what the ledger holds and the expected quantity SHALL remain readable beside it.

#### Scenario: A count matching the manifest brings stock on hand

- **WHEN** the counted quantity equals the expected quantity
- **THEN** stock on hand increases by that quantity

#### Scenario: A short count brings on hand only what was found

- **WHEN** fewer units are found than expected
- **THEN** stock on hand increases by the counted quantity only
- **AND** the shortfall is visible as the difference from the expected quantity

#### Scenario: A surplus count is recorded rather than refused

- **WHEN** more units are found than expected
- **THEN** stock on hand increases by the counted quantity
- **AND** the surplus is visible as the difference from the expected quantity

#### Scenario: Goods not on the manifest at all can be recorded

- **WHEN** an item is found in a box with no matching expected line
- **THEN** it can be recorded against the lot
- **AND** it is distinguishable from goods that were expected

### Requirement: A part-counted box is a resumable state, not an error

Counting SHALL be recorded as it happens rather than on completion of a box. A box that has been partly counted SHALL remain in that state indefinitely and SHALL show what remains when reopened.

Work stops at closing time, and an interruption must not discard what has already been counted or force it to be counted again.

#### Scenario: Counts survive leaving a box part done

- **WHEN** some lines in a box have been counted and the operator stops
- **THEN** those counts are retained
- **AND** the box shows the lines still to be counted when reopened

#### Scenario: A part-counted box is not reported as a problem

- **WHEN** a box is part counted
- **THEN** it is reported as in progress rather than as an error or exception

### Requirement: Completeness is reportable by box

The system SHALL report, for a lot, which boxes are untouched, which are part counted, and which are finished.

#### Scenario: Untouched boxes are identifiable

- **WHEN** the state of a lot is requested
- **THEN** boxes with no counts recorded are listed as not started

#### Scenario: A finished box is marked as such

- **WHEN** every expected line in a box has been counted and the box is marked finished
- **THEN** it is reported as finished
- **AND** any shortfall or surplus within it remains visible

### Requirement: Reconciliation reconciles quantities and cross-checks the amount paid

Reconciliation SHALL compare the quantities counted against those expected, reporting shortfalls and surpluses. It SHALL NOT apportion the amount paid across the goods — each product's cost is already pinned from its manifest line at receipt (see the stock-ledger spec), so there is nothing to divide. The lot's amount paid SHALL be reconciled against the sum of its received lines' pinned per-unit costs times their counted quantities, and a material difference SHALL be reported.

#### Scenario: Reconciliation reports shortfalls and surpluses

- **WHEN** a lot is reconciled
- **THEN** each line's counted quantity is compared to its expected quantity
- **AND** shortfalls and surpluses are reported

#### Scenario: Cost is not apportioned at reconciliation

- **WHEN** a lot is reconciled
- **THEN** no product's cost is derived by dividing the amount paid
- **AND** each product keeps the per-unit cost pinned from its manifest line

#### Scenario: The amount paid is cross-checked against the pinned costs

- **WHEN** a lot is reconciled
- **THEN** the sum of its received lines' pinned costs times their counted quantities is compared to the amount paid
- **AND** a material difference is reported rather than absorbed into the goods

#### Scenario: A shortfall does not change the cost of the units that arrived

- **WHEN** a line is counted short
- **THEN** the units that arrived each keep their stated per-unit cost
- **AND** the missing units' cost is simply not incurred

### Requirement: A lot cannot be closed silently over unopened boxes

Closing a lot while boxes remain uncounted SHALL require explicit confirmation, and the boxes concerned SHALL be reported. Closing SHALL NOT be prevented, because goods that never arrive would otherwise hold a lot open forever.

#### Scenario: Closing over unopened boxes reports them

- **WHEN** a lot is closed while some boxes have no counts
- **THEN** those boxes are reported before the lot closes
- **AND** closing proceeds only on explicit confirmation

#### Scenario: An uncounted expected line has no stock and no cost

- **WHEN** a lot is closed with expected lines never counted
- **THEN** those lines have no batch and no cost, having brought in no stock

### Requirement: A surplus with no stated cost is left uncosted

Goods found in a lot that no expected line names — a surplus — carry no stated cost, because the manifest never named them. They SHALL be left uncosted, an explicit state distinct from a cost of zero, rather than weighted at any lot average. A cost for them SHALL be a later, deliberate decision, not an automatic apportionment. This is the rare exception; the manifest states a cost for every product it does name.

#### Scenario: An unlisted surplus is left uncosted

- **WHEN** goods with no expected line are found in a lot
- **THEN** their batch is uncosted, reported as not yet determined rather than as zero

#### Scenario: A surplus takes no automatic cost

- **WHEN** a lot with an unlisted surplus is reconciled
- **THEN** the surplus receives no apportioned or averaged cost
- **AND** any cost it is later given is a deliberate, recorded decision

### Requirement: A closed lot does not silently reopen

Once a lot has been closed, recording further counts against it SHALL be refused. Correcting a closed lot SHALL be a deliberate, recorded act rather than a side effect of scanning.

A closed lot's pinned costs may already have been used to set prices, so changing them quietly would leave prices resting on figures that no longer exist.

#### Scenario: Counting against a closed lot is refused

- **WHEN** a count is recorded against a lot that has been closed
- **THEN** it is refused, reporting that the lot is closed

#### Scenario: Stock already on hand from a closed lot is unaffected

- **WHEN** a lot has been closed
- **THEN** stock recorded from it before closing remains on hand and keeps its apportioned cost

