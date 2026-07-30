# Spec: Label Format & Rendering

## Overview

4x6 thermal label with barcode (Code128), product name, category, cost/MRP, lot, expiry. Rendered as ZPL (Zebra Programming Language) for native TSC TE-244 output.

## Label Layout (4x6 inches @ 203 DPI)

```
Top margin: 0.1"
Left/right margin: 0.1"
Content width: 3.8"

┌─────────────────────────────┐
│  [CODE128 BARCODE]          │  0.3"
│  C0D3-2024-5871             │  0.15"
│                             │
│  Product Name Bold 14pt     │  0.4"
│  Kitchen Category 8pt       │  0.2"
│                             │
│  ┌─────────────┬─────────┐  │
│  │Cost ₹240/u  │Lot: LOT1│  │  0.35"
│  │MRP ₹450     │Exp: N/A │  │
│  └─────────────┴─────────┘  │
│                             │
│  Received: 2026-07-27       │  0.2"
│                             │
└─────────────────────────────┘
Total: ~2.0" (leaves ~4.0" for future use or multiple labels per sheet)
```

## Data Fields

| Field | Source | Required | Max Length | Format |
|-------|--------|----------|-----------|--------|
| Barcode | Product.code OR generated UUID | Yes | 50 chars | Code128 encodable |
| Product Name | Product.name | Yes | 40 chars | Text |
| Category | Category.label | No | 20 chars | Text |
| Cost per Unit | Box.allocationPerUnit / Batch.amountPerUnit | Yes | 10 chars | ₹###.##  |
| MRP | Product.mrpPaise | No | 10 chars | ₹###.## or "—" |
| Lot ID | Lot.id | Yes | 20 chars | UUID or label |
| Expiry Date | Product.expiryDate | No | 10 chars | YYYY-MM-DD or blank |
| Received Date | Lot.receivedOn | Yes | 10 chars | YYYY-MM-DD |

## Template Rendering

**Engine:** FreeMarker (Java built-in, no external deps)

**Template file:** `backend/resources/print-templates/label.zpl`

**Inputs:**
```json
{
  "barcode": "C0D3-2024-5871",
  "productName": "Coconut Oil 1L",
  "category": "Kitchen Essentials",
  "costPerUnit": "240",
  "mrapPaise": "45000",
  "lotId": "LOT-2024-07",
  "expiryDate": null,
  "receivedDate": "2026-07-27"
}
```

**ZPL Output Example:**
```zpl
^XA
^FO50,50^BY2,3.0^BC^FD${barcode}^FS
^FO50,150^A0N,28,28^FD${productName}^FS
^FO50,190^A0N,14,14^FD${category}^FS
^FO50,280^A0N,18,18^FDCost: ₹${costPerUnit}/u^FS
^FO50,310^A0N,18,18^FDMRP: ₹${mrpPaise?string("0.##")?replace('.', '-')}^FS
^FO500,280^A0N,12,12^FDLot: ${lotId}^FS
<#if expiryDate??>
^FO500,310^A0N,12,12^FDExp: ${expiryDate}^FS
</#if>
^FO50,400^A0N,12,12^FDRec: ${receivedDate}^FS
^XZ
```

## Rendering Service

**Class:** `com.bahikhaata.backend.print.LabelTemplateService`

```java
public class LabelTemplateService {
  private final Configuration freeMarker;
  
  public String renderLabel(PrintLabelRequest request) throws IOException {
    Template template = freeMarker.getTemplate("label.zpl");
    StringWriter out = new StringWriter();
    template.process(request.toMap(), out);
    return out.toString();
  }
}
```

**Request DTO:**
```java
public record PrintLabelRequest(
  String barcode,
  String productName,
  String category,
  String costPerUnit,
  Integer mrpPaise,
  String lotId,
  String expiryDate,  // nullable
  String receivedDate
) {
  public Map<String, Object> toMap() {
    return Map.of(
      "barcode", barcode,
      "productName", productName,
      // ... etc
    );
  }
}
```

## Data Sources by Item Type

### Box Label
- Barcode: Product.code (from ExpectedLine's Product)
- Product Name: Product.name
- Category: Product.category.label
- Cost: Box.allocationPerUnit (calculated at receive time, stored)
- MRP: Product.mrpPaise
- Lot ID: Lot.id
- Expiry: Product.expiryDate (if applicable)
- Received: Lot.receivedOn

### Batch Label (after unpacking)
- Barcode: Product.code (from unpacking line)
- Product Name: Product.name
- Category: Product.category.label
- Cost: Lot.amountPaidPaise / Lot.totalQuantity (lot-level allocation)
- MRP: Product.mrpPaise
- Lot ID: Lot.id
- Expiry: Product.expiryDate
- Received: Lot.receivedOn

### Product Catalog Label
- Barcode: Product.code
- Product Name: Product.name
- Category: Product.category.label
- Cost: Product.unitCostPaise (or current-state cost if multiple lots)
- MRP: Product.mrpPaise
- Lot ID: "—" (no lot context in catalog; optional to omit)
- Expiry: "—"
- Received: "—"

## Acceptance Criteria

✅ FreeMarker template renders without errors for all three item types
✅ ZPL output is valid (verifiable by dry-run against TSC TE-244 docs)
✅ Barcode encodes as Code128 scannable by standard barcode readers
✅ Product name fits on label without truncation (max 2 lines, 40 chars)
✅ Cost and MRP right-aligned without overlap
✅ Lot ID and expiry (if present) top/bottom right, readable
✅ Template handles null fields gracefully (omits field, no blank space)
