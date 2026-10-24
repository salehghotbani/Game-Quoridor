const messages = {
  en: {
    skip: 'Skip to game',
    language: 'Language',
    appearance: 'Appearance',
    dark: 'Dark',
    light: 'Light',
    about: 'About',
    close: 'Close',
    intro: 'Reach the opposite edge.',
    names: 'Player names',
    learn: 'Learn by playing',
    playAgain: 'Play again',
    back: 'Back',
    next: 'Next',
    done: 'Done',
    wellDone: 'Nice!',
    lessonTitle0: 'Find your path',
    lessonPrompt0: 'Click the glowing square. Your goal is the blue edge.',
    lessonTitle1: 'Change their path',
    lessonPrompt1:
      'Click the glowing gap to place a two-square wall. Leave a path open.',
    lessonTitle2: 'Jump a pawn',
    lessonPrompt2: 'Click beyond the other pawn to jump over it.',
    lessonTitle3: 'Go around',
    lessonPrompt3: 'A blocked jump? Step around the pawn.',
    lessonTitle4: 'Reach the edge',
    lessonPrompt4: 'One last move to the blue edge. You win!',
    gameMode: 'Game mode',
    vsBot: 'Play against a robot',
    local2: 'Two players',
    local4: 'Four players',
    difficulty: 'Robot strength',
    easy: 'Easy',
    medium: 'Medium',
    hard: 'Hard',
    blue: 'Blue',
    green: 'Green',
    red: 'Red',
    gold: 'Gold',
    start: 'Start game',
    continue: 'Continue',
    load: 'Load file',
    howTo: 'How to play',
    menu: 'Menu',
    save: 'Save file',
    horizontal: 'Horizontal wall',
    vertical: 'Vertical wall',
    undo: 'Undo',
    redo: 'Redo',
    hint: 'Hint',
    history: 'Move history',
    authors: 'Authors',
    aboutText: 'An open-source Quoridor game for the web, Android and desktop.',
    turn: '{name} to play',
    won: '{name} wins!',
    thinking: 'Robot is thinking…',
    wallStock: '{count} walls',
    directPlay: 'Click a square to move. Hover a gap to place a wall.',
    localMatch: 'Local · {count} players',
    botMatch: 'Robot · {level}',
    imported: 'Saved game loaded.',
    saved: 'Save file exported.',
    saveError:
      'The save file is invalid or unsupported. Your current game is unchanged.',
    storageError:
      'Browser storage is unavailable. Use Save file to keep this match.',
    emptyHistory: 'No moves yet.',
    moved: '{name}: square {row}, {col}',
    placed: '{name}: {orientation}, {row}, {col}',
    cell: 'Row {row}, column {col}',
    pawnCell: '{name}, row {row}, column {col}',
    legalCell: 'Legal move, row {row}, column {col}',
    wallAnchor: '{orientation}, row {row}, column {col}',
    boardLabel: 'Quoridor board',
    botError: 'The robot could not finish. Retry its turn.',
    nameError: 'Names must contain 1–24 characters without control characters.',
    noSave: 'No saved match is available.',
    botRetry: 'Retry robot',
    home: 'Quoridor home',
  },
  fa: {
    skip: 'رفتن به بازی',
    language: 'زبان',
    appearance: 'ظاهر',
    dark: 'تاریک',
    light: 'روشن',
    about: 'درباره',
    close: 'بستن',
    intro: 'به لبهٔ مقابل برس.',
    names: 'نام بازیکنان',
    learn: 'با بازی یاد بگیر',
    playAgain: 'دوباره بازی کن',
    back: 'قبلی',
    next: 'بعدی',
    done: 'پایان',
    wellDone: 'آفرین!',
    lessonTitle0: 'مسیرت را پیدا کن',
    lessonPrompt0: 'روی خانهٔ درخشان کلیک کن؛ هدفت لبهٔ آبی است.',
    lessonTitle1: 'مسیر حریف را عوض کن',
    lessonPrompt1:
      'روی شیار درخشان کلیک کن؛ دیوار دو خانه است و باید راهی باز بماند.',
    lessonTitle2: 'از مهره بپر',
    lessonPrompt2: 'روی خانهٔ آن‌سوی مهره کلیک کن تا از روی آن بپری.',
    lessonTitle3: 'از کنار مهره برو',
    lessonPrompt3: 'جای پرش بسته است؛ از کنار مهره عبور کن.',
    lessonTitle4: 'به هدف برس',
    lessonPrompt4: 'یک حرکت تا لبهٔ آبی؛ برنده شو!',
    gameMode: 'حالت بازی',
    vsBot: 'بازی با ربات',
    local2: 'دو بازیکن',
    local4: 'چهار بازیکن',
    difficulty: 'قدرت ربات',
    easy: 'آسان',
    medium: 'متوسط',
    hard: 'قوی',
    blue: 'آبی',
    green: 'سبز',
    red: 'قرمز',
    gold: 'طلایی',
    start: 'شروع بازی',
    continue: 'ادامهٔ بازی',
    load: 'بارگذاری فایل',
    howTo: 'روش بازی',
    menu: 'منو',
    save: 'ذخیرهٔ فایل',
    horizontal: 'دیوار افقی',
    vertical: 'دیوار عمودی',
    undo: 'برگشت',
    redo: 'تکرار',
    hint: 'راهنما',
    history: 'تاریخچهٔ حرکت‌ها',
    authors: 'سازندگان',
    aboutText: 'بازی متن‌باز کوریدور برای وب، اندروید و رایانه.',
    turn: 'نوبت {name} است',
    won: '{name} برنده شد!',
    thinking: 'ربات در حال فکر کردن است…',
    wallStock: '{count} دیوار',
    directPlay: 'خانه را برای حرکت و شیار را برای دیوار انتخاب کن.',
    localMatch: 'محلی · {count} نفر',
    botMatch: 'ربات · {level}',
    imported: 'بازی ذخیره‌شده بارگذاری شد.',
    saved: 'فایل بازی صادر شد.',
    saveError: 'فایل بازی نامعتبر یا پشتیبانی‌نشده است. بازی فعلی تغییر نکرد.',
    storageError:
      'حافظهٔ مرورگر در دسترس نیست؛ برای نگه‌داشتن بازی از ذخیرهٔ فایل استفاده کن.',
    emptyHistory: 'هنوز حرکتی انجام نشده است.',
    moved: '{name}: خانهٔ {row}، {col}',
    placed: '{name}: {orientation}، {row}، {col}',
    cell: 'ردیف {row}، ستون {col}',
    pawnCell: '{name}، ردیف {row}، ستون {col}',
    legalCell: 'حرکت مجاز، ردیف {row}، ستون {col}',
    wallAnchor: '{orientation}، ردیف {row}، ستون {col}',
    boardLabel: 'صفحهٔ بازی کوریدور',
    botError: 'ربات نتوانست حرکت کند. نوبت ربات را دوباره اجرا کن.',
    nameError: 'نام‌ها باید ۱ تا ۲۴ نویسه و بدون نویسه‌های کنترلی باشند.',
    noSave: 'بازی ذخیره‌شده‌ای وجود ندارد.',
    botRetry: 'تلاش دوبارهٔ ربات',
    home: 'صفحهٔ اصلی کوریدور',
  },
};

let language = 'en';
export const playerColors = ['blue', 'green', 'red', 'gold'];
export function setLanguage(value) {
  language = value === 'fa' ? 'fa' : 'en';
}
export function currentLanguage() {
  return language;
}
export function number(value) {
  return new Intl.NumberFormat(language === 'fa' ? 'fa-IR' : 'en-US', {
    useGrouping: false,
  }).format(value);
}
export function t(key, values = {}) {
  const message = messages[language][key] ?? messages.en[key] ?? key;
  return message.replace(/\{(\w+)\}/g, (_, field) =>
    String(values[field] ?? ''),
  );
}
export function translatePage() {
  document.documentElement.lang = language;
  document.documentElement.dir = language === 'fa' ? 'rtl' : 'ltr';
  document.querySelectorAll('[data-i18n]').forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  document.querySelectorAll('[data-placeholder]').forEach((element) => {
    element.placeholder = t(element.dataset.placeholder);
  });
  document.getElementById('language').setAttribute('aria-label', t('language'));
  document.getElementById('theme').setAttribute('aria-label', t('appearance'));
  document.querySelector('.brand').setAttribute('aria-label', t('home'));
}
