#!/usr/bin/env python3
"""Read a consignment workbook and record it in the running system.

    python3 tools/import_consignment.py "SUSHIL BHOPAL 17-7.xlsx" --supplier Sushil
    python3 tools/import_consignment.py book.xlsx --dry-run

Parsing a spreadsheet and recording a purchase are different jobs, and this does only the
first. It turns the workbook into the shape the backend accepts and posts it; the backend
creates the lots, products, costs and stock in one transaction, so a failure leaves nothing
behind.

**No MRP is sent.** A manifest states what goods cost or what they sold for online, and
neither is the maximum retail price printed on the pack. Every product therefore lands on
hand but unsellable, waiting for someone to read an MRP off the goods — which is the point:
the system should not pretend to know something nobody has looked at.

Categories are mapped from the supplier's own product-line codes where the sheet gives them,
falling back to the sheet name. An unknown category is refused by the backend rather than
guessed at.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.error
import urllib.request
from decimal import Decimal, ROUND_HALF_UP

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from consignment import read_workbook  # noqa: E402

# The supplier's product-line codes, as they appear in the sheets, to our category codes.
# The backend holds the same mapping in its category table; this covers the sheets that state
# a product line, and the sheet name covers those that do not.
PRODUCT_LINE_TO_CATEGORY = {
    "gl_home": "HOME_ESSENTIALS",
    "gl_kitchen": "KITCHEN",
    "gl_apparel": "FASHION",
    "gl_wireless_accessory": "WIRELESS",
    "gl_shoes": "FOOTWEAR",
    "gl_personal_care_appliances": "PERSONAL_CARE",
    "gl_lawn_and_garden": "GARDEN",
    "gl_home_improvement": "HOME_IMPROVEMENT",
    "gl_outdoors": "OUTDOORS",
    "gl_musical_instruments": "MUSICAL_INSTRUMENTS",
    "gl_luggage": "LUGGAGE",
}

SHEET_NAME_TO_CATEGORY = {
    "HOME": "HOME_ESSENTIALS",
    "KITCHEN": "KITCHEN",
    "PCA": "PERSONAL_CARE",
    "LAWN": "GARDEN",
    "WIRELESS": "WIRELESS",
    "APPREAL": "FASHION",
    "SHOES": "FOOTWEAR",
}


def paise(value: Decimal) -> int:
    return int((value * 100).to_integral_value(rounding=ROUND_HALF_UP))


def build_request(path: pathlib.Path, supplier: str, received_on: str) -> dict:
    categories, _ = read_workbook(path)
    lots = []

    for category in categories:
        if not category.lines or category.paid is None:
            continue

        # A category's lines mostly agree on their product line; the sheet name is the
        # fallback for the few that are blank or unusual.
        first_line = next(iter(category.lines.values()))
        code = PRODUCT_LINE_TO_CATEGORY.get(
            first_line["product_line"], SHEET_NAME_TO_CATEGORY.get(category.name.upper())
        )
        if code is None:
            raise SystemExit(
                f"error: no category mapping for sheet {category.name!r} "
                f"(product line {first_line['product_line']!r}). Add it above, and add the "
                f"category to the database if it is genuinely new."
            )

        lines = []
        for asin, line in category.lines.items():
            # The line's whole worth, since that is what decides its share. For a cost-based
            # sheet this is the supplier's cost; for a returns sheet, the selling price.
            per_unit = paise(line["value"]) // line["quantity"]
            if per_unit <= 0:
                continue
            lines.append(
                {
                    "code": asin,
                    "name": line["name"][:200],
                    "quantity": line["quantity"],
                    "weighingValuePaise": per_unit,
                    # Not pinned, even for a cost-based sheet. The supplier's cost is what
                    # they paid, not what we did — we paid it plus a markup — so pinning at it
                    # would understate every line by that markup. Apportioning the amount
                    # actually paid, weighted by those same costs, gives each line its cost
                    # plus the markup and reconciles by construction.
                    "pinnedUnitCostPaise": None,
                    "trackingNumber": None,
                }
            )

        if not lines:
            continue

        lots.append(
            {
                "categoryCode": code,
                "amountPaidPaise": paise(category.paid),
                # IMPORTED records that the weights came from a supplied cost list rather
                # than from retail values, so a cost can be judged later.
                "allocationMethod": "IMPORTED" if category.cost_based else "RELATIVE_MRP",
                "lines": lines,
            }
        )

    return {"supplier": supplier, "receivedOn": received_on, "lots": lots}


def post(url: str, payload: dict) -> None:
    body = json.dumps(payload).encode()
    request = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json"}, method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            result = json.load(response)
    except urllib.error.HTTPError as e:
        print(f"\n  the backend refused it ({e.code}):\n    {e.read().decode()[:400]}",
              file=sys.stderr)
        raise SystemExit(1)
    except urllib.error.URLError as e:
        print(f"\n  could not reach the backend: {e.reason}", file=sys.stderr)
        print("  start it with: ./gradlew :backend:bootRun", file=sys.stderr)
        raise SystemExit(1)

    print()
    print(f"  lots recorded      {result['lotsCreated']}")
    print(f"  products created   {result['productsCreated']}")
    print(f"  products matched   {result['productsMatched']}")
    print(f"  units on hand      {result['unitsReceived']}")
    print(f"  cost allocated     ₹{result['totalAllocatedPaise'] / 100:,.2f}")
    print()
    for warning in result["warnings"]:
        print(f"  {warning}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("xlsx", type=pathlib.Path)
    parser.add_argument("--supplier", default="Sushil")
    parser.add_argument("--received-on", default="2026-07-17", help="ISO date of the delivery")
    parser.add_argument("--url", default="http://127.0.0.1:8080/api/consignments/import")
    parser.add_argument("--dry-run", action="store_true",
                        help="show what would be sent, and send nothing")
    args = parser.parse_args()

    if not args.xlsx.exists():
        print(f"error: no such file: {args.xlsx}", file=sys.stderr)
        return 1

    payload = build_request(args.xlsx, args.supplier, args.received_on)

    total_lines = sum(len(lot["lines"]) for lot in payload["lots"])
    total_units = sum(l["quantity"] for lot in payload["lots"] for l in lot["lines"])
    total_paid = sum(lot["amountPaidPaise"] for lot in payload["lots"])

    print()
    print(f"  {args.xlsx.name}")
    print(f"  {len(payload['lots'])} lots, {total_lines} products, {total_units} units, "
          f"₹{total_paid / 100:,.2f} paid")
    for lot in payload["lots"]:
        print(f"    {lot['categoryCode']:<20}{len(lot['lines']):>5} products"
              f"{sum(l['quantity'] for l in lot['lines']):>7} units"
              f"   ₹{lot['amountPaidPaise'] / 100:>12,.2f}   {lot['allocationMethod']}")

    if args.dry_run:
        print()
        print("  Dry run: nothing sent.")
        return 0

    post(args.url, payload)
    return 0


if __name__ == "__main__":
    sys.exit(main())
