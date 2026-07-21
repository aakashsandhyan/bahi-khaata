#!/usr/bin/env python3
"""Read a whole consignment workbook: every category, both pricing schemes, reconciled.

    python3 tools/consignment.py "SUSHIL BHOPAL 17-7.xlsx"
    python3 tools/consignment.py book.xlsx --category HOME --lines 20

A consignment arrives as one workbook with a sheet per category and a summary sheet giving
what was paid for each. Each category is priced separately, so **each category is its own
lot** — that is the unit the costing works on, not the workbook as a whole.

Two schemes appear, and they need opposite treatment:

*Returns, priced off retail.* The sheet carries an ASP — an average selling price — and the
summary carries a discount. What was paid is spread across the lines in proportion to their
retail value, so a kettle carries more of the cost than a keychain. Nothing else divides it
sensibly: an equal split per unit would price low-value items above what they sell for.

*Supply, priced off cost.* The sheet already carries a cost per unit, and the summary applies
a markup to it. Nothing needs apportioning — every line's cost is known, so each is taken as
given and the markup applied. In the application's terms these lines are *pinned*.

Every figure is checked against the summary sheet rather than trusted. Money is handled as
Decimal throughout: these totals run to lakhs, and a float would not survive the journey.
"""

from __future__ import annotations

import argparse
import collections
import pathlib
import sys
import zipfile
from decimal import Decimal, ROUND_HALF_UP

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from xlsx_to_csv import read_sheet, sheet_names  # noqa: E402

# The summary sheet has no category rows of its own; it describes the others.
SUMMARY_HEADER = {"CAT", "ASP/PAID", "% DIS", "AMOUNT"}


def money(text: str) -> Decimal:
    """A figure from the sheet, as an exact Decimal."""
    cleaned = str(text).replace(",", "").replace("₹", "").strip()
    return Decimal(cleaned) if cleaned else Decimal(0)


def rupees(value: Decimal) -> str:
    return f"{value.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP):,}"


class Category:
    """One category sheet: a lot, its lines, and what was paid for it."""

    def __init__(self, name: str, header: list[str], rows: list[list[str]]):
        self.name = name
        self.cost_based = "Cost per unit" in header
        self.lines: dict[str, dict] = collections.OrderedDict()
        self.skipped = 0

        index = {column: position for position, column in enumerate(header)}

        def field(row, column):
            position = index.get(column)
            return row[position] if position is not None and position < len(row) else ""

        for row in rows:
            asin = field(row, "ASIN").strip()
            quantity = field(row, "Quantity").strip()
            tracking = field(row, "Tracking number").strip()
            if not asin or not quantity:
                self.skipped += 1
                continue

            # A cost-based sheet states the line's total; a returns sheet states a unit price
            # that has to be multiplied out.
            if self.cost_based:
                value = money(field(row, "Total amount"))
                unit = money(field(row, "Cost per unit"))
            else:
                unit = money(field(row, "ASP"))
                value = unit * int(money(quantity))

            # Keyed by box as well as product: the same ASIN routinely arrives in several
            # cartons, and a line is what should be found when one particular box is opened.
            key = (tracking, asin)
            line = self.lines.get(key)
            if line is None:
                line = self.lines[key] = {
                    "asin": asin,
                    "tracking": tracking,
                    "name": field(row, "Product").strip(),
                    "product_line": field(row, "Product Line").strip(),
                    "quantity": 0,
                    "value": Decimal(0),
                    "units_seen": set(),
                }
            line["quantity"] += int(money(quantity))
            line["value"] += value
            line["units_seen"].add(unit)

        self.paid: Decimal | None = None
        self.declared: Decimal | None = None
        self.scheme: str = ""

    @property
    def products(self) -> int:
        """Distinct products. Fewer than the number of lines, since one can span boxes."""
        return len({line["asin"] for line in self.lines.values()})

    @property
    def boxes(self) -> int:
        return len({line["tracking"] for line in self.lines.values()})

    @property
    def units(self) -> int:
        return sum(l["quantity"] for l in self.lines.values())

    @property
    def value(self) -> Decimal:
        """Total retail value for a returns sheet, total supplier cost for a cost sheet."""
        return sum((l["value"] for l in self.lines.values()), Decimal(0))

    def allocate(self) -> dict[str, Decimal]:
        """What each line costs, summing to exactly what was paid.

        Proportional to the line's value under either scheme — which for a cost-based sheet
        means every line simply keeps its own cost scaled by the markup, since its value *is*
        its cost. One calculation covers both, and the remainder is placed rather than lost.
        """
        if self.paid is None or self.value <= 0:
            return {}

        paid_paise = int((self.paid * 100).to_integral_value(rounding=ROUND_HALF_UP))
        weights = {
            key: int((line["value"] * 100).to_integral_value(rounding=ROUND_HALF_UP))
            for key, line in self.lines.items()
        }
        total_weight = sum(weights.values())

        shares = {k: paid_paise * w // total_weight for k, w in weights.items()}
        remainder = paid_paise - sum(shares.values())
        if remainder:
            shares[max(shares, key=lambda k: shares[k])] += remainder

        return {a: Decimal(p) / 100 for a, p in shares.items()}


def read_workbook(path: pathlib.Path) -> tuple[list[Category], Decimal | None]:
    categories: list[Category] = []
    summary: dict[str, dict] = {}
    stated_total: Decimal | None = None

    with zipfile.ZipFile(path) as book:
        names = sheet_names(book)

        for number, name in enumerate(names, start=1):
            rows = read_sheet(book, number)
            if not rows:
                continue
            header = rows[0]

            if SUMMARY_HEADER.issubset(set(header)):
                position = {c: i for i, c in enumerate(header)}
                for row in rows[1:]:
                    if not row:
                        continue
                    label = row[position["CAT"]].strip() if position["CAT"] < len(row) else ""
                    amount = row[position["AMOUNT"]] if position["AMOUNT"] < len(row) else ""
                    if not label:
                        # The unlabelled row at the foot is the workbook's grand total.
                        if amount:
                            stated_total = money(amount)
                        continue
                    summary[label] = {
                        "declared": money(row[position["ASP/PAID"]]) if position["ASP/PAID"] < len(row) else Decimal(0),
                        "discount": (row[position["% DIS"]] if position["% DIS"] < len(row) else "").strip(),
                        "paid": money(amount),
                    }
                continue

            if "ASIN" in header:
                categories.append(Category(name, header, rows[1:]))

    for category in categories:
        entry = summary.get(category.name)
        if entry:
            category.paid = entry["paid"]
            category.declared = entry["declared"]
            category.scheme = entry["discount"]

    # Categories named in the summary with no sheet of their own — bought as a line item.
    for label, entry in summary.items():
        if not any(c.name == label for c in categories):
            standalone = Category(label, [], [])
            standalone.paid = entry["paid"]
            standalone.scheme = "no manifest"
            categories.append(standalone)

    return categories, stated_total


def describe_scheme(category: Category) -> str:
    if category.scheme == "no manifest":
        return "bought as a line item"
    if not category.declared or category.declared == 0 or category.paid is None:
        return "?"
    ratio = category.paid / category.declared * 100
    if category.cost_based:
        return f"cost + {ratio - 100:.0f}%"
    return f"{ratio:.0f}% of retail"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("xlsx", type=pathlib.Path)
    parser.add_argument("--category", help="show the costed lines of one category")
    parser.add_argument("--lines", type=int, default=10, help="how many lines to show")
    args = parser.parse_args()

    if not args.xlsx.exists():
        print(f"error: no such file: {args.xlsx}", file=sys.stderr)
        return 1

    categories, stated_total = read_workbook(args.xlsx)
    if not categories:
        print("error: no category sheets found", file=sys.stderr)
        return 1

    if args.category:
        wanted = next((c for c in categories if c.name.upper() == args.category.upper()), None)
        if wanted is None:
            print(f"error: no category {args.category!r}", file=sys.stderr)
            return 1
        return show_category(wanted, args.lines)

    print()
    print(f"  {'category':<12}{'lots':>5}{'units':>7}{'value':>15}{'paid':>15}   basis")
    print("  " + "-" * 72)

    total_units = 0
    total_paid = Decimal(0)
    mismatches = []

    for category in categories:
        paid = category.paid or Decimal(0)
        total_units += category.units
        total_paid += paid

        if category.declared and abs(category.value - category.declared) > Decimal("0.5"):
            mismatches.append((category.name, category.value, category.declared))

        value = f"{rupees(category.value)}" if category.lines else ""
        print(f"  {category.name:<12}{1:>5}{category.units or '':>7}"
              f"{value:>15}{rupees(paid):>15}   {describe_scheme(category)}")

    print("  " + "-" * 72)
    print(f"  {'TOTAL':<12}{len(categories):>5}{total_units:>7}{'':>15}{rupees(total_paid):>15}")

    if stated_total is not None:
        gap = total_paid - stated_total
        note = "matches" if abs(gap) < Decimal("0.5") else f"DIFFERS by ₹{rupees(abs(gap))}"
        print(f"  workbook's own total: ₹{rupees(stated_total)}   ({note})")

    if mismatches:
        print()
        print("  SHEETS THAT DO NOT MATCH THE SUMMARY")
        for name, computed, declared in mismatches:
            print(f"    {name}: lines total ₹{rupees(computed)}, summary says ₹{rupees(declared)}")
    else:
        print()
        print("  Every category sheet reconciles against the summary.")

    print()
    print("  Each category is priced separately, so each is a lot of its own. Cost-based")
    print("  categories already carry a per-unit cost — those lines are pinned, and nothing")
    print("  is apportioned. Retail-based categories are spread by relative retail value.")
    print()
    print("  Use --category NAME to see the costed lines.")
    return 0


def show_category(category: Category, limit: int) -> int:
    shares = category.allocate()
    if not shares:
        print(f"  {category.name}: nothing to cost")
        return 0

    print()
    print(f"  {category.name} — {category.units} units, {category.products} products"
          f" in {category.boxes} boxes, {len(category.lines)} lines")
    print(f"  paid ₹{rupees(category.paid)} against ₹{rupees(category.value)}"
          f"  ({describe_scheme(category)})")
    allocated = sum(shares.values())
    # The sheet's amounts carry more precision than money has — 166394.2638888889 is a
    # spreadsheet's arithmetic, not a payable sum. Reconciliation is therefore against the
    # paid figure rounded to the paise, which is the most that can actually change hands.
    payable = category.paid.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    print(f"  allocated ₹{rupees(allocated)}"
          f"  {'(exact)' if allocated == payable else f'MISMATCH by ₹{rupees(allocated - payable)}'}")
    sub_paise = category.paid - payable
    if sub_paise:
        print(f"  the summary states ₹{category.paid}, which is ₹{abs(sub_paise):.4f} beyond")
        print("  the paise — rounded before allocating, since it cannot be paid.")

    varying = [l for l in category.lines.values() if len(l["units_seen"]) > 1]
    if varying:
        print(f"  {len(varying)} products appear at more than one price; each unit is counted")
        print("  at its own, so the total above is exact.")

    print()
    print(f"  {'product':<44}{'qty':>4}{'cost/unit':>12}{'line total':>14}")
    print("  " + "-" * 74)

    ordered = sorted(category.lines.items(), key=lambda kv: -shares[kv[0]])
    for key, line in ordered[:limit]:
        unit = shares[key] / line["quantity"]
        print(f"  {line['name'][:44]:<44}{line['quantity']:>4}"
              f"{rupees(unit):>12}{rupees(shares[key]):>14}")
    if len(ordered) > limit:
        print(f"  … and {len(ordered) - limit} more products")
    return 0


if __name__ == "__main__":
    sys.exit(main())
