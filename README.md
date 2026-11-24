# Radar Beep Android App

تطبيق أندرويد بلغة Kotlin و Jetpack Compose يعمل في الخلفية لتنبيه السائق بصوت Beep متدرج الحدة والسرعة عند الاقتراب لمسافة 100 متر من رادارات السرعة، ويتوقف تلقائياً عند الابتعاد.

## استخراج ملف APK من الموبايل عبر GitHub Actions (بدون كمبيوتر)
1. فك الضغط عن `RadarBeepApp-v2.zip` على هاتفك.
2. أنشئ مستودعاً جديداً (Repository) على حسابك في **GitHub**.
3. ارفع جميع الملفات والمجلدات الموجودة داخل `RadarBeepApp` (بما فيها مجلد `.github/workflows/build-apk.yml`).
4. افتح تبويب **Actions** في المستودع، واختر **Build Android APK** ثم اضغط **Run workflow** (أو سيبدأ تلقائياً عند الرفع).
5. بعد انتهاء البناء (حوالي دقيقتين)، انزل إلى قسم **Artifacts** وحمّل **`RadarBeep-debug-apk`** لتحصل على ملف **`app-debug.apk`** جاهزاً للتثبيت.
