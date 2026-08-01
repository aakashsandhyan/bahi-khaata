## MODIFIED Requirements

### Requirement: Pricing starts from a lot
The pricing workbench SHALL begin by selecting one lot to price, and all pricing in a session
SHALL be scoped to that lot. The lot list SHALL include any lot that still has counted-but-unpriced
stock — open lots (uncosted, hand-priced) and closed lots (costed, margin-suggested) alike — AND
any **open** lot that has no counted stock yet, so a mixed lot that is never counted can be selected
and hand-priced into. The selected lot SHALL be changeable during the session. A **closed** lot with
nothing left unpriced SHALL NOT be listed; an open lot SHALL remain listed until it is closed, so
that more stock can still be hand-priced into it.

#### Scenario: Selecting a lot to price against
- **WHEN** the user opens the pricing workbench and selects an open lot
- **THEN** the workbench scopes pricing to that lot and offers both to scan an item into it and
  to key one in by hand

#### Scenario: Selecting an uncounted mixed lot
- **WHEN** a lot has been added but not counted, so it holds no counted stock
- **THEN** the lot appears in the pricing lot list and can be selected, after which the user keys
  its items in by hand — each hand-add creating the stock as it is priced

#### Scenario: Changing the lot mid-session
- **WHEN** the user selects a different lot
- **THEN** the workbench rescopes to the new lot without losing any product already saved

#### Scenario: A closed, fully-priced lot drops off
- **WHEN** a closed lot has all of its counted stock priced
- **THEN** it is no longer listed in the pricing lot picker
