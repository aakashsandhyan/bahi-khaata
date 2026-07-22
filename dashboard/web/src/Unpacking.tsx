import { useEffect, useRef, useState } from 'react'
import { unpacking, BackendError } from './api'
import type { DeliveryProgress, UnpackingCarton, UnpackingLine } from './types'
import { rupees } from './money'

/**
 * Unpacking, on the web, so several people scan at once.
 *
 * The terminal is one machine; this is any phone or tablet with a scanner paired to it. The
 * flow is the terminal's: scan a box, scan each item, and because a returns sticker covers the
 * printed barcode, every scan is a code the manifest does not know — so it is matched to a line
 * (by itself where a box has one thing left, by a quick search otherwise) and the sticker is
 * remembered against those goods. The price printed on the pack is asked once per product.
 *
 * A Bluetooth scanner is a keyboard: it types the code into the focused field and presses
 * Enter. So the scan field must keep focus, or a scan lands nowhere — the same rule as on the
 * terminal, and the reason focus is pulled back after every action.
 *
 * Corrections — taking a count back, giving up a mis-scanned code, marking damage — are not
 * here yet. They remain on the terminal for now; this is the fast path for getting stock
 * counted, and the corrections follow.
 */
export function Unpacking() {
  const [deliveries, setDeliveries] = useState<DeliveryProgress[] | null>(null)
  const [carton, setCarton] = useState<UnpackingCarton | null>(null)
  const [lines, setLines] = useState<UnpackingLine[]>([])
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [step, setStep] = useState('Scan the number printed on the box.')

  // A code scanned off a pack that matched nothing, waiting for someone to say which line it is.
  const [choosing, setChoosing] = useState<{ code: string; filter: string } | null>(null)
  // A line whose printed price is being asked for, with the code to tag if this began as a tag.
  const [askingMrp, setAskingMrp] = useState<{ line: UnpackingLine; code: string | null } | null>(
    null,
  )

  const scanRef = useRef<HTMLInputElement>(null)
  const focusScan = () => setTimeout(() => scanRef.current?.focus(), 0)

  const loadDeliveries = () => unpacking.deliveries().then(setDeliveries).catch(() => {})
  useEffect(() => {
    loadDeliveries()
  }, [])

  const say = (text: string, tone: string) => setMessage({ text, tone })
  const fail = (e: unknown) =>
    say(e instanceof BackendError ? e.message : 'Cannot reach the system.', 'stop')

  const openBox = async (tracking: string) => {
    const found = await unpacking.cartonsByTracking(tracking)
    if (found.length === 0) {
      say('That box is not part of any delivery here. Check the number, or set it aside.', 'warn')
      return
    }
    setCarton(found[0])
    setLines(await unpacking.lines(found[0].boxId))
    setMessage(null)
    setStep('Scan each item in this box. Press "Box is done" when it is empty.')
  }

  const refreshLines = async (boxId: string) => setLines(await unpacking.lines(boxId))

  const onScan = async (raw: string) => {
    const scanned = raw.trim()
    if (!scanned) return
    try {
      if (!carton) {
        await openBox(scanned)
      } else {
        await onItem(scanned)
      }
    } catch (e) {
      fail(e)
    } finally {
      focusScan()
    }
  }

  const onItem = async (code: string) => {
    if (!carton) return
    const resolved = await unpacking.resolve(carton.boxId, code)
    const match = resolved[0] ?? lines.find((l) => l.code.toLowerCase() === code.toLowerCase())
    if (match) {
      await countOrAskMrp(match, null)
      return
    }
    // Unknown code. One thing left to find means one answer; otherwise ask which.
    const outstanding = lines.filter((l) => l.outstanding > 0)
    if (outstanding.length === 1) {
      await countOrAskMrp(outstanding[0], code)
    } else {
      setChoosing({ code, filter: '' })
      setStep('Tap the item you are holding, or type part of its name to find it.')
    }
  }

  const countOrAskMrp = async (line: UnpackingLine, tagCode: string | null) => {
    if (line.needsMrp) {
      setChoosing(null)
      setAskingMrp({ line, code: tagCode })
      setStep('Type the MRP printed on the pack, then press Enter.')
      return
    }
    await record(line, tagCode, null)
  }

  const record = async (line: UnpackingLine, tagCode: string | null, mrpPaise: number | null) => {
    if (!carton) return
    const outcome = tagCode
      ? await unpacking.tag(line.lineId, tagCode, 1, mrpPaise, 'GOOD')
      : await unpacking.count(line.lineId, 1, mrpPaise, 'GOOD')
    setChoosing(null)
    setAskingMrp(null)
    await refreshLines(carton.boxId)
    loadDeliveries()
    const left = Math.max(0, (outcome?.quantityExpected ?? 0) - (outcome?.quantityCounted ?? 0))
    say(
      shortName(line) +
        (left === 0
          ? ` — all ${outcome?.quantityCounted} found.`
          : ` — ${outcome?.quantityCounted} of ${outcome?.quantityExpected}, ${left} to find.`),
      'ok',
    )
    setStep('Scan the next item, or press "Box is done".')
    focusScan()
  }

  const finish = async () => {
    if (!carton) return
    try {
      await unpacking.finishCarton(carton.boxId)
      const missing = lines.reduce((n, l) => n + l.outstanding, 0)
      setCarton(null)
      setLines([])
      setChoosing(null)
      setAskingMrp(null)
      loadDeliveries()
      say(
        missing > 0
          ? `Box done, ${missing} item(s) not found — recorded. Scan the next box.`
          : 'Box done, everything found. Scan the next box.',
        missing > 0 ? 'warn' : 'ok',
      )
      setStep('Scan the number printed on the next box.')
      focusScan()
    } catch (e) {
      fail(e)
    }
  }

  const leave = () => {
    setCarton(null)
    setLines([])
    setChoosing(null)
    setAskingMrp(null)
    say('Left the box as it is. Everything counted so far is saved. Scan another box.', 'ok')
    setStep('Scan the number printed on the next box.')
    focusScan()
  }

  return (
    <div className="unpack">
      <h1>{carton ? `Box ${carton.trackingNumber}` : 'Scan a box to start'}</h1>
      <p className="step">→ {step}</p>
      {carton && <DeliveryLine lotId={carton.lotId} deliveries={deliveries} />}

      <ScanField refEl={scanRef} onScan={onScan} disabled={!!askingMrp || !!choosing} />

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {askingMrp && (
        <MrpPrompt
          line={askingMrp.line}
          onEnter={(paise) => record(askingMrp.line, askingMrp.code, paise).catch(fail)}
          onError={(m) => say(m, 'warn')}
        />
      )}

      {choosing && carton && (
        <WhichItem
          code={choosing.code}
          filter={choosing.filter}
          lines={lines}
          onFilter={(f) => setChoosing({ code: choosing.code, filter: f })}
          onChoose={(line) => countOrAskMrp(line, choosing.code).catch(fail)}
        />
      )}

      {carton && !choosing && !askingMrp && (
        <>
          <ItemList lines={lines} />
          <div className="actions">
            <button onClick={leave}>Leave this box</button>
            <button className="primary" onClick={finish}>
              Box is done
            </button>
          </div>
        </>
      )}

      {!carton && deliveries && <Overview deliveries={deliveries} />}
    </div>
  )
}

function ScanField({
  refEl,
  onScan,
  disabled,
}: {
  refEl: React.RefObject<HTMLInputElement>
  onScan: (code: string) => void
  disabled: boolean
}) {
  const [value, setValue] = useState('')
  useEffect(() => {
    if (!disabled) refEl.current?.focus()
  }, [disabled, refEl])
  return (
    <input
      ref={refEl}
      className="scan"
      placeholder="Scan here"
      value={value}
      disabled={disabled}
      onChange={(e) => setValue(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          onScan(value)
          setValue('')
        }
      }}
    />
  )
}

function MrpPrompt({
  line,
  onEnter,
  onError,
}: {
  line: UnpackingLine
  onEnter: (paise: number) => void
  onError: (message: string) => void
}) {
  const [value, setValue] = useState('')
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => ref.current?.focus(), [])
  const submit = () => {
    const cleaned = value.trim().replace(/,/g, '').replace('₹', '')
    if (/^\d{8}|\d{12,14}$/.test(cleaned)) {
      onError('That is the barcode, not the price. Type the MRP printed on the pack.')
      setValue('')
      return
    }
    if (!/^\d+(\.\d{1,2})?$/.test(cleaned) || Number(cleaned) <= 0) {
      onError('That is not a price. Type the number printed on the pack, like 249.')
      return
    }
    onEnter(Math.round(Number(cleaned) * 100))
  }
  return (
    <div className="mrp">
      <p>MRP printed on {shortName(line)}?</p>
      <input
        ref={ref}
        className="scan"
        placeholder="₹ printed on the pack"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
      />
    </div>
  )
}

function WhichItem({
  code,
  filter,
  lines,
  onFilter,
  onChoose,
}: {
  code: string
  filter: string
  lines: UnpackingLine[]
  onFilter: (f: string) => void
  onChoose: (line: UnpackingLine) => void
}) {
  const needle = filter.trim().toLowerCase()
  const choices = lines
    .filter((l) => l.outstanding > 0)
    .filter(
      (l) =>
        !needle || l.name.toLowerCase().includes(needle) || l.code.toLowerCase().includes(needle),
    )
    .sort((a, b) => b.outstanding - a.outstanding)
  return (
    <div className="which">
      <h2>Which of these is it?</h2>
      <p className="code">Code on the item: {code}</p>
      <input
        className="scan small"
        autoFocus
        placeholder="Type part of the name to narrow this down"
        value={filter}
        onChange={(e) => onFilter(e.target.value)}
      />
      {choices.length === 0 && <p>Nothing in this box matches that.</p>}
      {choices.map((line) => (
        <button key={line.lineId} className="choice" onClick={() => onChoose(line)}>
          <span>{shortName(line)}</span>
          <span className="meta">
            {line.outstanding} to find · {rupees(line.indicativeCostPaise)} cost ·{' '}
            {rupees(line.onlinePricePaise)} online
          </span>
        </button>
      ))}
    </div>
  )
}

function ItemList({ lines }: { lines: UnpackingLine[] }) {
  const sorted = [...lines].sort((a, b) => Number(a.outstanding === 0) - Number(b.outstanding === 0))
  return (
    <div className="items">
      {sorted.map((line) => (
        <div key={line.lineId} className={`item ${line.outstanding === 0 ? 'done' : ''}`}>
          <div className="who">
            <div title={line.name}>{shortName(line)}</div>
            <div className="meta">
              {line.code} · {rupees(line.indicativeCostPaise)} cost · {rupees(line.onlinePricePaise)}{' '}
              online
            </div>
          </div>
          <div className="count">
            {line.counted} / {line.expected}
          </div>
        </div>
      ))}
    </div>
  )
}

function DeliveryLine({
  lotId,
  deliveries,
}: {
  lotId: string
  deliveries: DeliveryProgress[] | null
}) {
  const d = deliveries?.find((x) => x.lotId === lotId)
  if (!d) return null
  return (
    <p className="delivery">
      {d.category} · boxes {d.cartonsFinished} done, {d.cartonsStarted} open,{' '}
      {d.cartonsTotal - d.cartonsFinished - d.cartonsStarted} untouched · items {d.unitsCounted} of{' '}
      {d.unitsExpected}
      {d.itemsWithoutMrp > 0 && ` · ${d.itemsWithoutMrp} waiting on a price`}
    </p>
  )
}

function Overview({ deliveries }: { deliveries: DeliveryProgress[] }) {
  if (deliveries.length === 0) return null
  return (
    <table className="overview">
      <thead>
        <tr>
          <th className="name">Delivery</th>
          <th>Boxes</th>
          <th>Items</th>
          <th>Waiting on a price</th>
        </tr>
      </thead>
      <tbody>
        {[...deliveries]
          .sort((a, b) => b.unitsExpected - a.unitsExpected)
          .map((d) => (
            <tr key={d.lotId}>
              <td className="name">{d.category}</td>
              <td>
                {d.cartonsFinished} of {d.cartonsTotal}
              </td>
              <td>
                {d.unitsCounted} of {d.unitsExpected}
              </td>
              <td className={d.itemsWithoutMrp ? 'wait' : ''}>
                {d.itemsWithoutMrp || '—'}
              </td>
            </tr>
          ))}
      </tbody>
    </table>
  )
}

function shortName(line: UnpackingLine): string {
  return line.name.length > 60 ? line.name.slice(0, 57) + '…' : line.name
}
