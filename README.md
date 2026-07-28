# AppGuard / שומר אפליקציות

אפליקציית אנדרואיד 17 (API 37) ברמת הגנה דומה ל־Kaspersky Safe Kids **בלי Device Owner**.

## יכולות

- רשימת אפליקציות מותרות (Whitelist)
- חסימת פתיחת כל אפליקציה שאינה מאושרת (Accessibility + Usage Access + Overlay)
- זיהוי התקנת/הסרת אפליקציות בזמן אמת
- חסימת שימוש באפליקציות חדשות מיד אחרי התקנה
- קוד ניהול לכניסה ושינוי הגדרות
- הקשיית הסרה עם Device Administrator
- הפעלה מחדש אחרי Boot (`BOOT_COMPLETED` + Foreground Service)

## הרשאות באשף ההגדרה

| הרשאה | למה |
|---|---|
| Accessibility | זיהוי/סגירת אפליקציה + חסימת מסכי התקנה |
| Usage Access | ידיעת האפליקציה בחזית |
| Device Admin | הקשיית הסרה |
| Display over other apps | מסך חסימה מיידי מעל הכל |
| Ignore Battery Optimizations | לא להרוג את השירות ברקע |
| Notifications | התראת Foreground Service |
| OEM Auto-start | Xiaomi/Huawei/Oppo/Vivo וכו׳ |
| BOOT_COMPLETED | עלייה אחרי אתחול |
| PACKAGE_ADDED/REMOVED | זיהוי התקנות/הסרות |

## בנייה

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## הפעלה

1. התקן → הגדר קוד ניהול
2. באשף ההרשאות – הפעל הכול
3. במסך הראשי – הדלק רמה 1 / רמה 2 ונהל רשימה מותרת
4. להסרה: הגדרות → שחרר נעילת הסרה (עם הקוד)
