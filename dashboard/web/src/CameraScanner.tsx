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
  const [attempt, setAttempt] = useState(0)
  const lastRef = useRef<{ code: string; at: number }>({ code: '', at: 0 })

  useEffect(() => {
    setError(null)
    const hints = new Map()
    hints.set(DecodeHintType.POSSIBLE_FORMATS, FORMATS)
    const reader = new BrowserMultiFormatReader(hints)
    let cancelled = false
    let stream: MediaStream | undefined
    let stop: (() => void) | undefined

    const onResult = (result: { getText(): string } | undefined) => {
      if (!result) return
      const code = result.getText()
      const now = Date.now()
      // Same sticker across successive frames is one scan, not many.
      if (code === lastRef.current.code && now - lastRef.current.at < 1500) return
      lastRef.current = { code, at: now }
      if ('vibrate' in navigator) navigator.vibrate(80)
      onDetected(code)
    }

    // The stream is taken and played by hand rather than left to the reader, because letting
    // the reader both open the camera and start the video raced on mobile: the stream arrived
    // but the element never played, so it showed a black rectangle. Grabbing the stream,
    // attaching it, and awaiting play() first makes the picture appear before decoding begins.
    ;(async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
        })
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        const video = videoRef.current!
        video.srcObject = stream
        await video.play()
        const controls = await reader.decodeFromVideoElement(video, onResult)
        if (cancelled) controls.stop()
        else stop = () => controls.stop()
      } catch (e: unknown) {
        const name = e instanceof DOMException ? e.name : ''
        if (name === 'NotAllowedError') {
          setError(
            'The camera is blocked for this page. Tap the lock or camera icon in the address' +
              ' bar, set Camera to Allow, then Try again. A scanner still works by typing into' +
              ' the box meanwhile.',
          )
        } else if (name === 'NotFoundError' || name === 'OverconstrainedError') {
          setError('No camera was found on this device. Use a scanner, or type the code.')
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
  }, [onDetected, attempt])

  return (
    <div className="camera">
      {error ? (
        <>
          <div className="banner stop">{error}</div>
          <button onClick={() => setAttempt((n) => n + 1)}>Try again</button>
        </>
      ) : (
        <video ref={videoRef} className="viewfinder" muted playsInline autoPlay />
      )}
      <button onClick={onClose}>Stop the camera</button>
    </div>
  )
}
