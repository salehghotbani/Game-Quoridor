import { test, expect } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GameState } from '../src/game.js';
import { decodeSave, encodeSave } from '../src/save.js';

const recoveryKey = 'quoridor.web.recovery.v1';
const screenshots = fileURLToPath(
  new URL('../../out/web/screenshots/', import.meta.url),
);
const errors = new WeakMap();
const cell = (page, row, col) =>
  page.locator(`.cell[data-row="${row}"][data-col="${col}"]`);
const anchor = (page, row, col, orientation = 'HORIZONTAL') =>
  page.locator(
    `.wall-anchor[data-row="${row}"][data-col="${col}"][data-orientation="${orientation}"]`,
  );

async function start(page, mode = 'LOCAL2', difficulty = 'MEDIUM') {
  await page.locator('#mode').selectOption(mode);
  if (mode === 'BOT')
    await page.locator('#difficulty').selectOption(difficulty);
  await page.locator('#start-game').click();
  await expect(page.locator('#game-view')).toBeVisible();
  await expect(page.locator('.cell')).toHaveCount(81);
}

async function turn(page, player) {
  await expect(page.locator('#turn-status')).toHaveAttribute(
    'data-current-player',
    String(player),
  );
}

async function position(page) {
  return decodeSave(
    await page.evaluate((key) => localStorage.getItem(key), recoveryKey),
  ).state.snapshot();
}

async function pawn(page, player, row, col) {
  await expect(
    cell(page, row, col).locator(`.pawn[data-player="${player}"]`),
  ).toHaveCount(1);
}

async function screenshot(page, name, testInfo, fullPage = true) {
  await mkdir(screenshots, { recursive: true });
  await page.screenshot({
    path: path.join(screenshots, `${name}-${testInfo.project.name}.png`),
    fullPage,
    animations: 'disabled',
  });
}

async function noOverflow(page) {
  const sizes = await page.evaluate(() => ({
    width: innerWidth,
    scroll: document.documentElement.scrollWidth,
    board: document.getElementById('board')?.getBoundingClientRect().toJSON(),
  }));
  expect(sizes.scroll).toBeLessThanOrEqual(sizes.width + 1);
  if (await page.locator('#game-view').isVisible()) {
    expect(Math.abs(sizes.board.width - sizes.board.height)).toBeLessThan(2);
    expect(sizes.board.x).toBeGreaterThanOrEqual(-1);
    expect(sizes.board.right).toBeLessThanOrEqual(sizes.width + 1);
  }
}

async function landscapeBoardFitsViewport(page) {
  const measure = () => {
    const board = document.getElementById('board').getBoundingClientRect();
    const frame = document
      .querySelector('.board-frame')
      .getBoundingClientRect();
    const rectangles = [
      board,
      ...[...document.querySelectorAll('.goal-edge')].map((edge) =>
        edge.getBoundingClientRect(),
      ),
    ];
    const top = Math.min(...rectangles.map((rectangle) => rectangle.top));
    const bottom = Math.max(...rectangles.map((rectangle) => rectangle.bottom));
    return {
      top,
      bottom,
      height: bottom - top,
      viewportHeight: innerHeight,
      centerOffset: Math.abs(
        (board.left + board.right) / 2 - (frame.left + frame.right) / 2,
      ),
    };
  };
  const initial = await page.evaluate(measure);
  // Include the physical goal strips, rather than hiding a tall board in a full-page image.
  expect(initial.height).toBeLessThanOrEqual(initial.viewportHeight);
  expect(initial.centerOffset).toBeLessThan(1);
  await page.evaluate(({ top, height, viewportHeight }) => {
    window.scrollTo({
      top: scrollY + top - (viewportHeight - height) / 2,
      behavior: 'instant',
    });
  }, initial);
  const visible = await page.evaluate(measure);
  expect(visible.top).toBeGreaterThanOrEqual(-1);
  expect(visible.bottom).toBeLessThanOrEqual(visible.viewportHeight + 1);
}

test.beforeEach(async ({ page }) => {
  const failures = [];
  errors.set(page, failures);
  page.on('pageerror', (error) => failures.push(error.message));
  await page.goto('./');
  await expect(page.locator('#app-version')).toHaveText(/^v\d+\.\d+\.\d+$/);
  await page.locator('#theme').selectOption('dark');
  await page.locator('#language').selectOption('en');
});

test.afterEach(async ({ page }) => {
  expect(errors.get(page)).toEqual([]);
});

test('menu, safe Unicode names, appearance, localization and credits work under the Pages path', async ({
  page,
}, testInfo) => {
  expect(new URL(page.url()).pathname).toBe('/Game-Quoridor/');
  await screenshot(page, 'web-menu-en-dark', testInfo);
  await page.locator('#mode').selectOption('LOCAL4');
  await page.locator('#name-settings > summary').click();
  await page.locator('#name-0').fill('<img src=x onerror=x>');
  await page.locator('#name-1').fill('سارا 🎮');
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.locator('#name-0')).toHaveValue('<img src=x onerror=x>');
  await expect(page.locator('#mode')).toHaveValue('LOCAL4');
  await page.locator('#start-game').click();
  await expect(page.locator('#wall-counts bdi').first()).toHaveText(
    '<img src=x onerror=x>',
  );
  await expect(page.locator('#wall-counts img')).toHaveCount(0);
  await page.locator('#about-toggle').click();
  await expect(page.locator('#about-dialog')).toBeVisible();
  await expect(page.locator('#about-dialog')).toContainText(
    'سیدمحمدصالح قطبانی',
  );
  await expect(page.locator('#about-dialog')).toContainText('محمدجواد بنان');
  await expect(page.locator('#about-dialog')).toContainText('امیرمحمد خسروی');
  await expect(page.locator('#license-text')).toContainText('MIT License');
  await page.locator('#about-close').click();
  await noOverflow(page);
});

test('direct moves and both wall orientations need no tool selection or confirmation', async ({
  page,
}, testInfo) => {
  await start(page);
  await expect(
    page.locator('#tool-horizontal, #tool-vertical, #tool-move, #confirm-wall'),
  ).toHaveCount(0);
  await cell(page, 7, 4).click();
  await cell(page, 1, 4).click();
  await turn(page, 0);
  const beforeWall = await position(page);
  await expect(page.locator('#board .wall-anchor')).toHaveCount(128);
  await anchor(page, 5, 3).hover();
  await expect(page.locator('#board .hover-wall')).toBeVisible();
  expect(await position(page)).toEqual(beforeWall);
  await anchor(page, 5, 3).click();
  await turn(page, 1);
  await expect(page.locator('.placed-wall')).toHaveCount(1);
  const beforeInvalid = await position(page);
  await anchor(page, 5, 3, 'VERTICAL').hover();
  await expect(page.locator('#board .hover-wall')).toHaveClass(/invalid/);
  await anchor(page, 5, 3, 'VERTICAL').click({ force: true });
  expect(await position(page)).toEqual(beforeInvalid);
  await anchor(page, 2, 5, 'VERTICAL').click();
  await expect(page.locator('.placed-wall')).toHaveCount(2);
  const withWalls = await position(page);
  for (let player = 0; player < 2; player++) {
    await expect(
      page
        .locator('.player-stat')
        .nth(player)
        .locator('.wall-reserve .available'),
    ).toHaveCount(withWalls.wallsRemaining[player]);
  }
  await expect(page.locator('#board .pawn.current')).toHaveCount(1);
  await expect(page.locator('#board-instruction')).not.toHaveText('directPlay');
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
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  expect(await position(page)).toEqual(withWalls);
  expect((await cell(page, 0, 0).boundingBox()).x).toBeLessThan(
    (await cell(page, 0, 8).boundingBox()).x,
  );
  await expect(page.locator('#wall-counts')).toContainText('۹');
  await screenshot(page, 'web-game-fa-light', testInfo);
  await noOverflow(page);
});

test('four-player clockwise turns preserve goals, inventory and physical board orientation', async ({
  page,
}, testInfo) => {
  await start(page, 'LOCAL4');
  await expect(page.locator('.pawn')).toHaveCount(4);
  await expect(page.locator('.goal-edge')).toHaveCount(4);
  await expect(page.locator('#wall-counts .player-stat')).toHaveCount(4);
  expect((await position(page)).wallsRemaining).toEqual([5, 5, 5, 5]);
  for (const [player, row, col, next] of [
    [0, 7, 4, 2],
    [2, 4, 1, 1],
    [1, 1, 4, 3],
    [3, 4, 7, 0],
  ]) {
    await turn(page, player);
    await cell(page, row, col).click();
    await pawn(page, player, row, col);
    await turn(page, next);
  }
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await expect(page.locator('#wall-counts')).toContainText('۵');
  // Goal colors denote the destination, not the edge where that pawn starts.
  const goals = await page.evaluate(() => {
    const color = (selector, property) =>
      getComputedStyle(document.querySelector(selector))[property];
    return {
      left: color('.goal-left', 'backgroundColor'),
      right: color('.goal-right', 'backgroundColor'),
      red: color('.player-stat:nth-child(3) .swatch', 'color'),
      gold: color('.player-stat:nth-child(4) .swatch', 'color'),
    };
  });
  expect(goals.left).toBe(goals.gold);
  expect(goals.right).toBe(goals.red);
  await screenshot(page, 'web-four-player-fa-light', testInfo);
  await noOverflow(page);
});

for (const difficulty of ['EASY', 'MEDIUM', 'HARD']) {
  test(`real ${difficulty} worker answers three turns and paired undo/redo restores the match`, async ({
    page,
  }, testInfo) => {
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
    if (difficulty === 'HARD')
      await screenshot(page, 'web-robot-hard-en-dark', testInfo);
  });
}

test('undo during a pending robot reply cancels stale work while settings remain usable', async ({
  page,
  context,
}) => {
  await context.route('**/src/bot-worker.js', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 350));
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

test('recovery, portable export/import, and invalid UTF-8 import preserve a real match', async ({
  page,
}, testInfo) => {
  await page.locator('#mode').selectOption('LOCAL2');
  await page.locator('#name-settings > summary').click();
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
  await page.locator('#save-file-input').setInputFiles({
    name: 'bad.qsave',
    mimeType: 'text/plain',
    buffer: Buffer.from([0xc3, 0x28]),
  });
  await expect(page.locator('#notice')).toHaveAttribute(
    'data-message',
    'saveError',
  );
  await page.locator('#continue-game').click();
  expect(await position(page)).toEqual(saved);
  await page.locator('#new-game').click();
  await page.locator('#save-file-input').setInputFiles({
    name: 'valid.qsave',
    mimeType: 'text/plain',
    buffer: exported,
  });
  await expect(page.locator('#game-view')).toBeVisible();
  expect(await position(page)).toEqual(saved);
  await expect(page.locator('#wall-counts')).toContainText('سارا 🎮');
});

test('keyboard controls and narrow/landscape layouts retain an accessible square board', async ({
  page,
}, testInfo) => {
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
  for (const viewport of [
    { width: 320, height: 740 },
    { width: 390, height: 844 },
    { width: 568, height: 320 },
    { width: 844, height: 390 },
  ]) {
    await page.setViewportSize(viewport);
    await noOverflow(page);
    await expect(page.locator('#board')).toHaveAttribute('role', 'grid');
    await expect(page.locator('.cell')).toHaveCount(81);
    await expect(page.locator('#board')).not.toHaveAttribute(
      'aria-label',
      'Quoridor board',
    );
    const bounds = await anchor(page, 3, 3).boundingBox();
    expect(bounds.height).toBeGreaterThanOrEqual(14);
    if (viewport.width > viewport.height)
      await landscapeBoardFitsViewport(page);
  }
  await screenshot(page, 'web-landscape-fa', testInfo, false);
});

test('web menu exposes only local play and robots, with no server-link entry', async ({
  page,
}) => {
  await expect(
    page.locator(
      '#online-form, #server-url, #online-create, #online-find, #online-join',
    ),
  ).toHaveCount(0);
  await expect(page.locator('#mode option')).toHaveCount(3);
  await expect(page.locator('#name-settings')).not.toHaveAttribute('open', '');
});

test('five hands-on lessons teach move, wall, jump, sidestep and win without changing a live match', async ({
  page,
}, testInfo) => {
  await start(page);
  await cell(page, 7, 4).click();
  const live = await position(page);
  await page.locator('#tutorial-game').click();
  await expect(page.locator('#tutorial-dialog')).toBeVisible();
  const practice = page.locator('#lesson-board');
  for (let lesson = 0; lesson < 5; lesson++) {
    await expect(page.locator('#lesson-progress')).toHaveText(
      lesson + 1 + ' / 5',
    );
    await expect(page.locator('#lesson-next')).toBeDisabled();
    if (lesson === 1) {
      await page.locator('#lesson-back').click();
      await expect(page.locator('#lesson-progress')).toHaveText('1 / 5');
      await expect(page.locator('#lesson-next')).toBeDisabled();
      await practice.locator('.lesson-target').click();
      await page.locator('#lesson-next').click();
    }
    if (lesson === 0)
      await screenshot(page, 'web-tutorial-en-dark', testInfo, false);
    await practice.locator('.lesson-target').click();
    await expect(page.locator('#lesson-next')).toBeEnabled();
    if (lesson === 4)
      await expect(practice.locator('.pawn[data-player="0"]')).toHaveCount(1);
    expect(await position(page)).toEqual(live);
    await page.locator('#lesson-next').click();
  }
  await expect(page.locator('#tutorial-dialog')).not.toBeVisible();
  expect(await position(page)).toEqual(live);
  await page.locator('#undo').click();
  await pawn(page, 0, 8, 4);
  await page.locator('#language').selectOption('fa');
  await page.locator('#theme').selectOption('light');
  await page.locator('#tutorial-game').click();
  await expect(page.locator('#lesson-title')).toHaveText('مسیرت را پیدا کن');
  await expect(page.locator('#lesson-progress')).toHaveText('۱ / ۵');
  await screenshot(page, 'web-tutorial-fa-light', testInfo, false);
  await page.keyboard.press('Escape');
  await expect(page.locator('#tutorial-dialog')).not.toBeVisible();
});

test('keyboard places both wall orientations directly and keeps physical arrows under RTL', async ({
  page,
}) => {
  await start(page);
  await page.locator('#language').selectOption('fa');
  await anchor(page, 3, 3).focus();
  await page.keyboard.press('ArrowRight');
  await expect(anchor(page, 3, 4)).toBeFocused();
  await page.keyboard.press('Enter');
  await turn(page, 1);
  await anchor(page, 2, 2, 'VERTICAL').focus();
  await page.keyboard.press('ArrowDown');
  await expect(anchor(page, 3, 2, 'VERTICAL')).toBeFocused();
  await page.keyboard.press('Space');
  const snapshot = await position(page);
  expect(snapshot.walls).toHaveLength(2);
  expect(snapshot.walls).toEqual(
    expect.arrayContaining([
      { row: 3, col: 4, orientation: 'HORIZONTAL' },
      { row: 3, col: 2, orientation: 'VERTICAL' },
    ]),
  );
  await expect(page.locator('#board .cell.legal')).not.toHaveCount(0);
});

for (const count of [2, 4])
  test(
    'a finished ' +
      count +
      '-player round has an immediate replay preserving names and mode',
    async ({ page }, testInfo) => {
      const state = new GameState(count);
      const snapshot = state.snapshot();
      snapshot.players[0] = { row: 1, col: 4 };
      snapshot.players[1] = { row: 7, col: 4 };
      const names = ['Winner 🎮', 'Other', 'Red', 'Gold'].slice(0, count);
      const config = {
        playerNames: names,
        mode: count === 2 ? 'BOT' : 'LOCAL',
        difficulty: 'HARD',
      };
      const saved = encodeSave({
        state: GameState.fromSnapshot(snapshot),
        config,
      });
      await page.locator('#save-file-input').setInputFiles({
        name: 'finish.qsave',
        mimeType: 'text/plain',
        buffer: Buffer.from(saved),
      });
      await cell(page, 0, 4).click();
      await expect(page.locator('#turn-status')).toHaveAttribute(
        'data-winner',
        '0',
      );
      await expect(page.locator('#replay-game')).toBeVisible();
      await expect(
        page.locator('#board .cell.legal, #board .wall-anchor'),
      ).toHaveCount(0);
      await screenshot(page, 'web-replay-' + count, testInfo);
      await page.locator('#replay-game').click();
      await expect(page.locator('#replay-game')).toBeHidden();
      const recovery = decodeSave(
        await page.evaluate((key) => localStorage.getItem(key), recoveryKey),
      );
      expect(recovery.state.snapshot()).toEqual(
        new GameState(count).snapshot(),
      );
      expect(recovery.config).toEqual(config);
      await expect(page.locator('#undo')).toBeDisabled();
      await expect(page.locator('#redo')).toBeDisabled();
    },
  );
