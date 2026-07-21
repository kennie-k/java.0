import { test, expect } from '@playwright/test'

test.describe('Public homepage', () => {
  test('loads without requiring login', async ({ page }) => {
    await page.goto('/')
    await expect(page).not.toHaveURL(/login/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('shows the hero search form', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByPlaceholder(/search by title/i)).toBeVisible()
  })

  test('search submits to the public listings page, not login', async ({ page }) => {
    await page.goto('/')
    await page.getByPlaceholder(/search by title/i).fill('Westlands')
    await page.getByRole('button', { name: /search/i }).first().click()
    await expect(page).toHaveURL(/\/properties/)
    await expect(page).not.toHaveURL(/login/)
  })

  test('header nav links to properties without login', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Buy' }).click()
    await expect(page).toHaveURL(/\/properties/)
    await expect(page).not.toHaveURL(/login/)
  })

  test('login and register buttons are present but not forced', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('link', { name: /log in/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /get started|start selling/i }).first()).toBeVisible()
  })
})
