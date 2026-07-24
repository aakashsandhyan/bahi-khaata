import { useEffect, useState } from 'react'
import { remediation, BackendError } from './api'
import type {
  BacklogItem,
  IssueTypeOption,
  ProductStates,
  RemediationLine,
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
  const [backlog, setBacklog] = useState<BacklogItem[]>([])
  const [states, setStates] = useState<ProductStates | null>(null)
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  const fail = (e: unknown) =>
    setMessage({
      text: e instanceof BackendError ? e.message : 'Cannot reach the system.',
      tone: 'stop',
    })

  const loadBacklog = () => remediation.backlog().then(setBacklog).catch(() => {})
  useEffect(() => {
    loadBacklog()
  }, [])

  const open = (productId: string) => remediation.states(productId).then(setStates).catch(fail)

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
          <h1>Prep backlog</h1>
          {backlog.length === 0 && <p className="empty">Nothing waiting on work.</p>}
          {[...groups.entries()].map(([label, items]) => (
            <section key={label} className="pile">
              <h2>
                {label}
                <span className="pile-count">
                  {items.reduce((n, i) => n + i.quantity, 0)} units · {items.length} items
                </span>
              </h2>
              {items.map((i) => (
                <button key={i.productId + i.lotId} className="choice" onClick={() => open(i.productId)}>
                  <span>{i.productName}</span>
                  <span className="meta">
                    {i.quantity} to {label.toLowerCase()} · {i.categoryCode}
                  </span>
                </button>
              ))}
            </section>
          ))}
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
