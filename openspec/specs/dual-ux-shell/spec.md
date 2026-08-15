# dual-ux-shell Specification

## Purpose
The whole-app Classic ⇄ Modern switch: one click flips the dashboard between the pre-palletworks UX and the palletworks shell, per device, over one shared backend.

## Requirements
### Requirement: One click switches the whole dashboard between Classic and Modern

The dashboard SHALL offer a visible, labeled control in both UXes that switches the entire app between the Classic UX (the pre-palletworks top-bar navigation and its screens) and the Modern UX (the palletworks shell), immediately and without a reload. The choice SHALL persist per device and hold across restarts until switched again.

#### Scenario: Switching to Classic
- **WHEN** "Classic UX" is clicked in the modern shell
- **THEN** the classic top bar and its screens replace the modern shell at once, and the device reopens in Classic thereafter

#### Scenario: Switching back
- **WHEN** "Modern UX" is clicked in the classic top bar
- **THEN** the modern shell returns at once and the device remembers Modern

### Requirement: Classic mode is the complete pre-palletworks UX

Classic mode SHALL present the full former navigation — including Till as a listed entry, the Lots, Receiving, and Catalog screens restored as they stood on main, and the admin config screens as separate entries. Classic screens' styling SHALL be scoped so it cannot alter the modern shell.

#### Scenario: The fold's deletions are back under Classic
- **WHEN** Classic mode is active
- **THEN** Lots, Receiving, and Catalog are reachable and function as they did before the palletworks fold

### Requirement: Both UXes drive one backend

Switching UX SHALL change presentation only: both modes SHALL call the same APIs against the same database, and work done in either mode SHALL be visible in the other.

#### Scenario: A classic sale shows in modern Invoices
- **WHEN** a sale is rung on the classic till and the device switches to Modern
- **THEN** that bill appears in Invoices

### Requirement: Station entry points survive both modes

The `#till` and `#capture` hash landings SHALL open their screens regardless of the device's UX mode, and phone stations SHALL be unaffected by the switch.

#### Scenario: Till bookmark under Classic
- **WHEN** a device in Classic mode opens `#till`
- **THEN** the till opens exactly as it does under Modern
