#!/usr/bin/env python3
"""Read a liquidation stock CSV, aggregate it, and cost it.

Written for the Amazon-returns manifests these consignments arrive with: one row per
physical unit, carrying an ASIN, a product name, a product line, a quantity and an ASP
(average selling price, which serves as the retail value).

    python3 tools/stock_csv.py "Stock 21 July - 1 - HOME.csv"
    python3 tools/stock_csv.py "Stock 21 July - 1 - HOME.csv" --paid 116000

Two things it is careful about, because both were got wrong by hand first:

*The same ASIN can carry different ASPs.* An average selling price varies with when the
item sold, so one product may appear at 1156, 1166, 1171 and 1178 in a single file. Taking
the first and applying it to every unit overstates the total. Units are therefore combined
with a **weighted average** ASP, which leaves the total retail value exactly as it was.

*The file may end with its own total row.* A row with no ASIN and a figure in the ASP column
is a spreadsheet footer, not stock. It is skipped, and reported against the computed total so
a discrepancy is visible rather than silently adopted.

Money is handled in paise as integers throughout, for the same reason the application does:
a rupee figure that has been through a float is not a figure anyone should price stock from.
"""

from __future__ import annotations

import argparse
import collections
import csv
import pathlib
import sys
from decimal import Decimal, ROUND_HALF_UP


def paise(rupees: str) -> int:
    """A rupee figure from the sheet, as whole paise.

    Handles the thousands separators a sheet export includes when the column is formatted
    as currency — "1,404.87" is a number to a human and a parse error to software.

    Parsed as Decimal rather than float, because a rupee figure that has been through binary
    floating point is not one to price stock from: 1404.87 becomes 1404.8699999… and rounds
    the wrong way often enough to matter across hundreds of lines.
    """
    cleaned = rupees.replace(",", "").replace("₹", "").strip()
    return int((Decimal(cleaned) * 100).to_integral_value(rounding=ROUND_HALF_UP))


def rupees(p: int) -> str:
    return f"{p // 100:,}.{abs(p) % 100:02d}"


class Line:
    """One product, after its per-unit rows have been combined."""

    def __init__(self, asin: str, name: str, product_line: str):
        self.asin = asin
        self.name = name
        self.product_line = product_line
        self.quantity = 0
        self.retail_paise = 0  # sum of each unit's ASP, kept exact
        self.asps: set[int] = set()

    @property
    def asp_has_paise(self) -> bool:
        return any(a % 100 for a in self.asps)

    def add_unit(self, quantity: int, asp_paise: int) -> None:
        self.quantity += quantity
        self.retail_paise += quantity * asp_paise
        self.asps.add(asp_paise)

    @property
    def mrp_paise(self) -> int:
        """Weighted average ASP.

        Weighted rather than first-seen or highest, so that quantity x mrp still equals the
        true retail value of the line — which is what the cost allocation weights depend on.
        """
        return self.retail_paise // self.quantity

    @property
    def asp_varies(self) -> bool:
        return len(self.asps) > 1


class Manifest:
    def __init__(self) -> None:
        self.lines: dict[str, Line] = collections.OrderedDict()
        self.rows_read = 0
        self.rows_skipped: list[tuple[int, str]] = []
        self.declared_total_paise: int | None = None

    @property
    def units(self) -> int:
        return sum(l.quantity for l in self.lines.values())

    @property
    def retail_paise(self) -> int:
        return sum(l.retail_paise for l in self.lines.values())

    @property
    def every_asp_is_whole_rupees(self) -> bool:
        return bool(self.lines) and not any(l.asp_has_paise for l in self.lines.values())


def read_manifest(path: pathlib.Path) -> Manifest:
    manifest = Manifest()

    with path.open(newline="", encoding="utf-8-sig") as handle:
        for number, row in enumerate(csv.DictReader(handle), start=2):
            asin = (row.get("ASIN") or "").strip()
            quantity = (row.get("Quantity") or "").strip()
            asp = (row.get("ASP") or "").strip()

            # A row with no ASIN but a figure in ASP is the sheet's own total, not stock.
            if not asin and asp and not quantity:
                manifest.declared_total_paise = paise(asp)
                manifest.rows_skipped.append((number, "the sheet's own total row"))
                continue

            if not asin or not quantity or not asp:
                manifest.rows_skipped.append((number, "missing ASIN, quantity or ASP"))
                continue

            manifest.rows_read += 1
            line = manifest.lines.get(asin)
            if line is None:
                line = Line(asin, (row.get("Product") or "").strip(),
                            (row.get("Product Line") or "").strip())
                manifest.lines[asin] = line
            line.add_unit(int(quantity), paise(asp))

    return manifest


def allocate(manifest: Manifest, paid_paise: int) -> dict[str, int]:
    """Spread what was paid across the lines, in proportion to retail value.

    The same arithmetic as the application's relative-MRP allocation, and reconciling the
    same way: floor division leaves a remainder of a few paise, which is given to the largest
    line rather than lost, so the shares sum to exactly what was paid.
    """
    total_weight = manifest.retail_paise
    if total_weight <= 0:
        raise ValueError("cannot allocate against zero retail value")

    shares = {
        asin: paid_paise * line.retail_paise // total_weight
        for asin, line in manifest.lines.items()
    }

    remainder = paid_paise - sum(shares.values())
    if remainder:
        largest = max(shares, key=lambda a: shares[a])
        shares[largest] += remainder

    return shares


def report(manifest: Manifest, paid_paise: int | None, show: int) -> None:
    print()
    print("  TOTALS")
    print(f"    rows            {manifest.rows_read}")
    print(f"    units           {manifest.units}")
    print(f"    products        {len(manifest.lines)}")
    print(f"    retail value    ₹{rupees(manifest.retail_paise)}")

    if manifest.declared_total_paise is not None:
        gap = manifest.retail_paise - manifest.declared_total_paise
        note = "matches" if gap == 0 else f"differs by ₹{rupees(abs(gap))}"
        print(f"    sheet's total   ₹{rupees(manifest.declared_total_paise)}   ({note})")

    for number, why in manifest.rows_skipped:
        print(f"    skipped line {number}: {why}")

    by_line: dict[str, list[int]] = {}
    for line in manifest.lines.values():
        bucket = by_line.setdefault(line.product_line or "(blank)", [0, 0, 0])
        bucket[0] += 1
        bucket[1] += line.quantity
        bucket[2] += line.retail_paise

    print()
    print("  BY PRODUCT LINE")
    print(f"    {'line':<16}{'products':>9}{'units':>7}{'retail':>15}")
    for name, (products, units, retail) in sorted(by_line.items(), key=lambda kv: -kv[1][2]):
        print(f"    {name:<16}{products:>9}{units:>7}{'₹' + rupees(retail):>15}")

    print()
    print("  PRICE SPREAD")
    ordered = sorted(manifest.lines.values(), key=lambda l: l.mrp_paise)
    print(f"    cheapest        ₹{rupees(ordered[0].mrp_paise)}   {ordered[0].name[:40]}")
    print(f"    dearest         ₹{rupees(ordered[-1].mrp_paise)}   {ordered[-1].name[:40]}")
    print(f"    average unit    ₹{rupees(manifest.retail_paise // manifest.units)}")

    varying = [l for l in manifest.lines.values() if l.asp_varies]
    if varying:
        print()
        print(f"  NOTE  {len(varying)} of {len(manifest.lines)} products appear at more than one ASP;")
        print("        each is summed at its own ASP, so the retail total above is exact.")

    if manifest.every_asp_is_whole_rupees:
        print()
        print("  WARNING  Every ASP is a whole rupee. An average selling price is revenue")
        print("           divided by units, so it rarely divides evenly — this is the")
        print("           signature of an export that rounded the column on the way out.")
        print("           Re-export showing the decimals before costing anything from it:")
        print("           the error is small per line but silent, and it lands on the one")
        print("           column the cost allocation is weighted by.")

    if paid_paise is None:
        return

    shares = allocate(manifest, paid_paise)
    print()
    print("  COST")
    print(f"    paid            ₹{rupees(paid_paise)}")
    print(f"    allocated       ₹{rupees(sum(shares.values()))}"
          f"   {'(exact)' if sum(shares.values()) == paid_paise else 'MISMATCH'}")
    print(f"    cost of retail  {paid_paise / manifest.retail_paise * 100:.2f}%")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv", type=pathlib.Path)
    parser.add_argument("--paid", type=float,
                        help="what the whole consignment cost, in rupees")
    parser.add_argument("--show", type=int, default=5,
                        help="lines to show from each end of the price range")
    args = parser.parse_args()

    if not args.csv.exists():
        print(f"error: no such file: {args.csv}", file=sys.stderr)
        return 1

    manifest = read_manifest(args.csv)
    if not manifest.lines:
        print("error: no usable rows — check the column names", file=sys.stderr)
        return 1

    report(manifest, paise(str(args.paid)) if args.paid is not None else None, args.show)
    return 0


if __name__ == "__main__":
    sys.exit(main())
