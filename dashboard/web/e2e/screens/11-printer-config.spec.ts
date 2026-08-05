import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

// Printer config is now the Label printer tab of Settings (design decision D1 of
// palletworks-nav) — reached via the sidebar's single Settings entry, not a nav entry of its own.
test('Settings — Label printer: the seeded config is disabled and unroutable; testing it proves printing is impossible', async ({ page }) => {
  await openScreen(page, 'Settings')
  await page.getByRole('button', { name: 'Label printer' }).click()

  const address = page.getByPlaceholder(/TSC TE244/)
  await expect(address).toHaveValue(seed.printerConfig.address)
  await expect(page.getByRole('checkbox')).not.toBeChecked()

  // "Test Print" (POST /api/admin/printer-config/test) is a plain TCP connectivity check —
  // PrinterConnectionTester.testConnection — and it does NOT consult the enabled flag at all.
  // What makes this safe is the address: 203.0.113.1 is RFC 5737 TEST-NET-3, guaranteed
  // unroutable, so the connect attempt can only ever time out — it can never reach a real
  // device. (The enabled flag's own guarantee is enforced one layer down, in
  // UsbPrinterDriver.sendLabel, which is what the print queue and bulk-print actually call.)
  // The connect attempt has its own 5s server-side timeout, so this assertion needs more than
  // Playwright's 5s default to observe the result without racing it.
  await page.getByRole('button', { name: 'Test Print' }).click()
  await expect(page.getByText(/did not respond after 5 seconds/i)).toBeVisible({ timeout: 10_000 })
})
