import { test, expect } from '@playwright/test'

test.describe('Authentication', () => {
  test('login page renders', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: /sign in/i })).toBeVisible()
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible()
  })

  test('register page renders with role selector', async ({ page }) => {
    await page.goto('/register')
    await expect(page.getByText('Buy or rent')).toBeVisible()
    await expect(page.getByText('Sell or list')).toBeVisible()
  })

  test('invalid login shows an error, not a crash', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('you@example.com').fill('nonexistent@smartre.co.ke')
    await page.getByPlaceholder(/••••••••/).fill('WrongPassword123!')
    await page.getByRole('button', { name: /sign in/i }).click()
    await expect(page.getByText(/invalid email or password|too many failed/i)).toBeVisible({ timeout: 10000 })
  })

  test('dashboard redirects unauthenticated users to login', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/login/, { timeout: 5000 })
  })

  test('admin console redirects unauthenticated users to login', async ({ page }) => {
    await page.goto('/overview')
    await expect(page).toHaveURL(/login/, { timeout: 5000 })
  })

  test('tab switcher moves between sign in and create account', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('link', { name: 'Create account' }).click()
    await expect(page).toHaveURL(/register/)
    await page.getByRole('link', { name: 'Sign in' }).click()
    await expect(page).toHaveURL(/login/)
  })

  test('seller callout on login pre-selects the seller role on register', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('link', { name: /want to sell a property/i }).click()
    await expect(page).toHaveURL(/register\?role=SELLER/)
    await expect(page.getByText('Sell or list')).toBeVisible()
  })
})
