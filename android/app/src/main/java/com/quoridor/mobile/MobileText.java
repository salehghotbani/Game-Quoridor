package com.quoridor.mobile;

import java.util.Locale;

/** The Android interface has exactly two explicit language choices. */
final class MobileText {
    enum Key {
        TAGLINE("A path to victory", "راهی به سوی پیروزی"),
        LOCAL_TITLE("Play on this device", "بازی روی این دستگاه"),
        LOCAL_HELP(
                "Race to the opposite edge. Place walls, keep a path open.",
                "به لبهٔ مقابل برسید؛ دیوار بگذارید و مسیرها را باز نگه دارید."),
        TWO("Two players", "دو بازیکن"),
        FOUR("Four players", "چهار بازیکن"),
        BOT("Play a robot", "بازی با ربات"),
        EASY("Easy", "آسان"),
        MEDIUM("Medium", "متوسط"),
        HARD("Hard", "قوی"),
        BLUE("Blue", "آبی"),
        GREEN("Green", "سبز"),
        RED("Red", "قرمز"),
        GOLD("Gold", "طلایی"),
        ROBOT("Robot", "ربات"),
        START("Start match", "شروع بازی"),
        CONTINUE("Continue saved match", "ادامهٔ بازی ذخیره‌شده"),
        IMPORT("Load a save file", "بارگذاری فایل بازی"),
        ONLINE_TITLE("Play online", "بازی آنلاین"),
        ONLINE_HELP(
                "Use the same server as your friends. Android and desktop players can share a room.",
                "نشانی سرور مشترک را وارد کنید؛ بازیکنان اندروید و رایانه می‌توانند در یک اتاق بازی کنند."),
        SERVER("Server address · HTTPS or local HTTP", "نشانی سرور · HTTPS یا HTTP محلی"),
        NAME("Your name", "نام شما"),
        ROOM_CODE("Room code", "کد اتاق"),
        CREATE("Create room", "ساخت اتاق"),
        JOIN("Join room", "ورود به اتاق"),
        RANDOM("Find random opponents", "یافتن حریف تصادفی"),
        RULES("How to play", "راهنمای بازی"),
        ABOUT("Authors & license", "سازندگان و مجوز"),
        DARK("Dark", "تاریک"),
        LIGHT("Light", "روشن"),
        MENU("Menu", "منو"),
        SAVE("Save", "ذخیره"),
        UNDO("Undo", "برگشت"),
        REDO("Redo", "تکرار"),
        HINT("Hint", "راهنما"),
        HISTORY("History", "تاریخچه"),
        MOVE("Move pawn", "حرکت مهره"),
        HORIZONTAL("Horizontal", "افقی"),
        VERTICAL("Vertical", "عمودی"),
        CONFIRM_WALL("Place selected wall", "قرار دادن دیوار انتخاب‌شده"),
        WALL_HELP(
                "Select a wall tool, then tap between four squares. Confirm the preview to place it.",
                "ابزار دیوار را انتخاب کنید، میان چهار خانه لمس کنید و پیش‌نمایش را تأیید کنید."),
        MOVE_HELP("Tap a highlighted square to move.", "برای حرکت، یک خانهٔ مشخص‌شده را لمس کنید."),
        TURN("%s to play", "نوبت %s است"),
        WINNER("%s wins!", "%s برنده شد!"),
        THINKING("Robot is thinking…", "ربات در حال فکر کردن است…"),
        WALLS("%s · %d walls", "%s · %d دیوار"),
        WAITING("Waiting for players · %d / %d", "در انتظار بازیکنان · %d از %d"),
        ROOM("Room: %s", "اتاق: %s"),
        SHARE("Share room", "اشتراک اتاق"),
        SHARE_MESSAGE(
                "Join my Quoridor room %s\nServer: %s",
                "به اتاق کوریدور من وارد شوید: %s\nسرور: %s"),
        YOU("You are %s", "شما %s هستید"),
        WAIT_TURN("Waiting for %s", "در انتظار حرکت %s"),
        CONNECTING("Connecting…", "در حال اتصال…"),
        RECONNECTING("Connection interrupted. Retrying…", "ارتباط قطع شده است؛ تلاش دوباره…"),
        CLOSED("This online room has closed.", "این اتاق آنلاین بسته شده است."),
        LEAVE("Leave room", "خروج از اتاق"),
        LEAVE_QUESTION(
                "Leave this room? The match will end for all players.",
                "از اتاق خارج می‌شوید؟ بازی برای همهٔ بازیکنان پایان می‌یابد."),
        MENU_QUESTION(
                "Return to the menu? Your local position will be saved on this device.",
                "به منو برمی‌گردید؟ وضعیت بازی محلی روی این دستگاه ذخیره می‌شود."),
        OK("OK", "تأیید"),
        CANCEL("Cancel", "لغو"),
        ERROR("Something went wrong", "خطایی رخ داد"),
        INVALID_SETUP(
                "Use a nonempty name of at most 24 characters for every player.",
                "نام هر بازیکن باید بین ۱ تا ۲۴ نویسه و بدون نویسه‌های کنترلی باشد."),
        NETWORK_ERROR(
                "Could not contact or enter the room. Check the server address, room code and network, then retry.",
                "ارتباط با اتاق ممکن نشد؛ نشانی سرور، کد اتاق و شبکه را بررسی و دوباره تلاش کنید."),
        FILE_ERROR(
                "The save could not be read or written. Choose an accessible, valid .qsave or compatible .G3M file.",
                "خواندن یا نوشتن بازی ممکن نشد؛ یک فایل معتبر و در دسترس با قالب qsave یا G3M سازگار انتخاب کنید."),
        SAVED("Game saved", "بازی ذخیره شد"),
        NO_HISTORY("No moves yet.", "هنوز حرکتی انجام نشده است."),
        MOVE_LINE("%s → row %d, column %d", "%s ← ردیف %d، ستون %d"),
        WALL_LINE("%s · %s wall at %d, %d", "%s · دیوار %s در %d، %d"),
        RULES_BODY(
                "Reach the opposite edge before your opponents. Tap a highlighted square to move. You may jump an adjacent pawn, or step around it when a wall, board edge or another pawn blocks the landing.\n\nUse the horizontal or vertical tool to preview a wall, then confirm it. Walls cannot cross or overlap, and every player must keep a path to their goal.\n\nTwo players receive 10 walls each. Four players receive 5 each, with clockwise turns: Blue → Red → Green → Gold. Robot games support two players.\n\nLocal games offer undo, redo and hints. Saved files contain the board and settings; online rooms and undo history are not saved.",
                "پیش از حریفان به لبهٔ مقابل برسید. برای حرکت، خانهٔ مشخص‌شده را لمس کنید. می‌توانید از مهرهٔ مجاور بپرید یا اگر دیوار، لبهٔ صفحه یا مهرهٔ دیگری جای فرود را بسته، از کنار آن عبور کنید.\n\nبا ابزار افقی یا عمودی، دیوار را انتخاب و سپس تأیید کنید. دیوارها نباید روی هم یا به‌صورت متقاطع قرار بگیرند و مسیر رسیدن همه به هدف باید باز بماند.\n\nدر بازی دونفره هر نفر ۱۰ دیوار و در بازی چهارنفری هر نفر ۵ دیوار دارد. نوبت چهارنفری ساعت‌گرد است: آبی ← قرمز ← سبز ← طلایی. ربات برای بازی دونفره است.\n\nبرگشت، تکرار و راهنما در بازی محلی فعال‌اند. فایل ذخیره شامل صفحه و تنظیمات است؛ اتاق آنلاین و تاریخچهٔ برگشت ذخیره نمی‌شوند."),
        ORIGINAL_AUTHORS("Original developers", "توسعه‌دهندگان اولیه"),
        ONLINE_NOTICE(
                "No public server is preconfigured. See ONLINE.md in the repository for free-host deployment.",
                "سرور عمومی از قبل تنظیم نشده است؛ راهنمای میزبانی رایگان در ONLINE.md پروژه است."),
        VERSION("Version %s · Android", "نسخهٔ %s · اندروید"),
        RESTORED("Saved local match restored", "بازی محلی ذخیره‌شده بازیابی شد"),
        INVALID_WALL(
                "That wall is not legal. Select another position.",
                "این دیوار مجاز نیست؛ جای دیگری انتخاب کنید."),
        BOT_ERROR(
                "Robot search was interrupted. Use Undo or return to the menu to continue.",
                "جست‌وجوی ربات متوقف شد؛ از برگشت حرکت یا بازگشت به منو استفاده کنید.");

        private final String english;
        private final String persian;

        Key(String english, String persian) {
            this.english = english;
            this.persian = persian;
        }
    }

    private final boolean persian;

    MobileText(boolean persian) {
        this.persian = persian;
    }

    boolean persian() {
        return persian;
    }

    String text(Key key, Object... arguments) {
        return String.format(
                persian ? Locale.forLanguageTag("fa-IR") : Locale.ENGLISH,
                persian ? key.persian : key.english,
                arguments);
    }

    String colorName(int player) {
        return text(new Key[] {Key.BLUE, Key.GREEN, Key.RED, Key.GOLD}[player]);
    }

    String difficulty(int index) {
        return text(new Key[] {Key.EASY, Key.MEDIUM, Key.HARD}[index]);
    }

    String isolate(String userText) {
        return "\u2068" + userText + "\u2069";
    }
}
