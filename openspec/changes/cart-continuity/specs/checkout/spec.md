# checkout — Delta Spec

## ADDED Requirements

### Requirement: A cart persists until paid, cleared, or the day ends

An open cart SHALL survive screen reloads: a device that remembers its cart SHALL get the same cart back while it remains open. A cart SHALL leave the open state only by completing into a sale, by being cleared, or by the end-of-day sweep.

#### Scenario: A reload changes nothing
- **WHEN** the till reloads mid-cart and re-fetches its remembered cart
- **THEN** the same cart returns with its lines and customer intact

### Requirement: Open carts are listable, newest touch first

The open carts SHALL be listable ordered by last touch descending, each with its customer name (absent for a walk-in), item count, a first-line summary, the total, the register it was opened on, and when it was last touched. Every cart mutation — adding, quantity, removal, clearing, customer attach — SHALL count as a touch.

#### Scenario: The just-abandoned queue-jumper sorts first
- **WHEN** cart A was touched two minutes after cart B
- **THEN** the list returns A before B

### Requirement: Any register resumes any open cart

Fetching an open cart by id SHALL work regardless of which register opened it. A completed cart SHALL refuse completion again regardless of who resumes it.

#### Scenario: The cart follows the customer across counters
- **WHEN** a cart opened on Register 2 is resumed on Register 1
- **THEN** its lines and customer load there, and it completes there normally

#### Scenario: A double payment is impossible
- **WHEN** two registers hold the same cart and one completes the sale
- **THEN** the other's completion attempt is refused

### Requirement: Yesterday's carts sweep at first touch

An open cart last touched before the current business day (IST) SHALL be marked abandoned by the first read that encounters it — the carts list or a device restoring its pointer — and SHALL NOT appear among open carts thereafter.

#### Scenario: The morning sweep
- **WHEN** the first till of the day lists carts and yesterday's forgotten cart is among them
- **THEN** that cart is abandoned and absent from the returned list

#### Scenario: A remembered cart from yesterday starts fresh
- **WHEN** a device restores a pointer to a cart last touched yesterday
- **THEN** that cart is abandoned and the device gets a fresh cart

### Requirement: The cart carries its customer

Attaching a captured customer SHALL write the customer onto the cart, so a held or resumed cart keeps its person; completing the cart SHALL copy the cart's customer onto the sale. A request-level customer on completion SHALL still be honored when the cart carries none.

#### Scenario: A hold keeps the customer
- **WHEN** a cart with a customer attached is held and later resumed on another register
- **THEN** the resumed cart still names that customer, and its sale references them
