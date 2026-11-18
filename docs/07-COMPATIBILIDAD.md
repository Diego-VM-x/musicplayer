# 07 — Compatibilidad

| Parámetro | Valor | Nota |
|---|---|---|
| compileSdk | 35 | Android 15 |
| targetSdk | 35 | Cumple requisitos de Play/FGS |
| minSdk | 24 | Android 7.0 (el original pedía 7.1 / API 25) |
| Kotlin | 2.0.21 | — |
| AGP | 8.7.3 | — |
| JDK | 17 | — |
| Compose BOM | 2024.10.01 | Material3 1.3.1 |
| Media3 | 1.4.1 | ExoPlayer + Session + UI |

## Permisos finales del Manifest (sin anuncios)

```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                 android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" /> <!-- ringtone -->
<uses-permission android:name="android.permission.INTERNET" /> <!-- lyrics online (opcional) -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

> ❌ No se incluyen: `AD_ID`, `ACCESS_ADSERVICES_*`, `BILLING`, `INSTALL_REFERRER`, `C2DM`, Crashlytics/Firebase.

## ABIs

El original incluía `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` por las librerías nativas iJK/MMKV/Crashlytics. Al eliminarlas, **no se necesitan ABIs nativos propios** → APK universal más pequeño.
