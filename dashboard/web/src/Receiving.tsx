import { useState } from 'react'

export function Receiving() {
  const [lotId, setLotId] = useState('')
  const [cartonId, setCartonId] = useState('')
  const [message, setMessage] = useState('')

  const receiveBox = async () => {
    if (!lotId || !cartonId) {
      setMessage('Enter lot ID and carton ID')
      return
    }

    try {
      const response = await fetch(`/api/lots/${lotId}/receive-box`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ manifestCartonId: cartonId }),
      })

      if (response.ok) {
        const data = await response.json()
        setMessage(`✓ ${cartonId} received (${data.lot.receivedCount}/${data.lot.totalExpected})`)
        setCartonId('')
      } else {
        setMessage('✗ Error: ' + (await response.text()))
      }
    } catch (err) {
      setMessage('✗ Error: ' + (err instanceof Error ? err.message : 'Unknown error'))
    }
  }

  const markNotReceived = async () => {
    if (!lotId || !cartonId) {
      setMessage('Enter lot ID and carton ID')
      return
    }

    try {
      const response = await fetch(`/api/lots/${lotId}/reject-box`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ manifestCartonId: cartonId, reason: 'NOT_RECEIVED' }),
      })

      if (response.ok) {
        setMessage(`✓ ${cartonId} marked not received`)
        setCartonId('')
      } else {
        setMessage('✗ Error')
      }
    } catch (err) {
      setMessage('✗ Error')
    }
  }

  return (
    <div className="receiving">
      <h2>Receive Boxes</h2>

      <div className="input-group">
        <label>Lot ID</label>
        <input
          type="text"
          value={lotId}
          onChange={(e) => setLotId(e.target.value)}
          placeholder="LOT-0231"
        />
      </div>

      <div className="input-group">
        <label>Carton ID</label>
        <input
          type="text"
          value={cartonId}
          onChange={(e) => setCartonId(e.target.value)}
          placeholder="BOX-0231-001"
          onKeyDown={(e) => e.key === 'Enter' && receiveBox()}
          autoFocus
        />
      </div>

      <div className="button-group">
        <button onClick={receiveBox} className="primary">
          📦 Receive
        </button>
        <button onClick={markNotReceived} className="secondary">
          ✗ Not Received
        </button>
      </div>

      {message && (
        <div
          className={`message ${message.startsWith('✓') ? 'success' : 'error'}`}
          style={{ marginTop: '20px' }}
        >
          {message}
        </div>
      )}

      <style>{`
        .receiving {
          max-width: 500px;
          margin: 0 auto;
          padding: 20px;
        }

        .receiving h2 {
          margin-bottom: 20px;
        }

        .input-group {
          margin-bottom: 15px;
        }

        .input-group label {
          display: block;
          margin-bottom: 5px;
          font-weight: 500;
        }

        .input-group input {
          width: 100%;
          padding: 10px;
          border: 1px solid #ccc;
          border-radius: 4px;
          font-size: 16px;
        }

        .button-group {
          display: flex;
          gap: 10px;
          margin-top: 20px;
        }

        .button-group button {
          flex: 1;
          padding: 12px;
          font-size: 14px;
          border: none;
          border-radius: 4px;
          cursor: pointer;
        }

        .button-group button.primary {
          background: #2563eb;
          color: white;
        }

        .button-group button.secondary {
          background: #e5e7eb;
          color: #333;
        }

        .message {
          padding: 10px;
          border-radius: 4px;
          text-align: center;
        }

        .message.success {
          background: #d1fae5;
          color: #065f46;
        }

        .message.error {
          background: #fee2e2;
          color: #991b1b;
        }
      `}</style>
    </div>
  )
}
