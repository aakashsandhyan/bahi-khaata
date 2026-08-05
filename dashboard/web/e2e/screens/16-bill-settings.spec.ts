import { test, expect } from '@playwright/test'
import { seed } from '../seed'
import { openScreen } from '../helpers'

// V43 seeds bill_settings with defaults (shop_name 'Bachat Bazaar', bill_title 'Bill of Supply',
// footer 'Thank you!'); V44, an earlier and unrelated migration, then overwrites shop_name to the
// shop's Devanagari name on every environment, including this scratch database. bill_title and
// footer are untouched by V44 and still carry their V43 defaults. See seed.ts's billSettings
// comment and the implementation return for palletworks-dashboard for the full note.
//
// Bill settings is now the Bill tab of Settings (design decision D1 of palletworks-nav) — reached
// via the sidebar's single Settings entry, not a nav entry of its own.
test('Settings — Bill: V43 defaults are visible, and an edited footer persists across a reload', async ({ page }) => {
  await openScreen(page, 'Settings')
  await page.getByRole('button', { name: 'Bill' }).click()

  const inputs = page.locator('input')
  const shopName = inputs.nth(0)
  const footer = inputs.nth(4)
  const billTitle = page.getByPlaceholder('Bill of Supply')

  await expect(shopName).toHaveValue(seed.billSettings.shopNameAsSeeded)
  await expect(billTitle).toHaveValue(seed.billSettings.billTitle)
  await expect(footer).toHaveValue(seed.billSettings.footer)

  const edited = 'Thanks for shopping with Bachat Bazaar!'
  await footer.fill(edited)
  await page.getByRole('button', { name: 'Save Settings' }).click()
  await expect(page.getByText('✓ Bill settings saved')).toBeVisible()

  // A fresh mount, re-fetched from the backend — not client-side memory — is what proves the edit
  // actually persisted.
  await openScreen(page, 'Settings')
  await page.getByRole('button', { name: 'Bill' }).click()
  await expect(page.locator('input').nth(4)).toHaveValue(edited)
})
