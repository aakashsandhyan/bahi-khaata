import { useEffect, useState } from 'react'
import { receiving, BackendError } from '../api'
import type { LotIntakeStats, LotSummary, ReceivingBoxes } from '../types'
import { ManualLotPanel } from './ManualLotPanel'

function flagClass(state: string): string {
  if (state === 'EXPECTED') return 'flag neutral'
  if (state === 'RECEIVED' || state === 'UNPACKING') return 'flag ok'
  return 'flag stop'
}

/**
 * The door-receiving flow, relocated from the retired Receiving screen with behavior identical
 * (design decision D7 of palletworks-intake): the same `receiving.*` endpoints, the same box
 * states, the same manual-lot add-product form — never a rewrite, because the flow is daily-use
 * and already correct. The manual-lot branch is {@link ManualLotPanel}, kept separate to stay
 * under the component size cap (D1).
 */
export function BoxesTab({
  lot,
  stats,
  onChanged,
}: {
  lot: LotSummary
  stats: LotIntakeStats | null
  onChanged: () => void
}) {
  const [boxes, setBoxes] = useState<ReceivingBoxes | null>(null)
  const [cartonId, setCartonId] = useState('')
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)

  const load = () => {
    receiving
      .boxes(lot.id)
      .then(setBoxes)
      .catch((err) =>
        setMessage({ text: err instanceof BackendError ? err.message : 'Cannot load boxes.', tone: 'stop' }),
      )
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [lot.id])

  const afterAction = (text: string, tone: string) => {
    setMessage({ text, tone })
    load()
    onChanged()
  }

  const receiveBox = async () => {
    if (!cartonId) return
    try {
      await receiving.receiveBox(lot.id, cartonId)
      setCartonId('')
      afterAction(`✓ ${cartonId} received`, 'ok')
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Error receiving box.', tone: 'stop' })
    }
  }

  const markNotReceived = async () => {
    if (!cartonId) return
    try {
      await receiving.markNotReceived(lot.id, cartonId)
      setCartonId('')
      afterAction(`✓ ${cartonId} marked not received`, 'warn')
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Error.', tone: 'stop' })
    }
  }

  const rejectBox = async () => {
    if (!cartonId) return
    try {
      await receiving.rejectBox(lot.id, cartonId, 'Damaged at dock')
      setCartonId('')
      afterAction(`✓ ${cartonId} marked damaged`, 'warn')
    } catch (err) {
      setMessage({ text: err instanceof BackendError ? err.message : 'Error.', tone: 'stop' })
    }
  }

  return (
    <div>
      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {lot.isManual ? (
        <ManualLotPanel lot={lot} boxes={boxes} stats={stats} onAdded={afterAction} />
      ) : (
        <>
          {boxes && (
            <div className="intake-recon-row">
              <span className="intake-math-label">Boxes dealt with</span>
              <span className="intake-math-value">
                {boxes.counts.received + boxes.counts.unpacked + boxes.counts.rejected + boxes.counts.notReceived} /{' '}
                {boxes.counts.expected}
              </span>
            </div>
          )}

          <input
            className="scan"
            placeholder="Scan carton ID"
            value={cartonId}
            onChange={(e) => setCartonId(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && receiveBox()}
            autoFocus
          />

          <div className="actions">
            <button className="btn-primary" onClick={receiveBox} style={{ flex: 1 }}>
              Receive
            </button>
            <button className="btn-warn" onClick={markNotReceived} style={{ flex: 1 }}>
              ✗ Not received
            </button>
            <button className="btn-warn" onClick={rejectBox} style={{ flex: 1 }}>
              Damaged
            </button>
          </div>
        </>
      )}

      {boxes && (
        <div className="items" style={{ marginTop: 'var(--space-3)' }}>
          {boxes.boxes.map((box) => (
            <div key={box.manifestCartonId} className="item">
              <div className="who">
                <div>{box.manifestCartonId}</div>
                {box.receivedAt && <div className="meta">{new Date(box.receivedAt).toLocaleTimeString()}</div>}
              </div>
              <div className="countcol">
                <span className={flagClass(box.state)}>{box.state}</span>
              </div>
            </div>
          ))}
          {boxes.boxes.length === 0 && <p className="intake-rail-empty">No boxes yet.</p>}
        </div>
      )}
    </div>
  )
}
