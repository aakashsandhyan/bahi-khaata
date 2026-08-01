import { useEffect, useState } from 'react'

/**
 * A quantity input that can be cleared while typing.
 *
 * A plain `parseInt(value) || default` handler snaps the field back the instant you delete the last
 * digit, so you cannot clear it to type a new number. This keeps the raw text while you type (empty
 * allowed) and only clamps to a whole number in [min, max] when the field loses focus. `onChange`
 * fires with the clamped number as you type, and again on blur — so the parent always holds a valid
 * quantity, while the box itself is free to be momentarily empty.
 */
export function QtyInput({
  value,
  onChange,
  min = 1,
  max,
  style,
  className,
  autoFocus,
  disabled,
}: {
  value: number
  onChange: (n: number) => void
  min?: number
  max?: number
  style?: React.CSSProperties
  className?: string
  autoFocus?: boolean
  disabled?: boolean
}) {
  const [text, setText] = useState(String(value))

  // Follow the value when the parent changes it (a reset, a new item), unless the box is mid-edit
  // showing the same number already.
  useEffect(() => {
    if (parseInt(text, 10) !== value) setText(String(value))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  const clamp = (n: number) => {
    const lower = Math.max(min, n)
    return max != null ? Math.min(max, lower) : lower
  }

  return (
    <input
      type="number"
      inputMode="numeric"
      min={min}
      max={max}
      value={text}
      autoFocus={autoFocus}
      disabled={disabled}
      className={className}
      style={style}
      onChange={(e) => {
        const raw = e.target.value
        setText(raw)
        // Fire only when the text parses, so an empty box leaves the parent on its last good value.
        const n = parseInt(raw, 10)
        if (Number.isFinite(n)) onChange(clamp(n))
      }}
      onBlur={() => {
        const n = parseInt(text, 10)
        const clamped = Number.isFinite(n) ? clamp(n) : min
        setText(String(clamped))
        onChange(clamped)
      }}
    />
  )
}
