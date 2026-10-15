import { test, expect } from '@playwright/test';

const server = process.env.QUORIDOR_TEST_SERVER;

async function pair(page, context) {
  page.on('dialog', dialog => dialog.accept());
  await page.goto('./');
  await page.locator('#server-url').fill(server);
  await page.locator('#online-name').fill('Blue');
  await page.locator('#online-count').selectOption('2');
  await page.locator('#online-create').click();
  await expect(page.locator('#game-view')).toBeVisible();
  const room = (await page.locator('#online-room-status').textContent()).match(/\b[A-Z2-9]{8}\b/)[0];
  const peer = await context.newPage();
  peer.on('dialog', dialog => dialog.accept());
  await peer.goto('./');
  await peer.locator('#server-url').fill(server);
  await peer.locator('#online-name').fill('Green');
  await peer.locator('#online-count').selectOption('2');
  await peer.locator('#room-code').fill(room);
  await peer.locator('#online-join').click();
  await expect(peer.locator('#game-view')).toBeVisible();
  await page.bringToFront();
  await expect(page.locator('.cell.legal')).not.toHaveCount(0);
  return peer;
}

test('terminal server closure freezes actions and does not offer an endless reconnect', async ({ page, context }) => {
  test.skip(!server, 'Run the full owned-server harness.');
  const peer = await pair(page, context);
  try {
    await peer.locator('#online-leave').click();
    await page.bringToFront();
    await expect(page.locator('#turn-status')).toContainText('closed');
    await expect(page.locator('.cell.legal')).toHaveCount(0);
    await expect(page.locator('#online-retry')).toBeHidden();
    await expect(page.locator('#undo')).toBeDisabled();
    await page.locator('#online-leave').click();
    await expect(page.locator('#menu-view')).toBeVisible();
  } finally {
    await peer.close();
  }
});

test('persisted page lifecycle drops a released online client and restores usable menu controls', async ({ page, context }) => {
  test.skip(!server, 'Run the full owned-server harness.');
  const peer = await pair(page, context);
  try {
    await page.evaluate(() => {
      window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }));
      window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    });
    await expect(page.locator('#menu-view')).toBeVisible();
    await expect(page.locator('#online-create')).toBeEnabled();
    await expect(page.locator('#online-cancel')).toBeHidden();
    await expect(page.locator('#notice')).toHaveAttribute('data-message', 'closedRoom');
    await page.locator('#mode').selectOption('LOCAL2');
    await page.locator('#start-game').click();
    await expect(page.locator('.cell.legal')).not.toHaveCount(0);
    await expect(page.locator('.pawn')).toHaveCount(2);
    await peer.locator('#online-leave').click();
  } finally {
    await peer.close();
  }
});
