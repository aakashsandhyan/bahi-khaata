# customer-records Specification

## Purpose
Customer identity for the counter: a name and the normalized mobile that keys it, the sale link that builds history, and the derived figures the Customers screen shows — groundwork for offers and e-invoicing.

## Requirements
### Requirement: A customer is identified by a normalized mobile number

A customer record SHALL carry a name and a mobile number; the mobile SHALL be normalized to ten digits (country code, spaces, and punctuation stripped) and SHALL be unique — one customer per number. A number that does not normalize to ten digits starting 6–9 SHALL be refused.

#### Scenario: The same number is one customer
- **WHEN** "+91 98214 55120" and "9821455120" are keyed on different days
- **THEN** both resolve to the same customer record

#### Scenario: A non-mobile number is refused
- **WHEN** a number that does not normalize to a ten-digit mobile is keyed
- **THEN** the save is refused with a plain message

### Requirement: Saving by a known mobile refreshes the name

Saving name + mobile SHALL create the customer when the mobile is unknown, and SHALL return the existing customer when it is known — updating the stored name when a different non-blank name was keyed, since the counter's latest utterance is the freshest record.

#### Scenario: A correction sticks
- **WHEN** an existing customer's mobile is keyed with the corrected name "Meera"
- **THEN** the record's name becomes "Meera" and no second record exists

### Requirement: A sale may carry its customer

A completed sale SHALL reference the attached customer when one was captured and SHALL remain valid with no reference for a walk-in. All sales history before customer capture SHALL remain valid unreferenced.

#### Scenario: An attached sale joins the customer's history
- **WHEN** a sale completes with a customer attached
- **THEN** that sale appears in the customer's visit history

### Requirement: Customer figures are derived, never stored

Per-customer figures (visits, total spent, average basket, category likes, last visit) and shop figures (people on file, repeat share of revenue, average repeat basket against walk-in, lapsed 60+ days) SHALL be derived from the customer and sale records at read time.

#### Scenario: Stats follow the sales with no bookkeeping
- **WHEN** a customer's third sale completes
- **THEN** their visit count reads 3 with no stored counter updated

### Requirement: Mobile numbers are masked outside a customer's own record

Customer lists SHALL show only the last four digits of a mobile; the full number SHALL render only on that customer's own detail view.

#### Scenario: The list leaks nothing
- **WHEN** the Customers list renders
- **THEN** every mobile shows as its last four digits only
