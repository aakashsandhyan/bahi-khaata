## ADDED Requirements

### Requirement: Pricing-free capture from a phone on the shop network
The system SHALL expose a phone-friendly interface, reachable on the shop LAN without a login,
for capturing raw product information: a name, an optional MRP, an optional description, and an
optional lot. A capture SHALL carry no selling price and SHALL NOT create a shelf product on its
own.

#### Scenario: Capturing from a phone
- **WHEN** a person on the shop network submits a name (with any of an optional MRP,
  description, or lot) from the mobile interface
- **THEN** the system records a pending capture and confirms it, without pricing it or placing
  anything on the shelf

#### Scenario: Capture carries no price
- **WHEN** a capture is submitted
- **THEN** it holds no selling price; price is decided later, at review

### Requirement: Captures land in a review queue
Submitted captures SHALL enter a review queue in a pending state, visible to a reviewer on the
desktop, ordered so the oldest is handled first.

#### Scenario: A capture appears for review
- **WHEN** a capture is submitted from a phone
- **THEN** it appears as a pending item in the desktop review queue

### Requirement: Review completes a capture into a shelf product
A reviewer SHALL open a pending capture pre-filled with its captured fields, assign its lot (if
not already set) and category, take the margin-suggested price, and approve it — which SHALL run
the same save as the pricing workbench (price, category, barcode, quantity onto the shelf) and
mark the capture approved. A reviewer SHALL also be able to reject a capture. A capture SHALL
reach the shelf only through approval.

#### Scenario: Approving a capture
- **WHEN** a reviewer opens a pending capture, assigns a lot and category, and approves it
- **THEN** the system prices and shelves the product exactly as a workbench save does, and marks
  the capture approved

#### Scenario: Rejecting a capture
- **WHEN** a reviewer rejects a pending capture
- **THEN** the capture is marked rejected and no product is created

#### Scenario: A pending capture is not on the shelf
- **WHEN** a capture is pending or rejected
- **THEN** no shelf product exists for it and it cannot be sold
