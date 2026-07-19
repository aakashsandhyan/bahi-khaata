# Immutability triggers

The specs require that ledger rows and issued invoices reject `UPDATE` and `DELETE`
**at the database**, not merely in application code — explicitly including a direct
`sqlite3` session. An ORM annotation cannot deliver that.

Immutability is therefore enforced twice, and the two layers do different jobs:

- **`@Immutable` on the entity** stops Hibernate's dirty checking from generating an
  `UPDATE`. This is the layer that gives a clear failure during development.
- **These triggers** stop everything else — a migration script, a maintenance query, a
  future change that forgets. This is the layer that still holds when the application
  layer is wrong.

Belt and braces is deliberate. The application guard is the one you notice; the database
guard is the one that matters.

## Which tables

| Tier | Tables | Triggers |
|---|---|---|
| Immutable from creation | `stock_ledger`, `invoice_line` | Unconditional |
| Immutable once issued | `invoice` | Conditional on issue |
| Frozen on consumption | `lot`, `batch` | None — enforced in application logic, because the condition depends on the ledger rather than on the row |
| Mutable | `cart`, `cart_line`, `product`, `barcode`, `setting` | None |

Getting this wrong in the permissive direction is silent. Adding a trigger to `cart` would
be loud and immediate, which is the safer failure — but it would also break checkout, so
check the tier before writing one.

## Pattern: immutable from creation

Two triggers per table. Naming is `<table>_no_update` and `<table>_no_delete` so a
missing one is obvious when listing triggers.

```sql
CREATE TRIGGER stock_ledger_no_update
BEFORE UPDATE ON stock_ledger
BEGIN
    SELECT RAISE(ABORT, 'stock_ledger is append-only: rows cannot be updated');
END;

CREATE TRIGGER stock_ledger_no_delete
BEFORE DELETE ON stock_ledger
BEGIN
    SELECT RAISE(ABORT, 'stock_ledger is append-only: rows cannot be deleted');
END;
```

`BEFORE` rather than `AFTER`: the write must never happen, not be undone afterwards.
`RAISE(ABORT, ...)` rolls back the statement and returns the message to the caller.

## Pattern: immutable once issued

An invoice is editable while being assembled and frozen the moment it is issued. The
`WHEN` clause carries that distinction.

```sql
CREATE TRIGGER invoice_no_update_once_issued
BEFORE UPDATE ON invoice
WHEN OLD.issued_at IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'invoice is immutable once issued: record a correction instead');
END;

CREATE TRIGGER invoice_no_delete_once_issued
BEFORE DELETE ON invoice
WHEN OLD.issued_at IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'invoice is immutable once issued: record a correction instead');
END;
```

`OLD.issued_at` — the state *before* the statement. Testing `NEW` would let an update
that clears `issued_at` slip through and unfreeze the invoice.

## Message convention

`'<table> <rule>: <what to do instead>'`

The message surfaces to whoever hit it, often months later in a maintenance session. It
should say what to do, not just what was refused. "record a correction instead" is the
difference between someone finding the right path and someone disabling the trigger.

## Rules

- Triggers are created in the same migration as their table. A table protected in a later
  migration has a window where it is not.
- Never drop a trigger to make a migration easier. If a migration needs to rewrite an
  immutable table, that is a signal the change is wrong, not that the trigger is.
- `RAISE(ABORT)` aborts the statement and rolls back the enclosing transaction's current
  statement only. Callers see a constraint failure.
- Verified by `ImmutabilityTriggerPatternTest` and, once per pattern change, by hand
  through the `sqlite3` CLI — because "the database rejects it" is a claim about the
  database, not about JDBC.
