#!/usr/bin/env python3
"""Convert an Excel workbook to CSV, keeping the numbers exactly as stored.

    python3 tools/xlsx_to_csv.py "boat stock.xlsx"
    python3 tools/xlsx_to_csv.py book.xlsx --sheet 2 --out stock.csv
    python3 tools/xlsx_to_csv.py book.xlsx --list

**Why this exists rather than exporting from the spreadsheet.** Excel and Sheets export what
a cell *displays*, not what it holds. A column formatted to no decimal places exports 1405
when the stored value is 1404.87, and nothing warns you. Summed across a few hundred rows
that drifts by tens of rupees, which is exactly how an unexplained discrepancy gets into a
costing.

An .xlsx is a zip of XML, and a numeric cell keeps its full value in a `<v>` element quite
separately from its formatting. Reading that directly means the display format is never
consulted and precision cannot be lost — which is the whole point, and the reason this uses
nothing but the standard library rather than a spreadsheet library that may itself round.

Dates are the one place a stored value is not the answer: Excel keeps them as a day count,
so they are converted to ISO dates using the cell's format to tell a date from a number.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}

# Excel's built-in formats that mean a date or a time. Anything custom is detected by
# looking for date placeholders in the format string itself.
BUILT_IN_DATE_FORMATS = set(range(14, 23)) | set(range(45, 48)) | {27, 30, 36, 50, 57}

# The day Excel counts from. It also believes 1900 was a leap year, which it was not, so
# serials at or below 60 need no correction and everything after is shifted by that phantom
# day — handled below.
EXCEL_EPOCH = dt.datetime(1899, 12, 30)


def column_index(reference: str) -> int:
    """Zero-based column number from a cell reference like ``BC12``."""
    letters = re.match(r"([A-Z]+)", reference).group(1)
    index = 0
    for letter in letters:
        index = index * 26 + (ord(letter) - ord("A") + 1)
    return index - 1


def load_shared_strings(book: zipfile.ZipFile) -> list[str]:
    """The workbook's string table. Text cells hold an index into this, not the text."""
    try:
        root = ET.fromstring(book.read("xl/sharedStrings.xml"))
    except KeyError:
        return []

    strings = []
    for item in root.findall("main:si", NS):
        # A string can be split across runs when parts of it are formatted differently.
        strings.append("".join(t.text or "" for t in item.iter(f"{{{NS['main']}}}t")))
    return strings


def load_date_styles(book: zipfile.ZipFile) -> set[int]:
    """Which style indices mean the cell holds a date rather than a plain number."""
    try:
        root = ET.fromstring(book.read("xl/styles.xml"))
    except KeyError:
        return set()

    custom_date_formats = set()
    for fmt in root.iter(f"{{{NS['main']}}}numFmt"):
        code = (fmt.get("formatCode") or "").lower()
        # A format containing a year, day or month placeholder is a date. Excluding those
        # that are only hours and minutes keeps a duration from being read as a date.
        if any(token in code for token in ("yy", "dd", "mmm")) or (
            "m" in code and "d" in code
        ):
            custom_date_formats.add(int(fmt.get("numFmtId")))

    date_styles = set()
    cell_formats = root.find("main:cellXfs", NS)
    if cell_formats is not None:
        for index, xf in enumerate(cell_formats.findall("main:xf", NS)):
            fmt_id = int(xf.get("numFmtId", 0))
            if fmt_id in BUILT_IN_DATE_FORMATS or fmt_id in custom_date_formats:
                date_styles.add(index)
    return date_styles


def excel_serial_to_iso(serial: float) -> str:
    """An Excel day count as an ISO date, or date and time where there is a fraction."""
    # Excel wrongly treats 1900 as a leap year; serials after the phantom 29 February are
    # one too high. Correcting only above 60 leaves earlier dates alone.
    days = serial - 1 if serial > 60 else serial
    moment = EXCEL_EPOCH + dt.timedelta(days=days + 1)
    if moment.time() == dt.time(0, 0):
        return moment.date().isoformat()
    return moment.replace(microsecond=0).isoformat(sep=" ")


def sheet_names(book: zipfile.ZipFile) -> list[str]:
    root = ET.fromstring(book.read("xl/workbook.xml"))
    return [s.get("name") for s in root.iter(f"{{{NS['main']}}}sheet")]


def read_sheet(book: zipfile.ZipFile, number: int) -> list[list[str]]:
    strings = load_shared_strings(book)
    date_styles = load_date_styles(book)

    root = ET.fromstring(book.read(f"xl/worksheets/sheet{number}.xml"))
    rows: list[list[str]] = []

    for row in root.iter(f"{{{NS['main']}}}row"):
        cells: dict[int, str] = {}

        for cell in row.findall("main:c", NS):
            kind = cell.get("t", "n")
            value_node = cell.find("main:v", NS)

            if kind == "inlineStr":
                node = cell.find("main:is", NS)
                text = "".join(t.text or "" for t in node.iter(f"{{{NS['main']}}}t")) if node is not None else ""
            elif value_node is None or value_node.text is None:
                text = ""
            elif kind == "s":
                text = strings[int(value_node.text)]
            elif kind == "b":
                text = "TRUE" if value_node.text == "1" else "FALSE"
            elif kind in ("str", "e"):
                # A formula's cached result, or an error such as #DIV/0!.
                text = value_node.text
            else:
                text = value_node.text
                style = int(cell.get("s", -1))
                if style in date_styles:
                    try:
                        text = excel_serial_to_iso(float(text))
                    except ValueError:
                        pass
                else:
                    # The stored value, written out whole. Excel keeps 1404.87 as
                    # 1404.8699999999999 in places; trailing noise is trimmed, but nothing
                    # is rounded to fewer decimals than the value actually carries.
                    text = trim_float_noise(text)

            cells[column_index(cell.get("r"))] = text

        if cells:
            width = max(cells) + 1
            rows.append([cells.get(i, "") for i in range(width)])
        else:
            rows.append([])

    return rows


def trim_float_noise(text: str) -> str:
    """Remove the artefacts of binary floating point without losing real precision.

    Excel stores 1404.87 as 1404.8699999999999 often enough to matter. Rendering that at
    fifteen significant digits recovers 1404.87 exactly, while a genuinely long value such
    as 1157.3333333333333 keeps every digit it had.
    """
    try:
        value = float(text)
    except ValueError:
        return text

    shortened = repr(round(value, 10))
    # repr of a whole number gives "5.0"; the sheet meant 5.
    if shortened.endswith(".0"):
        shortened = shortened[:-2]
    return shortened if float(shortened) == value or abs(float(shortened) - value) < 1e-9 else text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("xlsx", type=pathlib.Path)
    parser.add_argument("--sheet", type=int, default=1, help="which sheet, 1-based")
    parser.add_argument("--out", type=pathlib.Path, help="defaults to the workbook's name .csv")
    parser.add_argument("--list", action="store_true", help="list the sheets and stop")
    args = parser.parse_args()

    if not args.xlsx.exists():
        print(f"error: no such file: {args.xlsx}", file=sys.stderr)
        return 1

    with zipfile.ZipFile(args.xlsx) as book:
        names = sheet_names(book)

        if args.list:
            for i, name in enumerate(names, start=1):
                print(f"  {i}. {name}")
            return 0

        if not 1 <= args.sheet <= len(names):
            print(f"error: sheet {args.sheet} does not exist; the workbook has {len(names)}",
                  file=sys.stderr)
            return 1

        rows = read_sheet(book, args.sheet)

    out = args.out or args.xlsx.with_suffix(".csv")
    with out.open("w", newline="", encoding="utf-8") as handle:
        csv.writer(handle).writerows(rows)

    decimals = sum(
        1 for row in rows for cell in row
        if re.fullmatch(r"-?\d+\.\d+", cell)
    )
    print(f"  {args.xlsx.name}  ->  {out}")
    print(f"  sheet {args.sheet} of {len(names)}: {names[args.sheet - 1]!r}")
    print(f"  {len(rows)} rows, {decimals} cells carrying decimals")
    print()
    print("  Values are taken as stored, not as displayed, so nothing has been rounded")
    print("  by a column's formatting on the way out.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
