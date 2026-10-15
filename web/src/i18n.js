const messages = {
  en: {
    skip: "Skip to game",
    webEdition: "WEB EDITION",
    language: "Language",
    appearance: "Appearance",
    dark: "Dark",
    light: "Light",
    about: "About",
    close: "Close",
    strategy: "A GAME OF PATHS & POSSIBILITIES",
    welcome: "Find your way.\nShape theirs.",
    intro: "Reach the opposite edge. A clever wall can change everything.",
    localTitle: "Play on this device",
    onlineTitle: "Meet across the board",
    gameMode: "Game mode",
    vsBot: "Play against a robot",
    local2: "Two players",
    local4: "Four players",
    difficulty: "Robot strength",
    easy: "Easy",
    medium: "Medium",
    hard: "Hard",
    blue: "Blue",
    green: "Green",
    red: "Red",
    gold: "Gold",
    start: "Start game",
    continue: "Continue saved match",
    load: "Load a save file",
    onlineIntro:
      "Create a private room or find opponents on your shared game server.",
    server: "Server address",
    yourName: "Your name",
    players: "Players",
    createRoom: "Create room",
    findMatch: "Find opponents",
    roomCode: "Room code",
    joinRoom: "Join room",
    serverNote:
      "Online play needs a separate server. No public service is preconfigured.",
    howTo: "How to play",
    rulesMove:
      "Move one square towards your goal edge, or place a wall. Jump over an adjacent pawn when the square behind is free; when the jump is blocked, move diagonally around it.",
    rulesWall:
      "Walls span two squares. They cannot overlap or cross, and must leave every player a path to their goal. Two players get 10 walls each; four players get 5.",
    rulesGoal:
      "Blue aims north, Green south, Red east, Gold west. Four-player turns run Blue → Red → Green → Gold.",
    menu: "Menu",
    save: "Save file",
    leave: "Leave room",
    retry: "Retry connection",
    yourMove: "Your move",
    movePawn: "Move pawn",
    horizontal: "Horizontal wall",
    vertical: "Vertical wall",
    confirmWall: "Place wall",
    cancel: "Cancel",
    undo: "Undo",
    redo: "Redo",
    hint: "Hint",
    history: "Move history",
    madeBy: "Original development team",
    authors: "Authors",
    aboutText: "An open-source Quoridor game for the web, Android and desktop.",
    turn: "{name} to play",
    won: "{name} wins!",
    thinking: "Robot is thinking…",
    wallStock: "{count} walls",
    tapMove: "Choose a highlighted square. Arrow keys navigate the board.",
    tapWall: "Choose a wall anchor, then confirm the preview.",
    validWall: "This wall is legal. Confirm to place it.",
    invalidWall: "This wall would overlap, cross or block a route.",
    waitingTurn: "Waiting for your opponent…",
    waitingRoom: "Waiting for players…",
    closedRoom: "This room has closed.",
    roomInfo: "Room {code} · {joined}/{total} players · your seat {seat}",
    connecting: "Connecting to the server…",
    onlineMatch: "Online · {count} players",
    localMatch: "Local · {count} players",
    botMatch: "Robot · {level}",
    imported: "Saved game loaded.",
    saved: "Save file exported.",
    saveError:
      "The save file is invalid or unsupported. Your current game is unchanged.",
    storageError:
      "Browser storage is unavailable. Use Save file to keep this match.",
    emptyHistory: "No moves yet.",
    moved: "{name}: square {row}, {col}",
    placed: "{name}: {orientation}, {row}, {col}",
    cell: "Row {row}, column {col}",
    pawnCell: "{name}, row {row}, column {col}",
    legalCell: "Legal move, row {row}, column {col}",
    wallAnchor: "{orientation}, row {row}, column {col}",
    boardLabel: "Quoridor board",
    hintNotice: "A legal move along a shortest route is highlighted.",
    botError: "The robot could not finish. Retry its turn.",
    networkError:
      "Cannot reach the server. Check its HTTPS address, connection and allowed browser origin (CORS).",
    roomError:
      "The room request was rejected. Check the code, room status and player count.",
    nameError: "Names must contain 1–24 characters without control characters.",
    codeError: "Enter an eight-character room code.",
    waitingName: "Waiting…",
    reconnecting:
      "Connection lost. Input is paused; retry when the server is available.",
    localOnly: "Undo, redo and hints are available in local matches.",
    noSave: "No saved match is available.",
    onlineLeft: "You left the online room.",
    confirmLeave:
      "Leave the current online room? This closes an active match for everyone.",
    namePlaceholder: "Player name",
    botRetry: "Retry robot",
    home: "Quoridor home",
    moveTools: "Move tools",
  },
  fa: {
    skip: "رفتن به بازی",
    webEdition: "نسخهٔ وب",
    language: "زبان",
    appearance: "ظاهر",
    dark: "تاریک",
    light: "روشن",
    about: "درباره",
    close: "بستن",
    strategy: "بازی مسیرها و انتخاب‌ها",
    welcome: "مسیرت را پیدا کن.\nمسیر حریف را تغییر بده.",
    intro: "به لبهٔ مقابل برس. یک دیوار هوشمندانه می‌تواند همه‌چیز را عوض کند.",
    localTitle: "بازی روی این دستگاه",
    onlineTitle: "رقابت از راه دور",
    gameMode: "حالت بازی",
    vsBot: "بازی با ربات",
    local2: "دو بازیکن",
    local4: "چهار بازیکن",
    difficulty: "قدرت ربات",
    easy: "آسان",
    medium: "متوسط",
    hard: "قوی",
    blue: "آبی",
    green: "سبز",
    red: "قرمز",
    gold: "طلایی",
    start: "شروع بازی",
    continue: "ادامهٔ بازی ذخیره‌شده",
    load: "بارگذاری فایل بازی",
    onlineIntro: "در سرور مشترک بازی، اتاق خصوصی بساز یا حریف پیدا کن.",
    server: "نشانی سرور",
    yourName: "نام شما",
    players: "بازیکنان",
    createRoom: "ساخت اتاق",
    findMatch: "یافتن حریف",
    roomCode: "کد اتاق",
    joinRoom: "ورود به اتاق",
    serverNote:
      "بازی آنلاین به سرور جداگانه نیاز دارد. سرور عمومی از قبل تنظیم نشده است.",
    howTo: "روش بازی",
    rulesMove:
      "یک خانه به سوی لبهٔ هدف حرکت کن یا دیوار بگذار. از روی مهرهٔ مجاور بپر؛ اگر پشت آن بسته باشد، می‌توانی به صورت مورب از کنار آن رد شوی.",
    rulesWall:
      "دیوار به اندازهٔ دو خانه است. دیوارها نباید هم‌پوشانی یا تقاطع داشته باشند و باید برای همهٔ بازیکنان مسیر باز بماند. در بازی دونفره هر نفر ۱۰ دیوار و در بازی چهارنفری ۵ دیوار دارد.",
    rulesGoal:
      "هدف آبی لبهٔ بالا، سبز پایین، قرمز راست و طلایی چپ است. ترتیب نوبت چهارنفری: آبی ← قرمز ← سبز ← طلایی.",
    menu: "منو",
    save: "ذخیرهٔ فایل",
    leave: "خروج از اتاق",
    retry: "تلاش دوباره",
    yourMove: "حرکت شما",
    movePawn: "حرکت مهره",
    horizontal: "دیوار افقی",
    vertical: "دیوار عمودی",
    confirmWall: "قرار دادن دیوار",
    cancel: "لغو",
    undo: "برگشت",
    redo: "تکرار",
    hint: "راهنما",
    history: "تاریخچهٔ حرکت‌ها",
    madeBy: "توسعه‌دهندگان اولیه",
    authors: "سازندگان",
    aboutText: "بازی متن‌باز کوریدور برای وب، اندروید و رایانه.",
    turn: "نوبت {name} است",
    won: "{name} برنده شد!",
    thinking: "ربات در حال فکر کردن است…",
    wallStock: "{count} دیوار",
    tapMove:
      "یک خانهٔ مشخص‌شده را انتخاب کن. با کلیدهای جهت هم می‌توانی حرکت کنی.",
    tapWall: "محل دیوار را انتخاب و سپس پیش‌نمایش را تأیید کن.",
    validWall: "این دیوار مجاز است؛ برای قرار دادن آن تأیید کن.",
    invalidWall: "این دیوار هم‌پوشانی یا تقاطع دارد یا مسیر را می‌بندد.",
    waitingTurn: "منتظر حرکت حریف…",
    waitingRoom: "منتظر پیوستن بازیکنان…",
    closedRoom: "این اتاق بسته شده است.",
    roomInfo: "اتاق {code} · {joined} از {total} بازیکن · جایگاه شما {seat}",
    connecting: "در حال اتصال به سرور…",
    onlineMatch: "آنلاین · {count} نفر",
    localMatch: "محلی · {count} نفر",
    botMatch: "ربات · {level}",
    imported: "بازی ذخیره‌شده بارگذاری شد.",
    saved: "فایل بازی صادر شد.",
    saveError: "فایل بازی نامعتبر یا پشتیبانی‌نشده است. بازی فعلی تغییر نکرد.",
    storageError:
      "حافظهٔ مرورگر در دسترس نیست؛ برای نگه‌داشتن بازی از ذخیرهٔ فایل استفاده کن.",
    emptyHistory: "هنوز حرکتی انجام نشده است.",
    moved: "{name}: خانهٔ {row}، {col}",
    placed: "{name}: {orientation}، {row}، {col}",
    cell: "ردیف {row}، ستون {col}",
    pawnCell: "{name}، ردیف {row}، ستون {col}",
    legalCell: "حرکت مجاز، ردیف {row}، ستون {col}",
    wallAnchor: "{orientation}، ردیف {row}، ستون {col}",
    boardLabel: "صفحهٔ بازی کوریدور",
    hintNotice: "یک حرکت مجاز در مسیر کوتاه‌تر مشخص شد.",
    botError: "ربات نتوانست حرکت کند. نوبت ربات را دوباره اجرا کن.",
    networkError:
      "اتصال به سرور ممکن نیست. نشانی HTTPS، اتصال و مجازبودن مبدأ مرورگر در CORS را بررسی کن.",
    roomError:
      "درخواست اتاق پذیرفته نشد. کد، وضعیت اتاق و تعداد نفرات را بررسی کن.",
    nameError: "نام‌ها باید ۱ تا ۲۴ نویسه و بدون نویسه‌های کنترلی باشند.",
    codeError: "کد هشت‌نویسه‌ای اتاق را وارد کن.",
    waitingName: "در انتظار…",
    reconnecting:
      "ارتباط قطع شد. حرکت متوقف است؛ پس از دسترس‌پذیرشدن سرور دوباره تلاش کن.",
    localOnly: "برگشت، تکرار و راهنما در بازی محلی فعال‌اند.",
    noSave: "بازی ذخیره‌شده‌ای وجود ندارد.",
    onlineLeft: "از اتاق آنلاین خارج شدی.",
    confirmLeave:
      "از اتاق آنلاین خارج می‌شوی؟ خروج، بازی فعال را برای همه می‌بندد.",
    namePlaceholder: "نام بازیکن",
    botRetry: "تلاش دوبارهٔ ربات",
    home: "صفحهٔ اصلی کوریدور",
    moveTools: "ابزار حرکت",
  },
};

let language = "en";
export const playerColors = ["blue", "green", "red", "gold"];
export function setLanguage(value) {
  language = value === "fa" ? "fa" : "en";
}
export function currentLanguage() {
  return language;
}
export function number(value) {
  return new Intl.NumberFormat(language === "fa" ? "fa-IR" : "en-US", {
    useGrouping: false,
  }).format(value);
}
export function t(key, values = {}) {
  const message = messages[language][key] ?? messages.en[key] ?? key;
  return message.replace(/\{(\w+)\}/g, (_, field) =>
    String(values[field] ?? ""),
  );
}
export function translatePage() {
  document.documentElement.lang = language;
  document.documentElement.dir = language === "fa" ? "rtl" : "ltr";
  document.querySelectorAll("[data-i18n]").forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  document.querySelectorAll("[data-placeholder]").forEach((element) => {
    element.placeholder = t(element.dataset.placeholder);
  });
  document.getElementById("language").setAttribute("aria-label", t("language"));
  document.getElementById("theme").setAttribute("aria-label", t("appearance"));
  document.querySelector(".brand").setAttribute("aria-label", t("home"));
  document
    .querySelector(".tool-grid")
    .setAttribute("aria-label", t("moveTools"));
}
