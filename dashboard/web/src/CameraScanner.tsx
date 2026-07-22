import { useEffect, useRef, useState } from 'react'
import { BrowserMultiFormatReader } from '@zxing/browser'
import { BarcodeFormat, DecodeHintType } from '@zxing/library'

/**
 * Reading a barcode off the phone's camera.
 *
 * For phones with no scanner paired to them. A dedicated scanner is faster and surer, and this
 * does not replace it — it is the fallback for goods whose sticker a camera can manage and a
 * phone someone already has in hand.
 *
 * Restricted to the formats these goods actually wear — Code 128 for the returns stickers, and
 * the EAN and UPC a manufacturer prints. Telling the reader what to look for makes it quicker and
 * stops it reporting a stray pattern as some format nothing here uses.
 *
 * The rear camera, because that is the one pointed at the goods. A repeat of the same code within
 * a moment is ignored, since the camera reads many frames a second and would otherwise count one
 * sticker a dozen times.
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
  const [error, setError] = useState<string | null>(null)
  const lastRef = useRef<{ code: string; at: number }>({ code: '', at: 0 })

  useEffect(() => {
    const hints = new Map()
    hints.set(DecodeHintType.POSSIBLE_FORMATS, FORMATS)
    const reader = new BrowserMultiFormatReader(hints)
    let stop: (() => void) | undefined
    let cancelled = false

    reader
      .decodeFromConstraints(
        { video: { facingMode: 'environment' } },
        videoRef.current!,
        (result) => {
          if (!result) return
          const code = result.getText()
          const now = Date.now()
          // Same sticker across successive frames is one scan, not many.
          if (code === lastRef.current.code && now - lastRef.current.at < 1500) return
          lastRef.current = { code, at: now }
          if ('vibrate' in navigator) navigator.vibrate(80)
          onDetected(code)
        },
      )
      .then((controls) => {
        if (cancelled) controls.stop()
        else stop = () => controls.stop()
      })
      .catch((e: unknown) => {
        setError(
          e instanceof DOMException && e.name === 'NotAllowedError'
            ? 'The camera was blocked. Allow camera access for this page and try again.'
            : 'Could not start the camera. A scanner still works by typing into the box.',
        )
      })

    return () => {
      cancelled = true
      stop?.()
    }
  }, [onDetected])

  return (
    <div className="camera">
      {error ? (
        <div className="banner stop">{error}</div>
      ) : (
        <video ref={videoRef} className="viewfinder" muted playsInline />
      )}
      <button onClick={onClose}>Stop the camera</button>
    </div>
  )
}
