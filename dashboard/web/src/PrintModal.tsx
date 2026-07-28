import { useEffect, useState } from 'react'
import { printer, BackendError } from './api'

interface PrintModalProps {
  itemType: 'box' | 'batch' | 'product'
  itemId: string
  itemName: string
  defaultCopies?: number
  productName?: string
  category?: string
  costPerUnit?: string
  mrp?: string
  lotId?: string
  onClose: () => void
  onSuccess?: () => void
}

export function PrintModal({ itemType, itemId, itemName, defaultCopies = 1, onClose, onSuccess }: PrintModalProps) {
  const [copies, setCopies] = useState(defaultCopies)
  const [jobId, setJobId] = useState<string | null>(null)
  const [status, setStatus] = useState<'idle' | 'printing' | 'done' | 'failed'>('idle')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!jobId) return

    const interval = setInterval(async () => {
      try {
        const job = await printer.getJobStatus(jobId)
        if (!job) return
        setStatus(job.status as 'printing' | 'done' | 'failed')

        if (job.status === 'done') {
          clearInterval(interval)
          setMessage('✓ Labels printed successfully')
          setTimeout(() => {
            onSuccess?.()
            onClose()
          }, 1500)
        } else if (job.status === 'failed') {
          clearInterval(interval)
          setMessage(`✗ Print failed: ${job.error || 'Unknown error'}`)
        }
      } catch (err) {
        setMessage(`Error checking status: ${err instanceof BackendError ? err.message : 'Network error'}`)
      }
    }, 500)

    return () => clearInterval(interval)
  }, [jobId, onClose, onSuccess])

  const handlePrint = async () => {
    setStatus('printing')
    setMessage('Printing...')

    try {
      const job = await printer.queueJob(itemType, itemId, copies)
      if (job) {
        setJobId(job.jobId)
      }
    } catch (err) {
      setStatus('failed')
      setMessage(err instanceof BackendError ? err.message : 'Failed to queue print job')
    }
  }

  const handleRetry = () => {
    setJobId(null)
    setStatus('idle')
    setMessage('')
  }

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget && status !== 'printing') {
          onClose()
        }
      }}
    >
      <div className="modal-content" style={{ maxWidth: '420px' }}>
        <div className="modal-header">
          <h2>Print Label</h2>
          {status !== 'printing' && (
            <button
              onClick={onClose}
              style={{
                background: 'transparent',
                border: 'none',
                fontSize: '18px',
                cursor: 'pointer',
                color: 'var(--ink-faint)',
              }}
            >
              ✕
            </button>
          )}
        </div>

        <div className="modal-body">
          {status === 'idle' && (
            <>
              <div style={{ marginBottom: 'var(--s3)', padding: 'var(--s3)', background: 'var(--line-soft)', borderRadius: 'var(--r1)' }}>
                <div style={{ fontSize: '11px', color: 'var(--ink-faint)', textTransform: 'uppercase', marginBottom: 'var(--s2)' }}>
                  Label Preview
                </div>
                <div style={{ fontSize: '13px', lineHeight: 1.6, fontFamily: 'monospace' }}>
                  <div style={{ fontWeight: '600', marginBottom: 'var(--s1)' }}>┌─ BARCODE ─┐</div>
                  {productName && <div>{productName}</div>}
                  {category && <div style={{ fontSize: '11px', color: 'var(--ink-faint)' }}>{category}</div>}
                  {costPerUnit && <div>Cost: ₹{costPerUnit}</div>}
                  {mrp && <div>MRP: ₹{mrp}</div>}
                  {lotId && <div style={{ fontSize: '11px' }}>Lot: {lotId}</div>}
                  <div style={{ fontSize: '11px', color: 'var(--ink-faint)', marginTop: 'var(--s1)' }}>4x6 thermal label</div>
                </div>
              </div>

              <div style={{ marginBottom: 'var(--s3)' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: '600', marginBottom: 'var(--s1)' }}>
                  Number of Copies
                </label>
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={copies}
                  onChange={(e) => setCopies(Math.max(1, parseInt(e.target.value) || 1))}
                  style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r1)',
                    fontSize: '14px',
                    fontFamily: 'inherit',
                  }}
                />
              </div>
            </>
          )}

          {(status === 'printing' || status === 'done' || status === 'failed') && (
            <div
              style={{
                padding: 'var(--s3)',
                background: status === 'done' ? 'var(--good-tint)' : status === 'failed' ? 'var(--stop-tint)' : 'var(--line-soft)',
                borderRadius: 'var(--r1)',
                textAlign: 'center',
                marginBottom: 'var(--s3)',
                color: status === 'done' ? 'var(--good)' : status === 'failed' ? 'var(--stop)' : 'var(--ink-soft)',
              }}
            >
              {status === 'printing' && (
                <div>
                  <div style={{ fontSize: '18px', marginBottom: 'var(--s1)' }}>⟳</div>
                  <div>{message}</div>
                </div>
              )}
              {status === 'done' && (
                <div>
                  <div style={{ fontSize: '18px', marginBottom: 'var(--s1)' }}>✓</div>
                  <div>{message}</div>
                </div>
              )}
              {status === 'failed' && (
                <div>
                  <div style={{ fontSize: '14px' }}>{message}</div>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="modal-footer" style={{ display: 'flex', gap: 'var(--s2)' }}>
          {status === 'idle' && (
            <>
              <button onClick={handlePrint} className="btn-primary" style={{ flex: 1 }}>
                Print
              </button>
              <button onClick={onClose} style={{ flex: 1 }}>
                Cancel
              </button>
            </>
          )}
          {status === 'failed' && (
            <>
              <button onClick={handleRetry} className="btn-primary" style={{ flex: 1 }}>
                Retry
              </button>
              <button onClick={onClose} style={{ flex: 1 }}>
                Close
              </button>
            </>
          )}
          {status === 'done' && (
            <button onClick={onClose} className="btn-primary" style={{ flex: 1 }}>
              Close
            </button>
          )}
          {status === 'printing' && (
            <button disabled style={{ flex: 1, opacity: 0.5, cursor: 'default' }}>
              Printing...
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
