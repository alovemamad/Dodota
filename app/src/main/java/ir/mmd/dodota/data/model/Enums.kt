package ir.mmd.dodota.data.model

enum class TransactionType(val title: String) {
    INCOME("درآمد"),
    EXPENSE("هزینه"),
    TRANSFER("انتقال")
}

enum class AccountType(val title: String, val defaultIconName: String) {
    WALLET("کیف پول", "wallet"),
    CASH("پول نقد", "cash"),
    BANK_CARD("کارت بانکی", "card"),
    BANK_ACCOUNT("حساب بانکی", "account"),
    SAVINGS("پس‌انداز", "savings"),
    CUSTOM("حساب دلخواه", "custom")
}

enum class DebtType(val title: String) {
    DEBT_I_OWE("بدهی من"),
    RECEIVABLE_OWED_TO_ME("طلب من")
}

enum class DebtStatus(val title: String) {
    ACTIVE("فعال"),
    SETTLED("تسویه‌شده"),
    OVERDUE("عقب‌افتاده")
}

enum class RecurrenceInterval(val title: String) {
    DAILY("روزانه"),
    WEEKLY("هفتگی"),
    MONTHLY("ماهانه"),
    YEARLY("سالانه"),
    CUSTOM("دوره‌ای")
}

enum class ReminderTargetType(val title: String) {
    DEBT("بدهی/طلب"),
    INSTALLMENT("قسط وام"),
    RECURRING("پرداخت دوره‌ای"),
    BUDGET("هشدار بودجه"),
    CUSTOM("یادآور دلخواه")
}

enum class AssetType(val title: String) {
    GOLD("طلا و سکه"),
    CAR("خودرو"),
    PROPERTY("املاک و زمین"),
    ELECTRONICS("کالای دیجیتال"),
    INVESTMENT("بورس و سرمایه‌گذاری"),
    OTHER("سایر دارایی‌ها")
}

enum class AppCurrency(
    val title: String,
    val unitName: String,
    val code: String,
    val isEnglishDigits: Boolean
) {
    TOMAN("تومان (ارقام فارسی)", "تومان", "IRT", false),
    RIAL("ریال (ارقام فارسی)", "ریال", "IRR", false),
    TOMAN_ENGLISH("تومان با ارقام انگلیسی", "تومان", "IRT_EN", true),
    RIAL_ENGLISH("ریال با ارقام انگلیسی", "ریال", "IRR_EN", true)
}

enum class AppThemeMode(val title: String, val subtitle: String = "") {
    VIP_RED("VIP Red (شیشه‌ای و قرمز اسپرت)", "طراحی اختصاصی مشکی-قرمز با حالت شیشه‌ای لوکس"),
    SYSTEM("خودکار سیستم", "هماهنگ با تنظیمات حالت شب/روز گوشی"),
    LIGHT("روز (روشن)", "تم روشن تمیز و استاندارد"),
    DARK("شب (تاریک)", "تم تیره و مدرن برای محیط‌های کم‌نور"),
    BLUBANK("بلو رویال (ترکیبی)", "ترکیب آبی نئون و سرمه‌ای پریمیوم"),
    EMERALD("زمرد و مینت (ترکیبی)", "ترکیب سبز زمردی و نعنایی شیک"),
    SUNSET("غروب نئونی (ترکیبی)", "ترکیب ارغوانی و نارنجی درخشان"),
    GOLD("طلایی کریپتو (ترکیبی)", "ترکیب طلایی لوکس با پس‌زمینه زغالی"),
    PURPLE("بنفش کهکشانی (ترکیبی)", "ترکیب بنفش سلطنتی و یاسی"),
    BLACK("مشکی اولد (OLED)", "سیاه خالص جهت صرفه‌جویی باتری")
}

enum class PriorityLevel(val title: String, val colorHex: String) {
    URGENT("فوری و ضروری", "#EF4444"),
    HIGH("مهم و اولویت‌دار", "#F59E0B"),
    MEDIUM("عادی", "#3B82F6"),
    LOW("کم‌اهمیت", "#64748B")
}

enum class AppFontFamilyOption(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    VAZIRMATN("وزیرمتن (پیش‌فرض)", "مدرن، خوانا و استاندارد برای محیط‌های مالی", "پیشنهادی"),
    CAIRO("کایرو (Cairo)", "طراحی هندسی، شیک و مدرن مناسب داشبوردهای دیجیتال", "فین‌تک"),
    ALMARAI("المراعی (Almarai)", "ظریف، مینیمال و بسیار تمیز برای اعداد و جداول", "مینیمال"),
    RUBIK("روبیک (Rubik)", "لبه‌های گرد و نرم با حس صمیمی و مدرن", "فانتزی مدرن"),
    LALEZAR("لاله‌زار (Lalezar)", "ضخیم، گرافیکی و پرانرژی برای نمایش درشت و بولد", "گرافیکی"),
    MARKAZI("مرکزی (Markazi)", "کلاسیک، رسمی و مناسب متون طولانی و گزارش‌ها", "رسمی"),
    MIRZA("میرزا (Mirza)", "سبک خوشنویسی و اصیل ایرانی با جلوه هنری", "هنری"),
    NOTO_NASKH("نسخ (Noto Naskh)", "فونت سنتی و مطبوعاتی با خوانایی بسیار بالا", "کلاسیک")
}

enum class AppFontSizePreset(
    val title: String,
    val scale: Float
) {
    EXTRA_SMALL("خیلی کوچک", 0.85f),
    SMALL("کوچک", 0.92f),
    NORMAL("استاندارد", 1.00f),
    MEDIUM_LARGE("نسبتاً درشت", 1.08f),
    LARGE("درشت", 1.16f),
    EXTRA_LARGE("خیلی درشت", 1.25f);

    companion object {
        fun fromScale(scale: Float): AppFontSizePreset {
            return entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: NORMAL
        }
    }
}

