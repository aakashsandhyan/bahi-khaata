import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

// Deliberately does not click "Test Print": unlike the label printer's test-connect (see
// 11-printer-config.spec.ts, whose unroutable TEST-NET-3 address makes that click provably safe),
// this spec only asserts the seeded config is loaded and disabled — no connection is attempted.
//
// Receipt printer config is now the Receipt printer tab of Settings (design decision D1 of
// palletworks-nav) — reached via the sidebar's single Settings entry, not a nav entry of its own.
test('Settings — Receipt printer: the seeded TEST-NET config is loaded, disabled', async ({ page }) => {
  await openScreen(page, 'Settings')
  await page.getByRole('button', { name: 'Receipt printer' }).click()

  // The address field's placeholder text depends on the transport (LAN vs USB) — V44 (an earlier,
  // unrelated migration) leaves this config's transport as USB, so a placeholder-based locator
  // would be transport-dependent for no reason; the address is the only textbox on this screen.
  const address = page.getByRole('textbox')
  await expect(address).toHaveValue(seed.receiptPrinterConfig.address)
  await expect(page.getByRole('checkbox')).not.toBeChecked()
})
