import { useEffect, useState } from 'react'
import { registers, BackendError } from './api'
import type { RegisterStateView } from './types'
import { Checkout } from './Checkout'

// Which physical drawer this device is — remembered per device, like the operator name.
const REGISTER_KEY = 'till.register'

/**
 * The modern checkout: the same proven till, sold only through an open register so every sale
 * lands in a drawer session. No open session is not a dead end — the gate offers opening the
 * register right here (spec: selling-screens).
 */
export function CheckoutModern() {
  const [registerName, setRegisterName] = useState(
    () => localStorage.getItem(REGISTER_KEY) ?? 'Register 1')
  const [state, setState] = useState<RegisterStateView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [operator, setOperator] = useState(() => localStorage.getItem('pricing.operator') ?? '')
  const [floatRupees, setFloatRupees] = useState('')

  const pickRegister = (name: string) => {
    localStorage.setItem(REGISTER_KEY, name)
    setRegisterName(name)
  }

  const load = () => {
    registers.state().then(setState).catch(() => setError('Cannot reach the registers.'))
  }
  useEffect(load, [])

  if (state === null) {
    return <div className="page">{error ? <div className="banner stop">{error}</div> : 'Loading…'}</div>
  }

  const mine = state.find((r) => r.registerName === registerName) ?? state[0]

  if (!mine?.open) {
    return (
      <div className="page" style={{ maxWidth: 480 }}>
        <h1>Checkout</h1>
        {error && <div className="banner stop">{error}</div>}
        <p>
          <strong>{mine?.registerName}</strong> is not open — open it to start selling.
        </p>
        <div className="mode-toggle" style={{ marginBottom: 12 }}>
          {state.map((r) => (
            <button
              key={r.registerName}
              type="button"
              className={r.registerName === registerName ? 'on' : ''}
              onClick={() => pickRegister(r.registerName)}
            >
              {r.registerName}
            </button>
          ))}
        </div>
        <label style={{ display: 'block', marginBottom: 8 }}>
          Operator
          <input value={operator} onChange={(e) => setOperator(e.target.value)} placeholder="Who is on this drawer" />
        </label>
        <label style={{ display: 'block', marginBottom: 12 }}>
          Float counted in (₹)
          <input value={floatRupees} onChange={(e) => setFloatRupees(e.target.value)} inputMode="decimal" placeholder="2000" />
        </label>
        <button
          disabled={!operator.trim()}
          onClick={async () => {
            setError(null)
            try {
              await registers.open(
                mine.registerName, operator.trim(), Math.round(parseFloat(floatRupees || '0') * 100))
              load()
            } catch (e) {
              setError(e instanceof BackendError ? e.message : 'Cannot reach the registers.')
            }
          }}
        >
          Open {mine?.registerName} and start selling
        </button>
      </div>
    )
  }

  return (
    <>
      <div className="text-muted" style={{ padding: '4px 16px', fontSize: 13 }}>
        {mine.registerName} open · {mine.operatorName}
      </div>
      <Checkout registerSessionId={mine.sessionId} />
    </>
  )
}
