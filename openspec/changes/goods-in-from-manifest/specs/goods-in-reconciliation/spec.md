## ADDED Requirements

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

### Requirement: Lot cost is apportioned when the lot is closed, over what actually arrived

The amount paid for a lot SHALL be apportioned only when the lot is closed, and SHALL be apportioned across the quantities actually counted rather than those expected. The apportioned amounts SHALL sum exactly to the amount paid.

The lot amount was paid regardless of what turned up, so the goods that genuinely arrived must carry all of it. A share cannot be final while boxes remain unopened, because every share depends on every other line.

#### Scenario: Closing a lot apportions its cost

- **WHEN** a lot is closed
- **THEN** each batch receives a share of the amount paid
- **AND** the shares sum exactly to the amount paid

#### Scenario: A shortfall raises the cost of the units that arrived

- **WHEN** a line is counted short and the lot is closed
- **THEN** the units that arrived carry that line's whole share between them
- **AND** each unit therefore costs more than it would have at the expected quantity

#### Scenario: Cost is apportioned by stated value, not by unit count

- **WHEN** a lot containing goods of differing value is closed
- **THEN** each line's share is proportional to its own stated value
- **AND** two lines of equal per-unit value receive equal per-unit cost whatever their quantities

#### Scenario: An open lot has no apportioned cost

- **WHEN** a lot has not been closed
- **THEN** its batches carry stock with no allocated cost
- **AND** that state is distinguishable from a cost of zero

### Requirement: A lot cannot be closed silently over unopened boxes

Closing a lot while boxes remain uncounted SHALL require explicit confirmation, and the boxes concerned SHALL be reported. Closing SHALL NOT be prevented, because goods that never arrive would otherwise hold a lot open forever.

#### Scenario: Closing over unopened boxes reports them

- **WHEN** a lot is closed while some boxes have no counts
- **THEN** those boxes are reported before the lot closes
- **AND** closing proceeds only on explicit confirmation

#### Scenario: Uncounted expected lines receive no cost

- **WHEN** a lot is closed with expected lines never counted
- **THEN** those lines receive no share of the amount paid
- **AND** the full amount is carried by the goods that did arrive

### Requirement: Goods not on the manifest are weighted at the lot's average unit value

Goods found in a lot that no expected line names SHALL still receive a share of the amount paid, because the money bought whatever arrived. Having no stated value of their own, they SHALL be weighted at the average per-unit stated value of the lot's named lines, and that weight SHALL be recorded as an estimate rather than as a stated value.

Excluding them would give them a cost of zero and make every margin computed from them meaningless.

#### Scenario: Unlisted goods receive a share of the lot

- **WHEN** a lot containing goods with no expected line is closed
- **THEN** those goods receive a share of the amount paid
- **AND** their cost is not zero

#### Scenario: The weight is the average unit value of the named lines

- **WHEN** an unlisted line is weighted
- **THEN** its per-unit weight is the total stated value of the lot's named lines divided by their total quantity

#### Scenario: A derived weight is marked an estimate

- **WHEN** an unlisted line has been weighted from the lot average
- **THEN** its cost basis records that the weight was estimated rather than stated

#### Scenario: A lot with no stated values at all cannot derive an average

- **WHEN** a lot is closed in which no line carries a stated value
- **THEN** closing is refused, reporting that a value must be supplied
- **AND** no cost is apportioned

### Requirement: A closed lot does not silently reopen

Once a lot has been closed and its costs apportioned, recording further counts against it SHALL be refused. Correcting a closed lot SHALL be a deliberate, recorded act rather than a side effect of scanning.

Costs from a closed lot may already have been used to set prices, so changing them quietly would leave prices resting on figures that no longer exist.

#### Scenario: Counting against a closed lot is refused

- **WHEN** a count is recorded against a lot that has been closed
- **THEN** it is refused, reporting that the lot is closed

#### Scenario: Stock already on hand from a closed lot is unaffected

- **WHEN** a lot has been closed
- **THEN** stock recorded from it before closing remains on hand and keeps its apportioned cost
