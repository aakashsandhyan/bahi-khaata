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

export type PaymentMethod = 'CASH' | 'UPI' | 'CARD'

export interface SaleLineView {
  productId: string
  name: string
  barcode: string | null
  mrpPaise: number
  unitPricePaise: number
  quantity: number
  lineTotalPaise: number
  savingPaise: number
}

// A completed sale, as the till confirmation and the sales screen show it. printFailed is true when
// the bill did not come out (offline/jam/unconfigured) — the sale still stands; reprint it.
export interface SaleView {
  saleId: string
  billNo: number
  billNoFormatted: string
  paymentMethod: PaymentMethod
  subtotalPaise: number
  savingPaise: number
  taxPaise: number
  totalPaise: number
  operatorName: string | null
  createdAt: string
  lines: SaleLineView[]
  printFailed: boolean
}

export interface SaleSummary {
  saleId: string
  billNo: number
  billNoFormatted: string
  totalPaise: number
  paymentMethod: PaymentMethod
  createdAt: string
  itemCount: number
}

// --- dashboard ---
// Mirrors com.bahikhaata.contracts.DashboardView and its nested records. One aggregate payload
// for the whole screen — see api.ts's dashboard.get().

export interface RevenueTodayKpi {
  totalPaise: number
  billCount: number
  // null when there are no bills yet — never a bare ₹0 average.
  averagePaise: number | null
}

export interface ReceivedVsPricedKpi {
  receivedUnits: number
  pricedUnits: number
  unpricedBacklogUnits: number
}

export interface RecoveryKpi {
  revenuePaise: number
  paidPaise: number
  // null when no lot has any amount paid recorded yet — never a divide-by-zero.
  ratio: number | null
}

export interface GstKpi {
  taxAllTimePaise: number
  // Always false today: GST is not computed until the separate gst-inclusive-pricing change
  // ships. The tile must show this rather than let a bare ₹0 read as a real figure.
  computed: boolean
}

export interface DashboardKpis {
  revenueToday: RevenueTodayKpi
  receivedVsPriced: ReceivedVsPricedKpi
  recovery: RecoveryKpi
  gst: GstKpi
}

export type DashboardFunnelStage = 'RECEIVED' | 'PRICED' | 'SOLD'

export interface DashboardFunnelPoint {
  stage: DashboardFunnelStage
  units: number
  mrpPaise: number
}

export interface DashboardAlert {
  signal: string
  count: number
  // A View literal (see Sidebar.tsx) — the screen this alert's row navigates to when clicked.
  targetView: string
  message: string
}

export interface DashboardView {
  kpis: DashboardKpis
  // Always exactly three points, in order: RECEIVED, PRICED, SOLD.
  funnel: DashboardFunnelPoint[]
  // Only the signals with a non-zero count — a zero-count signal is omitted, never a "0" row.
  alerts: DashboardAlert[]
  recentSales: SaleSummary[]
}

// The receipt printer's config, and the editable text on a bill — both admin-only, single-row.
export interface ReceiptPrinterConfig {
  address: string
  transport: 'LAN' | 'USB'
  enabled: boolean
  testStatus: string | null
  testError: string | null
  lastTestedAt: string | null
}

export interface BillSettings {
  shopName: string
  address: string
  gstin: string
  billTitle: string
  declaration: string
  footer: string
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
// Browsing the product catalogue by name and found status (palletworks-nav: surfaced by
// Inventory's On paper / All scopes, not a standalone screen). Opening one product to its detail
// is owned by item-detail now; the catalogue's own per-product detail payload is gone with the
// deleted Catalog screen — see CatalogEntry's own doc.

export type CatalogStatus = 'FOUND' | 'ON_PAPER'

export interface CatalogEntry {
  productId: string
  name: string
  categoryCode: string
  status: CatalogStatus
  priced: boolean
  unitsExpected: number
  unitsCounted: number
}

// --- product-centric counting ---
// Counting a product across every box of one delivery at once, instead of box by box.

export interface ProductBoxLine {
  lineId: string
  boxTracking: string
  outstanding: number
}

export interface ProductLotLines {
  productId: string
  productName: string
  lotId: string
  lines: ProductBoxLine[]
}

export interface BoxCountEntry {
  lineId: string
  quantity: number
  outstandingSeen: number
}

export interface ProductCountRequest {
  productId: string
  lotId: string
  condition: string
  mrpPaise: number | null
  mrpIsEstimate: boolean
  entries: BoxCountEntry[]
}

export interface RejectedEntry {
  lineId: string
  boxTracking: string
  nowOutstanding: number
}

export interface ProductCountResult {
  linesCounted: number
  unitsCounted: number
  rejected: RejectedEntry[]
}

// --- receiving ---

export interface LotSummary {
  id: string
  supplier: string
  receivedOn: string
  receivingComplete: boolean
  isManual: boolean
  categoryCode: string | null
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

export interface CreateManualLotRequest {
  supplierId: string
  receivedOn: string
  amountPaidPaise: number
  allocationMethod: string
}

export interface AddProductRequest {
  code: string | null
  name: string
  quantity: number
  categoryCode: string
  estimatedCostPaise: number | null
}

export interface AddProductResponse {
  success: boolean
  totalProducts: number
  totalQuantity: number
  allocationPerUnit: number
}

// --- printer (barcode labels) ---------------------------------------------------------------

export interface PrintJob {
  jobId: string
  status: 'queued' | 'printing' | 'done' | 'failed'
  itemType: 'box' | 'batch' | 'product'
  itemId: string
  copies: number
  error: string | null
  createdAt?: string
  updatedAt?: string
}

export interface PrinterConfig {
  id: string
  address: string
  portSpeed: number
  paperSize: string
  copiesDefault: number
  enabled: boolean
  testStatus: 'OK' | 'UNREACHABLE' | 'ERROR' | null
  lastTestedAt: string | null
  testError: string | null
}

export interface PrinterTestResult {
  testStatus: 'OK' | 'UNREACHABLE' | 'ERROR'
  message: string
  testedAt: string
}

// --- pricing workbench, mobile capture, bulk label print ---------------------------------------

export interface ShelfLot {
  lotId: string
  supplier: string
  receivedOn: string | null
  categoryCode: string | null
}

export interface ScannedItem {
  productId: string
  name: string
  categoryCode: string | null
  batchId: string
  costed: boolean
  unitCostPaise: number | null
  mrpPaise: number | null
  mrpIsEstimate: boolean
  quantity: number
  sellingPricePaise: number | null
  expectedQuantity: number | null
}

export interface PriceSuggestion {
  marginPercent: number
  suggestedPricePaise: number
}

export interface ShelfPricedProduct {
  productId: string
  barcode: string
  name: string
  sellingPricePaise: number
  mrpPaise: number | null
}

export interface CaptureSummary {
  id: string
  name: string
  mrpPaise: number | null
  description: string | null
  lotId: string | null
  status: string
  capturedAt: string
}

export interface AwaitingLabelProduct {
  productId: string
  barcode: string
  name: string
  sellingPricePaise: number
  mrpPaise: number | null
  quantity: number
  batchId: string | null
  categoryCode: string
}

export interface QueueAwaitingResult {
  productsQueued: number
  labelsQueued: number
}

export interface LabelReviewEntry {
  jobId: string
  productId: string
  batchId: string | null
  barcode: string
  name: string
  categoryCode: string
  sellingPricePaise: number
  mrpPaise: number | null
  copies: number
  onHand: number
  operatorName: string | null
}

export interface PhantomLine {
  productId: string
  name: string
  batchId: string
  quantity: number
}

export interface LotPhantomReport {
  lotId: string
  totalPhantom: number
  lines: PhantomLine[]
}

export interface WriteOffResult {
  lotId: string
  quantityWrittenOff: number
}

export interface BulkPrintResult {
  printed: number
  failed: number
}

// --- suppliers ---
// One vendor goods are bought from. GSTIN is the legal identity when present; the name is the
// fallback identity. A retired vendor is deactivated (active false), never deleted, because lots
// point at it.

export interface Supplier {
  id: string
  name: string
  gstin: string | null
  phone: string | null
  address: string | null
  contactPerson: string | null
  notes: string | null
  active: boolean
}

export interface SupplierInput {
  name: string
  gstin: string | null
  phone: string | null
  address: string | null
  contactPerson: string | null
  notes: string | null
}

export interface SupplierLot {
  id: string
  receivedOn: string
  amountPaidPaise: number
  receivingComplete: boolean
  isManual: boolean
  categoryCode: string | null
}

// --- inventory ---
// Mirrors com.bahikhaata.contracts.InventoryRow/InventoryDetail and friends. The list and the
// bin write are the Inventory screen's reads/write; detail composes one product's full story —
// see api.ts's `inventory` namespace.

export interface InventoryRow {
  productId: string
  productName: string
  categoryCode: string
  condition: 'GOOD' | 'DAMAGED'
  // The single backing lot's "supplier · received-on" label, or an "N lots" marker.
  lotLabel: string
  // Every distinct bin backing this row — empty when none of its stock has one.
  bins: string[]
  onHandQuantity: number
  // null when any contributing batch is not yet costed — an honest absence, never a fake zero.
  costBasisPaise: number | null
  sellingPricePaise: number | null
  marginPercent: number | null
  ageDays: number
}

export interface InventoryBatchLine {
  batchId: string
  condition: 'GOOD' | 'DAMAGED' | 'NEEDS_WORK' | 'UNUSABLE'
  lotLabel: string
  bin: string | null
  quantityReceived: number
  quantityDamaged: number
  allocatedUnitCostPaise: number | null
  mrpPaise: number | null
  createdAt: string | null
}

export interface InventoryMovement {
  movementType: 'PURCHASE_RECEIPT' | 'SALE' | 'WRITE_OFF' | 'ADJUSTMENT'
  quantity: number
  cogsPaise: number | null
  effectiveAt: string
}

export interface PriceChange {
  // null marks the product's first-ever price set — rendered as an explicit "first price" marker,
  // never a zero or blank.
  oldPricePaise: number | null
  newPricePaise: number
  operatorName: string | null
  changedAt: string
}

export interface InventoryDetail {
  productId: string
  productName: string
  categoryCode: string
  barcodes: string[]
  costBasisPaise: number | null
  sellingPricePaise: number | null
  marginPercent: number | null
  receivedUnits: number
  soldUnits: number
  batches: InventoryBatchLine[]
  movements: InventoryMovement[]
  priceHistory: PriceChange[]
}

export interface SetBinResult {
  batchId: string
  bin: string | null
}
