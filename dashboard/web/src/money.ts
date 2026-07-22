// Paise in, rupees on screen. Money is an integer count of paise everywhere it travels; it
// becomes a decimal only at the moment a person reads it, and never a float before that.

export function rupees(paise: number | null | undefined): string {
  if (paise == null) return '—'
  return `₹${(paise / 100).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}
