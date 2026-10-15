package Board;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable English-source/Persian translation catalog. */
final class TranslationCatalog {
    private TranslationCatalog() {}

    static Map<String, String> persian() {
        String[][] entries = {
            {"Quoridor — Your next move", "کوریدور — حرکت بعدی شما"},
            {"QUORIDOR", "کوریدور"},
            {"QUORIDOR  /  THE STRATEGY CLASSIC", "کوریدور / بازی کلاسیک استراتژی"},
            {"Your next", "حرکت بعدی شما،"},
            {"great move.", "یک حرکت عالی."},
            {"A simple race. A world of possibilities.", "مسابقه‌ای ساده با دنیایی از انتخاب‌ها."},
            {"MOVE YOUR PAWN. SHAPE THEIR PATH.", "مهره را حرکت دهید؛ مسیر حریف را شکل دهید."},
            {"Reach the far edge before your opponent.", "پیش از حریف به سمت دیگر صفحه برسید."},
            {
                "Place walls, but always leave a way through.",
                "دیوار بگذارید، اما راه عبور را باز نگه دارید."
            },
            {"LET'S PLAY", "آمادهٔ بازی"},
            {"Make it your game.", "بازی را به دلخواه تنظیم کنید."},
            {"Two players", "دو بازیکن"},
            {"Play a robot", "بازی با ربات"},
            {"BLUE PLAYER", "بازیکن آبی"},
            {"GREEN PLAYER", "بازیکن سبز"},
            {"YOUR OPPONENT", "حریف شما"},
            {"ROBOT DIFFICULTY", "درجهٔ سختی ربات"},
            {"Robot difficulty", "درجهٔ سختی ربات"},
            {"Blue player name", "نام بازیکن آبی"},
            {"Green player name", "نام بازیکن سبز"},
            {
                "Blue moves first. Each player has 10 walls.",
                "آبی شروع می‌کند. هر بازیکن ۱۰ دیوار دارد."
            },
            {"Start a new match  →", "شروع بازی جدید ←"},
            {"Load a saved match", "بارگذاری بازی ذخیره‌شده"},
            {"Resume your match", "ادامهٔ بازی"},
            {"You play blue. The robot plays green.", "شما آبی هستید و ربات سبز است."},
            {"A friendly rivalry, on the same screen.", "رقابتی دوستانه روی یک صفحه."},
            {"Blue", "آبی"},
            {"Green", "سبز"},
            {"Robot", "ربات"},
            {"Save", "ذخیره"},
            {"Load", "بارگذاری"},
            {"Main menu", "منوی اصلی"},
            {"CURRENT TURN", "نوبت فعلی"},
            {"PLACE A WALL", "گذاشتن دیوار"},
            {"Horizontal", "افقی"},
            {"Vertical", "عمودی"},
            {"horizontal", "افقی"},
            {"vertical", "عمودی"},
            {
                "Hover a small slot to preview your wall. Click a glowing square to move.",
                "برای پیش‌نمایش دیوار، نشانگر را روی نقطهٔ بین خانه‌ها ببرید. برای حرکت، روی خانهٔ روشن کلیک کنید."
            },
            {"New game", "بازی جدید"},
            {
                "Use player names between 1 and 24 characters.",
                "نام بازیکن باید بین ۱ تا ۲۴ نویسه باشد."
            },
            {"Check player names", "بررسی نام بازیکنان"},
            {
                "That wall cannot be placed. Both players need a route to their goal.",
                "این دیوار مجاز نیست. هر دو بازیکن باید راهی به مقصد داشته باشند."
            },
            {
                "The robot played a safe move. Your turn.",
                "ربات یک حرکت مجاز انجام داد. نوبت شماست."
            },
            {"Save your match", "ذخیرهٔ بازی"},
            {"Replace the saved match “%s”?", "بازی ذخیره‌شدهٔ «%s» جایگزین شود؟"},
            {"Replace saved match", "جایگزینی بازی ذخیره‌شده"},
            {
                "Match saved to %s. You can continue playing.",
                "بازی در %s ذخیره شد. می‌توانید بازی را ادامه دهید."
            },
            {"Couldn't save this match", "ذخیرهٔ بازی انجام نشد"},
            {
                "Choose a writable folder and try again.",
                "پوشه‌ای با امکان نوشتن انتخاب کنید و دوباره تلاش کنید."
            },
            {"Couldn't load this match", "بارگذاری بازی انجام نشد"},
            {
                "Choose a valid Quoridor .qsave or compatible old .G3M file.",
                "یک فایل معتبر کوریدور با پسوند \u2066.qsave\u2069 یا فایل قدیمی سازگار \u2066.G3M\u2069 انتخاب کنید."
            },
            {"Quoridor matches (*.qsave, *.G3M)", "بازی‌های کوریدور (\u2066*.qsave, *.G3M\u2069)"},
            {"Your current match is still available.", "بازی فعلی شما همچنان در دسترس است."},
            {"%s to move", "نوبت حرکت %s"},
            {"%s wins!", "%s برنده شد!"},
            {"%s is thinking…", "%s در حال فکر کردن است…"},
            {"%d walls remaining", "%d دیوار باقی مانده"},
            {"LOCAL TWO-PLAYER MATCH", "بازی دونفره"},
            {"VERSUS ROBOT  /  %s", "بازی با ربات / %s"},
            {"EASY", "آسان"},
            {"MEDIUM", "متوسط"},
            {"HARD", "سخت"},
            {"Easy", "آسان"},
            {"Medium", "متوسط"},
            {"Hard", "سخت"},
            {
                "%s reached the far edge. Choose New game for another match.",
                "%s به سمت دیگر صفحه رسید. برای بازی دوباره، «بازی جدید» را انتخاب کنید."
            },
            {"The robot is considering its next move…", "ربات در حال بررسی حرکت بعدی است…"},
            {
                "Click a glowing square to move, or choose a wall direction and click a slot.",
                "برای حرکت، روی خانهٔ روشن کلیک کنید؛ یا جهت دیوار را انتخاب کنید و روی نقطهٔ بین خانه‌ها بزنید."
            },
            {"%s pawn", "مهرهٔ %s"},
            {"Move here", "حرکت به این خانه"},
            {"Square", "خانه"},
            {"%s (%d, %d)", "%s (%d، %d)"},
            {"Place %s wall (%d, %d)", "گذاشتن دیوار %s (%d، %d)"},
            {"Easy — a relaxed challenge", "آسان — رقابتی آرام"},
            {"Medium — a worthy opponent", "متوسط — حریفی توانمند"},
            {"Hard — think a few moves ahead", "سخت — چند حرکت جلوتر فکر کنید"},
            {"Goal: top edge ↑", "مقصد: لبهٔ بالا ↑"},
            {"Goal: bottom edge ↓", "مقصد: لبهٔ پایین ↓"},
            {"BLUE'S GOAL", "مقصد آبی"},
            {"GREEN'S GOAL", "مقصد سبز"},
            {"Language", "زبان"},
            {"Appearance", "ظاهر"},
            {"Dark", "تاریک"},
            {"Light", "روشن"},
            {"English", "English"},
            {"فارسی", "فارسی"},
            {"OK", "تأیید"},
            {"Yes", "بله"},
            {"No", "خیر"},
            {"Cancel", "لغو"},
            {"Open", "باز کردن"},
            {"Help", "راهنما"},
            {"File name:", "نام فایل:"},
            {"Files of type:", "نوع فایل:"},
            {"Look in:", "جست‌وجو در:"},
            {"Save in:", "ذخیره در:"},
            {"All files", "همهٔ فایل‌ها"},
            {"File chooser", "انتخاب فایل"},
            {"Files List", "فهرست فایل‌ها"},
            {"Files Details", "جزئیات فایل‌ها"},
            {"Up one level", "یک پوشه بالاتر"},
            {"Home", "پوشهٔ اصلی"},
            {"Create new folder", "ساخت پوشهٔ جدید"},
            {"List", "فهرست"},
            {"Details", "جزئیات"},
            {"Name", "نام"},
            {"Size", "اندازه"},
            {"Type", "نوع"},
            {"Date modified", "تاریخ تغییر"},
            {"Folder", "پوشه"},
            {"New folder", "پوشهٔ جدید"},
            {"New Folder", "پوشهٔ جدید"},
            {"Open selected file", "باز کردن فایل انتخاب‌شده"},
            {"Save selected file", "ذخیرهٔ فایل انتخاب‌شده"},
            {"Cancel file chooser", "لغو انتخاب فایل"},
            {"Update directory listing", "به‌روزرسانی فهرست پوشه"},
            {"Refresh", "نوسازی"},
            {"View", "نمایش"},
            {"File", "فایل"},
            {"Open folder", "باز کردن پوشه"},
            {"Update", "به‌روزرسانی"},
            {"File chooser help", "راهنمای انتخاب فایل"},
            {"FileChooser help", "راهنمای انتخاب فایل"},
            {"Open selected directory", "باز کردن پوشهٔ انتخاب‌شده"},
            {"Abort file chooser dialog", "لغو انتخاب فایل"},
            {"Modified", "آخرین تغییر"},
            {"Attributes", "ویژگی‌ها"},
            {"Look In:", "جست‌وجو در:"},
            {"Save In:", "ذخیره در:"},
            {"File Name:", "نام فایل:"},
            {"Files of Type:", "نوع فایل:"},
            {"All Files", "همهٔ فایل‌ها"},
            {"Up One Level", "یک پوشه بالاتر"},
            {"Create New Folder", "ساخت پوشهٔ جدید"},
            {"Cannot rename {0}", "تغییر نام {0} ممکن نیست"},
            {"Error Renaming File or Folder", "خطا در تغییر نام فایل یا پوشه"},
            {"Error creating new folder", "خطا در ساخت پوشهٔ جدید"},
            {
                "Cannot rename {0}: A file with the name you specified already exists. Specify a different file name.",
                "تغییر نام {0} ممکن نیست: فایلی با این نام وجود دارد. نام دیگری انتخاب کنید."
            },
            {"Unable to create folder", "ساخت پوشه ممکن نیست"},
            {
                "Unable to create the folder.\n\nThe system cannot find the path specified.",
                "ساخت پوشه ممکن نیست.\n\nسیستم مسیر مشخص‌شده را پیدا نمی‌کند."
            }
        };
        Map<String, String> catalog = new LinkedHashMap<>();
        for (String[] entry : entries) {
            if (catalog.put(entry[0], entry[1]) != null
                    || entry[1].isBlank()
                    || !TranslationFormat.sameArguments(entry[0], entry[1])) {
                throw new IllegalStateException("Invalid translation catalog entry: " + entry[0]);
            }
        }
        return Collections.unmodifiableMap(catalog);
    }
}
