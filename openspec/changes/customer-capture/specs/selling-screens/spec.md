# selling-screens — Delta Spec

## ADDED Requirements

### Requirement: Payment leads with the customer ask

Taking payment SHALL present the customer step before the payment method: a mobile field, focused, where ten digits look up the customer — a match shows the name to confirm and attach, an unknown number reveals a name field to save and attach. A **Walk-in** control SHALL always be one tap and SHALL proceed straight to the payment method with no customer. The sale SHALL never be blocked by the customer step — a failed lookup proceeds as walk-in.

#### Scenario: A known customer attaches in one confirm
- **WHEN** a known mobile is keyed at the customer step
- **THEN** the stored name is shown, and confirming attaches the customer and moves to the payment method

#### Scenario: Declining is one tap
- **WHEN** Walk-in is tapped at the customer step
- **THEN** the payment method appears at once and the sale completes unreferenced

#### Scenario: A lookup failure never blocks the sale
- **WHEN** the customer lookup cannot be reached
- **THEN** the flow proceeds as walk-in and the sale completes

### Requirement: The cart names its customer

Once a customer is attached, the cart header SHALL show their name in place of "Walk-in customer"; clearing the cart SHALL clear the attachment.

#### Scenario: The header follows the attachment
- **WHEN** a customer is attached and the cart is then cleared
- **THEN** the header shows the name after attaching and "Walk-in customer" again after clearing
