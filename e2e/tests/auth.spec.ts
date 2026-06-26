import { test, expect } from '@playwright/test';

const EMAIL    = process.env['TEST_USER_EMAIL']     ?? 'admin@druk3d.pl';
const PASSWORD = process.env['TEST_USER_PASSWORD'] ?? 'admin123';

test.describe('Auth flows', () => {
  test('auth guard redirects unauthenticated user from /profil to /logowanie', async ({ page }) => {
    await page.goto('/profil');
    await expect(page).toHaveURL(/logowanie/);
    await expect(page.locator('#login-email')).toBeVisible();
  });

  test('auth guard redirects unauthenticated user from /moje-zlecenia to /logowanie', async ({ page }) => {
    await page.goto('/moje-zlecenia');
    await expect(page).toHaveURL(/logowanie/);
  });

  test('auth guard redirects unauthenticated user from /wiadomosci to /logowanie', async ({ page }) => {
    await page.goto('/wiadomosci');
    await expect(page).toHaveURL(/logowanie/);
  });

  test('login with wrong credentials shows error message', async ({ page }) => {
    await page.goto('/logowanie');
    await page.fill('#login-email', 'nosuchuser@example.com');
    await page.fill('#login-pw', 'wrongpassword');
    await page.click('button[type=submit]');
    await expect(page.locator('[role="alert"]')).toBeVisible({ timeout: 8_000 });
  });

  test('login with valid credentials navigates away from /logowanie', async ({ page }) => {
    await page.goto('/logowanie');
    await page.fill('#login-email', EMAIL);
    await page.fill('#login-pw', PASSWORD);
    await page.click('button[type=submit]');
    await expect(page).toHaveURL('/', { timeout: 10_000 });
  });

  test('register tab toggle renders register form', async ({ page }) => {
    await page.goto('/logowanie');
    await page.click('button[role="tab"]:has-text("Zarejestruj się")');
    await expect(page.locator('#reg-email')).toBeVisible();
    await expect(page.locator('#reg-pw')).toBeVisible();
    await expect(page.locator('#reg-pw2')).toBeVisible();
  });

  test('register with mismatched passwords shows validation error', async ({ page }) => {
    await page.goto('/logowanie');
    await page.click('button[role="tab"]:has-text("Zarejestruj się")');
    await page.fill('#reg-email', 'test@example.com');
    await page.fill('#reg-pw', 'password1');
    await page.fill('#reg-pw2', 'differentpassword');
    await page.click('button[type=submit]');
    // Angular marks form touched on submit → mismatch error becomes visible
    await expect(page.locator('[role="alert"]:has-text("Hasła nie są identyczne.")')).toBeVisible();
  });
});
