// The only route to the backend. Every call goes through here, so the base address, the error
// handling, and the fact that this is a client of a separate service all live in one place.
//
// A refusal the backend wrote — a price above the MRP, a cost not yet known — comes back as a
// sentence meant for a person. It is thrown as-is rather than turned into "request failed",
// because the sentence is the useful part.

import type { BulkResult, PriceProposal, PriceableItem } from './types'

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
} from './types'

function countBody(
  quantity: number,
  mrpPaise: number | null,
  condition: string,
  remark: string | null,
  issueType: string | null,
) {
  const body: Record<string, unknown> = { quantity, mrpIsEstimate: false, condition }
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

  resolve: (boxId: string, code: string) =>
    getList<_UnpackingLine>(`/api/unpacking/boxes/${boxId}/resolve?code=${encodeURIComponent(code)}`),

  count: (
    lineId: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
    issueType: string | null = null,
  ) =>
    post<_CountOutcome>(
      `/api/unpacking/lines/${lineId}/count`,
      countBody(quantity, mrpPaise, condition, remark, issueType),
    ),

  tag: (
    lineId: string,
    scannedCode: string,
    quantity: number,
    mrpPaise: number | null,
    condition: string,
    remark: string | null,
    issueType: string | null = null,
  ) =>
    post<_CountOutcome>(`/api/unpacking/lines/${lineId}/tag`, {
      scannedCode,
      ...countBody(quantity, mrpPaise, condition, remark, issueType),
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

  // Reattribute an extra to the real product it turned out to be.
  link: (body: { extraProductId: string; targetLineId: string; quantity: number }) =>
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
