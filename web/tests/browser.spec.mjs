import { test, expect } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodeSave } from '../src/save.js';

const recoveryKey = 'quoridor.web.recovery.v1';
const screenshots = fileURLToPath(new URL('../../out/web/screenshots/', import.meta.url));
const server = process.env.QUORIDOR_TEST_SERVER;
const errors = new WeakMap();
const cell = (page, row, col) => page.locator(`.cell[data-row="${row}"][data-col="${col}"]`);
const anchor = (page, row, col, orientation = 'HORIZONTAL') => page.locator(`.wall-anchor[data-row="${row}"][data-col="${col}"][data-orientation="${orientation}"]`);

async function start(page, mode = 'LOCAL2', difficulty = 'MEDIUM') {
  await page.locator('#mode').selectOption(mode);
  if (mode === 'BOT') await page.locator('#difficulty').selectOption(difficulty);
  await page.locator('#start-game').click();
  await expect(page.locator('#game-view')).toBeVisible();
  await expect(page.locator('.cell')).toHaveCount(81);
}

async function turn(page, player) {
  await expect(page.locator('#turn-status')).toHaveAttribute('data-current-player', String(player));
}

async function position(page) {
  return decodeSave(await page.evaluate(key => localStorage.getItem(key), recoveryKey)).state.snapshot();
}

async function pawn(page, player, row, col) {
  await expect(cell(page, row, col).locator(`.pawn[data-player="${player}"]`)).toHaveCount(1);
}

async function screenshot(page, name, testInfo) {
  await mkdir(screenshots, { recursive: true });
  await page.screenshot({ path: path.join(screenshots, `${name}-${testInfo.project.name}.png`), fullPage: true, animations: 'disabled' });
}

async function noOverflow(page) {
  const sizes = await page.evaluate(() => ({
    width: innerWidth, scroll: document.documentElement.scrollWidth,
    board: document.getElementById('board')?.getBoundingClientRect().toJSON(),
  }));
  expect(sizes.scroll).toBeLessThanOrEqual(sizes.width + 1);
  if (await page.locator('#game-view').isVisible()) {
    expect(Math.abs(sizes.board.width - sizes.board.height)).toBeLessThan(2);
    expect(sizes.board.x).toBeGreaterThanOrEqual(-1);
    expect(sizes.board.right).toBeLessThanOrEqual(sizes.width + 1);
  }
}

test.beforeEach(async ({ page }) => {
  const failures = [];
  errors.set(page, failures);
  page.on('pageerror', error => failures.push(error.message));
  await page.goto('./');
  await expect(page.locator('#app-version')).toHaveText(/^v\d+\.\d+\.\d+$/);
  await page.locator('#theme').selectOption('dark');
  await page.locator('#language').selectOption('en');
});

test.afterEach(async ({ page }) => {
  expect(errors.get(page)).toEqual([]);
});

test('menu, safe Unicode names, appearance, localization and credits work under the Pages path', async ({ page }, testInfo) => {
  expect(new URL(page.url()).pathname).toBe('/Game-Quoridor/');
  await screenshot(page, 'web-menu-en-dark', testInfo);
  await page.locator('#mode').selectOption('LOCAL4');
  await page.locator('#name-0').fill('<img src=x onerror=x>');
  await page.locator('#name-1').fill('سارا 🎮');
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.locator('#name-0')).toHaveValue('<img src=x onerror=x>');
  await expect(page.locator('#mode')).toHaveValue('LOCAL4');
  await page.locator('#start-game').click();
  await expect(page.locator('#wall-counts bdi').first()).toHaveText('<img src=x onerror=x>');
  await expect(page.locator('#wall-counts img')).toHaveCount(0);
  await page.locator('#about-toggle').click();
  await expect(page.locator('#about-dialog')).toBeVisible();
  await expect(page.locator('#about-dialog')).toContainText('سیدمحمدصالح قطبانی');
  await expect(page.locator('#about-dialog')).toContainText('محمدجواد بنان');
  await expect(page.locator('#about-dialog')).toContainText('امیرمحمد خسروی');
  await expect(page.locator('#license-text')).toContainText('MIT License');
  await page.locator('#about-close').click();
  await noOverflow(page);
});

test('local moves, wall preview/confirmation, crossing rejection, history, hints and undo/redo', async ({ page }, testInfo) => {
  await start(page);
  await cell(page, 7, 4).click();
  await turn(page, 1);
  await cell(page, 1, 4).click();
  await turn(page, 0);
  const beforeWall = await position(page);
  await page.locator('#tool-horizontal').click();
  await expect(page.locator('.wall-anchor')).toHaveCount(64);
  await anchor(page, 5, 3).click();
  expect(await position(page)).toEqual(beforeWall);
  await expect(page.locator('#confirm-wall')).toBeEnabled();
  await page.locator('#cancel-wall').click();
  expect(await position(page)).toEqual(beforeWall);
  await anchor(page, 5, 3).click();
  await page.locator('#confirm-wall').click();
  await turn(page, 1);
  await expect(page.locator('.placed-wall')).toHaveCount(1);
  await page.locator('#tool-vertical').click();
  await anchor(page, 5, 3, 'VERTICAL').click();
  await expect(page.locator('#confirm-wall')).toBeDisabled();
  await page.locator('#cancel-wall').click();
  await anchor(page, 2, 5, 'VERTICAL').click();
  await page.locator('#confirm-wall').click();
  await expect(page.locator('.placed-wall')).toHaveCount(2);
  const withWalls = await position(page);
  await page.locator('#undo').click();
  await expect(page.locator('.placed-wall')).toHaveCount(1);
  await page.locator('#redo').click();
  expect(await position(page)).toEqual(withWalls);
  await page.locator('#hint').click();
  await expect(page.locator('.cell.hint.legal')).toHaveCount(1);
  expect(await position(page)).toEqual(withWalls);
  await page.locator('#show-history').click();
  await expect(page.locator('#history-list li')).toHaveCount(4);
  await screenshot(page, 'web-game-en-dark', testInfo);
  const left = await cell(page, 0, 0).boundingBox();
  const right = await cell(page, 0, 8).boundingBox();
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  expect(await position(page)).toEqual(withWalls);
  expect((await cell(page, 0, 0).boundingBox()).x).toBeLessThan((await cell(page, 0, 8).boundingBox()).x);
  expect(left.x).toBeLessThan(right.x);
  await expect(page.locator('#wall-counts')).toContainText('۹');
  await screenshot(page, 'web-game-fa-light', testInfo);
  await noOverflow(page);
});

test('four-player clockwise turns preserve goals, inventory and physical board orientation', async ({ page }, testInfo) => {
  await start(page, 'LOCAL4');
  await expect(page.locator('.pawn')).toHaveCount(4);
  await expect(page.locator('.goal-edge')).toHaveCount(4);
  await expect(page.locator('#wall-counts .player-stat')).toHaveCount(4);
  expect((await position(page)).wallsRemaining).toEqual([5, 5, 5, 5]);
  for (const [player, row, col, next] of [[0, 7, 4, 2], [2, 4, 1, 1], [1, 1, 4, 3], [3, 4, 7, 0]]) {
    await turn(page, player);
    await cell(page, row, col).click();
    await pawn(page, player, row, col);
    await turn(page, next);
  }
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await expect(page.locator('#wall-counts')).toContainText('۵');
  await screenshot(page, 'web-four-player-fa-light', testInfo);
  await noOverflow(page);
});

for (const difficulty of ['EASY', 'MEDIUM', 'HARD']) {
  test(`real ${difficulty} worker answers three turns and paired undo/redo restores the match`, async ({ page }, testInfo) => {
    await start(page, 'BOT', difficulty);
    await page.locator('#show-history').click();
    for (let pair = 1; pair <= 3; pair++) {
      await page.locator('.cell.legal').first().click();
      await turn(page, 0);
      await expect(page.locator('#history-list li')).toHaveCount(pair * 2);
    }
    const settled = await position(page);
    await page.locator('#undo').click();
    await turn(page, 0);
    await expect(page.locator('#history-list li')).toHaveCount(4);
    await page.locator('#redo').click();
    expect(await position(page)).toEqual(settled);
    await expect(page.locator('#history-list li')).toHaveCount(6);
    if (difficulty === 'HARD') await screenshot(page, 'web-robot-hard-en-dark', testInfo);
  });
}

test('undo during a pending robot reply cancels stale work while settings remain usable', async ({ page, context }) => {
  await context.route('**/src/bot-worker.js', async route => {
    await new Promise(resolve => setTimeout(resolve, 350));
    await route.continue().catch(() => {});
  });
  await start(page, 'BOT', 'HARD');
  await cell(page, 7, 4).click();
  await turn(page, 1);
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await page.locator('#undo').click();
  await turn(page, 0);
  await pawn(page, 0, 8, 4);
  await page.waitForTimeout(600);
  await pawn(page, 0, 8, 4);
  expect((await position(page)).walls).toHaveLength(0);
  await expect(page.locator('#redo')).toBeEnabled();
});

test('recovery, portable export/import, and invalid UTF-8 import preserve a real match', async ({ page }, testInfo) => {
  await page.locator('#mode').selectOption('LOCAL2');
  await page.locator('#name-0').fill('سارا 🎮');
  await page.locator('#start-game').click();
  await cell(page, 7, 4).click();
  const saved = await position(page);
  const downloadPromise = page.waitForEvent('download');
  await page.locator('#save-game').click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.qsave$/);
  const exportPath = testInfo.outputPath('export.qsave');
  await download.saveAs(exportPath);
  const exported = await readFile(exportPath);
  expect(decodeSave(exported.toString('utf8')).state.snapshot()).toEqual(saved);
  await page.reload();
  await expect(page.locator('#continue-game')).toBeEnabled();
  await page.locator('#continue-game').click();
  expect(await position(page)).toEqual(saved);
  await pawn(page, 0, 7, 4);
  await page.locator('#new-game').click();
  await page.locator('#save-file-input').setInputFiles({ name: 'bad.qsave', mimeType: 'text/plain', buffer: Buffer.from([0xc3, 0x28]) });
  await expect(page.locator('#notice')).toHaveAttribute('data-message', 'saveError');
  await page.locator('#continue-game').click();
  expect(await position(page)).toEqual(saved);
  await page.locator('#new-game').click();
  await page.locator('#save-file-input').setInputFiles({ name: 'valid.qsave', mimeType: 'text/plain', buffer: exported });
  await expect(page.locator('#game-view')).toBeVisible();
  expect(await position(page)).toEqual(saved);
  await expect(page.locator('#wall-counts')).toContainText('سارا 🎮');
});

test('keyboard controls and narrow/landscape layouts retain an accessible square board', async ({ page }, testInfo) => {
  await start(page);
  await cell(page, 8, 4).focus();
  await page.keyboard.press('ArrowUp');
  await expect(cell(page, 7, 4)).toBeFocused();
  await page.keyboard.press('Enter');
  await pawn(page, 0, 7, 4);
  await page.locator('#language').selectOption('fa');
  await cell(page, 0, 4).focus();
  await page.keyboard.press('ArrowRight');
  await expect(cell(page, 0, 5)).toBeFocused();
  for (const viewport of [{ width: 320, height: 740 }, { width: 390, height: 844 }, { width: 844, height: 390 }]) {
    await page.setViewportSize(viewport);
    await noOverflow(page);
    await expect(page.locator('#board')).toHaveAttribute('role', 'grid');
    await expect(page.locator('.cell')).toHaveCount(81);
    await expect(page.locator('#board')).not.toHaveAttribute('aria-label', 'Quoridor board');
    const bounds = await page.locator('#tool-horizontal').boundingBox();
    expect(bounds.height).toBeGreaterThanOrEqual(40);
  }
  await screenshot(page, 'web-landscape-fa', testInfo);
});

async function onlineSetup(page, endpoint, count, name) {
  await page.locator('#server-url').fill(endpoint);
  await page.locator('#online-name').fill(name);
  await page.locator('#online-count').selectOption(String(count));
}

async function activate(page) { await page.bringToFront(); }

for (const count of [2, 4]) {
  test(`actual cross-origin Java private room supports ${count} browser seats and authoritative actions`, async ({ page, context }) => {
    test.skip(!server, 'Run test-web.ps1 to start the owned Java interoperability server.');
    const pages = [page];
    const failures = errors.get(page);
    page.on('dialog', dialog => dialog.accept());
    try {
      await onlineSetup(page, server, count, 'Blue');
      await page.locator('#online-create').click();
      await expect(page.locator('#game-view')).toBeVisible();
      const text = await page.locator('#online-room-status').textContent();
      const code = text.match(/\b[A-Z2-9]{8}\b/)?.[0];
      expect(code).toBeTruthy();
      for (let index = 1; index < count; index++) {
        const peer = await context.newPage(); pages.push(peer);
        peer.on('pageerror', error => failures.push(error.message));
        peer.on('dialog', dialog => dialog.accept());
        await peer.goto('./');
        await onlineSetup(peer, server, count, `Player ${index + 1}`);
        await peer.locator('#room-code').fill(code);
        await peer.locator('#online-join').click();
        await expect(peer.locator('#game-view')).toBeVisible();
      }
      await activate(page);
      await expect(cell(page, 7, 4)).toHaveClass(/legal/);
      await cell(page, 7, 4).click();
      await turn(page, count === 2 ? 1 : 2);
      const next = pages[count === 2 ? 1 : 2];
      await activate(next);
      await expect(next.locator('#tool-horizontal')).toBeEnabled();
      await next.locator('#tool-horizontal').click();
      await anchor(next, 3, 3).click();
      await next.locator('#confirm-wall').click();
      await expect(next.locator('.placed-wall')).toHaveCount(1);
      await activate(page);
      await expect(page.locator('.placed-wall')).toHaveCount(1);
      await expect(page.locator('#undo')).toBeDisabled();
      await expect(page.locator('#hint')).toBeDisabled();
      expect(await page.evaluate(() => Object.keys(localStorage).filter(key => /token|credential|membership/i.test(key)))).toEqual([]);
    } finally {
      for (const peer of pages) {
        if (!peer.isClosed() && await peer.locator('#online-leave').isVisible().catch(() => false)) await peer.locator('#online-leave').click().catch(() => {});
      }
      for (const peer of pages.slice(1)) await peer.close();
    }
  });
}

for (const count of [2, 4]) {
  test(`real Java matchmaking fills a ${count}-player browser room and closes cleanly`, async ({ page, context }) => {
    test.skip(!server, 'Run test-web.ps1 to start the owned Java interoperability server.');
    const pages = [page];
    try {
      for (let index = 0; index < count; index++) {
        const participant = index === 0 ? page : await context.newPage();
        if (index !== 0) { pages.push(participant); await participant.goto('./'); }
        participant.on('dialog', dialog => dialog.accept());
        await onlineSetup(participant, server, count, `Player ${index + 1}`);
        await participant.locator('#online-find').click();
        await expect(participant.locator('#game-view')).toBeVisible();
      }
      await activate(page);
      await expect(page.locator('.cell.legal')).not.toHaveCount(0);
      const roomTexts = [];
      for (const participant of pages) roomTexts.push((await participant.locator('#online-room-status').textContent()).match(/\b[A-Z2-9]{8}\b/)?.[0]);
      expect(new Set(roomTexts).size).toBe(1);
    } finally {
      for (const participant of pages) {
        if (!participant.isClosed() && await participant.locator('#online-leave').isVisible().catch(() => false)) await participant.locator('#online-leave').click().catch(() => {});
      }
      for (const participant of pages.slice(1)) await participant.close();
    }
  });
}
