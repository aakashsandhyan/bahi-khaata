import { test, expect } from '@playwright/test'

// The stock table is wide by nature; at phone width it must scroll inside its own container,
// never the page body — the shell (header, drawer) stays put while the table pans.
test.use({ viewport: { width: 420, height: 800 } })

test('Inventory: at phone width the table scrolls in its wrap, not the body', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Open navigation' }).click()
  await page.getByRole('button', { name: 'Inventory', exact: true }).click()

  await expect(page.locator('tr.inv-row').first()).toBeVisible()

  const overflow = await page.evaluate(() => {
    const doc = document.documentElement
    const wrap = document.querySelector('.inv-table-wrap')!
    return {
      bodyOverflows: doc.scrollWidth > doc.clientWidth + 1,
      wrapScrolls: wrap.scrollWidth > wrap.clientWidth,
    }
  })
  expect(overflow.bodyOverflows).toBe(false)
  expect(overflow.wrapScrolls).toBe(true)
})
