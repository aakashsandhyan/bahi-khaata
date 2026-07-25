import { useEffect, useRef, useState } from 'react'
import { api, unpacking, remediation, BackendError } from './api'
import type {
  CartonProgress,
  DeliveryProgress,
  IssueTypeOption,
  MrpBackfillStatus,
  UnpackingCarton,
  UnpackingLine,
} from './types'
import { rupees } from './money'

/**
 * Unpacking, on the web, so several people scan at once.
 *
 * The terminal is one machine; this is any laptop with a scanner attached. The flow is the
 * terminal's: scan a box, scan each item, and because a returns sticker covers the printed
 * barcode, every scan is a code the manifest does not know — so it is matched to a line (by
 * itself where a box has one thing left, by a quick search otherwise) and the sticker is
 * remembered against those goods.
 *
 * Once the item is identified, one pane takes everything about it at once — its state (fine,
 * damaged, broken), a remark if it is not sound, and the price printed on the pack — and submits
 * in a single step, rather than walking through a screen each.
 *
 * A scanner is a keyboard: it types the code into the focused field and presses Enter. So the
 * scan field must keep focus, or a scan lands nowhere — the same rule as on the terminal, and the
 * reason focus is pulled back after every action.
 */
type Condition = 'GOOD' | 'DAMAGED' | 'NEEDS_WORK' | 'UNUSABLE'

export function Unpacking() {
  const [deliveries, setDeliveries] = useState<DeliveryProgress[] | null>(null)
  const [carton, setCarton] = useState<UnpackingCarton | null>(null)
  const [lines, setLines] = useState<UnpackingLine[]>([])
  const [message, setMessage] = useState<{ text: string; tone: string } | null>(null)
  const [step, setStep] = useState('Scan the number printed on the box.')

  // A code scanned off a pack that matched nothing, waiting for someone to say which line it is.
  const [choosing, setChoosing] = useState<{ code: string; filter: string } | null>(null)
  // A product is identified — scanned-and-known, or picked from the list — and its count pane is
  // open. State, an optional remark, and the MRP are answered together in one pane, then submitted.
  // tagCode carries a code that still needs mapping to this line.
  const [counting, setCounting] = useState<{ line: UnpackingLine; tagCode: string | null } | null>(
    null,
  )

  const scanRef = useRef<HTMLInputElement>(null)
  const focusScan = () => setTimeout(() => scanRef.current?.focus(), 0)

  // A scan that hit a line already fully counted: either another really arrived, or the code is
  // on the wrong goods.
  const [surplus, setSurplus] = useState<{ line: UnpackingLine; code: string } | null>(null)
  // A code that matches nothing on this box's sheet, being recorded as an extra found here.
  const [extra, setExtra] = useState<{ code: string } | null>(null)
  // Set by "Add extra product": the next scan is taken as an extra rather than counted against a
  // line — a deliberate escape from the sheet, added as its own product in this lot.
  const [expectExtra, setExpectExtra] = useState(false)
  // A returns sticker scanned again though it already resolves. Usually a double-scan of the one
  // unit it names; sometimes a legitimate re-set after that unit's count was taken back. The person
  // says which, since the count is not tracked per sticker and the system cannot tell them apart.
  const [rescan, setRescan] = useState<{ code: string; line: UnpackingLine } | null>(null)
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

  const onScan = async (raw: string) => {
    // A scanner can deliver stray control characters; they break the request URL before it ever
    // reaches the backend. Strip them, keep the printable code.
    const scanned = raw.replace(/[\x00-\x1f\x7f]/g, '').trim()
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
    // "Add extra product" was pressed: take this scan as an extra, straight past the sheet.
    if (expectExtra) {
      setExpectExtra(false)
      setExtra({ code })
      setStep('Add this extra as its own product.')
      return
    }
    const resolved = await unpacking.resolve(carton.boxId, code)
    // A returns sticker (LPN…) names one physical unit. Once scanned it is tagged and counted, so
    // it resolves from then on — which means a second scan of the same sticker is that same item,
    // not another. Do not silently count it. A manufacturer code is shared by every unit of a
    // product, so scanning it again is a genuine second unit and passes straight through; only the
    // per-unit sticker is caught. The person decides between a slip and a real re-set, because the
    // count is held per product, not per sticker, so the two cannot be told apart here.
    if (resolved[0] && code.trim().toUpperCase().startsWith('LPN')) {
      setRescan({ code, line: resolved[0] })
      setStep('That sticker is already counted — a slip, or set it again?')
      return
    }
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

  // The sticker was taken back or put on the wrong goods: forget the mapping, then run the scan
  // again — now unknown, it goes to setting it fresh rather than being refused as a double-scan.
  const forgetAndReset = async (code: string) => {
    try {
      await unpacking.releaseCode(code)
      setRescan(null)
      await onItem(code)
    } catch (e) {
      fail(e)
    }
  }

  // An item is in hand: ask its condition before counting it.
  const beginCount = (line: UnpackingLine, tagCode: string | null) => {
    setChoosing(null)
    setSurplus(null)
    setCounting({ line, tagCode })
    setStep('Say what state it is in, add the MRP, and submit.')
  }

  // One pane answers state, remark, MRP, and — for needs-work — the kind of work, then submits.
  // Broken goods are never sold, so their MRP is dropped whatever was typed; an empty MRP field
  // means none was printed and the goods are counted unpriced, to be found later in the backlog.
  const submitCount = (
    condition: Condition,
    remark: string | null,
    mrpPaise: number | null,
    issueType: string | null,
    mrpIsEstimate: boolean,
  ) => {
    if (!counting) return
    const mrp = condition === 'UNUSABLE' ? null : mrpPaise
    record(counting.line, counting.tagCode, condition, remark, mrp, issueType, mrpIsEstimate).catch(
      fail,
    )
  }

  // An extra the sheet did not name — recorded against this box, costed at the lot average.
  const recordExtra = async (name: string, categoryCode: string, mrpPaise: number | null) => {
    if (!carton || !extra) return
    try {
      await unpacking.unlisted(carton.boxId, {
        code: extra.code,
        name,
        categoryCode,
        quantity: 1,
        mrpPaise,
        mrpIsEstimate: false,
      })
      setExtra(null)
      await refreshLines(carton.boxId)
      loadDeliveries()
      say('Recorded one extra found here.', 'ok')
      setStep('Scan the next item, or press "Box is done".')
      focusScan()
    } catch (e) {
      fail(e)
    }
  }

  const record = async (
    line: UnpackingLine,
    tagCode: string | null,
    condition: Condition,
    remark: string | null,
    mrpPaise: number | null,
    issueType: string | null,
    mrpIsEstimate: boolean,
  ) => {
    if (!carton) return
    const outcome = tagCode
      ? await unpacking.tag(
          line.lineId, tagCode, 1, mrpPaise, condition, remark, issueType, mrpIsEstimate,
        )
      : await unpacking.count(line.lineId, 1, mrpPaise, condition, remark, issueType, mrpIsEstimate)
    setChoosing(null)
    setCounting(null)
    const fresh = await refreshLines(carton.boxId)
    loadDeliveries()

    // The last item closes the box on its own — nobody should have to reach for a button when
    // the carton is empty. Anything still to find keeps it open, since it is not done.
    if (fresh.length > 0 && fresh.every((l) => l.outstanding <= 0)) {
      await finish(true)
      return
    }

    const mark =
      condition === 'DAMAGED'
        ? ' (damaged)'
        : condition === 'UNUSABLE'
          ? ' (broken)'
          : condition === 'NEEDS_WORK'
            ? ' (needs work)'
            : ''
    const tone = condition === 'UNUSABLE' ? 'stop' : condition === 'GOOD' ? 'ok' : 'warn'
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
      // Removes one from this box's line only. The sticker mapping stays behind, and freeing it is
      // not offered here — dumping every code the product ever gathered, across every box, is not
      // what "take one back" is about. If a specific sticker was mis-mapped, re-scanning it opens
      // the "forget it and set again" fork, which frees that one code.
      await unpacking.undo(line.lineId, 1)
      if (carton) await refreshLines(carton.boxId)
      loadDeliveries()
      say('Took one back: ' + shortName(line), 'warn')
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
      setCounting(null)
      setSurplus(null)
      setRescan(null)
      setExtra(null)
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
    setCounting(null)
    setSurplus(null)
    setRescan(null)
    setExtra(null)
    setExpectExtra(false)
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
        disabled={!!counting || !!choosing || !!surplus || !!rescan || !!extra}
      />

      {message && <div className={`banner ${message.tone}`}>{message.text}</div>}

      {counting && (
        <CountPane
          line={counting.line}
          onSubmit={submitCount}
          onBack={() => {
            setCounting(null)
            setStep('Scan the next item, or press "Box is done".')
            focusScan()
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
          onExtra={() => {
            const c = choosing.code
            setChoosing(null)
            setExtra({ code: c })
            setStep('Not on the sheet — record it as extra found here.')
          }}
          onBack={() => {
            setChoosing(null)
            setStep('Scan the next item, or press "Box is done".')
            focusScan()
          }}
        />
      )}

      {extra && (
        <ExtraForm
          code={extra.code}
          onSubmit={recordExtra}
          onBack={() => {
            setExtra(null)
            setStep('Scan the next item, or press "Box is done".')
            focusScan()
          }}
        />
      )}

      {rescan && (
        <div className="card">
          <h2>That sticker is already counted</h2>
          <p className="code">
            {rescan.code} · {shortName(rescan.line)}
          </p>
          <p>
            A returns sticker names one item, so scanning it again is usually the same one twice.
            If its count was taken back, or it was put on the wrong goods, forget it and set it
            again.
          </p>
          <button
            className="choice"
            onClick={() => {
              setRescan(null)
              setStep('Scan the next item, or press "Box is done".')
              focusScan()
            }}
          >
            It&rsquo;s a slip — ignore
          </button>
          <button className="choice warn-choice" onClick={() => forgetAndReset(rescan.code)}>
            Taken back or wrong — forget it and set it again
          </button>
        </div>
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

      {carton && !choosing && !counting && !surplus && !rescan && !extra && (
        <>
          <ItemList lines={lines} onTakeBack={takeBack} />
          <button
            className={`btn-extra${expectExtra ? ' armed' : ''}`}
            onClick={() => {
              if (expectExtra) {
                setExpectExtra(false)
                setStep('Scan the next item, or press "Box is done".')
              } else {
                setExpectExtra(true)
                setStep('Scan the extra item — it is added as its own product in this delivery.')
              }
              focusScan()
            }}
          >
            {expectExtra ? '✕ Cancel — waiting to scan an extra' : '+ Add an extra product'}
          </button>
          <div className="actions">
            <button onClick={leave}>Leave this box</button>
            <button className="btn-primary" onClick={() => finish()}>
              Box is done
            </button>
          </div>
        </>
      )}

      {!carton && deliveries && (
        <Overview deliveries={deliveries} onOpenBox={openBox} onRefresh={loadDeliveries} />
      )}
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

// One pane for an identified item: its state, a remark if not sound, and the MRP — answered
// together and submitted in a single step. The state defaults to Fine and the MRP field takes
// focus, so the common case is scan, type the price, Enter. An empty MRP means none was printed;
// the goods are counted unpriced and show up later in the price backlog. Broken goods are never
// sold, so their price row is hidden.
function CountPane({
  line,
  onSubmit,
  onBack,
}: {
  line: UnpackingLine
  onSubmit: (
    condition: Condition,
    remark: string | null,
    mrpPaise: number | null,
    issueType: string | null,
    mrpIsEstimate: boolean,
  ) => void
  onBack: () => void
}) {
  const [condition, setCondition] = useState<Condition>('GOOD')
  const [remark, setRemark] = useState('')
  // Prefilled with the price already read off this product earlier in the delivery, so a later
  // unit shows it back rather than asking again; editable in case this pack is printed different.
  const [mrp, setMrp] = useState(
    line.recordedMrpPaise != null ? String(line.recordedMrpPaise / 100) : '',
  )
  const [error, setError] = useState<string | null>(null)
  // The kinds of work this department offers, fetched the first time needs-work is chosen.
  const [issues, setIssues] = useState<IssueTypeOption[]>([])
  const [issue, setIssue] = useState<string | null>(null)
  // A looked-up price, prefilled only if nothing was read yet, and only ever as an estimate the
  // operator confirms against the pack. Touching the field marks it read, not estimated.
  const [estimate, setEstimate] = useState(false)
  const [suggestion, setSuggestion] = useState<string | null>(null)
  const touched = useRef(false)
  const mrpRef = useRef<HTMLInputElement>(null)
  useEffect(() => {
    mrpRef.current?.focus()
  }, [])
  useEffect(() => {
    if (condition === 'NEEDS_WORK' && issues.length === 0) {
      remediation.issueTypes(line.categoryCode).then(setIssues).catch(() => {})
    }
  }, [condition, line.categoryCode, issues.length])
  useEffect(() => {
    // No price read yet: try a lookup in the background. Slow and often empty, so it never blocks.
    if (!line.needsMrp) return
    unpacking
      .suggestedMrp(line.lineId)
      .then((s) => {
        if (s.pricePaise == null || touched.current) return
        setSuggestion(s.source)
        setMrp(String(s.pricePaise / 100))
        setEstimate(true)
      })
      .catch(() => {})
  }, [line.lineId, line.needsMrp])

  const sound = condition === 'GOOD'
  const broken = condition === 'UNUSABLE'
  const needsWork = condition === 'NEEDS_WORK'

  const submit = () => {
    if (needsWork && !issue) {
      setError('Pick the kind of work it needs.')
      return
    }
    let mrpPaise: number | null = null
    if (!broken) {
      const cleaned = mrp.trim().replace(/,/g, '').replace('₹', '')
      if (cleaned) {
        if (/^\d{8}|\d{12,14}$/.test(cleaned)) {
          setError('That is the barcode, not the price. Type the MRP printed on the pack.')
          setMrp('')
          return
        }
        if (!/^\d+(\.\d{1,2})?$/.test(cleaned) || Number(cleaned) <= 0) {
          setError('That is not a price. Type the number printed on the pack, like 249.')
          return
        }
        mrpPaise = Math.round(Number(cleaned) * 100)
      }
    }
    onSubmit(
      condition,
      sound ? null : remark.trim() || null,
      mrpPaise,
      needsWork ? issue : null,
      mrpPaise != null && estimate,
    )
  }

  return (
    <div className="card count-pane">
      <button className="back" onClick={onBack}>
        ← Back
      </button>
      <h2>{shortName(line)}</h2>

      <div className="cond-row">
        <button className={`cond on-good${sound ? ' sel' : ''}`} onClick={() => setCondition('GOOD')}>
          Fine
        </button>
        <button
          className={`cond on-damaged${condition === 'DAMAGED' ? ' sel' : ''}`}
          onClick={() => setCondition('DAMAGED')}
        >
          Damaged
        </button>
        <button
          className={`cond on-needswork${needsWork ? ' sel' : ''}`}
          onClick={() => {
            setCondition('NEEDS_WORK')
            setError(null)
          }}
        >
          Needs work
        </button>
        <button
          className={`cond on-broken${broken ? ' sel' : ''}`}
          onClick={() => setCondition('UNUSABLE')}
        >
          Broken
        </button>
      </div>

      {needsWork && (
        <div className="issue-row">
          {issues.map((it) => (
            <button
              key={it.code}
              className={`issue${issue === it.code ? ' sel' : ''}`}
              onClick={() => {
                setIssue(it.code)
                setError(null)
              }}
            >
              {it.label}
            </button>
          ))}
          {issues.length === 0 && <p className="mrp-hint">No kinds of work listed for this department.</p>}
        </div>
      )}

      {!sound && (
        <input
          className="scan small"
          placeholder={
            broken
              ? 'What is broken? e.g. screen dead'
              : needsWork
                ? 'Note, if any — e.g. missing base'
                : "What's wrong? e.g. lid cracked"
          }
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
        />
      )}

      {!broken && (
        <>
          <input
            ref={mrpRef}
            className={`scan${estimate ? ' estimated' : ''}`}
            placeholder="₹ MRP on the pack (blank = none printed)"
            value={mrp}
            onChange={(e) => {
              touched.current = true
              setEstimate(false)
              setMrp(e.target.value)
              setError(null)
            }}
            onKeyDown={(e) => e.key === 'Enter' && submit()}
          />
          {estimate && (
            <p className="mrp-hint warn">
              Estimate from {suggestion ?? 'a lookup'} — check it against the pack.
            </p>
          )}
          {line.onlinePricePaise != null && <p className="mrp-hint">{rupees(line.onlinePricePaise)} online</p>}
          {error && <p className="mrp-hint stop">{error}</p>}
        </>
      )}

      <button className="btn-primary big" onClick={submit}>
        Submit
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
  onExtra,
  onBack,
}: {
  code: string
  filter: string
  lines: UnpackingLine[]
  onFilter: (f: string) => void
  onChoose: (line: UnpackingLine) => void
  onExtra: () => void
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
            {line.code} · {line.outstanding} to find · {rupees(line.indicativeCostPaise)} cost ·{' '}
            {rupees(line.onlinePricePaise)} online
          </span>
        </button>
      ))}
      <button className="choice warn-choice" onClick={onExtra}>
        None of these — record it as extra found here
      </button>
    </div>
  )
}

const CATEGORIES = [
  'KITCHEN',
  'WIRELESS',
  'FASHION',
  'FOOTWEAR',
  'HOME_ESSENTIALS',
  'PERSONAL_CARE',
  'GARDEN',
]

// Recording an extra the sheet did not name. If the scanned code is already a known product — a
// mispack that belongs in another box — it is recognised and recorded with one tap. Otherwise its
// name and department are asked, to make a new product. An MRP is optional either way.
function ExtraForm({
  code,
  onSubmit,
  onBack,
}: {
  code: string
  onSubmit: (name: string, categoryCode: string, mrpPaise: number | null) => void
  onBack: () => void
}) {
  // undefined = still resolving, null = unknown code, object = a known product.
  const [known, setKnown] = useState<{ name: string; categoryCode: string } | null | undefined>(
    undefined,
  )
  const [name, setName] = useState('')
  const [category, setCategory] = useState(CATEGORIES[0])
  const [mrp, setMrp] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    remediation
      .statesByCode(code)
      .then((s) => setKnown({ name: s.productName, categoryCode: s.categoryCode }))
      .catch(() => setKnown(null))
  }, [code])

  const submit = () => {
    let mrpPaise: number | null = null
    const cleaned = mrp.trim().replace(/,/g, '').replace('₹', '')
    if (cleaned) {
      if (!/^\d+(\.\d{1,2})?$/.test(cleaned) || Number(cleaned) <= 0) {
        setError('That is not a price. Type the number on the pack, like 249.')
        return
      }
      mrpPaise = Math.round(Number(cleaned) * 100)
    }
    if (known) {
      onSubmit(known.name, known.categoryCode, mrpPaise)
    } else {
      if (!name.trim()) {
        setError('Give it a name so it can be found and priced.')
        return
      }
      onSubmit(name.trim(), category, mrpPaise)
    }
  }

  return (
    <div className="card">
      <button className="back" onClick={onBack}>
        ← Back
      </button>
      <h2>Extra found in this box</h2>
      <p className="code">Code on the item: {code}</p>

      {known === undefined && <p>Checking the code…</p>}

      {known && (
        <p>
          This is{' '}
          <strong>{known.name.length > 60 ? known.name.slice(0, 57) + '…' : known.name}</strong> —
          from another box. Record one here as extra.
        </p>
      )}

      {known === null && (
        <>
          <input
            className="scan small"
            autoFocus
            placeholder="What is it? A short name"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              setError(null)
            }}
          />
          <select className="scan small" value={category} onChange={(e) => setCategory(e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </>
      )}

      {known !== undefined && (
        <input
          className="scan"
          placeholder="₹ MRP on the pack (blank = none printed)"
          value={mrp}
          onChange={(e) => {
            setMrp(e.target.value)
            setError(null)
          }}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
      )}

      {error && <p className="mrp-hint stop">{error}</p>}

      {known !== undefined && (
        <button className="btn-primary big" onClick={submit}>
          Record as extra
        </button>
      )}
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

function Overview({
  deliveries,
  onOpenBox,
  onRefresh,
}: {
  deliveries: DeliveryProgress[]
  onOpenBox: (tracking: string) => void
  onRefresh: () => void
}) {
  const [expanded, setExpanded] = useState<string | null>(null)
  const [boxes, setBoxes] = useState<Record<string, CartonProgress[]>>({})
  const waiting = deliveries.reduce((sum, d) => sum + d.itemsWithoutMrp, 0)

  const toggle = (lotId: string) => {
    if (expanded === lotId) {
      setExpanded(null)
      return
    }
    setExpanded(lotId)
    if (!boxes[lotId]) {
      unpacking
        .boxesOf(lotId)
        .then((b) => setBoxes((prev) => ({ ...prev, [lotId]: b })))
        .catch(() => {})
    }
  }

  if (deliveries.length === 0) return null
  return (
    <div className="overview-cards">
      <div className="ov-head">Deliveries — tap a department to see boxes left</div>
      <MrpFillBar waiting={waiting} onDone={onRefresh} />
      {[...deliveries]
        .sort((a, b) => b.unitsExpected - a.unitsExpected)
        .map((d) => {
          const done = d.cartonsFinished === d.cartonsTotal
          const pct = d.unitsExpected ? Math.round((d.unitsCounted / d.unitsExpected) * 100) : 0
          const left = (boxes[d.lotId] ?? [])
            .filter((b) => !b.finished && b.unitsExpected - b.unitsCounted > 0)
            .sort(
              (a, b) => b.unitsExpected - b.unitsCounted - (a.unitsExpected - a.unitsCounted),
            )
          return (
            <div key={d.lotId} className={done ? 'ov done' : 'ov'}>
              <div className="ov-row" onClick={() => toggle(d.lotId)}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="ov-name">
                    {expanded === d.lotId ? '▾' : '▸'} {d.category}
                  </div>
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
              {expanded === d.lotId && (
                <div className="ov-boxes">
                  {!boxes[d.lotId] && <p className="empty">Loading…</p>}
                  {boxes[d.lotId] && left.length === 0 && (
                    <p className="empty">Every box here is done.</p>
                  )}
                  {left.map((b) => (
                    <button
                      key={b.boxId}
                      className="ov-box"
                      onClick={() => onOpenBox(b.trackingNumber)}
                    >
                      <span className="ov-box-num">{b.trackingNumber}</span>
                      <span className="ov-box-left">
                        {b.unitsExpected - b.unitsCounted} left →
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )
        })}
    </div>
  )
}

/**
 * Fill missing printed prices from a marketplace listing, in the background, right where the
 * backlog shows. It never blocks unpacking: it runs on the server, one product is ever looked up
 * once, and what it writes is an estimate to be confirmed against the pack — not a price read off
 * the goods. The same control lives on Pricing; this puts it where the "no price" counts are seen.
 */
function MrpFillBar({ waiting, onDone }: { waiting: number; onDone: () => void }) {
  const [mrp, setMrp] = useState<MrpBackfillStatus | null>(null)

  // Show a fill already running (or its last result) on open.
  useEffect(() => {
    api.mrpBackfillStatus().then(setMrp).catch(() => {})
  }, [])

  // While one runs, poll it; refresh the delivery counts once it finishes.
  useEffect(() => {
    if (!mrp?.running) return
    const id = setInterval(() => {
      api
        .mrpBackfillStatus()
        .then((s) => {
          setMrp(s)
          if (!s.running) onDone()
        })
        .catch(() => {})
    }, 3000)
    return () => clearInterval(id)
  }, [mrp?.running])

  const start = async () => {
    try {
      setMrp(await api.startMrpBackfill())
    } catch {
      // A fill is best-effort; a failure to start is not worth interrupting unpacking over.
    }
  }

  // Nothing waiting and nothing to report — stay out of the way.
  if (waiting === 0 && !mrp?.running && !(mrp && mrp.done > 0)) return null

  return (
    <div className="mrp-fill">
      <button className="btn-ghost" onClick={start} disabled={mrp?.running}>
        {mrp?.running
          ? `Filling prices… ${mrp.done}/${mrp.total}`
          : '↻ Fill missing prices in the background (estimates)'}
      </button>
      {mrp && (mrp.running || mrp.done > 0) ? (
        <span className="mrp-fill-note">
          {mrp.running
            ? `${mrp.done} of ${mrp.total} checked, ${mrp.recorded} priced`
            : mrp.message}
        </span>
      ) : (
        waiting > 0 && <span className="mrp-fill-note">{waiting} waiting on a price</span>
      )}
    </div>
  )
}

function shortName(line: UnpackingLine): string {
  return line.name.length > 60 ? line.name.slice(0, 57) + '…' : line.name
}
