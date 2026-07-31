// The only route to the backend. Every call goes through here, so the base address, the error
// handling, and the fact that this is a client of a separate service all live in one place.
//
// A refusal the backend wrote — a price above the MRP, a cost not yet known — comes back as a
// sentence meant for a person. It is thrown as-is rather than turned into "request failed",
// because the sentence is the useful part.

import type {
  BulkResult,
  MrpBackfillResult,
  MrpBackfillStatus,
  PriceProposal,
  PriceableItem,
} from './types'

// Relative, so every call goes through this server, which proxies it to the backend. That keeps
// the browser on one secure origin — needed for the camera — with no mixed content and no
// cross-origin request. An explicit VITE_BACKEND still overrides it for anyone pointing the app
// at a backend elsewhere.
const BASE = import.meta.env.VITE_BACKEND ?? ''

class BackendError extends Error {}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`)
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T>
}

// For endpoints that answer with a list, where a 404 means "nothing here" rather than a fault —
// an unknown box tracking number, a code that resolves to nothing. Returns an empty list so the
// caller shows its ordinary "not found" message instead of a raw backend error.
async function getList<T>(path: string): Promise<T[]> {
  const response = await fetch(`${BASE}${path}`)
  if (response.status === 404) return []
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T[]>
}

async function post<T>(path: string, body?: unknown): Promise<T | null> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) throw new BackendError(await message(response))
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : null
}

async function put<T>(path: string, body?: unknown): Promise<T | null> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) throw new BackendError(await message(response))
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : null
}

async function message(response: Response): Promise<string> {
  const text = await response.text()
  return text || `The backend answered ${response.status}.`
}

export const api = {
  priceable: (category: string) =>
    get<PriceableItem[]>(`/api/admin/pricing/priceable?category=${encodeURIComponent(category)}`),

  preview: (category: string, marginPercent: number) =>
    get<PriceProposal[]>(
      `/api/admin/pricing/preview?category=${encodeURIComponent(category)}&marginPercent=${marginPercent}`,
    ),

  setPrice: (productId: string, pricePaise: number) =>
    post<void>(`/api/admin/pricing/products/${productId}`, { pricePaise }),

  priceCategory: (category: string, marginPercent: number) =>
    post<BulkResult>(
      `/api/admin/pricing/category/${encodeURIComponent(category)}?marginPercent=${marginPercent}`,
    ),

  // Look up missing MRPs (bounded by a limit; each recorded as an estimate). Slow — a scrape each.
  backfillMrp: (limit: number) =>
    post<MrpBackfillResult>(`/api/admin/mrp/backfill?limit=${limit}`),

  // Start a background fill of every unpriced item, and poll how it is going.
  startMrpBackfill: () => post<MrpBackfillStatus>('/api/admin/mrp/backfill/start'),
  mrpBackfillStatus: () => get<MrpBackfillStatus>('/api/admin/mrp/backfill/status'),
}

export { BackendError }

// --- unpacking -----------------------------------------------------------------------------
// The same operations the terminal drives, exposed to the web so several people can scan at
// once. A condition of "GOOD" is sent explicitly; a null MRP is left out of the body entirely,
// because an empty string is not a valid figure and the backend would refuse the whole request.

import type {
  CountOutcome as _CountOutcome,
  DeliveryProgress as _DeliveryProgress,
  LearntCode as _LearntCode,
  SuggestedMrp as _SuggestedMrp,
  UnpackingCarton as _UnpackingCarton,
  UnpackingLine as _UnpackingLine,
  CartonProgress as _CartonProgress,
  RecentBox as _RecentBox,
} from './types'

function countBody(
  quantity: number,
  mrpPaise: number | null,
  condition: string,
  remark: string | null,
  issueType: string | null,
  mrpIsEstimate: boolean,
) {
  const body: Record<string, unknown> = { quantity, mrpIsEstimate, condition }
  if (mrpPaise != null) body.mrpPaise = mrpPaise
  if (remark) body.remark = remark
  if (issueType) body.issueType = issueType
  return body
}

export const unpacking = {
  deliveries: () => get<_DeliveryProgress[]>('/api/unpacking/deliveries'),

  cartonsByTracking: (tracking: string) =>
    getList<_UnpackingCarton>(`/api/unpacking/boxes/by-tracking?tracking=${encodeURIComponent(tracking)}`),

  lines: (boxId: string) => getList<_UnpackingLine>(`/api/unpacking/boxes/${boxId}/lines`),

  // Every carton of a delivery and how far each is counted, for the "what to open next" list.
  boxesOf: (lotId: string) => getList<_CartonProgress>(`/api/unpacking/lots/${lotId}/boxes`),

  // The boxes worked most recently, newest first — for the left rail. A tap reopens one.
  recentBoxes: (limit = 5) =>
    getList<_RecentBox>(`/api/unpacking/recent-boxes?limit=${limit}`),

  resolve: (boxId: string, code: string) =>
    getList<_UnpackingLine>(`/api/unpacking/boxes/${boxId}/resolve?code=${encodeURIComponent(code)}`),

  count: (
    lineId: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
    issueType: string | null = null,
    mrpIsEstimate = false,
  ) =>
    post<_CountOutcome>(
      `/api/unpacking/lines/${lineId}/count`,
      countBody(quantity, mrpPaise, condition, remark, issueType, mrpIsEstimate),
    ),

  tag: (
    lineId: string,
    scannedCode: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
    issueType: string | null = null,
    mrpIsEstimate = false,
  ) =>
    post<_CountOutcome>(`/api/unpacking/lines/${lineId}/tag`, {
      scannedCode,
      ...countBody(quantity, mrpPaise, condition, remark, issueType, mrpIsEstimate),
    }),

  // An extra found in a box that no line names — recorded against the box, costed at the lot
  // average. The code resolves to a known product where possible; otherwise name + category make
  // a new one.
  unlisted: (
    boxId: string,
    body: {
      code: string
      name: string
      categoryCode: string
      quantity: number
      mrpPaise: number | null
      mrpIsEstimate: boolean
    },
  ) => post<_CountOutcome>(`/api/unpacking/boxes/${boxId}/unlisted`, body),

  suggestedMrp: (lineId: string) =>
    get<_SuggestedMrp>(`/api/unpacking/lines/${lineId}/suggested-mrp`),

  finishCarton: (boxId: string) => post<void>(`/api/unpacking/boxes/${boxId}/finish`),

  // --- corrections ---
  // A count taken back is a new reversing entry, never a deleted one — the ledger is
  // append-only. A condition left null means "whichever it was counted as".
  undo: (lineId: string, quantity: number) =>
    post<void>(`/api/unpacking/lines/${lineId}/undo`, { quantity }),

  // Every code that scans as a line's goods, and which may be given up.
  codesFor: (lineId: string) => get<_LearntCode[]>(`/api/unpacking/lines/${lineId}/codes`),

  // Forget a code put on the wrong goods, so the sticker can be scanned onto the right item.
  releaseCode: (code: string) =>
    post<void>(`/api/unpacking/codes/release?code=${encodeURIComponent(code)}`),
}

// --- checkout ---
import type { CartView as _CartView } from './types'

async function del<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`, { method: 'DELETE' })
  if (!response.ok) throw new BackendError(await message(response))
  return response.json() as Promise<T>
}

export const checkout = {
  open: () => post<_CartView>('/api/checkout/cart') as Promise<_CartView>,
  view: (cartId: string) => get<_CartView>(`/api/checkout/cart/${cartId}`),
  scan: (cartId: string, code: string) =>
    post<_CartView>(`/api/checkout/cart/${cartId}/scan`, { code }) as Promise<_CartView>,
  setQuantity: (cartId: string, lineId: string, quantity: number) =>
    post<_CartView>(`/api/checkout/cart/${cartId}/lines/${lineId}/quantity`, {
      quantity,
    }) as Promise<_CartView>,
  removeLine: (cartId: string, lineId: string) =>
    del<_CartView>(`/api/checkout/cart/${cartId}/lines/${lineId}`),
  clear: (cartId: string) =>
    post<_CartView>(`/api/checkout/cart/${cartId}/clear`) as Promise<_CartView>,
}

// --- remediation ---
import type {
  IssueTypeOption as _IssueTypeOption,
  ProductStates as _ProductStates,
  ProductSummary as _ProductSummary,
  BacklogItem as _BacklogItem,
  ReviewItem as _ReviewItem,
  ExtraRecord as _ExtraRecord,
  ShortLine as _ShortLine,
} from './types'

export const remediation = {
  // The kinds of work a department offers, for the picker when marking an item needs-work.
  issueTypes: (category: string) =>
    getList<_IssueTypeOption>(
      `/api/remediation/issue-types?category=${encodeURIComponent(category)}`,
    ),

  // A product and every state its stock is held in, for moving units between them.
  states: (productId: string) => get<_ProductStates>(`/api/remediation/products/${productId}/states`),

  // The same, reached from a scanned code rather than a product id.
  statesByCode: (code: string) =>
    get<_ProductStates>(`/api/remediation/resolve?code=${encodeURIComponent(code)}`),

  // Products whose name matches, for looking one up by search.
  search: (q: string) => getList<_ProductSummary>(`/api/remediation/search?q=${encodeURIComponent(q)}`),

  // Every pile of needs-work goods waiting on preparation.
  backlog: () => getList<_BacklogItem>('/api/remediation/backlog'),

  // Damaged and broken goods with their notes, for sorting the fixable into needs-work.
  review: () => getList<_ReviewItem>('/api/remediation/review'),

  // Extras recorded against boxes, held as their own product, waiting to be linked.
  extras: () => getList<_ExtraRecord>('/api/remediation/extras'),

  // The lines of a delivery still missing units — candidates an extra can fill.
  shortsInLot: (lotId: string) => getList<_ShortLine>(`/api/remediation/lots/${lotId}/shorts`),

  // Merge an extra into the real product it turned out to be (the whole extra; they are one).
  link: (body: { extraProductId: string; targetLineId: string }) =>
    post<void>('/api/remediation/link', body),

  // Move a quantity of a product's units from one state to another within an open lot.
  changeState: (body: {
    productId: string
    lotId: string
    from: string
    fromIssueType: string | null
    to: string
    toIssueType: string | null
    quantity: number
  }) => post<void>('/api/remediation/change-state', body),
}

// --- receiving ---

import type {
  LotSummary as _LotSummary,
  ReceivingBoxes as _ReceivingBoxes,
  CreateManualLotRequest as _CreateManualLotRequest,
  AddProductRequest as _AddProductRequest,
  AddProductResponse as _AddProductResponse,
} from './types'

export const receiving = {
  lots: () => getList<_LotSummary>('/api/lots'),
  boxes: (lotId: string) => get<_ReceivingBoxes>(`/api/lots/${lotId}/boxes`),
  receiveBox: (lotId: string, manifestCartonId: string) =>
    post(`/api/lots/${lotId}/receive-box`, { manifestCartonId }),
  markNotReceived: (lotId: string, manifestCartonId: string) =>
    post(`/api/lots/${lotId}/mark-not-received`, { manifestCartonId }),
  rejectBox: (lotId: string, manifestCartonId: string, reason: string) =>
    post(`/api/lots/${lotId}/reject-box`, { manifestCartonId, reason }),
  createManualLot: (supplierId: string, receivedOn: string, amountPaidPaise: number) =>
    post<_LotSummary>('/api/lots/manual', {
      supplierId,
      receivedOn,
      amountPaidPaise,
      allocationMethod: 'RELATIVE_MRP',
    }),
  addProduct: (
    lotId: string,
    code: string | null,
    name: string,
    quantity: number,
    categoryCode: string,
    estimatedCostPaise: number | null,
  ) =>
    post<_AddProductResponse>(`/api/lots/${lotId}/add-product`, {
      code,
      name,
      quantity,
      categoryCode,
      estimatedCostPaise,
    }),
}

// --- suppliers ---
// The supplier master: list (all, or active-only for the receipt pick-list), search, create, edit,
// deactivate/reactivate, and the lots received from one supplier.

import type {
  Supplier as _Supplier,
  SupplierInput as _SupplierInput,
  SupplierLot as _SupplierLot,
} from './types'

export const suppliers = {
  list: (activeOnly = false, search = '') =>
    getList<_Supplier>(
      `/api/suppliers?active=${activeOnly}&search=${encodeURIComponent(search)}`,
    ),
  get: (id: string) => get<_Supplier>(`/api/suppliers/${id}`),
  create: (body: _SupplierInput) => post<_Supplier>('/api/suppliers', body),
  update: (id: string, body: _SupplierInput) => put<_Supplier>(`/api/suppliers/${id}`, body),
  deactivate: (id: string) => post<_Supplier>(`/api/suppliers/${id}/deactivate`),
  reactivate: (id: string) => post<_Supplier>(`/api/suppliers/${id}/reactivate`),
  lots: (id: string) => getList<_SupplierLot>(`/api/suppliers/${id}/lots`),
}

// --- catalog ---
// Browsing the product catalogue by name and found status, and opening one product to its detail.
// Setting a price on a catalogue product reuses api.setPrice — there is no separate endpoint for it.

import type { CatalogDetail as _CatalogDetail, CatalogEntry as _CatalogEntry } from './types'

export const catalog = {
  // Name, status, category, and lot narrow together; a blank lot spans every delivery.
  browse: (q: string, status: string, category = '', page = 0, size = 25, lot = '') =>
    getList<_CatalogEntry>(
      `/api/catalog?q=${encodeURIComponent(q)}&status=${encodeURIComponent(status)}` +
        `&category=${encodeURIComponent(category)}&page=${page}&size=${size}` +
        `&lot=${encodeURIComponent(lot)}`,
    ),

  detail: (productId: string) => get<_CatalogDetail>(`/api/catalog/products/${productId}`),
}

// --- product-centric counting ---
// Counting one product across every box of a single, chosen delivery — the boxes that still
// owe it units, in one grid, rather than opening each box in turn.

import type { ProductCountRequest as _ProductCountRequest, ProductCountResult as _ProductCountResult, ProductLotLines as _ProductLotLines } from './types'

export const productCounting = {
  lines: (lotId: string, productId: string) =>
    get<_ProductLotLines>(`/api/product-counting/lots/${lotId}/products/${productId}/lines`),

  count: (body: _ProductCountRequest) =>
    post<_ProductCountResult>('/api/product-counting/count', body) as Promise<_ProductCountResult>,
}

// --- printer (barcode labels) -----------------------------------------------------------------------

import type {
  PrintJob as _PrintJob,
  PrinterConfig as _PrinterConfig,
  PrinterTestResult as _PrinterTestResult,
} from './types'

export const printer = {
  // A self-contained label job: the fields the label carries, so the executor renders without a
  // database read. productId lets a successful print mark that product labelled.
  queueLabel: (job: {
    barcode: string
    productName: string
    sellingPricePaise: number
    mrpPaise: number | null
    copies: number
    productId: string | null
  }) => post<_PrintJob>('/api/print-jobs', job),

  getJobStatus: (jobId: string) =>
    get<_PrintJob>(`/api/print-jobs/${jobId}`),

  // Labels print two to a row, so a lone label is held until a second pairs with it. This is how
  // many are held, and the flush that prints a leftover (as a duplicate pair) on demand.
  pendingCount: () => get<{ count: number }>('/api/print-jobs/pending-count'),
  flush: () => post<{ printed: number }>('/api/print-jobs/flush'),

  getConfig: () =>
    get<_PrinterConfig>('/api/admin/printer-config'),

  saveConfig: (config: Partial<_PrinterConfig>) =>
    put<_PrinterConfig>('/api/admin/printer-config', config),

  testPrinter: () =>
    post<_PrinterTestResult>('/api/admin/printer-config/test'),
}

// --- pricing workbench, review queue, bulk label print, reconciliation --------------------------

import type {
  AwaitingLabelProduct as _AwaitingLabelProduct,
  BulkPrintResult as _BulkPrintResult,
  CaptureSummary as _CaptureSummary,
  LotPhantomReport as _LotPhantomReport,
  PriceSuggestion as _PriceSuggestion,
  ScannedItem as _ScannedItem,
  ShelfLot as _ShelfLot,
  ShelfPricedProduct as _ShelfPricedProduct,
  WriteOffResult as _WriteOffResult,
} from './types'

export const shelfPricing = {
  lots: () => get<_ShelfLot[]>('/api/pricing/shelf/lots'),

  categoriesForLot: (lotId: string) =>
    get<string[]>(`/api/pricing/shelf/lots/${lotId}/categories`),

  // A scanned code the lot does not hold answers 404 — getList turns that into an empty list, so
  // "no match, key it by hand" is a clean [] rather than a thrown error.
  scan: (lotId: string, code: string) =>
    getList<_ScannedItem>(
      `/api/pricing/shelf/scan?lotId=${lotId}&code=${encodeURIComponent(code)}`,
    ).then((items) => items[0] ?? null),

  // TRANSIENT — direct scan with no lot chosen, resolves already-counted stock in any lot.
  scanAny: (code: string) =>
    getList<_ScannedItem>(
      `/api/pricing/shelf/scan-any?code=${encodeURIComponent(code)}`,
    ).then((items) => items[0] ?? null),

  suggest: (unitCostPaise: number, categoryCode: string, marginPercent?: number) =>
    get<_PriceSuggestion>(
      `/api/pricing/shelf/suggest?unitCostPaise=${unitCostPaise}&categoryCode=${encodeURIComponent(
        categoryCode,
      )}${marginPercent != null ? `&marginPercent=${marginPercent}` : ''}`,
    ),

  saveExisting: (body: {
    productId: string
    batchId: string
    categoryCode: string
    sellingPricePaise: number
    mrpPaise: number | null
  }) => post<_ShelfPricedProduct>('/api/pricing/shelf/existing', body) as Promise<_ShelfPricedProduct>,

  saveManual: (body: {
    lotId: string
    name: string
    categoryCode: string
    condition: string
    quantity: number
    sellingPricePaise: number
    mrpPaise: number | null
  }) => post<_ShelfPricedProduct>('/api/pricing/shelf/manual', body) as Promise<_ShelfPricedProduct>,

  phantomReport: (lotId: string) =>
    get<_LotPhantomReport>(`/api/pricing/lots/${lotId}/reconcile`),

  writeOff: (lotId: string) =>
    post<_WriteOffResult>(`/api/pricing/lots/${lotId}/write-off`) as Promise<_WriteOffResult>,
}

export const reviewQueue = {
  pending: () => get<_CaptureSummary[]>('/api/pricing/review-queue'),

  approve: (
    captureId: string,
    body: {
      lotId: string
      categoryCode: string
      condition: string
      quantity: number
      sellingPricePaise: number
      mrpPaise: number | null
    },
  ) =>
    post<_ShelfPricedProduct>(`/api/pricing/review-queue/${captureId}/approve`, body) as Promise<
      _ShelfPricedProduct
    >,

  reject: (captureId: string) => post<void>(`/api/pricing/review-queue/${captureId}/reject`),
}

export const capture = {
  submit: (body: { name: string; mrpPaise: number | null; description: string | null; lotId: string | null }) =>
    post<_CaptureSummary>('/api/capture', body) as Promise<_CaptureSummary>,
}

export const bulkPrint = {
  awaiting: () => get<_AwaitingLabelProduct[]>('/api/print-jobs/bulk/awaiting'),
  print: (productIds: string[]) =>
    post<_BulkPrintResult>('/api/print-jobs/bulk', { productIds }) as Promise<_BulkPrintResult>,
}
