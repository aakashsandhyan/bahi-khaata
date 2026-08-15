import { useEffect, useState } from 'react'
import { registers, sales, BackendError } from './api'
import type { RegisterCloseSummary, RegisterStateView, SaleSummary } from './types'
import { rupees } from './money'

/**
 * The Register screen (design artifact: "Register 1 open", drawer over/short): both drawers'
 * live state, open-with-float, cash in/out against the open session, and the close flow — count
 * the drawer, see expected vs counted, the over/short pinned, and the session's bills.
 */
export function Register() {
  const [state, setState] = useState<RegisterStateView[]>([])
  const [error, setError] = useState<string | null>(null)
  const [closed, setClosed] = useState<RegisterCloseSummary | null>(null)
  const [closedBills, setClosedBills] = useState<SaleSummary[]>([])

  const load = () => {
    registers.state().then(setState).catch(() => setError('Cannot reach the registers.'))
  }
  useEffect(load, [])

  const run = async (fn: () => Promise<unknown>) => {
    setError(null)
    try {
      await fn()
      load()
    } catch (e) {
      setError(e instanceof BackendError ? e.message : 'Cannot reach the registers.')
    }
  }

  return (
    <div className="page">
      <h1>Registers</h1>
      {error && <div className="banner stop">{error}</div>}

      {closed && (
        <CloseSlip summary={closed} bills={closedBills} onDone={() => { setClosed(null); setClosedBills([]) }} />
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, maxWidth: 900 }}>
        {state.map((r) => (
          <RegisterCard
            key={r.registerName}
            register={r}
            onOpen={(operator, floatPaise) => run(() => registers.open(r.registerName, operator, floatPaise))}
            onMovement={(dir, amount, note) => run(() => registers.cashMovement(r.registerName, dir, amount, note))}
            onClose={async (countedPaise) =>
              run(async () => {
                const sessionId = r.sessionId
                const summary = await registers.close(r.registerName, countedPaise)
                setClosed(summary)
                if (sessionId) setClosedBills(await sales.recent(50, sessionId))
              })}
          />
        ))}
      </div>
    </div>
  )
}

function RegisterCard({
  register, onOpen, onMovement, onClose,
}: {
  register: RegisterStateView
  onOpen: (operator: string, floatPaise: number) => void
  onMovement: (dir: 'IN' | 'OUT', amountPaise: number, note: string) => void
  onClose: (countedPaise: number) => void
}) {
  const [operator, setOperator] = useState(() => localStorage.getItem('pricing.operator') ?? '')
  const [floatRupees, setFloatRupees] = useState('')
  const [moveRupees, setMoveRupees] = useState('')
  const [moveNote, setMoveNote] = useState('')
  const [countRupees, setCountRupees] = useState('')
  const [closing, setClosing] = useState(false)

  const paise = (s: string) => Math.round(parseFloat(s || '0') * 100)

  if (!register.open) {
    return (
      <div className="dash-alert" style={{ display: 'block', padding: 16 }}>
        <h2>{register.registerName}</h2>
        <div className="text-muted" style={{ marginBottom: 12 }}>Closed</div>
        <label style={{ display: 'block', marginBottom: 8 }}>
          Operator
          <input value={operator} onChange={(e) => setOperator(e.target.value)} placeholder="Who is on this drawer" />
        </label>
        <label style={{ display: 'block', marginBottom: 8 }}>
          Float counted in (₹)
          <input value={floatRupees} onChange={(e) => setFloatRupees(e.target.value)} inputMode="decimal" placeholder="2000" />
        </label>
        <button disabled={!operator.trim()} onClick={() => onOpen(operator.trim(), paise(floatRupees))}>
          Open {register.registerName}
        </button>
      </div>
    )
  }

  return (
    <div className="dash-alert" style={{ display: 'block', padding: 16 }}>
      <h2>{register.registerName} <span style={{ color: 'var(--color-accent)', fontSize: 14 }}>open</span></h2>
      <div className="text-muted" style={{ marginBottom: 4 }}>
        {register.operatorName} · float {rupees(register.floatPaise ?? 0)}
      </div>
      <div style={{ marginBottom: 12 }}>
        Expected in drawer now: <strong>{rupees(register.expectedPaise ?? 0)}</strong>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        <input value={moveRupees} onChange={(e) => setMoveRupees(e.target.value)} inputMode="decimal" placeholder="₹ amount" style={{ width: 110 }} />
        <input value={moveNote} onChange={(e) => setMoveNote(e.target.value)} placeholder="note (why)" />
      </div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <button disabled={!paise(moveRupees)} onClick={() => { onMovement('IN', paise(moveRupees), moveNote); setMoveRupees(''); setMoveNote('') }}>Cash in</button>
        <button disabled={!paise(moveRupees)} onClick={() => { onMovement('OUT', paise(moveRupees), moveNote); setMoveRupees(''); setMoveNote('') }}>Cash out</button>
      </div>

      {!closing ? (
        <button onClick={() => setClosing(true)}>Close {register.registerName}…</button>
      ) : (
        <div>
          <label style={{ display: 'block', marginBottom: 8 }}>
            Counted in drawer (₹)
            <input value={countRupees} onChange={(e) => setCountRupees(e.target.value)} inputMode="decimal" autoFocus />
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={() => onClose(paise(countRupees))}>Close with {rupees(paise(countRupees))}</button>
            <button onClick={() => setClosing(false)}>Keep open</button>
          </div>
        </div>
      )}
    </div>
  )
}

/** The close summary: the arithmetic a drawer answer needs, plus the session's bills. */
function CloseSlip({ summary, bills, onDone }: {
  summary: RegisterCloseSummary
  bills: SaleSummary[]
  onDone: () => void
}) {
  const os = summary.overShortPaise
  return (
    <div className="dash-alert" style={{ display: 'block', padding: 16, marginBottom: 16, maxWidth: 900 }}>
      <h2>{summary.registerName} closed — {summary.operatorName}</h2>
      <table style={{ margin: '8px 0' }}>
        <tbody>
          <tr><td>Float</td><td style={{ textAlign: 'right' }}>{rupees(summary.floatPaise)}</td></tr>
          <tr><td>Cash sales</td><td style={{ textAlign: 'right' }}>{rupees(summary.cashSalesPaise)}</td></tr>
          <tr><td><strong>Expected</strong></td><td style={{ textAlign: 'right' }}><strong>{rupees(summary.expectedPaise)}</strong></td></tr>
          <tr><td>Counted</td><td style={{ textAlign: 'right' }}>{rupees(summary.countedPaise)}</td></tr>
          <tr>
            <td><strong>{os === 0 ? 'Exact' : os > 0 ? 'Drawer over' : 'Drawer short'}</strong></td>
            <td style={{ textAlign: 'right', color: os === 0 ? 'inherit' : 'var(--color-accent)' }}>
              <strong>{os > 0 ? '+' : ''}{rupees(os)}</strong>
            </td>
          </tr>
        </tbody>
      </table>
      {bills.length > 0 && (
        <div className="text-muted">
          {bills.length} bill{bills.length === 1 ? '' : 's'} this session: {bills.map((b) => b.billNoFormatted).join(', ')}
        </div>
      )}
      <button style={{ marginTop: 8 }} onClick={onDone}>Done</button>
    </div>
  )
}
