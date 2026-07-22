import { useCallback, useEffect, useRef, useState } from 'react'
import { BrowserMultiFormatReader } from '@zxing/browser'
import { BarcodeFormat, DecodeHintType } from '@zxing/library'

/**
 * Reading a barcode off the phone's camera.
 *
 * A paired scanner is faster and surer; this is the fallback. The stubborn part is a dense
 * one-dimensional code — the Code 128 on a returns sticker — which a phone reads only when it is
 * sharp and large in the frame. Two changes matter most:
 *
 *  - It decodes only a band across the middle of the picture, at full resolution, rather than
 *    the whole frame shrunk to fit. A barcode small in the frame has too few pixels once the
 *    whole frame is processed; cropping to where the barcode is aimed keeps every pixel on it.
 *    An aiming box shows where to hold it.
 *  - The lens can be chosen and a torch lit, because a phone often hands back its fixed-focus
 *    ultra-wide, which cannot focus close, and thermal stickers read far better with light.
 *
 * The resolution actually delivered and the lens in use are shown, so a picture that will not
 * read can be told apart — blurred by the wrong lens, or too coarse to resolve the bars.
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
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const trackRef = useRef<MediaStreamTrack | null>(null)
  const lastRef = useRef<{ code: string; at: number }>({ code: '', at: 0 })

  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [cameras, setCameras] = useState<MediaDeviceInfo[]>([])
  const [deviceId, setDeviceId] = useState<string | undefined>()
  const [torchOn, setTorchOn] = useState(false)
  const [torchable, setTorchable] = useState(false)
  const [readout, setReadout] = useState('')

  const hit = useCallback(
    (code: string) => {
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
    hints.set(DecodeHintType.TRY_HARDER, true)
    const reader = new BrowserMultiFormatReader(hints)

    let cancelled = false
    let stream: MediaStream | undefined
    let timer: number | undefined

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
        if (cancelled) return stream.getTracks().forEach((t) => t.stop())

        const track = stream.getVideoTracks()[0]
        trackRef.current = track
        try {
          await track.applyConstraints({ advanced: [{ focusMode: 'continuous' }] } as never)
        } catch {
          /* unsupported */
        }
        const caps = track.getCapabilities?.() ?? {}
        const hasTorch = 'torch' in caps
        setTorchable(hasTorch)
        // Lit by default where the device allows it: HD with the light on is what reads a
        // thermal sticker, and asking someone to turn it on every time is a step to forget. The
        // toggle stays, for the odd glossy label where the light glares back.
        if (hasTorch) {
          try {
            await track.applyConstraints({ advanced: [{ torch: true }] } as never)
            setTorchOn(true)
          } catch {
            /* could not light it — the toggle remains */
          }
        }
        const settings = track.getSettings?.() ?? {}
        setReadout(`${settings.width ?? '?'}×${settings.height ?? '?'} · ${track.label || 'camera'}`)

        const all = await navigator.mediaDevices.enumerateDevices()
        setCameras(all.filter((d) => d.kind === 'videoinput'))

        const el = videoRef.current!
        el.srcObject = stream
        await el.play()

        // Decode a centre band only, at native resolution. The barcode aimed into the box keeps
        // its pixels instead of being lost when the whole frame is shrunk to decode.
        const canvas = canvasRef.current!
        const ctx = canvas.getContext('2d', { willReadFrequently: true })!
        const tick = () => {
          if (cancelled || el.videoWidth === 0) return
          const bw = Math.floor(el.videoWidth * 0.9)
          const bh = Math.floor(el.videoHeight * 0.35)
          const bx = Math.floor((el.videoWidth - bw) / 2)
          const by = Math.floor((el.videoHeight - bh) / 2)
          canvas.width = bw
          canvas.height = bh
          ctx.drawImage(el, bx, by, bw, bh, 0, 0, bw, bh)
          try {
            const result = reader.decodeFromCanvas(canvas)
            if (result) hit(result.getText())
          } catch {
            /* no code in this frame — ordinary */
          }
        }
        timer = window.setInterval(tick, 200)
      } catch (e: unknown) {
        const name = e instanceof DOMException ? e.name : ''
        if (name === 'NotAllowedError') {
          setError('The camera is blocked. Allow it in the address bar, then Try again.')
        } else if (name === 'NotFoundError' || name === 'OverconstrainedError') {
          setError('No usable camera here. Use a scanner, or type the code.')
        } else if (name === 'NotReadableError') {
          setError('The camera is in use by another app. Close it and Try again.')
        } else {
          setError('Could not start the camera. A scanner still works by typing into the box.')
        }
      }
    })()

    return () => {
      cancelled = true
      if (timer) clearInterval(timer)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [hit, attempt, deviceId])

  const toggleTorch = async () => {
    const track = trackRef.current
    if (!track) return
    const next = !torchOn
    try {
      await track.applyConstraints({ advanced: [{ torch: next }] } as never)
      setTorchOn(next)
    } catch {
      /* not controllable */
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
          <div className="viewwrap">
            <video ref={videoRef} className="viewfinder" muted playsInline autoPlay />
            <div className="aim" />
          </div>
          <canvas ref={canvasRef} style={{ display: 'none' }} />
          <p className="hint">Aim the barcode inside the box. Hold flat, close, steady.</p>
          {readout && <p className="readout">{readout}</p>}
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
