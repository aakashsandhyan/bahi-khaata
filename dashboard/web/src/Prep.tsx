import { useEffect, useState } from 'react'
import { remediation, BackendError } from './api'
import type {
  BacklogItem,
  ExtraRecord,
  IssueTypeOption,
  ProductStates,
  ProductSummary,
  RemediationLine,
  ReviewItem,
  ShortLine,
  StockCondition,
} from './types'

/**
 * Preparing imperfect goods into sellable stock, and the backlog of what waits.
 *
 * The shop's language, not the schema's: Ready, Seconds, Needs work, Scrap. The backlog lists every
 * pile of needs-work goods, grouped by the kind of work; opening one shows that product's stock in
 * every state it is held, and lets a unit be moved as the work is done — cleaned into Ready, found
 * damaged into Seconds, or given up as Scrap.
 */
const STATE_LABEL: Record<StockCondition, string> = {
  GOOD: 'Ready',
  DAMAGED: 'Seconds',
  NEEDS_WORK: 'Needs work',
  UNUSABLE: 'Scrap',
}

const STATE_CSS: Record<StockCondition, string> = {
  GOOD: 'on-good',
  DAMAGED: 'on-damaged',
  NEEDS_WORK: 'on-needswork',
  UNUSABLE: 'on-broken',
}

export function Prep() {
  const [mode, setMode] = useState<'backlog' | 'review' | 'extras'>('backlog')
  const [backlog, setBacklog] = useState<BacklogItem[]>([])
  const [review, setReview] = useState<ReviewItem[]>([])
  const [extras, setExtras] = useState<ExtraRecord[]>([])
  const [folded, setFolded] = useState<Set<string>>(new Set())
  const [states, setStates] = useState<ProductStates | null>(null)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  const fail = (e: unknown) =>
    setMessage({
      text: e instanceof BackendError ? e.message : 'Cannot reach the system.',
      tone: 'stop',
    })

  const loadBacklog = () => remediation.backlog().then(setBacklog).catch(() => {})
  const loadReview = () => remediation.review().then(setReview).catch(() => {})
  const loadExtras = () => remediation.extras().then(setExtras).catch(() => {})
  useEffect(() => {
    loadBacklog()
  }, [])
  useEffect(() => {
    if (mode === 'review') loadReview()
    if (mode === 'extras') loadExtras()
  }, [mode])

  const open = (productId: string) => remediation.states(productId).then(setStates).catch(fail)

  // A scanned code (LSN, EAN, or the ASIN) resolves straight to its product's states.
  const openByCode = (code: string) =>
    remediation
      .statesByCode(code.trim())
      .then(setStates)
      .catch(() => setMessage({ text: `Nothing scans as "${code.trim()}".`, tone: 'warn' }))

  const move = async (body: Parameters<typeof remediation.changeState>[0], label: string) => {
    try {
      await remediation.changeState(body)
      setMessage({ text: `Moved ${body.quantity} to ${label}.`, tone: 'ok' })
      setStates(await remediation.states(body.productId))
      loadBacklog()
    } catch (e) {
      fail(e)
    }
  }

  // A move from the review list stays on the list — it does not open the product's state panel.
  const reviewMove = async (body: Parameters<typeof remediation.changeState>[0], label: string) => {
    try {
      await remediation.changeState(body)
      setMessage({ text: `Moved ${body.quantity} to ${label}.`, tone: 'ok' })
      loadReview()
      loadBacklog()
    } catch (e) {
      fail(e)
    }
  }

  const foldPile = (label: string) =>
    setFolded((prev) => {
      const next = new Set(prev)
      next.has(label) ? next.delete(label) : next.add(label)
      return next
    })

  // Group the backlog by the kind of work, so it reads as piles to route.
  const groups = new Map<string, BacklogItem[]>()
  for (const item of backlog) {
    const key = item.issueLabel ?? item.issueType ?? 'Needs work'
    groups.set(key, [...(groups.get(key) ?? []), item])
  }

  return (
    <div className="prep">
      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {states ? (
        <StatesPanel states={states} onBack={() => setStates(null)} onMove={move} />
      ) : (
        <>
          <h1>Prep</h1>
          <div className="mode-toggle">
            <button className={mode === 'backlog' ? 'on' : ''} onClick={() => setMode('backlog')}>
              Backlog
            </button>
            <button className={mode === 'review' ? 'on' : ''} onClick={() => setMode('review')}>
              Review damaged
            </button>
            <button className={mode === 'extras' ? 'on' : ''} onClick={() => setMode('extras')}>
              Extras{extras.length > 0 ? ` (${extras.length})` : ''}
            </button>
          </div>

          {mode === 'extras' ? (
            <ExtrasList
              items={extras}
              onLinked={() => {
                loadExtras()
                loadBacklog()
                setMessage({ text: 'Linked.', tone: 'ok' })
              }}
              onError={fail}
            />
          ) : mode === 'backlog' ? (
            <>
              <ProductFinder onOpen={open} onScan={openByCode} />
              {backlog.length === 0 && <p className="empty">Nothing waiting on work.</p>}
              {[...groups.entries()].map(([label, items]) => (
                <section key={label} className="pile">
                  <h2 className="pile-toggle" onClick={() => foldPile(label)}>
                    <span>
                      {folded.has(label) ? '▸' : '▾'} {label}
                    </span>
                    <span className="pile-count">
                      {items.reduce((n, i) => n + i.quantity, 0)} units · {items.length} items
                    </span>
                  </h2>
                  {!folded.has(label) &&
                    items.map((i) => (
                      <button
                        key={i.productId + i.lotId}
                        className="choice"
                        onClick={() => open(i.productId)}
                      >
                        <span>{i.productName}</span>
                        <span className="meta">
                          {i.quantity} to {label.toLowerCase()} · {i.categoryCode}
                        </span>
                      </button>
                    ))}
                </section>
              ))}
            </>
          ) : (
            <ReviewList items={review} onMove={reviewMove} />
          )}
        </>
      )}
    </div>
  )
}

function StatesPanel({
  states,
  onBack,
  onMove,
}: {
  states: ProductStates
  onBack: () => void
  onMove: (body: Parameters<typeof remediation.changeState>[0], label: string) => void
}) {
  const [moving, setMoving] = useState<RemediationLine | null>(null)
  return (
    <div className="states">
      <button className="back" onClick={onBack}>
        ← Backlog
      </button>
      <h1>{states.productName}</h1>
      <p className="code">{states.categoryCode}</p>

      {states.lines.length === 0 && <p className="empty">No stock held for this product.</p>}
      {states.lines.map((line, idx) => (
        <button
          key={idx}
          className={`choice${moving === line ? ' warn-choice' : ''}`}
          onClick={() => setMoving(line)}
        >
          <span>
            {STATE_LABEL[line.condition]}
            {line.issueLabel ? ` · ${line.issueLabel}` : ''}
          </span>
          <span className="meta">{line.quantity} units — tap to move</span>
        </button>
      ))}

      {moving && (
        <MoveForm
          productId={states.productId}
          categoryCode={states.categoryCode}
          from={moving}
          onCancel={() => setMoving(null)}
          onMove={(body, label) => {
            onMove(body, label)
            setMoving(null)
          }}
        />
      )}
    </div>
  )
}

function MoveForm({
  productId,
  categoryCode,
  from,
  onCancel,
  onMove,
}: {
  productId: string
  categoryCode: string
  from: RemediationLine
  onCancel: () => void
  onMove: (body: Parameters<typeof remediation.changeState>[0], label: string) => void
}) {
  // A unit can go to any state but the one it is already in.
  const targets = (['GOOD', 'DAMAGED', 'NEEDS_WORK', 'UNUSABLE'] as StockCondition[]).filter(
    (t) => t !== from.condition || from.issueType != null,
  )
  const [to, setTo] = useState<StockCondition>(targets[0])
  const [issue, setIssue] = useState<string | null>(null)
  const [issues, setIssues] = useState<IssueTypeOption[]>([])
  const [qty, setQty] = useState(1)

  useEffect(() => {
    if (to === 'NEEDS_WORK' && issues.length === 0) {
      remediation.issueTypes(categoryCode).then(setIssues).catch(() => {})
    }
  }, [to, categoryCode, issues.length])

  const submit = () => {
    if (to === 'NEEDS_WORK' && !issue) return
    onMove(
      {
        productId,
        lotId: from.lotId,
        from: from.condition,
        fromIssueType: from.issueType,
        to,
        toIssueType: to === 'NEEDS_WORK' ? issue : null,
        quantity: qty,
      },
      STATE_LABEL[to],
    )
  }

  return (
    <div className="move-form">
      <p>
        Move from <strong>{STATE_LABEL[from.condition]}</strong>
        {from.issueLabel ? ` · ${from.issueLabel}` : ''} to:
      </p>
      <div className="cond-row">
        {targets.map((t) => (
          <button
            key={t}
            className={`cond ${STATE_CSS[t]}${to === t ? ' sel' : ''}`}
            onClick={() => setTo(t)}
          >
            {STATE_LABEL[t]}
          </button>
        ))}
      </div>
      {to === 'NEEDS_WORK' && (
        <div className="issue-row">
          {issues.map((it) => (
            <button
              key={it.code}
              className={`issue${issue === it.code ? ' sel' : ''}`}
              onClick={() => setIssue(it.code)}
            >
              {it.label}
            </button>
          ))}
        </div>
      )}
      <div className="qty-row">
        <label>How many?</label>
        <input
          className="scan small"
          type="number"
          min={1}
          max={from.quantity}
          value={qty}
          onChange={(e) => setQty(Math.max(1, Math.min(from.quantity, Number(e.target.value) || 1)))}
        />
        <span className="meta">of {from.quantity}</span>
      </div>
      <div className="actions">
        <button onClick={onCancel}>Cancel</button>
        <button className="btn-primary" onClick={submit}>
          Move {qty} to {STATE_LABEL[to]}
        </button>
      </div>
    </div>
  )
}

function ProductFinder({
  onOpen,
  onScan,
}: {
  onOpen: (productId: string) => void
  onScan: (code: string) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<ProductSummary[]>([])

  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) {
      setResults([])
      return
    }
    let live = true
    remediation
      .search(q)
      .then((r) => live && setResults(r))
      .catch(() => {})
    return () => {
      live = false
    }
  }, [query])

  const clear = () => {
    setQuery('')
    setResults([])
  }

  return (
    <div className="finder">
      <input
        className="scan small"
        autoFocus
        placeholder="Scan an item, or type a name to find it"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => {
          // A scanner types the code then Enter, resolving straight to the product. A typed name
          // shows matches; Enter then opens the first, since the code path would find nothing.
          if (e.key === 'Enter' && query.trim()) {
            if (results.length > 0) onOpen(results[0].productId)
            else onScan(query)
            clear()
          }
        }}
      />
      {results.map((p) => (
        <button
          key={p.productId}
          className="choice"
          onClick={() => {
            onOpen(p.productId)
            clear()
          }}
        >
          <span>{p.productName}</span>
          <span className="meta">{p.categoryCode}</span>
        </button>
      ))}
    </div>
  )
}

function ReviewList({
  items,
  onMove,
}: {
  items: ReviewItem[]
  onMove: (body: Parameters<typeof remediation.changeState>[0], label: string) => void
}) {
  const [open, setOpen] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  if (items.length === 0) return <p className="empty">Nothing marked damaged or broken.</p>

  const toggle = (category: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev)
      next.has(category) ? next.delete(category) : next.add(category)
      return next
    })

  // Group by department, keeping the backend's notes-first order within each.
  const groups = new Map<string, ReviewItem[]>()
  for (const it of items) groups.set(it.categoryCode, [...(groups.get(it.categoryCode) ?? []), it])

  return (
    <div className="review">
      <p className="review-hint">
        Sort the fixable — missing a part, needs a wash — into <strong>Needs work</strong>: they sell
        at full price once mended. Leave the true scratches as <strong>Seconds</strong>.
      </p>
      {[...groups.entries()].map(([category, catItems]) => (
        <section key={category} className="pile">
          <h2 className="pile-toggle" onClick={() => toggle(category)}>
            <span>
              {collapsed.has(category) ? '▸' : '▾'} {category}
            </span>
            <span className="pile-count">
              {catItems.reduce((n, i) => n + i.quantity, 0)} units · {catItems.length} items
            </span>
          </h2>
          {!collapsed.has(category) &&
            catItems.map((it) => {
            const key = it.productId + it.lotId + it.condition
            return (
              <div key={key} className="review-item">
                <button
                  className={`choice ${it.condition === 'UNUSABLE' ? 'stop-choice' : 'warn-choice'}`}
                  onClick={() => setOpen(open === key ? null : key)}
                >
                  <span>{it.productName}</span>
                  <span className="meta">
                    {STATE_LABEL[it.condition]} · {it.quantity}
                    {it.remark ? ` — ${it.remark}` : ''}
                  </span>
                </button>
                {open === key && (
                  <MoveForm
                    productId={it.productId}
                    categoryCode={it.categoryCode}
                    from={{
                      lotId: it.lotId,
                      condition: it.condition,
                      issueType: null,
                      issueLabel: null,
                      quantity: it.quantity,
                    }}
                    onCancel={() => setOpen(null)}
                    onMove={(body, label) => {
                      onMove(body, label)
                      setOpen(null)
                    }}
                  />
                )}
              </div>
            )
          })}
        </section>
      ))}
    </div>
  )
}

function ExtrasList({
  items,
  onLinked,
  onError,
}: {
  items: ExtraRecord[]
  onLinked: () => void
  onError: (e: unknown) => void
}) {
  const [open, setOpen] = useState<string | null>(null)
  if (items.length === 0) return <p className="empty">No extras waiting to be linked.</p>
  return (
    <div className="extras">
      <p className="review-hint">
        An extra was recorded as its own product. If a line came up short, link the extra to it — the
        units become that product's and the shortfall fills. Leave it if it's genuinely new.
      </p>
      {items.map((e) => (
        <div key={e.productId} className="review-item">
          <button
            className="choice"
            onClick={() => setOpen(open === e.productId ? null : e.productId)}
          >
            <span>{e.productName}</span>
            <span className="meta">
              {e.quantity} · box {e.boxTracking} · {e.categoryCode}
              {e.code ? ` · ${e.code}` : ''}
            </span>
          </button>
          {open === e.productId && (
            <LinkForm
              extra={e}
              onCancel={() => setOpen(null)}
              onLinked={() => {
                setOpen(null)
                onLinked()
              }}
              onError={onError}
            />
          )}
        </div>
      ))}
    </div>
  )
}

function LinkForm({
  extra,
  onCancel,
  onLinked,
  onError,
}: {
  extra: ExtraRecord
  onCancel: () => void
  onLinked: () => void
  onError: (e: unknown) => void
}) {
  const [shorts, setShorts] = useState<ShortLine[]>([])
  const [target, setTarget] = useState<ShortLine | null>(null)
  const [filter, setFilter] = useState('')

  useEffect(() => {
    remediation.shortsInLot(extra.lotId).then(setShorts).catch(() => {})
  }, [extra.lotId])

  const submit = () => {
    if (!target) return
    remediation
      .link({ extraProductId: extra.productId, targetLineId: target.lineId })
      .then(onLinked)
      .catch(onError)
  }

  const needle = filter.trim().toLowerCase()
  const choices = shorts.filter(
    (s) =>
      !needle ||
      s.productName.toLowerCase().includes(needle) ||
      s.asin.toLowerCase().includes(needle),
  )

  return (
    <div className="move-form">
      {!target ? (
        <>
          <p>Which product is it really? (lines still short in this delivery)</p>
          <input
            className="scan small"
            placeholder="Type part of the name or ASIN"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          {choices.length === 0 && <p className="empty">No short lines here.</p>}
          {choices.map((s) => (
            <button key={s.lineId} className="choice" onClick={() => setTarget(s)}>
              <span>{s.productName}</span>
              <span className="meta">
                {s.asin} · short {s.shortBy} of {s.expected} · box {s.boxTracking}
              </span>
            </button>
          ))}
        </>
      ) : (
        <p>
          These are the same product. Merge the whole extra ({extra.quantity}) into{' '}
          <strong>{target.productName}</strong> — it fills {target.shortBy} short
          {extra.quantity > target.shortBy ? `, ${extra.quantity - target.shortBy} over` : ''}, and
          the extra's sticker will name it from now on.
        </p>
      )}
      <div className="actions">
        <button onClick={target ? () => setTarget(null) : onCancel}>
          {target ? 'Back' : 'Cancel'}
        </button>
        {target && (
          <button className="btn-primary" onClick={submit}>
            Merge into {target.productName.length > 24 ? target.productName.slice(0, 21) + '…' : target.productName}
          </button>
        )}
      </div>
    </div>
  )
}
