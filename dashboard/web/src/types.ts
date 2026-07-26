// The shapes the backend sends, mirrored from the contracts records so the compiler catches a
// drift the moment a field is added or renamed on either side. Paise throughout — money is an
// integer count of the smallest unit, never a float.

export interface PriceableItem {
  productId: string
  name: string
  categoryCode: string
  unitCostPaise: number
  onlinePricePaise: number | null
  mrpPaise: number | null
  mrpIsEstimate: boolean
  currentPricePaise: number | null
  condition: string
  remark: string | null
}

export interface PriceProposal {
  productId: string
  pricePaise: number
  grossMarginPercent: number
  percentOfMrp: number | null
  beatsOnline: boolean | null
  aboveMrp: boolean
}

export interface BulkResult {
  priced: number
  skippedAlreadyPriced: number
  skippedAboveMrp: number
}

// --- unpacking ---------------------------------------------------------------------------

export interface DeliveryProgress {
  lotId: string
  supplier: string
  category: string
  cartonsTotal: number
  cartonsFinished: number
  cartonsStarted: number
  unitsExpected: number
  unitsCounted: number
  unitsUnlisted: number
  itemsWithoutMrp: number
  closed: boolean
}

export interface UnpackingCarton {
  boxId: string
  trackingNumber: string
  lotId: string
  finished: boolean
}

export interface UnpackingLine {
  lineId: string
  code: string
  name: string
  categoryCode: string
  expected: number
  counted: number
  outstanding: number
  needsMrp: boolean
  statedValuePaise: number | null
  onlinePricePaise: number | null
  indicativeCostPaise: number | null
  recordedMrpPaise: number | null
}

export interface CountOutcome {
  batchId: string
  quantityExpected: number
  quantityCounted: number
  discrepancy: number
}

export interface SuggestedMrp {
  pricePaise: number | null
  source: string | null
  message: string | null
}

export interface LearntCode {
  code: string
  origin: string
  releasable: boolean
}

// --- checkout ---
export interface CartLineView {
  lineId: string
  productId: string
  name: string
  asin: string | null
  mrpPaise: number
  unitPricePaise: number
  quantity: number
  lineTotalPaise: number
  savingPaise: number
  savingPercent: number
}

export interface CartView {
  cartId: string
  lines: CartLineView[]
  subtotalPaise: number
  taxPaise: number
  totalPaise: number
  savingPaise: number
  taxIsPlaceholder: boolean
}

// --- remediation ---

export type StockCondition = 'GOOD' | 'DAMAGED' | 'NEEDS_WORK' | 'UNUSABLE'

export interface IssueTypeOption {
  code: string
  label: string
}

export interface RemediationLine {
  lotId: string
  condition: StockCondition
  issueType: string | null
  issueLabel: string | null
  quantity: number
}

export interface ProductStates {
  productId: string
  productName: string
  categoryCode: string
  lines: RemediationLine[]
}

export interface BacklogItem {
  productId: string
  productName: string
  categoryCode: string
  lotId: string
  issueType: string | null
  issueLabel: string | null
  quantity: number
}

export interface ProductSummary {
  productId: string
  productName: string
  categoryCode: string
}

export interface ReviewItem {
  productId: string
  productName: string
  categoryCode: string
  lotId: string
  condition: StockCondition
  remark: string | null
  quantity: number
}

export interface CartonProgress {
  boxId: string
  trackingNumber: string
  lines: number
  unitsExpected: number
  unitsCounted: number
  unitsUnlisted: number
  finished: boolean
}

// A box worked recently, for the left rail that offers back the ones in hand.
export interface RecentBox {
  boxId: string
  trackingNumber: string
  categoryCode: string
  unitsExpected: number
  unitsCounted: number
  unitsUnlisted: number
  finished: boolean
  lastActivityAt: string
}

export interface ExtraRecord {
  productId: string
  productName: string
  categoryCode: string
  lotId: string
  boxTracking: string
  code: string | null
  quantity: number
}

export interface ShortLine {
  lineId: string
  productId: string
  productName: string
  asin: string
  boxTracking: string
  expected: number
  counted: number
  shortBy: number
}

export interface MrpBackfillResult {
  attempted: number
  recorded: number
  refused: number
  message: string | null
}

export interface MrpBackfillStatus {
  running: boolean
  total: number
  done: number
  recorded: number
  message: string
}

// --- catalog ---
// Browsing the product catalogue by name and found status, and opening one product to its detail.

export type CatalogStatus = 'FOUND' | 'ON_PAPER'

export interface CatalogEntry {
  productId: string
  name: string
  categoryCode: string
  status: CatalogStatus
  priced: boolean
}

export interface ProductCode {
  code: string
  origin: 'MANUFACTURER' | 'INTERNAL' | 'MARKETPLACE' | 'UNIT_LABEL'
}

// Reuses ProductStates verbatim — the catalogue detail and the remediation view stay one shape.
export interface CatalogDetail {
  states: ProductStates
  codes: ProductCode[]
  status: CatalogStatus
  priced: boolean
}

// --- receiving ---

export interface LotSummary {
  id: string
  supplier: string
  receivedOn: string
  receivingComplete: boolean
  expected: number
  received: number
  unpacked: number
  rejected: number
  notReceived: number
}

export interface ReceivingBox {
  manifestCartonId: string
  state: string
  receivedAt: string | null
  rejectedReason: string | null
}

export interface ReceivingBoxes {
  boxes: ReceivingBox[]
  receivingComplete: boolean
  allTerminal: boolean
  counts: {
    expected: number
    received: number
    unpacked: number
    rejected: number
    notReceived: number
  }
}
