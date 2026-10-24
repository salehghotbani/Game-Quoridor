import { test, expect } from '@playwright/test';

test('tutorial pauses pending analysis, fences its stale reply and resumes the same robot match', async ({
  page,
  context,
}) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await context.route('**/src/bot-worker.js', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    await route.continue().catch(() => {});
  });
  await page.goto('./');
  await page.locator('#mode').selectOption('BOT');
  await page.locator('#start-game').click();
  await page.locator('#board .cell[data-row="7"][data-col="4"]').click();
  await page.locator('#tutorial-game').click();
  await expect(page.locator('#tutorial-dialog')).toBeVisible();
  const pending = await page.evaluate(() =>
    localStorage.getItem('quoridor.web.recovery.v1'),
  );
  await page.waitForTimeout(700);
  expect(
    await page.evaluate(() => localStorage.getItem('quoridor.web.recovery.v1')),
  ).toEqual(pending);
  await page.locator('#tutorial-close').click();
  await expect(page.locator('#turn-status')).toHaveAttribute(
    'data-current-player',
    '0',
  );
  await page.locator('#show-history').click();
  await expect(page.locator('#history-list li')).toHaveCount(2);
  expect(errors).toEqual([]);
});

test('persisted page lifecycle keeps a local position and history usable', async ({
  page,
}) => {
  await page.goto('./');
  await page.locator('#start-game').click();
  await page.locator('#board .cell[data-row="7"][data-col="4"]').click();
  const saved = await page.evaluate(() =>
    localStorage.getItem('quoridor.web.recovery.v1'),
  );
  await page.evaluate(() => {
    window.dispatchEvent(
      new PageTransitionEvent('pagehide', { persisted: true }),
    );
    window.dispatchEvent(
      new PageTransitionEvent('pageshow', { persisted: true }),
    );
  });
  expect(
    await page.evaluate(() => localStorage.getItem('quoridor.web.recovery.v1')),
  ).toEqual(saved);
  await page.locator('#undo').click();
  await expect(
    page.locator(
      '#board .cell[data-row="8"][data-col="4"] .pawn[data-player="0"]',
    ),
  ).toHaveCount(1);
});
