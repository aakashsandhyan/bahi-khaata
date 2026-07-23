import { useCallback, useEffect, useRef, useState } from 'react'
import { unpacking, BackendError } from './api'
import type { DeliveryProgress, LearntCode, UnpackingCarton, UnpackingLine } from './types'
import { rupees } from './money'
import { CameraScanner } from './CameraScanner'

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
type Condition = 'GOOD' | 'DAMAGED' | 'UNUSABLE'

export function Unpacking() {
  const [deliveries, setDeliveries] = useState<DeliveryProgress[] | null>(null)
  const [carton, setCarton] = useState<UnpackingCarton | null>(null)
  const [lines, setLines] = useState<UnpackingLine[]>([])
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [step, setStep] = useState('Scan the number printed on the box.')

  // A code scanned off a pack that matched nothing, waiting for someone to say which line it is.
  const [choosing, setChoosing] = useState<{ code: string; filter: string } | null>(null)
  // A line whose printed price is being asked for, with the code to tag if this began as a tag.
  const [askingMrp, setAskingMrp] = useState<{
    line: UnpackingLine
    code: string | null
    condition: Condition
    remark: string | null
  } | null>(null)

  const scanRef = useRef<HTMLInputElement>(null)
  const focusScan = () => setTimeout(() => scanRef.current?.focus(), 0)

  const [cameraOn, setCameraOn] = useState(false)
  // What condition scanned items are recorded in. Stays until changed, and is repeated back on
  // every count so a setting left on is seen, not remembered — the same rule as the terminal.
  // Set per item, after it is scanned, not held across the box: scan an item, then say what
  // state it is in. tagCode carries a code that still needs mapping to this line.
  const [picking, setPicking] = useState<{ line: UnpackingLine; tagCode: string | null } | null>(
    null,
  )
  // A note on damaged or broken goods — why they are not sound. Optional.
  const [remarking, setRemarking] = useState<{
    line: UnpackingLine
    tagCode: string | null
    condition: Condition
  } | null>(null)
  // A scan that hit a line already fully counted: either another really arrived, or the code is
  // on the wrong goods.
  const [surplus, setSurplus] = useState<{ line: UnpackingLine; code: string } | null>(null)
  // Codes offered for release after a count is taken back, so a mis-scanned sticker can be freed.
  const [releaseOffer, setReleaseOffer] = useState<{ name: string; codes: LearntCode[] } | null>(
    null,
  )
  // The camera fires many times a second from a stable callback; this ref lets that callback
  // reach the latest handler without being rebuilt, which would restart the camera each time.
  const handleScanRef = useRef<(code: string) => void>(() => {})
  const onCameraDetect = useCallback((code: string) => {
    // Stop after a read. The camera closes on a successful scan — the shutter and light go off,
    // and it cannot double-read the same or the next barcode — then the scan is handled. Reopen
    // it for the next item.
    setCameraOn(false)
    handleScanRef.current(code)
  }, [])

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

  const refreshLines = async (boxId: string) => {
    const fresh = await unpacking.lines(boxId)
    setLines(fresh)
    return fresh
  }

  // A camera read is ignored while a question is open — the same rule as the typed field being
  // disabled then. The person must answer the price or pick the item before the next scan lands.
  handleScanRef.current = (code: string) => {
    if (askingMrp || choosing || surplus || releaseOffer || picking || remarking) return
    onScan(code)
  }

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
      if (match.outstanding <= 0) {
        // Nothing left to find for this item. Another really arrived, or the code is on the
        // wrong goods — the person decides.
        setSurplus({ line: match, code })
        setStep('More than the sheet expected — is it really another one?')
        return
      }
      beginCount(match, null)
      return
    }
    // Unknown code. One thing left to find means one answer; otherwise ask which.
    const outstanding = lines.filter((l) => l.outstanding > 0)
    if (outstanding.length === 1) {
      beginCount(outstanding[0], code)
    } else {
      setChoosing({ code, filter: '' })
      setStep('Tap the item you are holding, or type part of its name to find it.')
    }
  }

  // An item is in hand: ask its condition before counting it.
  const beginCount = (line: UnpackingLine, tagCode: string | null) => {
    setChoosing(null)
    setSurplus(null)
    setPicking({ line, tagCode })
    setStep('What state is this item in?')
  }

  const countOrAskMrp = async (
    line: UnpackingLine,
    tagCode: string | null,
    condition: Condition,
    remark: string | null,
  ) => {
    setPicking(null)
    setRemarking(null)
    // Broken goods are never sold, so a printed price means nothing for them — counted straight
    // through without asking.
    if (condition !== 'UNUSABLE' && line.needsMrp) {
      setAskingMrp({ line, code: tagCode, condition, remark })
      setStep('Type the MRP printed on the pack, then press Enter.')
      return
    }
    await record(line, tagCode, condition, remark, null)
  }

  const record = async (
    line: UnpackingLine,
    tagCode: string | null,
    condition: Condition,
    remark: string | null,
    mrpPaise: number | null,
  ) => {
    if (!carton) return
    const outcome = tagCode
      ? await unpacking.tag(line.lineId, tagCode, 1, mrpPaise, condition, remark)
      : await unpacking.count(line.lineId, 1, mrpPaise, condition, remark)
    setChoosing(null)
    setAskingMrp(null)
    const fresh = await refreshLines(carton.boxId)
    loadDeliveries()

    // The last item closes the box on its own — nobody should have to reach for a button when
    // the carton is empty. Anything still to find keeps it open, since it is not done.
    if (fresh.length > 0 && fresh.every((l) => l.outstanding <= 0)) {
      await finish(true)
      return
    }

    const mark = condition === 'DAMAGED' ? ' (damaged)' : condition === 'UNUSABLE' ? ' (broken)' : ''
    const tone = condition === 'UNUSABLE' ? 'stop' : condition === 'DAMAGED' ? 'warn' : 'ok'
    const left = Math.max(0, (outcome?.quantityExpected ?? 0) - (outcome?.quantityCounted ?? 0))
    say(
      shortName(line) + mark +
        (left === 0
          ? ` — all ${outcome?.quantityCounted} found.`
          : ` — ${outcome?.quantityCounted} of ${outcome?.quantityExpected}, ${left} to find.`),
      tone,
    )
    setStep('Scan the next item, or press "Box is done".')
    focusScan()
  }

  const takeBack = async (line: UnpackingLine) => {
    try {
      await unpacking.undo(line.lineId, 1)
      if (carton) await refreshLines(carton.boxId)
      loadDeliveries()
      say('Took one back: ' + shortName(line), 'warn')
      // Taking the count back leaves any code mapping behind — offer to free it, since a sticker
      // on the wrong goods keeps resolving them.
      const codes = (await unpacking.codesFor(line.lineId)).filter((c) => c.releasable)
      if (codes.length > 0) setReleaseOffer({ name: shortName(line), codes })
    } catch (e) {
      fail(e)
    } finally {
      focusScan()
    }
  }

  const release = async (code: string) => {
    try {
      await unpacking.releaseCode(code)
      if (carton) await refreshLines(carton.boxId)
      setReleaseOffer(null)
      setSurplus(null)
      say(`Forgot ${code}. Scan it again and say which item it really is.`, 'warn')
    } catch (e) {
      fail(e)
    } finally {
      focusScan()
    }
  }

  const finish = async (automatic = false) => {
    if (!carton) return
    try {
      const current = await unpacking.lines(carton.boxId)
      await unpacking.finishCarton(carton.boxId)
      const missing = current.reduce((n, l) => n + Math.max(0, l.outstanding), 0)
      setCarton(null)
      setLines([])
      setChoosing(null)
      setAskingMrp(null)
      setPicking(null)
      setRemarking(null)
      setSurplus(null)
      setReleaseOffer(null)
      loadDeliveries()
      say(
        missing > 0
          ? `Box done, ${missing} item(s) not found — recorded. Scan the next box.`
          : automatic
            ? 'That was the last one — box done. Scan the next box.'
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
    setPicking(null)
    setRemarking(null)
    setSurplus(null)
    setReleaseOffer(null)
    say('Left the box as it is. Everything counted so far is saved. Scan another box.', 'ok')
    setStep('Scan the number printed on the next box.')
    focusScan()
  }

  return (
    <div className="unpack">
      <div className="unpack-head">
        <h1>
          {carton ? (
            <>
              Box <span className="box-num">{carton.trackingNumber}</span>
            </>
          ) : (
            'Scan a box to start'
          )}
        </h1>
        <p className="step">→ {step}</p>
        {carton && <DeliveryLine lotId={carton.lotId} deliveries={deliveries} />}
      </div>

      <ScanField
        refEl={scanRef}
        onScan={onScan}
        disabled={!!askingMrp || !!choosing || !!surplus || !!releaseOffer || !!picking || !!remarking}
      />
      <button
        className="camera-btn"
        onClick={() => setCameraOn((on) => !on)}
        disabled={!!askingMrp || !!choosing || !!surplus || !!releaseOffer || !!picking || !!remarking}
      >
        {cameraOn ? 'Hide camera' : '📷 Scan with camera'}
      </button>

      {cameraOn && !askingMrp && !choosing && (
        <CameraScanner onDetected={onCameraDetect} onClose={() => setCameraOn(false)} />
      )}

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {askingMrp && (
        <MrpPrompt
          line={askingMrp.line}
          onEnter={(paise) =>
            record(
              askingMrp.line,
              askingMrp.code,
              askingMrp.condition,
              askingMrp.remark,
              paise,
            ).catch(fail)
          }
          onError={(m) => say(m, 'warn')}
          onBack={() => {
            setAskingMrp(null)
            setStep('Scan the next item, or press "Box is done".')
            focusScan()
          }}
          onSkip={() => {
            record(
              askingMrp.line,
              askingMrp.code,
              askingMrp.condition,
              askingMrp.remark,
              null,
            ).catch(fail)
            say('Counted without a price. It cannot be sold until someone finds one.', 'warn')
          }}
        />
      )}

      {choosing && carton && (
        <WhichItem
          code={choosing.code}
          filter={choosing.filter}
          lines={lines}
          onFilter={(f) => setChoosing({ code: choosing.code, filter: f })}
          onChoose={(line) => beginCount(line, choosing.code)}
          onBack={() => {
            setChoosing(null)
            setStep('Scan the next item, or press "Box is done".')
            focusScan()
          }}
        />
      )}

      {surplus && (
        <div className="card">
          <h2>More of these than the sheet expected</h2>
          <p className="code">Code on the item: {surplus.code}</p>
          <p>
            This code is on "{surplus.line.name}". If another really arrived, count it. If the
            item in your hand is something else, the code was put on the wrong thing.
          </p>
          <button
            className="choice"
            onClick={() => {
              const s = surplus
              setSurplus(null)
              beginCount(s.line, s.code)
            }}
          >
            Yes — another one really did arrive, count it
          </button>
          <button className="choice warn-choice" onClick={() => release(surplus.code)}>
            Wrong item — forget this code and let me scan it again
          </button>
        </div>
      )}

      {releaseOffer && (
        <div className="card">
          <h2>Was a code put on this by mistake?</h2>
          <p className="code">{releaseOffer.name}</p>
          {releaseOffer.codes.map((c) => (
            <button key={c.code} className="choice warn-choice" onClick={() => release(c.code)}>
              Forget {c.code}
            </button>
          ))}
          <button className="back" onClick={() => setReleaseOffer(null)}>
            ← None of these, done
          </button>
        </div>
      )}

      {picking && (
        <div className="card">
          <button className="back" onClick={() => { setPicking(null); focusScan() }}>
            ← Back
          </button>
          <h2>What state is it in?</h2>
          <p className="code">{shortName(picking.line)}</p>
          <button
            className="cond on-good big"
            onClick={() => countOrAskMrp(picking.line, picking.tagCode, 'GOOD', null).catch(fail)}
          >
            Fine
          </button>
          <button
            className="cond on-damaged big"
            onClick={() =>
              setRemarking({ line: picking.line, tagCode: picking.tagCode, condition: 'DAMAGED' })
            }
          >
            Damaged — sells cheaper
          </button>
          <button
            className="cond on-broken big"
            onClick={() =>
              setRemarking({ line: picking.line, tagCode: picking.tagCode, condition: 'UNUSABLE' })
            }
          >
            Broken — cannot sell
          </button>
        </div>
      )}

      {remarking && (
        <RemarkPrompt
          line={remarking.line}
          condition={remarking.condition}
          onDone={(note) =>
            countOrAskMrp(remarking.line, remarking.tagCode, remarking.condition, note).catch(fail)
          }
          onBack={() => {
            // Back to the condition choice, not out of the item entirely.
            setPicking({ line: remarking.line, tagCode: remarking.tagCode })
            setRemarking(null)
          }}
        />
      )}

      {carton && !choosing && !askingMrp && !surplus && !releaseOffer && !picking && (
        <>
          <ItemList lines={lines} onTakeBack={takeBack} />
          <div className="actions">
            <button onClick={leave}>Leave this box</button>
            <button className="btn-primary" onClick={() => finish()}>
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
  onBack,
  onSkip,
}: {
  line: UnpackingLine
  onEnter: (paise: number) => void
  onError: (message: string) => void
  onBack: () => void
  onSkip: () => void
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
    <div className="card">
      <button className="back" onClick={onBack}>
        ← Back
      </button>
      <p>MRP printed on {shortName(line)}?</p>
      <input
        ref={ref}
        className="scan"
        placeholder="₹ printed on the pack"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
      />
      <button className="choice" onClick={onSkip}>
        No MRP printed on it — count it anyway
      </button>
    </div>
  )
}

function WhichItem({
  code,
  filter,
  lines,
  onFilter,
  onChoose,
  onBack,
}: {
  code: string
  filter: string
  lines: UnpackingLine[]
  onFilter: (f: string) => void
  onChoose: (line: UnpackingLine) => void
  onBack: () => void
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
    <div className="card">
      <button className="back" onClick={onBack}>
        ← Back
      </button>
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

function RemarkPrompt({
  line,
  condition,
  onDone,
  onBack,
}: {
  line: UnpackingLine
  condition: Condition
  onDone: (note: string | null) => void
  onBack: () => void
}) {
  const [note, setNote] = useState('')
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => ref.current?.focus(), [])
  const done = () => onDone(note.trim() || null)
  return (
    <div className="card">
      <button className="back" onClick={onBack}>
        ← Back
      </button>
      <h2>{condition === 'UNUSABLE' ? 'What is broken?' : "What's wrong with it?"}</h2>
      <p className="code">{shortName(line)}</p>
      <input
        ref={ref}
        className="scan small"
        placeholder="e.g. lid cracked, box opened, screen dead"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && done()}
      />
      <button className="choice" onClick={done}>
        {note.trim() ? 'Save note and continue' : 'No note — continue'}
      </button>
    </div>
  )
}

function ItemList({
  lines,
  onTakeBack,
}: {
  lines: UnpackingLine[]
  onTakeBack: (line: UnpackingLine) => void
}) {
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
          <div className="countcol">
            <div className="count">
              {line.counted} / {line.expected}
            </div>
            {line.counted > 0 && (
              <button className="takeback" onClick={() => onTakeBack(line)}>
                Take one back
              </button>
            )}
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
    <div className="overview-cards">
      <div className="ov-head">Deliveries</div>
      {[...deliveries]
        .sort((a, b) => b.unitsExpected - a.unitsExpected)
        .map((d) => {
          const done = d.cartonsFinished === d.cartonsTotal
          const pct = d.unitsExpected ? Math.round((d.unitsCounted / d.unitsExpected) * 100) : 0
          return (
            <div key={d.lotId} className={done ? 'ov done' : 'ov'}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="ov-name">{d.category}</div>
                <div className="ov-stat">
                  {d.cartonsFinished} of {d.cartonsTotal} boxes · {d.unitsCounted} of{' '}
                  {d.unitsExpected} items
                </div>
                <div className="ov-bar">
                  <i style={{ width: `${pct}%` }} />
                </div>
              </div>
              {d.itemsWithoutMrp > 0 && (
                <div className="ov-wait" title="items waiting on a price">
                  {d.itemsWithoutMrp}
                  <div style={{ fontSize: 10, fontWeight: 400 }}>no price</div>
                </div>
              )}
            </div>
          )
        })}
    </div>
  )
}

function shortName(line: UnpackingLine): string {
  return line.name.length > 60 ? line.name.slice(0, 57) + '…' : line.name
}
