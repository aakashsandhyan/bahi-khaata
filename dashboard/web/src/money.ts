// Paise in, rupees on screen. Money is an integer count of paise everywhere it travels; it
// becomes a decimal only at the moment a person reads it, and never a float before that.

export function rupees(paise: number | null | undefined): string {
  if (paise == null) return '—'
  return `₹${(paise / 100).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

// Dense contexts — tables, totals, funnel bars — round to the whole rupee: at a glance
// "₹6,36,860" reads, "₹6,36,859.8" stalls. Exact paise stay where a single figure is the
// point (item detail, the till) and in CSV exports, which are data, not display.
export function rupeesWhole(paise: number | null | undefined): string {
  if (paise == null) return '—'
  return `₹${Math.round(paise / 100).toLocaleString('en-IN')}`
}
