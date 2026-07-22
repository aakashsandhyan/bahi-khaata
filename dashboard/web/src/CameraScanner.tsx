import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserMultiFormatReader } from '@zxing/browser'
import { BarcodeFormat, DecodeHintType } from '@zxing/library'

/**
 * Reading a barcode off the phone's camera.
 *
 * A dedicated scanner is faster and surer; this is the fallback for a phone with none paired.
 * The hard part is not permission — once granted — but focus and resolution. Phone cameras read
 * a dense one-dimensional code, like the Code 128 on a returns sticker, only when it is sharp and
 * large in the frame, and two things fight that:
 *
 *  - The default camera stream is coarse — around 640×480 — too few pixels for a dense code. So
 *    the highest resolution the device will give is asked for.
 *  - A phone with several rear lenses may hand back the ultra-wide, which is fixed-focus and
 *    cannot focus on something held close, so the code is always a blur. So the lens can be
 *    chosen, and a torch turned on, because thermal stickers read far better lit.
 *
 * Restricted to the formats these goods wear, told to try hard on each frame, and a repeat of the
 * same code within a moment is ignored since the camera reads many frames a second.
 */
const FORMATS = [
  BarcodeFormat.CODE_128,
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.UPC_A,
  BarcodeFormat.UPC_E,
  BarcodeFormat.CODE_39,
]

export function CameraScanner({
  onDetected,
  onClose,
}: {
  onDetected: (code: string) => void
  onClose: () => void
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const trackRef = useRef<MediaStreamTrack | null>(null)
  const lastRef = useRef<{ code: string; at: number }>({ code: '', at: 0 })

  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [cameras, setCameras] = useState<MediaDeviceInfo[]>([])
  const [deviceId, setDeviceId] = useState<string | undefined>()
  const [torchOn, setTorchOn] = useState(false)
  const [torchable, setTorchable] = useState(false)

  const onResult = useCallback(
    (result: { getText(): string } | undefined) => {
      if (!result) return
      const code = result.getText()
      const now = Date.now()
      if (code === lastRef.current.code && now - lastRef.current.at < 1500) return
      lastRef.current = { code, at: now }
      if ('vibrate' in navigator) navigator.vibrate(80)
      onDetected(code)
    },
    [onDetected],
  )

  useEffect(() => {
    setError(null)
    const hints = new Map()
    hints.set(DecodeHintType.POSSIBLE_FORMATS, FORMATS)
    hints.set(DecodeHintType.TRY_HARDER, true) // work harder per frame; slower, reads more
    const reader = new BrowserMultiFormatReader(hints)

    let cancelled = false
    let stream: MediaStream | undefined
    let stop: (() => void) | undefined

    ;(async () => {
      try {
        const video: MediaTrackConstraints = {
          width: { ideal: 1920 },
          height: { ideal: 1080 },
          ...(deviceId
            ? { deviceId: { exact: deviceId } }
            : { facingMode: { ideal: 'environment' } }),
        }
        stream = await navigator.mediaDevices.getUserMedia({ video })
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        const track = stream.getVideoTracks()[0]
        trackRef.current = track

        // Best-effort: ask the lens to keep focusing. Ignored where unsupported.
        try {
          await track.applyConstraints({ advanced: [{ focusMode: 'continuous' }] } as never)
        } catch {
          /* not supported — nothing lost */
        }
        setTorchable('torch' in (track.getCapabilities?.() ?? {}))

        // Labels are only readable once permission is granted, so the lens list is built here.
        const all = await navigator.mediaDevices.enumerateDevices()
        setCameras(all.filter((d) => d.kind === 'videoinput'))

        const el = videoRef.current!
        el.srcObject = stream
        await el.play()
        const controls = await reader.decodeFromVideoElement(el, onResult)
        if (cancelled) controls.stop()
        else stop = () => controls.stop()
      } catch (e: unknown) {
        const name = e instanceof DOMException ? e.name : ''
        if (name === 'NotAllowedError') {
          setError('The camera is blocked for this page. Allow it in the address bar, then Try again.')
        } else if (name === 'NotFoundError' || name === 'OverconstrainedError') {
          setError('No usable camera on this device. Use a scanner, or type the code.')
        } else if (name === 'NotReadableError') {
          setError('The camera is in use by another app. Close it and Try again.')
        } else {
          setError('Could not start the camera. A scanner still works by typing into the box.')
        }
      }
    })()

    return () => {
      cancelled = true
      stop?.()
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [onResult, attempt, deviceId])

  const toggleTorch = async () => {
    const track = trackRef.current
    if (!track) return
    const next = !torchOn
    try {
      await track.applyConstraints({ advanced: [{ torch: next }] } as never)
      setTorchOn(next)
    } catch {
      /* torch not controllable here */
    }
  }

  return (
    <div className="camera">
      {error ? (
        <>
          <div className="banner stop">{error}</div>
          <button onClick={() => setAttempt((n) => n + 1)}>Try again</button>
        </>
      ) : (
        <>
          <video ref={videoRef} className="viewfinder" muted playsInline autoPlay />
          <p className="hint">
            Hold the barcode flat and close, filling the frame. Steady for a second.
          </p>
          <div className="camcontrols">
            {torchable && (
              <button onClick={toggleTorch}>{torchOn ? 'Light off' : '🔦 Light'}</button>
            )}
            {cameras.length > 1 && (
              <select
                value={deviceId ?? ''}
                onChange={(e) => setDeviceId(e.target.value || undefined)}
              >
                <option value="">Back camera (auto)</option>
                {cameras.map((c, i) => (
                  <option key={c.deviceId} value={c.deviceId}>
                    {c.label || `Camera ${i + 1}`}
                  </option>
                ))}
              </select>
            )}
          </div>
        </>
      )}
      <button onClick={onClose}>Stop the camera</button>
    </div>
  )
}
