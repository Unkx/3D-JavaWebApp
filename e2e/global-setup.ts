import { chromium } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'fs';
import { join } from 'path';

const BACKEND = process.env['BACKEND_URL'] ?? 'http://localhost:8080';
const BASE    = process.env['BASE_URL']    ?? 'http://localhost:4200';
const EMAIL   = process.env['TEST_USER_EMAIL']     ?? 'admin@druk3d.pl';
const PASSWORD = process.env['TEST_USER_PASSWORD'] ?? 'admin123';
const AUTH_DIR = join(__dirname, '.auth');

export default async function globalSetup() {
  mkdirSync(AUTH_DIR, { recursive: true });

  // 1. Get JWT from backend API
  const loginRes = await fetch(`${BACKEND}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  if (!loginRes.ok) throw new Error(`Login failed: ${loginRes.status} ${await loginRes.text()}`);
  const { token } = await loginRes.json() as { token: string };

  // 2. Create a test listing via backend API
  const listingRes = await fetch(`${BACKEND}/api/listings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({
      title: 'E2E Smoke Test Listing',
      description: 'Created by Playwright global setup — safe to delete.',
      requiredMaterial: 'PLA',
    }),
  });
  if (!listingRes.ok) throw new Error(`Create listing failed: ${listingRes.status} ${await listingRes.text()}`);
  const listing = await listingRes.json() as { id: string };
  writeFileSync(join(AUTH_DIR, 'test-listing.json'), JSON.stringify({ id: listing.id }));

  // 3. Log in via browser UI to capture localStorage storageState
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ baseURL: BASE });
  const page = await ctx.newPage();

  await page.goto('/logowanie');
  await page.fill('#login-email', EMAIL);
  await page.fill('#login-pw', PASSWORD);
  await page.click('button[type=submit]');
  await page.waitForURL(url => !url.pathname.includes('logowanie'), { timeout: 15_000 });

  await ctx.storageState({ path: join(AUTH_DIR, 'user.json') });
  await browser.close();
}
